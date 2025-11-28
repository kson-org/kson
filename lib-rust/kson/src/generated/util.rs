#![allow(unused_variables)]

use std::cell::RefCell;
use std::ffi::CStr;
use std::sync::{Arc, LazyLock};

use super::sys::*;

struct Jvm(*mut JavaVM);

// Safety: the JNI guarantees that the `JavaVM` pointer is thread-safe.
unsafe impl Send for Jvm {}
unsafe impl Sync for Jvm {}


pub struct OwnedKotlinPtr {
    pub inner: jobject,
}

impl Drop for OwnedKotlinPtr {
    fn drop(&mut self) {
        let (env, _detach_guard) = attach_thread_to_java_vm();
        unsafe {
            let delete = (**env).DeleteGlobalRef.unwrap();
            delete(env, self.inner)
        }
    }
}

// Safety: the `OwnedKotlinPtr` struct is backed by a `jobject`. Leaving aside the object's functions,
// the pointer itself is thread-safe. As for any functions provided by the object behind the pointer,
// developers carry the responsibility of ensuring their thread-safety. Here we assume there are no bugs
// (otherwise we'd have to wrap each and every `jobject` in a mutex, which sounds way too defensive and
// might even cause deadlocks or other issues).
unsafe impl Send for OwnedKotlinPtr {}
unsafe impl Sync for OwnedKotlinPtr {}


#[derive(Clone)]
pub(super) struct KotlinPtr {
    pub(super) inner: Arc<OwnedKotlinPtr>,
}

impl AsKotlinObject for KotlinPtr {
    fn as_kotlin_object(&self) -> jobject {
        self.inner.inner
    }
}

impl ToKotlinObject for KotlinPtr {
    fn to_kotlin_object(&self) -> KotlinPtr {
        self.clone()
    }
}

static JVM: LazyLock<Jvm> = LazyLock::new(|| {
    let mut jvm = std::ptr::null_mut();
    let mut env = std::ptr::null_mut();
    let mut args = JavaVMInitArgs {
        version: 0x00010008, // JNI_VERSION_1_8
        nOptions: 0,
        options: std::ptr::null_mut(),
        ignoreUnrecognized: 1
    };

    if unsafe { JNI_CreateJavaVM(&mut jvm as *mut _, &mut env as *mut _ as *mut _, &mut args as *mut _ as *mut _) } != 0 {
        panic!("failed to load JNI");
    }

    Jvm(jvm)
});

thread_local! {
    static ATTACHED_JNI_ENV: RefCell<Option<*mut JNIEnv>> = RefCell::new(None);
}

macro_rules! call_jvm_function {
    ($util:ident, $class_name:expr, $method_name:expr, $method_signature:expr, $call_fn:ident, $obj:expr $(, $arg:expr )* $(,)?) => {{
        let (env, _detach_guard) = $util::attach_thread_to_java_vm();
        let class = $util::get_class(env, $class_name);
        let method = $util::get_method(env, class.as_kotlin_object(), $method_name, $method_signature);
        let result = unsafe { (**env).$call_fn.unwrap()(env, $obj, method,
            $($arg, )*
        )};
        $util::panic_upon_exception(env);
        result
    }};
}

pub struct DetachGuard {
    should_detach: bool
}

impl Drop for DetachGuard {
    fn drop(&mut self) {
        if self.should_detach {
            detach_thread_from_java_vm();
        }
    }
}

pub(super) fn attach_thread_to_java_vm() -> (*mut JNIEnv, DetachGuard) {
    if let Some(env) = ATTACHED_JNI_ENV.with_borrow(|maybe_env| maybe_env.clone()) {
        return (env, DetachGuard { should_detach: false });
    }

    let mut env = std::ptr::null_mut();
    let attach_result = unsafe {
        let attach = (**JVM.0).AttachCurrentThread.unwrap();
        attach(JVM.0, &mut env as *mut _ as *mut _, std::ptr::null_mut())
    };
    if attach_result != 0 {
        panic!("failed to attach current thread to JNI");
    }

    ATTACHED_JNI_ENV.with_borrow_mut(|maybe_env| *maybe_env = Some(env));
    (env, DetachGuard { should_detach: true })
}

pub(super) fn detach_thread_from_java_vm() {
    let detach_result = unsafe {
        let detach = (**JVM.0).DetachCurrentThread.unwrap();
        detach(JVM.0)
    };

    if detach_result != 0 {
        panic!("failed to detach current thread from JNI");
    }

    ATTACHED_JNI_ENV.with_borrow_mut(|maybe_env| *maybe_env = None);
}

pub(super) fn get_class(env: *mut JNIEnv, name: &CStr) -> OwnedKotlinPtr {
    let class = unsafe {
        let find_class = (**env).FindClass.unwrap();
        find_class(env, name.as_ptr())
    };
    panic_upon_exception(env);
    to_global_ref(env, class)
}

fn delete_local_ref(env: *mut JNIEnv, local_ref: jobject) {
    unsafe {
        let delete_local_ref = (**env).DeleteLocalRef.unwrap();
        delete_local_ref(env, local_ref);
    }
    panic_upon_exception(env);
}

fn to_global_ref(env: *mut JNIEnv, local_ref: jobject) -> OwnedKotlinPtr {
    let global_ref = unsafe {
        let new_global_ref = (**env).NewGlobalRef.unwrap();
        new_global_ref(env, local_ref)
    };
    panic_upon_exception(env);
    delete_local_ref(env, local_ref);
    OwnedKotlinPtr { inner: global_ref }
}

pub(super) fn to_gc_global_ref(env: *mut JNIEnv, local_ref: jobject) -> KotlinPtr {
    KotlinPtr {
        inner: Arc::new(to_global_ref(env, local_ref))
    }
}

pub(super) fn access_static_field(class_name: &CStr, field_name: &CStr, field_signature: &CStr) -> KotlinPtr {
    let (env, _detach_guard) = attach_thread_to_java_vm();
    let class = get_class(env, class_name);
    let static_field_id = unsafe {
        let get_static_field_id = (**env).GetStaticFieldID.unwrap();
        get_static_field_id(env, class.as_kotlin_object(), field_name.as_ptr(), field_signature.as_ptr())
    };
    panic_upon_exception(env);
    let field_value = unsafe {
        let get_static_field = (**env).GetStaticObjectField.unwrap();
        get_static_field(env, class.as_kotlin_object(), static_field_id)
    };
    panic_upon_exception(env);
    to_gc_global_ref(env, field_value)
}

pub(super) fn get_method(env: *mut JNIEnv, class: jobject, name: &CStr, signature: &CStr) -> jmethodID {
    let method_id = unsafe {
        let get_method_id = (**env).GetMethodID.unwrap();
        get_method_id(env, class, name.as_ptr(), signature.as_ptr())
    };
    panic_upon_exception(env);
    method_id
}

pub(super) fn panic_upon_exception(env: *mut JNIEnv) {
    let has_exception = unsafe {
        let exception_check = (**env).ExceptionCheck.unwrap();
        exception_check(env) == 1
    };

    if has_exception {
        unsafe {
            let exception_describe = (**env).ExceptionDescribe.unwrap();
            exception_describe(env);

            let exception_clear = (**env).ExceptionClear.unwrap();
            exception_clear(env);
        }

        panic!("entered unreachable code (uncaught exception after JNI call)")
    }

}

pub(super) fn call_to_string(class_name: &CStr, obj: &impl AsKotlinObject) -> String {
    let (_, _detach_guard) = attach_thread_to_java_vm();
    let result_local = call_jvm_function!(self, class_name, c"toString", c"()Ljava/lang/String;", CallObjectMethod, obj.as_kotlin_object());
    String::from_kotlin_object(result_local)
}

impl AsKotlinObject for OwnedKotlinPtr {
    fn as_kotlin_object(&self) -> jobject {
        self.inner
    }
}

pub(super) trait FromKotlinObject {
    fn from_kotlin_object(obj: jobject) -> Self;
}

impl<T: FromKotlinObject> FromKotlinObject for Option<T> {
    fn from_kotlin_object(obj: jobject) -> Option<T> {
        if obj.is_null() {
            None
        } else {
            Some(T::from_kotlin_object(obj))
        }
    }
}

impl FromKotlinObject for String {
    fn from_kotlin_object(obj: jobject) -> String {
        let (env, _detach_guard) = attach_thread_to_java_vm();
        let jvm_chars = unsafe {
            let get_string_chars = (**env).GetStringChars.unwrap();
            get_string_chars(env, obj, std::ptr::null_mut())
        };
        panic_upon_exception(env);

        let jvm_string_length = unsafe {
            let get_string_length = (**env).GetStringLength.unwrap();
            get_string_length(env, obj)
        };
        panic_upon_exception(env);

        let jvm_string = unsafe { std::slice::from_raw_parts(jvm_chars, jvm_string_length as usize) };
        let rust_string = String::from_utf16_lossy(jvm_string);

        unsafe {
            let release_string_chars = (**env).ReleaseStringChars.unwrap();
            release_string_chars(env, obj, jvm_chars);
        }
        panic_upon_exception(env);
        rust_string
    }
}

pub(super) trait ToKotlinObject {
    fn to_kotlin_object(&self) -> KotlinPtr;
}

impl ToKotlinObject for String {
    fn to_kotlin_object(&self) -> KotlinPtr {
        self.as_str().to_kotlin_object()
    }
}

impl ToKotlinObject for &'_ str {
    fn to_kotlin_object(&self) -> KotlinPtr {
        let utf16_bytes: Vec<_> = self.encode_utf16().collect();
        let (env, _detach_guard) = attach_thread_to_java_vm();
        let jvm_string = unsafe {
            let new_string = (**env).NewString.unwrap();
            new_string(env, utf16_bytes.as_ptr(), utf16_bytes.len() as i32)
        };
        panic_upon_exception(env);
        to_gc_global_ref(env, jvm_string)
    }
}

impl<T: ToKotlinObject> ToKotlinObject for &T {
    fn to_kotlin_object(&self) -> KotlinPtr {
        (*self).to_kotlin_object()
    }
}

pub(super) trait AsKotlinObject {
    fn as_kotlin_object(&self) -> jobject;
}

impl AsKotlinObject for jobject {
    fn as_kotlin_object(&self) -> jobject {
        *self
    }
}

pub(super) fn equals<T: AsKotlinObject, U: AsKotlinObject>(obj: T, other: U) -> bool {
    let is_equal = call_jvm_function!(self, c"java/lang/Object", c"equals", c"(Ljava/lang/Object;)Z", CallIntMethod, obj.as_kotlin_object(), other.as_kotlin_object());
    if is_equal == 0 { false } else { true }
}

pub(super) fn apply_hash_code<T: AsKotlinObject, H: std::hash::Hasher>(obj: T, hasher: &mut H) {
    let kotlin_hash = call_jvm_function!(self, c"java/lang/Object", c"hashCode", c"()I", CallIntMethod, obj.as_kotlin_object());
    hasher.write_i32(kotlin_hash);
}

pub(super) fn enum_name(obj: &impl AsKotlinObject) -> String {
    let name_local = call_jvm_function!(self, c"java/lang/Enum", c"name", c"()Ljava/lang/String;", CallObjectMethod, obj.as_kotlin_object());
    String::from_kotlin_object(name_local)
}

pub(super) fn class_name(obj: jobject) -> String {
    if obj.is_null() {
        panic!("entered unreachable code: attempted to obtain class name of null object");
    }

    let (env, _detach_guard) = attach_thread_to_java_vm();
    let class = unsafe {
        let get_class = (**env).GetObjectClass.unwrap();
        get_class(env, obj)
    };
    panic_upon_exception(env);

    let name_local = call_jvm_function!(self, c"java/lang/Class", c"getName", c"()Ljava/lang/String;", CallObjectMethod, class);
    let rust_string = String::from_kotlin_object(name_local);
    delete_local_ref(env, class);
    rust_string
}

pub(super) fn enum_ordinal(class_name: &CStr, obj: jobject) -> i32 {
    call_jvm_function!(self, class_name, c"ordinal", c"()I", CallIntMethod, obj)
}

pub(super) fn to_kotlin_list<T: ToKotlinObject>(list: &[T]) -> KotlinPtr {
    unimplemented!()
}

pub(super) fn from_kotlin_list<T: FromKotlinObject>(list: jobject) -> Vec<T> {
    let mut elements = Vec::new();

    let (env, _detach_guard) = attach_thread_to_java_vm();
    let iterator_class_name = c"java/util/Iterator";
    let iterator_class = get_class(env, iterator_class_name);
    let iterator = call_jvm_function!(self, c"java/util/List", c"iterator", c"()Ljava/util/Iterator;", CallObjectMethod, list);
    loop {
        let has_next = call_jvm_function!(self, iterator_class_name, c"hasNext", c"()Z", CallBooleanMethod, iterator.as_kotlin_object());
        if has_next == 0 {
            break;
        }

        let item_local = call_jvm_function!(self, iterator_class_name, c"next", c"()Ljava/lang/Object;", CallObjectMethod, iterator.as_kotlin_object());
        elements.push(T::from_kotlin_object(item_local));
    }

    elements
}

pub(super) fn from_kotlin_value_map<
    K: FromKotlinObject + Eq + std::hash::Hash,
    V: FromKotlinObject,
>(
    map: jobject
) -> std::collections::HashMap<K, V> {
    let mut elements = std::collections::HashMap::new();

    let (env, _detach_guard) = attach_thread_to_java_vm();

    // Obtain iterator
    let entry_set = call_jvm_function!(self, c"java/util/Map", c"entrySet", c"()Ljava/util/Set;", CallObjectMethod, map);
    let iterator = call_jvm_function!(self, c"java/util/Set", c"iterator", c"()Ljava/util/Iterator;", CallObjectMethod, entry_set);

    let iterator_class_name = c"java/util/Iterator";
    let iterator_class = get_class(env, iterator_class_name);
    let pair_class_name = c"java/util/Map$Entry";
    loop {
        let has_next = call_jvm_function!(self, iterator_class_name, c"hasNext", c"()Z", CallBooleanMethod, iterator.as_kotlin_object());
        if has_next == 0 {
            break;
        }

        let pair_local = call_jvm_function!(self, iterator_class_name, c"next", c"()Ljava/lang/Object;", CallObjectMethod, iterator.as_kotlin_object());
        if pair_local.is_null() {
            break
        }

        let key = call_jvm_function!(self, pair_class_name, c"getKey", c"()Ljava/lang/Object;", CallObjectMethod, pair_local);
        let value = call_jvm_function!(self, pair_class_name, c"getValue", c"()Ljava/lang/Object;", CallObjectMethod, pair_local);
        elements.insert(K::from_kotlin_object(key), V::from_kotlin_object(value));
    }

    elements
}

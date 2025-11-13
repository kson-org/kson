use crate::KSON_SYMBOLS;
use kson_sys::*;

pub(crate) struct OwnedKotlinPtr {
    pub(crate) inner: kson_KNativePtr,
}

impl Drop for OwnedKotlinPtr {
    fn drop(&mut self) {
        unsafe { KSON_SYMBOLS.DisposeStablePointer.unwrap()(self.inner) };
    }
}

// Safety: the `OwnedKotlinPtr` struct is backed by a `kson_KNativePtr`, which points to an opaque
// type corresponding to a Kotlin object. Leaving aside the object's functions, the pointer itself
// can only be used to call the `DisposeStablePointer` and `IsInstance` functions, both of which we
// consider to be thread-safe (this is not explicitly documented anywhere, but it seems logical
// based on the function names, their use-cases and a cursory reading of the Kotlin runtime's source
// code). As for any functions provided by the object behind the pointer, we kson developers carry
// the responsibility of ensuring their thread-safety. Here we assume there are no bugs (otherwise
// we'd have to wrap each and every `kson_KNativePtr` in a mutex, which sounds way too defensive).
unsafe impl Send for OwnedKotlinPtr {}
unsafe impl Sync for OwnedKotlinPtr {}

pub(crate) struct KotlinType {
    pub(crate) inner: *const kson_KType,
}

// Safety: the `KotlinType` struct is backed by a `*const kson_KType`, which points to an opaque
// struct. We were unable to find any authoritative sources regarding the thread-safety of the
// actual implementation behind `kson_KType`, but based on its name, its use-cases and a cursory
// reading of the Kotlin runtime's source code, we believe it's immutable and therefore thread-safe.
unsafe impl Send for KotlinType {}
unsafe impl Sync for KotlinType {}

pub(crate) trait FromKotlinObject {
    fn from_kotlin_object(obj: kson_KNativePtr) -> Self;
}

pub(crate) trait ToKotlinObject {
    fn to_kotlin_object(&self) -> kson_KNativePtr;
}

impl ToKotlinObject for kson_KNativePtr {
    fn to_kotlin_object(&self) -> kson_KNativePtr {
        *self
    }
}

impl<T: ToKotlinObject> ToKotlinObject for &T {
    fn to_kotlin_object(&self) -> kson_KNativePtr {
        (*self).to_kotlin_object()
    }
}

pub(crate) fn to_string<T: ToKotlinObject>(x: T) -> String {
    let helper_instance = unsafe {
        KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .AnyHelper
            ._instance
            .unwrap()()
    };
    let f = KSON_SYMBOLS
        .kotlin
        .root
        .org
        .kson
        .AnyHelper
        .toString
        .unwrap();
    let result = unsafe {
        f(
            helper_instance,
            kson_sys::kson_kref_kotlin_Any {
                pinned: x.to_kotlin_object(),
            },
        )
    };
    from_kotlin_string(result)
}

pub(crate) fn equals<T: ToKotlinObject, U: ToKotlinObject>(x: T, y: U) -> bool {
    let helper_instance = unsafe {
        KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .AnyHelper
            ._instance
            .unwrap()()
    };
    let f = KSON_SYMBOLS.kotlin.root.org.kson.AnyHelper.equals.unwrap();
    unsafe {
        f(
            helper_instance,
            kson_sys::kson_kref_kotlin_Any {
                pinned: x.to_kotlin_object(),
            },
            kson_sys::kson_kref_kotlin_Any {
                pinned: y.to_kotlin_object(),
            },
        )
    }
}

pub(crate) fn apply_hash_code<T: ToKotlinObject, H: std::hash::Hasher>(x: T, hasher: &mut H) {
    let helper_instance = unsafe {
        KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .AnyHelper
            ._instance
            .unwrap()()
    };
    let f = KSON_SYMBOLS
        .kotlin
        .root
        .org
        .kson
        .AnyHelper
        .hashCode
        .unwrap();
    let kotlin_hash = unsafe {
        f(
            helper_instance,
            kson_sys::kson_kref_kotlin_Any {
                pinned: x.to_kotlin_object(),
            },
        )
    };
    hasher.write_i32(kotlin_hash);
}

pub(crate) fn enum_name<T: ToKotlinObject>(value: T) -> String {
    let ptr = value.to_kotlin_object();
    let helper_instance = unsafe {
        KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .EnumHelper
            ._instance
            .unwrap()()
    };
    let value = kson_kref_kotlin_Enum { pinned: ptr };
    let name = unsafe {
        KSON_SYMBOLS.kotlin.root.org.kson.EnumHelper.name.unwrap()(helper_instance, value)
    };

    unsafe { KSON_SYMBOLS.DisposeStablePointer.unwrap()(ptr) };
    from_kotlin_string(name)
}

pub(crate) fn enum_ordinal<T: ToKotlinObject>(value: T) -> i32 {
    let ptr = value.to_kotlin_object();
    let helper_instance = unsafe {
        KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .EnumHelper
            ._instance
            .unwrap()()
    };
    let value = kson_kref_kotlin_Enum { pinned: ptr };
    let ordinal = unsafe {
        KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .EnumHelper
            .ordinal
            .unwrap()(helper_instance, value)
    };

    unsafe { KSON_SYMBOLS.DisposeStablePointer.unwrap()(ptr) };
    ordinal
}

pub(crate) fn to_kotlin_string(s: &str) -> std::ffi::CString {
    std::ffi::CString::new(s).unwrap()
}

pub(crate) fn from_kotlin_string(result: *const i8) -> String {
    let result_string = unsafe { std::ffi::CStr::from_ptr(result) };
    let result_string = result_string.to_string_lossy().to_string();
    unsafe { super::KSON_SYMBOLS.DisposeString.unwrap()(result) };
    result_string
}

pub(crate) fn from_kotlin_list<T: FromKotlinObject>(
    list: kson_kref_kotlin_collections_List,
) -> Vec<T> {
    let mut vec = Vec::new();
    let iterator = unsafe {
        KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .SimpleListIterator
            .SimpleListIterator
            .unwrap()(list)
    };
    loop {
        let next = unsafe {
            KSON_SYMBOLS
                .kotlin
                .root
                .org
                .kson
                .SimpleListIterator
                .next
                .unwrap()(iterator)
        };
        if next.pinned.is_null() {
            break;
        }

        vec.push(FromKotlinObject::from_kotlin_object(next.pinned));
    }

    unsafe { KSON_SYMBOLS.DisposeStablePointer.unwrap()(iterator.pinned) };
    vec
}

pub(crate) fn from_kotlin_string_map<V: FromKotlinObject>(
    map: kson_kref_kotlin_collections_Map,
) -> std::collections::HashMap<String, V> {
    let mut hash_map = std::collections::HashMap::new();
    let iterator = unsafe {
        KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .SimpleMapIterator
            .SimpleMapIterator
            .unwrap()(map)
    };
    loop {
        let next = unsafe {
            KSON_SYMBOLS
                .kotlin
                .root
                .org
                .kson
                .SimpleMapIterator
                .next
                .unwrap()(iterator)
        };
        if next.pinned.is_null() {
            break;
        }

        let key = unsafe {
            KSON_SYMBOLS
                .kotlin
                .root
                .org
                .kson
                .SimpleMapEntry
                .get_key
                .unwrap()(next)
        };

        // Use AnyHelper to convert kotlin.Any key to String
        let key_string = to_string(key.pinned);

        let value = unsafe {
            KSON_SYMBOLS
                .kotlin
                .root
                .org
                .kson
                .SimpleMapEntry
                .get_value
                .unwrap()(next)
        };

        hash_map.insert(
            key_string,
            FromKotlinObject::from_kotlin_object(value.pinned),
        );

        unsafe { KSON_SYMBOLS.DisposeStablePointer.unwrap()(next.pinned) };
    }

    unsafe { KSON_SYMBOLS.DisposeStablePointer.unwrap()(iterator.pinned) };
    hash_map
}

#[derive(Clone)]
pub(crate) struct KsonPtr {
    pub(crate) inner: std::sync::Arc<OwnedKotlinPtr>,
}

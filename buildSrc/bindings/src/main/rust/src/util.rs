use crate::kson_ffi::*;
use crate::KSON_SYMBOLS;

pub(crate) struct KotlinType {
    pub(crate) inner: *const kson_KType
}

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

pub(crate) fn enum_name<T: ToKotlinObject>(value: T) -> String {
    let ptr = value.to_kotlin_object();
    let helper_instance = unsafe { KSON_SYMBOLS.kotlin.root.org.kson.EnumHelper._instance.unwrap()() };
    let value = kson_kref_kotlin_Enum { pinned: ptr };
    let name = unsafe { KSON_SYMBOLS.kotlin.root.org.kson.EnumHelper.name.unwrap()(helper_instance, value) };

    unsafe { KSON_SYMBOLS.DisposeStablePointer.unwrap()(ptr) };
    from_kotlin_string(name)
}

pub(crate) fn enum_ordinal<T: ToKotlinObject>(value: T) -> i32 {
    let ptr = value.to_kotlin_object();
    let helper_instance = unsafe { KSON_SYMBOLS.kotlin.root.org.kson.EnumHelper._instance.unwrap()() };
    let value = kson_kref_kotlin_Enum { pinned: ptr };
    let ordinal = unsafe { KSON_SYMBOLS.kotlin.root.org.kson.EnumHelper.ordinal.unwrap()(helper_instance, value) };

    unsafe { KSON_SYMBOLS.DisposeStablePointer.unwrap()(ptr) };
    ordinal
}

pub(crate) fn to_kotlin_string(s: &str) -> std::ffi::CString {
    // Note: we are responsible for deallocating the string
    std::ffi::CString::new(s).unwrap()
}

pub(crate) fn from_kotlin_string(result: *const i8) -> String {
    let result_string = unsafe { std::ffi::CStr::from_ptr(result) };
    let result_string = result_string.to_string_lossy().to_string();
    unsafe { super::KSON_SYMBOLS.DisposeString.unwrap()(result) };
    result_string
}

pub(crate) fn from_kotlin_list<T: FromKotlinObject>(list: kson_kref_kotlin_collections_List) -> Vec<T> {
    let mut vec = Vec::new();
    let iterator = unsafe { KSON_SYMBOLS.kotlin.root.org.kson.SimpleListIterator.SimpleListIterator.unwrap()(list) };
    loop {
        let next = unsafe { KSON_SYMBOLS.kotlin.root.org.kson.SimpleListIterator.next.unwrap()(iterator) };
        if next.pinned.is_null() {
            break;
        }

        vec.push(FromKotlinObject::from_kotlin_object(next.pinned));
    }

    unsafe { KSON_SYMBOLS.DisposeStablePointer.unwrap()(iterator.pinned) };
    vec
}

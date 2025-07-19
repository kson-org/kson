use crate::kson_ffi::*;
use crate::KSON_SYMBOLS;

pub(crate) trait FromKotlinObject {
    fn from_kotlin_object(obj: kson_KNativePtr) -> Self;
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

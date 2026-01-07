from __future__ import annotations

import sys
import threading
from typing import cast, Any, Callable, Dict, List, Optional, Type, TypeAlias
from cffi import FFI
from pathlib import Path
from enum import Enum

tls = threading.local()
tls.attached_jni_thread = None

JNI_OK = 0

# Utility functions
def _raise_exception_if_any(env: Any):
    if env[0].ExceptionCheck(env) == 1:
        env[0].ExceptionDescribe(env)
        env[0].ExceptionClear(env)
        raise Exception("entered unreachable code")

def _raise_if_null(env: Any, ptr: Any):
    if ptr == ffi.NULL:
        _raise_exception_if_any(env)
        raise Exception("entered unreachable code")

# Initialize library
ffi = FFI()

package_dir = Path(__file__).parent
with open(package_dir / "jni_simplified.h", "r") as f:
    header = f.read()
ffi.cdef(header)

LIBRARY_NAMES: Dict[str, str] = {
    "win32": "kson.dll",
    "darwin": "libkson.dylib",
    "linux": "libkson.so",
}

lib_name = LIBRARY_NAMES.get(sys.platform)
if lib_name is None:
    raise RuntimeError(f"Unsupported platform: {sys.platform}")

lib: Any = ffi.dlopen(str(package_dir / lib_name))
env_ptr = ffi.new("JNIEnv **")
jvm_ptr = ffi.new("JavaVM **")

vm_args = ffi.new("JavaVMInitArgs *")
vm_args[0].version = 0x00010008  # JNI_VERSION_1_8
vm_args[0].nOptions = 0
vm_args[0].options = ffi.NULL
vm_args[0].ignoreUnrecognized = 1  # JNI_TRUE

if lib.JNI_CreateJavaVM(jvm_ptr, ffi.cast("void **", env_ptr), vm_args) != 0:
    raise Exception("failed to load kson dynamic library")

jvm = ffi.gc(jvm_ptr[0], lambda x: x[0].DestroyJavaVM(x))

###############
# JNI Helpers #
###############

def _attach_jni_thread() -> Any:
    if tls.attached_jni_thread:
        return tls.attached_jni_thread

    env_ptr = ffi.new("JNIEnv **")
    if jvm[0].AttachCurrentThread(jvm, ffi.cast("void **", env_ptr), ffi.NULL) != JNI_OK:
        raise RuntimeError("failed to attach JNI thread")

    tls.attached_jni_thread = env_ptr[0]
    return env_ptr[0]

def _detach_jni_thread():
    if jvm[0].DetachCurrentThread(jvm) != JNI_OK:
        raise RuntimeError("failed to detach JNI thread")
    tls.attached_jni_thread = None

def _delete_local_ref(env, jni_ref: Any):
    env[0].DeleteLocalRef(env, ffi.cast("jobject", jni_ref))

def _delete_global_ref(jni_ref):
    should_detach = tls.attached_jni_thread is None
    env = _attach_jni_thread()
    env[0].DeleteGlobalRef(env, ffi.cast("jobject", jni_ref))
    if should_detach:
        _detach_jni_thread()

def _to_gc_global_ref(env, jni_ref: Any) -> Any:
    global_jni_ref = env[0].NewGlobalRef(env, jni_ref)
    _delete_local_ref(env, jni_ref)
    return ffi.gc(global_jni_ref, _delete_global_ref)

def _get_class(env, class_name: bytes) -> Any:
    class_name_cstr = ffi.new("char[]", class_name)
    clazz = env[0].FindClass(env, class_name_cstr)
    _raise_if_null(env, clazz)
    return _to_gc_global_ref(env, clazz)

def _get_method(env, clazz: Any, method_name: bytes, method_signature: bytes) -> Any:
    method_name_cstr = ffi.new("char[]", method_name)
    method_sig_cstr = ffi.new("char[]", method_signature)
    method = env[0].GetMethodID(env, clazz, method_name_cstr, method_sig_cstr)
    _raise_if_null(env, method)
    return method

def _construct(class_name: bytes, constructor_signature: bytes, args: Any) -> Any:
    env = _attach_jni_thread()
    clazz = _get_class(env, class_name)
    constructor = _get_method(env, clazz, b"<init>", constructor_signature)
    jni_ref = env[0].NewObject(env, clazz, constructor, *args)
    _raise_exception_if_any(env)
    jni_ref_global = _to_gc_global_ref(env, jni_ref)
    _detach_jni_thread()
    return jni_ref_global

def _access_static_field(class_name: bytes, field_name: bytes, field_type: bytes) -> Any:
    env = _attach_jni_thread()
    c = _get_class(env, class_name)
    signature_cstr = ffi.new("char[]", field_type)
    field_name_cstr = ffi.new("char[]", field_name)

    # Get static id
    field = env[0].GetStaticFieldID(env, c, field_name_cstr, signature_cstr)
    _raise_if_null(env, field)

    # Access field
    field_value = _to_gc_global_ref(env, env[0].GetStaticObjectField(env, c, field))
    _raise_if_null(env, field_value)
    _detach_jni_thread()
    return field_value

def _call_method_raw(env: Any, class_name: bytes, jni_ref: Any, func_name: bytes, func_signature: bytes, jni_call_name: str, args: List[Any]) -> Any:
    clazz = _get_class(env, class_name)
    method = _get_method(env, clazz, func_name, func_signature)
    result = getattr(env[0], f"Call{jni_call_name}")(env, jni_ref, method, *args)
    _raise_exception_if_any(env)
    return result

def _call_method(class_name: bytes, jni_ref: Any, func_name: bytes, func_signature: bytes, jni_call_name: str, args: List[Any]) -> Any:
    env = _attach_jni_thread()
    result = _call_method_raw(env, class_name, jni_ref, func_name, func_signature, jni_call_name, args)
    if jni_call_name == "ObjectMethod":
        result = _to_gc_global_ref(env, result)
    _detach_jni_thread()
    return result

def _python_str_to_java_string(s: str) -> Any:
    utf16_bytes = s.encode("utf-16-le")
    utf16_str_len = len(utf16_bytes) / 2
    if utf16_str_len.is_integer():
        utf16_str_len = int(utf16_str_len)
    else:
        raise RuntimeError("entered unreachable code: raw string length was not divisible by 2")
    utf16_str = ffi.new("char[]", utf16_bytes)

    env = _attach_jni_thread()
    jni_ref = env[0].NewString(env, ffi.cast("jchar *", utf16_str), utf16_str_len)
    _raise_if_null(env, jni_ref)
    jni_ref = _to_gc_global_ref(env, jni_ref)
    _detach_jni_thread()
    return jni_ref

def _java_string_to_python_str(jni_ref: Any) -> str:
    env = _attach_jni_thread()
    native_chars = env[0].GetStringChars(env, jni_ref, ffi.NULL)
    _raise_if_null(env, native_chars)
    native_chars_byte_len = env[0].GetStringLength(env, jni_ref) * 2
    python_str = bytes(cast(Any, ffi.buffer(native_chars, native_chars_byte_len))).decode("utf-16-le", "strict")
    env[0].ReleaseStringChars(env, jni_ref, native_chars)
    _detach_jni_thread()
    return python_str

def _jni_class_name(jni_ref: Any):
    if jni_ref == ffi.NULL:
        raise RuntimeError("entered unreachable code: attempted to obtain class name of null object")

    env = _attach_jni_thread()
    clazz = env[0].GetObjectClass(env, jni_ref)
    _raise_if_null(env, clazz)
    name_local = _call_method_raw(env, b"java/lang/Class", clazz, b"getName", b"()Ljava/lang/String;", "ObjectMethod", [])
    name = _to_gc_global_ref(env, name_local)
    _delete_local_ref(env, clazz)
    _detach_jni_thread()
    return _java_string_to_python_str(name)

def _from_kotlin_object(python_class, jni_ref):
    obj = object.__new__(python_class)
    obj._jni_ref = jni_ref
    return obj

def _from_kotlin_list(
    jni_ref: Any, wrap_item_fn: Callable[[Any], Any]
) -> List[Any]:
    python_list: List[Any] = []
    iterator_class_name = b"java/util/Iterator"
    iterator = _call_method(b"java/util/List", jni_ref, b"iterator", b"()Ljava/util/Iterator;", "ObjectMethod", [])
    while True:
        has_next = _call_method(iterator_class_name, iterator, b"hasNext", b"()Z", "BooleanMethod", [])
        if has_next == 0:
            break

        item_ref = _call_method(iterator_class_name, iterator, b"next", b"()Ljava/lang/Object;", "ObjectMethod", [])
        python_list.append(wrap_item_fn(item_ref))

    return python_list

def _to_kotlin_list(list: List[Any]) -> Any:
    raise RuntimeError("not implemented")

def _to_kotlin_map(list: Dict[Any, Any]) -> Any:
    raise RuntimeError("not implemented")

def _from_kotlin_map(
    jni_ref: Any,
    wrap_key_fn: Callable[[Any], Any],
    wrap_value_fn: Callable[[Any], Any]
) -> Dict[Any, Any]:
    python_dict: dict[Any, Any] = {}
    entry_set = _call_method(b"java/util/Map", jni_ref, b"entrySet", b"()Ljava/util/Set;", "ObjectMethod", [])
    iterator = _call_method(b"java/util/Set", entry_set, b"iterator", b"()Ljava/util/Iterator;", "ObjectMethod", [])

    iterator_class_name = b"java/util/Iterator"
    pair_class_name = b"java/util/Map$Entry"
    while True:
        has_next = _call_method(iterator_class_name, iterator, b"hasNext", b"()Z", "BooleanMethod", [])
        if has_next == 0:
            break

        pair_ref = _call_method(iterator_class_name, iterator, b"next", b"()Ljava/lang/Object;", "ObjectMethod", [])
        _key_ref =  _call_method(pair_class_name, pair_ref, b"getKey", b"()Ljava/lang/Object;", "ObjectMethod", [])
        _value_ref =  _call_method(pair_class_name, pair_ref, b"getValue", b"()Ljava/lang/Object;", "ObjectMethod", [])
        python_dict[wrap_key_fn(_key_ref)] = wrap_value_fn(_value_ref)

    return python_dict

############
# Wrappers #
############


class FormatOptions:
    """Options for formatting Kson output."""

    _jni_ref: Any

    def __init__(
        self,
        indent_type: IndentType,
        formatting_style: FormattingStyle,
    ):
        if indent_type is None:
            raise ValueError("`indent_type` cannot be None")
        if formatting_style is None:
            raise ValueError("`formatting_style` cannot be None")
        self._jni_ref = _construct(
            b"org/kson/FormatOptions",
            b"(Lorg/kson/IndentType;Lorg/kson/FormattingStyle;)V",
            [

                indent_type._jni_ref,
                formatting_style._to_kotlin_enum(),
            ]
        )
    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def indent_type(
        self,
    ) -> IndentType:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/FormatOptions",
            jni_ref,
            b"getIndentType",
            b"()Lorg/kson/IndentType;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: IndentType._downcast(x0))(result))

    def formatting_style(
        self,
    ) -> FormattingStyle:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/FormatOptions",
            jni_ref,
            b"getFormattingStyle",
            b"()Lorg/kson/FormattingStyle;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: FormattingStyle._from_kotlin_enum(x0))(result))


class Position:
    """A zero-based line/column position in a document

    @param line The line number where the error occurred (0-based)
    @param column The column number where the error occurred (0-based)
    """

    _jni_ref: Any

    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def line(
        self,
    ) -> int:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/Position",
            jni_ref,
            b"getLine",
            b"()I",
            "IntMethod",
            []
        )

        return cast(Any, (lambda x0: x0)(result))

    def column(
        self,
    ) -> int:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/Position",
            jni_ref,
            b"getColumn",
            b"()I",
            "IntMethod",
            []
        )

        return cast(Any, (lambda x0: x0)(result))


class Message:
    """Represents a message logged during Kson processing"""

    _jni_ref: Any

    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def message(
        self,
    ) -> str:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/Message",
            jni_ref,
            b"getMessage",
            b"()Ljava/lang/String;",
            "ObjectMethod",
            []
        )

        return cast(Any, (_java_string_to_python_str)(result))

    def severity(
        self,
    ) -> MessageSeverity:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/Message",
            jni_ref,
            b"getSeverity",
            b"()Lorg/kson/MessageSeverity;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: MessageSeverity._from_kotlin_enum(x0))(result))

    def start(
        self,
    ) -> Position:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/Message",
            jni_ref,
            b"getStart",
            b"()Lorg/kson/Position;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: _from_kotlin_object(Position, x0))(result))

    def end(
        self,
    ) -> Position:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/Message",
            jni_ref,
            b"getEnd",
            b"()Lorg/kson/Position;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: _from_kotlin_object(Position, x0))(result))


class Token:
    """[Token] produced by the lexing phase of a Kson parse"""

    _jni_ref: Any

    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def token_type(
        self,
    ) -> TokenType:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/Token",
            jni_ref,
            b"getTokenType",
            b"()Lorg/kson/TokenType;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: TokenType._from_kotlin_enum(x0))(result))

    def text(
        self,
    ) -> str:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/Token",
            jni_ref,
            b"getText",
            b"()Ljava/lang/String;",
            "ObjectMethod",
            []
        )

        return cast(Any, (_java_string_to_python_str)(result))

    def start(
        self,
    ) -> Position:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/Token",
            jni_ref,
            b"getStart",
            b"()Lorg/kson/Position;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: _from_kotlin_object(Position, x0))(result))

    def end(
        self,
    ) -> Position:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/Token",
            jni_ref,
            b"getEnd",
            b"()Lorg/kson/Position;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: _from_kotlin_object(Position, x0))(result))


class KsonValue:
    """Represents a parsed [InternalKsonValue] in the public API"""

    _jni_ref: Any

    KsonNull: TypeAlias
    KsonArray: TypeAlias
    KsonString: TypeAlias
    KsonEmbed: TypeAlias
    KsonBoolean: TypeAlias
    KsonObject: TypeAlias
    KsonNumber: TypeAlias
    Decimal: TypeAlias
    Integer: TypeAlias
    def __init__(
        self,
        start: Position,
        end: Position,
    ):
        if start is None:
            raise ValueError("`start` cannot be None")
        if end is None:
            raise ValueError("`end` cannot be None")
        self._jni_ref = _construct(
            b"org/kson/KsonValue",
            b"(Lorg/kson/Position;Lorg/kson/Position;)V",
            [

                start._jni_ref,
                end._jni_ref,
            ]
        )
    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def start(
        self,
    ) -> Position:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue",
            jni_ref,
            b"getStart",
            b"()Lorg/kson/Position;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: _from_kotlin_object(Position, x0))(result))

    def end(
        self,
    ) -> Position:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue",
            jni_ref,
            b"getEnd",
            b"()Lorg/kson/Position;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: _from_kotlin_object(Position, x0))(result))

    def type(
        self,
    ) -> KsonValueType:
        """Type discriminator for easier type checking in TypeScript/JavaScript"""


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue",
            jni_ref,
            b"getType",
            b"()Lorg/kson/KsonValueType;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: KsonValueType._from_kotlin_enum(x0))(result))
    @staticmethod
    def _downcast(jni_ref) -> Any:
        match _jni_class_name(jni_ref):

            case "org.kson.KsonValue$KsonNull":
                return _from_kotlin_object(_KsonValue_KsonNull, jni_ref)

            case "org.kson.KsonValue$KsonArray":
                return _from_kotlin_object(_KsonValue_KsonArray, jni_ref)

            case "org.kson.KsonValue$KsonString":
                return _from_kotlin_object(_KsonValue_KsonString, jni_ref)

            case "org.kson.KsonValue$KsonEmbed":
                return _from_kotlin_object(_KsonValue_KsonEmbed, jni_ref)

            case "org.kson.KsonValue$KsonBoolean":
                return _from_kotlin_object(_KsonValue_KsonBoolean, jni_ref)

            case "org.kson.KsonValue$KsonObject":
                return _from_kotlin_object(_KsonValue_KsonObject, jni_ref)

            case "org.kson.KsonValue$KsonNumber":
                return _from_kotlin_object(_KsonValue_KsonNumber, jni_ref)

            case "org.kson.KsonValue$KsonNumber$Decimal":
                return _from_kotlin_object(_KsonValue_KsonNumber_Decimal, jni_ref)

            case "org.kson.KsonValue$KsonNumber$Integer":
                return _from_kotlin_object(_KsonValue_KsonNumber_Integer, jni_ref)

class _KsonValue_KsonObject(KsonValue):
    """A Kson object with key-value pairs"""

    _jni_ref: Any

    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def properties(
        self,
    ) -> Dict[str, KsonValue]:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue$KsonObject",
            jni_ref,
            b"getProperties",
            b"()Ljava/util/Map;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: _from_kotlin_map(x0, _java_string_to_python_str, lambda x1: KsonValue._downcast(x1)))(result))

    def property_keys(
        self,
    ) -> Dict[str, _KsonValue_KsonString]:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue$KsonObject",
            jni_ref,
            b"getPropertyKeys",
            b"()Ljava/util/Map;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: _from_kotlin_map(x0, _java_string_to_python_str, lambda x1: _from_kotlin_object(KsonValue.KsonString, x1)))(result))

    def type(
        self,
    ) -> KsonValueType:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue$KsonObject",
            jni_ref,
            b"getType",
            b"()Lorg/kson/KsonValueType;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: KsonValueType._from_kotlin_enum(x0))(result))
KsonValue.KsonObject = _KsonValue_KsonObject


class _KsonValue_KsonArray(KsonValue):
    """A Kson array with elements"""

    _jni_ref: Any

    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def elements(
        self,
    ) -> List[KsonValue]:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue$KsonArray",
            jni_ref,
            b"getElements",
            b"()Ljava/util/List;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: _from_kotlin_list(x0, lambda x1: KsonValue._downcast(x1)))(result))

    def type(
        self,
    ) -> KsonValueType:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue$KsonArray",
            jni_ref,
            b"getType",
            b"()Lorg/kson/KsonValueType;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: KsonValueType._from_kotlin_enum(x0))(result))
KsonValue.KsonArray = _KsonValue_KsonArray


class _KsonValue_KsonString(KsonValue):
    """A Kson string value"""

    _jni_ref: Any

    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def value(
        self,
    ) -> str:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue$KsonString",
            jni_ref,
            b"getValue",
            b"()Ljava/lang/String;",
            "ObjectMethod",
            []
        )

        return cast(Any, (_java_string_to_python_str)(result))

    def type(
        self,
    ) -> KsonValueType:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue$KsonString",
            jni_ref,
            b"getType",
            b"()Lorg/kson/KsonValueType;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: KsonValueType._from_kotlin_enum(x0))(result))
KsonValue.KsonString = _KsonValue_KsonString


class _KsonValue_KsonNumber(KsonValue):
    """A Kson number value."""

    _jni_ref: Any

    Decimal: TypeAlias
    Integer: TypeAlias
    def __init__(
        self,
        start: Position,
        end: Position,
    ):
        if start is None:
            raise ValueError("`start` cannot be None")
        if end is None:
            raise ValueError("`end` cannot be None")
        self._jni_ref = _construct(
            b"org/kson/KsonValue$KsonNumber",
            b"(Lorg/kson/Position;Lorg/kson/Position;)V",
            [

                start._jni_ref,
                end._jni_ref,
            ]
        )
    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])

    @staticmethod
    def _downcast(jni_ref) -> Any:
        match _jni_class_name(jni_ref):

            case "org.kson.KsonValue$KsonNumber$Decimal":
                return _from_kotlin_object(_KsonValue_KsonNumber_Decimal, jni_ref)

            case "org.kson.KsonValue$KsonNumber$Integer":
                return _from_kotlin_object(_KsonValue_KsonNumber_Integer, jni_ref)
KsonValue.KsonNumber = _KsonValue_KsonNumber

class _KsonValue_KsonNumber_Integer(KsonValue.KsonNumber):

    _jni_ref: Any

    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def value(
        self,
    ) -> int:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue$KsonNumber$Integer",
            jni_ref,
            b"getValue",
            b"()I",
            "IntMethod",
            []
        )

        return cast(Any, (lambda x0: x0)(result))

    def internal_start(
        self,
    ) -> Position:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue$KsonNumber$Integer",
            jni_ref,
            b"getInternalStart",
            b"()Lorg/kson/Position;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: _from_kotlin_object(Position, x0))(result))

    def internal_end(
        self,
    ) -> Position:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue$KsonNumber$Integer",
            jni_ref,
            b"getInternalEnd",
            b"()Lorg/kson/Position;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: _from_kotlin_object(Position, x0))(result))

    def type(
        self,
    ) -> KsonValueType:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue$KsonNumber$Integer",
            jni_ref,
            b"getType",
            b"()Lorg/kson/KsonValueType;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: KsonValueType._from_kotlin_enum(x0))(result))
KsonValue.KsonNumber.Integer = _KsonValue_KsonNumber_Integer


class _KsonValue_KsonNumber_Decimal(KsonValue.KsonNumber):

    _jni_ref: Any

    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def value(
        self,
    ) -> float:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue$KsonNumber$Decimal",
            jni_ref,
            b"getValue",
            b"()D",
            "DoubleMethod",
            []
        )

        return cast(Any, (lambda x0: x0)(result))

    def type(
        self,
    ) -> KsonValueType:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue$KsonNumber$Decimal",
            jni_ref,
            b"getType",
            b"()Lorg/kson/KsonValueType;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: KsonValueType._from_kotlin_enum(x0))(result))
KsonValue.KsonNumber.Decimal = _KsonValue_KsonNumber_Decimal



class _KsonValue_KsonBoolean(KsonValue):
    """A Kson boolean value"""

    _jni_ref: Any

    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def value(
        self,
    ) -> bool:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue$KsonBoolean",
            jni_ref,
            b"getValue",
            b"()Z",
            "BooleanMethod",
            []
        )

        return cast(Any, (lambda x0: x0)(result))

    def type(
        self,
    ) -> KsonValueType:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue$KsonBoolean",
            jni_ref,
            b"getType",
            b"()Lorg/kson/KsonValueType;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: KsonValueType._from_kotlin_enum(x0))(result))
KsonValue.KsonBoolean = _KsonValue_KsonBoolean


class _KsonValue_KsonNull(KsonValue):
    """A Kson null value"""

    _jni_ref: Any

    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def type(
        self,
    ) -> KsonValueType:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue$KsonNull",
            jni_ref,
            b"getType",
            b"()Lorg/kson/KsonValueType;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: KsonValueType._from_kotlin_enum(x0))(result))
KsonValue.KsonNull = _KsonValue_KsonNull


class _KsonValue_KsonEmbed(KsonValue):
    """A Kson embed block"""

    _jni_ref: Any

    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def tag(
        self,
    ) -> Optional[str]:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue$KsonEmbed",
            jni_ref,
            b"getTag",
            b"()Ljava/lang/String;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: None if x0 == ffi.NULL else (_java_string_to_python_str)(x0))(result))

    def metadata(
        self,
    ) -> Optional[str]:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue$KsonEmbed",
            jni_ref,
            b"getMetadata",
            b"()Ljava/lang/String;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: None if x0 == ffi.NULL else (_java_string_to_python_str)(x0))(result))

    def content(
        self,
    ) -> str:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue$KsonEmbed",
            jni_ref,
            b"getContent",
            b"()Ljava/lang/String;",
            "ObjectMethod",
            []
        )

        return cast(Any, (_java_string_to_python_str)(result))

    def type(
        self,
    ) -> KsonValueType:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/KsonValue$KsonEmbed",
            jni_ref,
            b"getType",
            b"()Lorg/kson/KsonValueType;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: KsonValueType._from_kotlin_enum(x0))(result))
KsonValue.KsonEmbed = _KsonValue_KsonEmbed



class SchemaValidator:
    """A validator that can check if Kson source conforms to a schema."""

    _jni_ref: Any

    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def validate(
        self,
        kson: str,
        filepath: Optional[str],

    ) -> List[Message]:
        """Validates the given Kson source against this validator's schema.
        @param kson The Kson source to validate
        @param filepath Optional filepath of the document being validated, used by validators to determine which rules to apply

        @return A list of validation error messages, or empty list if valid
        """

        if kson is None:
            raise ValueError("`kson` cannot be None")
        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/SchemaValidator",
            jni_ref,
            b"validate",
            b"(Ljava/lang/String;Ljava/lang/String;)Ljava/util/List;",
            "ObjectMethod",
            [

                _python_str_to_java_string(kson),
                _python_str_to_java_string(filepath) if filepath is not None else ffi.NULL,
            ]
        )

        return cast(Any, (lambda x0: _from_kotlin_list(x0, lambda x1: _from_kotlin_object(Message, x1)))(result))


class Analysis:
    """The result of statically analyzing a Kson document"""

    _jni_ref: Any

    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def errors(
        self,
    ) -> List[Message]:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/Analysis",
            jni_ref,
            b"getErrors",
            b"()Ljava/util/List;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: _from_kotlin_list(x0, lambda x1: _from_kotlin_object(Message, x1)))(result))

    def tokens(
        self,
    ) -> List[Token]:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/Analysis",
            jni_ref,
            b"getTokens",
            b"()Ljava/util/List;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: _from_kotlin_list(x0, lambda x1: _from_kotlin_object(Token, x1)))(result))

    def kson_value(
        self,
    ) -> Optional[KsonValue]:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/Analysis",
            jni_ref,
            b"getKsonValue",
            b"()Lorg/kson/KsonValue;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: None if x0 == ffi.NULL else (lambda x0: KsonValue._downcast(x0))(x0))(result))


class Result:
    """Result of a Kson conversion operation"""

    _jni_ref: Any

    Failure: TypeAlias
    Success: TypeAlias
    def __init__(
        self,

    ):

        self._jni_ref = _construct(
            b"org/kson/Result",
            b"()V",
            []
        )
    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])

    @staticmethod
    def _downcast(jni_ref) -> Any:
        match _jni_class_name(jni_ref):

            case "org.kson.Result$Failure":
                return _from_kotlin_object(_Result_Failure, jni_ref)

            case "org.kson.Result$Success":
                return _from_kotlin_object(_Result_Success, jni_ref)

class _Result_Success(Result):

    _jni_ref: Any

    def __init__(
        self,
        output: str,
    ):
        if output is None:
            raise ValueError("`output` cannot be None")
        self._jni_ref = _construct(
            b"org/kson/Result$Success",
            b"(Ljava/lang/String;)V",
            [

                _python_str_to_java_string(output),
            ]
        )
    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def output(
        self,
    ) -> str:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/Result$Success",
            jni_ref,
            b"getOutput",
            b"()Ljava/lang/String;",
            "ObjectMethod",
            []
        )

        return cast(Any, (_java_string_to_python_str)(result))
Result.Success = _Result_Success


class _Result_Failure(Result):

    _jni_ref: Any

    def __init__(
        self,
        errors: List[Message],
    ):
        if errors is None:
            raise ValueError("`errors` cannot be None")
        self._jni_ref = _construct(
            b"org/kson/Result$Failure",
            b"(Ljava/util/List;)V",
            [

                _to_kotlin_list(errors),
            ]
        )
    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def errors(
        self,
    ) -> List[Message]:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/Result$Failure",
            jni_ref,
            b"getErrors",
            b"()Ljava/util/List;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: _from_kotlin_list(x0, lambda x1: _from_kotlin_object(Message, x1)))(result))
Result.Failure = _Result_Failure



class SchemaResult:
    """A [parseSchema] result"""

    _jni_ref: Any

    Failure: TypeAlias
    Success: TypeAlias
    def __init__(
        self,

    ):

        self._jni_ref = _construct(
            b"org/kson/SchemaResult",
            b"()V",
            []
        )
    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])

    @staticmethod
    def _downcast(jni_ref) -> Any:
        match _jni_class_name(jni_ref):

            case "org.kson.SchemaResult$Failure":
                return _from_kotlin_object(_SchemaResult_Failure, jni_ref)

            case "org.kson.SchemaResult$Success":
                return _from_kotlin_object(_SchemaResult_Success, jni_ref)

class _SchemaResult_Success(SchemaResult):

    _jni_ref: Any

    def __init__(
        self,
        schema_validator: SchemaValidator,
    ):
        if schema_validator is None:
            raise ValueError("`schema_validator` cannot be None")
        self._jni_ref = _construct(
            b"org/kson/SchemaResult$Success",
            b"(Lorg/kson/SchemaValidator;)V",
            [

                schema_validator._jni_ref,
            ]
        )
    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def schema_validator(
        self,
    ) -> SchemaValidator:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/SchemaResult$Success",
            jni_ref,
            b"getSchemaValidator",
            b"()Lorg/kson/SchemaValidator;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: _from_kotlin_object(SchemaValidator, x0))(result))
SchemaResult.Success = _SchemaResult_Success


class _SchemaResult_Failure(SchemaResult):

    _jni_ref: Any

    def __init__(
        self,
        errors: List[Message],
    ):
        if errors is None:
            raise ValueError("`errors` cannot be None")
        self._jni_ref = _construct(
            b"org/kson/SchemaResult$Failure",
            b"(Ljava/util/List;)V",
            [

                _to_kotlin_list(errors),
            ]
        )
    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def errors(
        self,
    ) -> List[Message]:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/SchemaResult$Failure",
            jni_ref,
            b"getErrors",
            b"()Ljava/util/List;",
            "ObjectMethod",
            []
        )

        return cast(Any, (lambda x0: _from_kotlin_list(x0, lambda x1: _from_kotlin_object(Message, x1)))(result))
SchemaResult.Failure = _SchemaResult_Failure



class TranspileOptions:
    """Core interface for transpilation options shared across all output formats."""

    _jni_ref: Any

    Json: TypeAlias
    Yaml: TypeAlias
    def __init__(
        self,

    ):

        self._jni_ref = _construct(
            b"org/kson/TranspileOptions",
            b"()V",
            []
        )
    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def retain_embed_tags(
        self,
    ) -> bool:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/TranspileOptions",
            jni_ref,
            b"getRetainEmbedTags",
            b"()Z",
            "BooleanMethod",
            []
        )

        return cast(Any, (lambda x0: x0)(result))
    @staticmethod
    def _downcast(jni_ref) -> Any:
        match _jni_class_name(jni_ref):

            case "org.kson.TranspileOptions$Json":
                return _from_kotlin_object(_TranspileOptions_Json, jni_ref)

            case "org.kson.TranspileOptions$Yaml":
                return _from_kotlin_object(_TranspileOptions_Yaml, jni_ref)

class _TranspileOptions_Json(TranspileOptions):
    """Options for transpiling Kson to JSON."""

    _jni_ref: Any

    def __init__(
        self,
        retain_embed_tags: bool,
    ):
        if retain_embed_tags is None:
            raise ValueError("`retain_embed_tags` cannot be None")
        self._jni_ref = _construct(
            b"org/kson/TranspileOptions$Json",
            b"(Z)V",
            [

                ffi.cast('jboolean', retain_embed_tags),
            ]
        )
    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def retain_embed_tags(
        self,
    ) -> bool:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/TranspileOptions$Json",
            jni_ref,
            b"getRetainEmbedTags",
            b"()Z",
            "BooleanMethod",
            []
        )

        return cast(Any, (lambda x0: x0)(result))
TranspileOptions.Json = _TranspileOptions_Json


class _TranspileOptions_Yaml(TranspileOptions):
    """Options for transpiling Kson to YAML."""

    _jni_ref: Any

    def __init__(
        self,
        retain_embed_tags: bool,
    ):
        if retain_embed_tags is None:
            raise ValueError("`retain_embed_tags` cannot be None")
        self._jni_ref = _construct(
            b"org/kson/TranspileOptions$Yaml",
            b"(Z)V",
            [

                ffi.cast('jboolean', retain_embed_tags),
            ]
        )
    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def retain_embed_tags(
        self,
    ) -> bool:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/TranspileOptions$Yaml",
            jni_ref,
            b"getRetainEmbedTags",
            b"()Z",
            "BooleanMethod",
            []
        )

        return cast(Any, (lambda x0: x0)(result))
TranspileOptions.Yaml = _TranspileOptions_Yaml



class Kson:
    """The [Kson](https://kson.org) language"""

    _jni_ref: Any

    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    @staticmethod
    def format(
        kson: str,
        format_options: FormatOptions,

    ) -> str:
        """Formats Kson source with the specified formatting options.

        @param kson The Kson source to format
        @param formatOptions The formatting options to apply
        @return The formatted Kson source
        """

        if kson is None:
            raise ValueError("`kson` cannot be None")
        if format_options is None:
            raise ValueError("`format_options` cannot be None")
        jni_ref = _access_static_field(b"org/kson/Kson", b"INSTANCE", b"Lorg/kson/Kson;")
        result = _call_method(
            b"org/kson/Kson",
            jni_ref,
            b"format",
            b"(Ljava/lang/String;Lorg/kson/FormatOptions;)Ljava/lang/String;",
            "ObjectMethod",
            [

                _python_str_to_java_string(kson),
                format_options._jni_ref,
            ]
        )

        return cast(Any, (_java_string_to_python_str)(result))

    @staticmethod
    def to_json(
        kson: str,
        options: _TranspileOptions_Json,

    ) -> Result:
        """Converts Kson to Json.

        @param kson The Kson source to convert
        @param options Options for the JSON transpilation
        @return A Result containing either the Json output or error messages
        """

        if kson is None:
            raise ValueError("`kson` cannot be None")
        if options is None:
            raise ValueError("`options` cannot be None")
        jni_ref = _access_static_field(b"org/kson/Kson", b"INSTANCE", b"Lorg/kson/Kson;")
        result = _call_method(
            b"org/kson/Kson",
            jni_ref,
            b"toJson",
            b"(Ljava/lang/String;Lorg/kson/TranspileOptions$Json;)Lorg/kson/Result;",
            "ObjectMethod",
            [

                _python_str_to_java_string(kson),
                options._jni_ref,
            ]
        )

        return cast(Any, (lambda x0: Result._downcast(x0))(result))

    @staticmethod
    def to_yaml(
        kson: str,
        options: _TranspileOptions_Yaml,

    ) -> Result:
        """Converts Kson to Yaml, preserving comments

        @param kson The Kson source to convert
        @param options Options for the YAML transpilation
        @return A Result containing either the Yaml output or error messages
        """

        if kson is None:
            raise ValueError("`kson` cannot be None")
        if options is None:
            raise ValueError("`options` cannot be None")
        jni_ref = _access_static_field(b"org/kson/Kson", b"INSTANCE", b"Lorg/kson/Kson;")
        result = _call_method(
            b"org/kson/Kson",
            jni_ref,
            b"toYaml",
            b"(Ljava/lang/String;Lorg/kson/TranspileOptions$Yaml;)Lorg/kson/Result;",
            "ObjectMethod",
            [

                _python_str_to_java_string(kson),
                options._jni_ref,
            ]
        )

        return cast(Any, (lambda x0: Result._downcast(x0))(result))

    @staticmethod
    def analyze(
        kson: str,
        filepath: Optional[str],

    ) -> Analysis:
        """Statically analyze the given Kson and return an [Analysis] object containing any messages generated along with a
        tokenized version of the source.  Useful for tooling/editor support.
        @param kson The Kson source to analyze
        @param filepath Filepath of the document being analyzed
        """

        if kson is None:
            raise ValueError("`kson` cannot be None")
        jni_ref = _access_static_field(b"org/kson/Kson", b"INSTANCE", b"Lorg/kson/Kson;")
        result = _call_method(
            b"org/kson/Kson",
            jni_ref,
            b"analyze",
            b"(Ljava/lang/String;Ljava/lang/String;)Lorg/kson/Analysis;",
            "ObjectMethod",
            [

                _python_str_to_java_string(kson),
                _python_str_to_java_string(filepath) if filepath is not None else ffi.NULL,
            ]
        )

        return cast(Any, (lambda x0: _from_kotlin_object(Analysis, x0))(result))

    @staticmethod
    def parse_schema(
        schema_kson: str,

    ) -> SchemaResult:
        """Parses a Kson schema definition and returns a validator for that schema.

        @param schemaKson The Kson source defining a Json Schema
        @return A SchemaValidator that can validate Kson documents against the schema
        """

        if schema_kson is None:
            raise ValueError("`schema_kson` cannot be None")
        jni_ref = _access_static_field(b"org/kson/Kson", b"INSTANCE", b"Lorg/kson/Kson;")
        result = _call_method(
            b"org/kson/Kson",
            jni_ref,
            b"parseSchema",
            b"(Ljava/lang/String;)Lorg/kson/SchemaResult;",
            "ObjectMethod",
            [

                _python_str_to_java_string(schema_kson),
            ]
        )

        return cast(Any, (lambda x0: SchemaResult._downcast(x0))(result))


class IndentType:
    """Options for indenting Kson Output"""

    _jni_ref: Any

    Tabs: TypeAlias
    Spaces: TypeAlias
    def __init__(
        self,

    ):

        self._jni_ref = _construct(
            b"org/kson/IndentType",
            b"()V",
            []
        )
    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])

    @staticmethod
    def _downcast(jni_ref) -> Any:
        match _jni_class_name(jni_ref):

            case "org.kson.IndentType$Tabs":
                return _from_kotlin_object(_IndentType_Tabs, jni_ref)

            case "org.kson.IndentType$Spaces":
                return _from_kotlin_object(_IndentType_Spaces, jni_ref)

class _IndentType_Spaces(IndentType):
    """Use spaces for indentation with the specified count"""

    _jni_ref: Any

    def __init__(
        self,
        size: int,
    ):
        if size is None:
            raise ValueError("`size` cannot be None")
        self._jni_ref = _construct(
            b"org/kson/IndentType$Spaces",
            b"(I)V",
            [

                ffi.cast('jint', size),
            ]
        )
    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])


    def size(
        self,
    ) -> int:


        jni_ref = self._jni_ref
        result = _call_method(
            b"org/kson/IndentType$Spaces",
            jni_ref,
            b"getSize",
            b"()I",
            "IntMethod",
            []
        )

        return cast(Any, (lambda x0: x0)(result))
IndentType.Spaces = _IndentType_Spaces


class _IndentType_Tabs(IndentType):
    """Use tabs for indentation"""

    _jni_ref: Any

    def __eq__(self, other):
        return _call_method(b"java/lang/Object", self._jni_ref, b"equals", b"(Ljava/lang/Object;)Z", "BooleanMethod", [other._jni_ref])

    def __hash__(self):
        return _call_method(b"java/lang/Object", self._jni_ref, b"hashCode", b"()I", "IntMethod", [])

IndentType.Tabs = _IndentType_Tabs



class MessageSeverity(Enum):
    """Represents the severity of a [Message]"""

    def _to_kotlin_enum(self):
        match self:
            case MessageSeverity.ERROR:
                return _access_static_field(b"org/kson/MessageSeverity", b"ERROR", b"Lorg/kson/MessageSeverity;")
            case MessageSeverity.WARNING:
                return _access_static_field(b"org/kson/MessageSeverity", b"WARNING", b"Lorg/kson/MessageSeverity;")
    @staticmethod
    def _from_kotlin_enum(jni_ref):
        index = _call_method(b"org/kson/MessageSeverity", jni_ref, b"ordinal", b"()I", "IntMethod", [])
        return MessageSeverity(index)

    ERROR = 0
    WARNING = 1


class KsonValueType(Enum):
    """Type discriminator for KsonValue subclasses"""

    def _to_kotlin_enum(self):
        match self:
            case KsonValueType.OBJECT:
                return _access_static_field(b"org/kson/KsonValueType", b"OBJECT", b"Lorg/kson/KsonValueType;")
            case KsonValueType.ARRAY:
                return _access_static_field(b"org/kson/KsonValueType", b"ARRAY", b"Lorg/kson/KsonValueType;")
            case KsonValueType.STRING:
                return _access_static_field(b"org/kson/KsonValueType", b"STRING", b"Lorg/kson/KsonValueType;")
            case KsonValueType.INTEGER:
                return _access_static_field(b"org/kson/KsonValueType", b"INTEGER", b"Lorg/kson/KsonValueType;")
            case KsonValueType.DECIMAL:
                return _access_static_field(b"org/kson/KsonValueType", b"DECIMAL", b"Lorg/kson/KsonValueType;")
            case KsonValueType.BOOLEAN:
                return _access_static_field(b"org/kson/KsonValueType", b"BOOLEAN", b"Lorg/kson/KsonValueType;")
            case KsonValueType.NULL:
                return _access_static_field(b"org/kson/KsonValueType", b"NULL", b"Lorg/kson/KsonValueType;")
            case KsonValueType.EMBED:
                return _access_static_field(b"org/kson/KsonValueType", b"EMBED", b"Lorg/kson/KsonValueType;")
    @staticmethod
    def _from_kotlin_enum(jni_ref):
        index = _call_method(b"org/kson/KsonValueType", jni_ref, b"ordinal", b"()I", "IntMethod", [])
        return KsonValueType(index)

    OBJECT = 0
    ARRAY = 1
    STRING = 2
    INTEGER = 3
    DECIMAL = 4
    BOOLEAN = 5
    NULL = 6
    EMBED = 7


class FormattingStyle(Enum):
    """[FormattingStyle] options for Kson Output"""

    def _to_kotlin_enum(self):
        match self:
            case FormattingStyle.PLAIN:
                return _access_static_field(b"org/kson/FormattingStyle", b"PLAIN", b"Lorg/kson/FormattingStyle;")
            case FormattingStyle.DELIMITED:
                return _access_static_field(b"org/kson/FormattingStyle", b"DELIMITED", b"Lorg/kson/FormattingStyle;")
            case FormattingStyle.COMPACT:
                return _access_static_field(b"org/kson/FormattingStyle", b"COMPACT", b"Lorg/kson/FormattingStyle;")
            case FormattingStyle.CLASSIC:
                return _access_static_field(b"org/kson/FormattingStyle", b"CLASSIC", b"Lorg/kson/FormattingStyle;")
    @staticmethod
    def _from_kotlin_enum(jni_ref):
        index = _call_method(b"org/kson/FormattingStyle", jni_ref, b"ordinal", b"()I", "IntMethod", [])
        return FormattingStyle(index)

    PLAIN = 0
    DELIMITED = 1
    COMPACT = 2
    CLASSIC = 3


class TokenType(Enum):
    def _to_kotlin_enum(self):
        match self:
            case TokenType.CURLY_BRACE_L:
                return _access_static_field(b"org/kson/TokenType", b"CURLY_BRACE_L", b"Lorg/kson/TokenType;")
            case TokenType.CURLY_BRACE_R:
                return _access_static_field(b"org/kson/TokenType", b"CURLY_BRACE_R", b"Lorg/kson/TokenType;")
            case TokenType.SQUARE_BRACKET_L:
                return _access_static_field(b"org/kson/TokenType", b"SQUARE_BRACKET_L", b"Lorg/kson/TokenType;")
            case TokenType.SQUARE_BRACKET_R:
                return _access_static_field(b"org/kson/TokenType", b"SQUARE_BRACKET_R", b"Lorg/kson/TokenType;")
            case TokenType.ANGLE_BRACKET_L:
                return _access_static_field(b"org/kson/TokenType", b"ANGLE_BRACKET_L", b"Lorg/kson/TokenType;")
            case TokenType.ANGLE_BRACKET_R:
                return _access_static_field(b"org/kson/TokenType", b"ANGLE_BRACKET_R", b"Lorg/kson/TokenType;")
            case TokenType.COLON:
                return _access_static_field(b"org/kson/TokenType", b"COLON", b"Lorg/kson/TokenType;")
            case TokenType.DOT:
                return _access_static_field(b"org/kson/TokenType", b"DOT", b"Lorg/kson/TokenType;")
            case TokenType.END_DASH:
                return _access_static_field(b"org/kson/TokenType", b"END_DASH", b"Lorg/kson/TokenType;")
            case TokenType.COMMA:
                return _access_static_field(b"org/kson/TokenType", b"COMMA", b"Lorg/kson/TokenType;")
            case TokenType.COMMENT:
                return _access_static_field(b"org/kson/TokenType", b"COMMENT", b"Lorg/kson/TokenType;")
            case TokenType.EMBED_OPEN_DELIM:
                return _access_static_field(b"org/kson/TokenType", b"EMBED_OPEN_DELIM", b"Lorg/kson/TokenType;")
            case TokenType.EMBED_CLOSE_DELIM:
                return _access_static_field(b"org/kson/TokenType", b"EMBED_CLOSE_DELIM", b"Lorg/kson/TokenType;")
            case TokenType.EMBED_TAG:
                return _access_static_field(b"org/kson/TokenType", b"EMBED_TAG", b"Lorg/kson/TokenType;")
            case TokenType.EMBED_TAG_STOP:
                return _access_static_field(b"org/kson/TokenType", b"EMBED_TAG_STOP", b"Lorg/kson/TokenType;")
            case TokenType.EMBED_METADATA:
                return _access_static_field(b"org/kson/TokenType", b"EMBED_METADATA", b"Lorg/kson/TokenType;")
            case TokenType.EMBED_PREAMBLE_NEWLINE:
                return _access_static_field(b"org/kson/TokenType", b"EMBED_PREAMBLE_NEWLINE", b"Lorg/kson/TokenType;")
            case TokenType.EMBED_CONTENT:
                return _access_static_field(b"org/kson/TokenType", b"EMBED_CONTENT", b"Lorg/kson/TokenType;")
            case TokenType.FALSE:
                return _access_static_field(b"org/kson/TokenType", b"FALSE", b"Lorg/kson/TokenType;")
            case TokenType.UNQUOTED_STRING:
                return _access_static_field(b"org/kson/TokenType", b"UNQUOTED_STRING", b"Lorg/kson/TokenType;")
            case TokenType.ILLEGAL_CHAR:
                return _access_static_field(b"org/kson/TokenType", b"ILLEGAL_CHAR", b"Lorg/kson/TokenType;")
            case TokenType.LIST_DASH:
                return _access_static_field(b"org/kson/TokenType", b"LIST_DASH", b"Lorg/kson/TokenType;")
            case TokenType.NULL:
                return _access_static_field(b"org/kson/TokenType", b"NULL", b"Lorg/kson/TokenType;")
            case TokenType.NUMBER:
                return _access_static_field(b"org/kson/TokenType", b"NUMBER", b"Lorg/kson/TokenType;")
            case TokenType.STRING_OPEN_QUOTE:
                return _access_static_field(b"org/kson/TokenType", b"STRING_OPEN_QUOTE", b"Lorg/kson/TokenType;")
            case TokenType.STRING_CLOSE_QUOTE:
                return _access_static_field(b"org/kson/TokenType", b"STRING_CLOSE_QUOTE", b"Lorg/kson/TokenType;")
            case TokenType.STRING_CONTENT:
                return _access_static_field(b"org/kson/TokenType", b"STRING_CONTENT", b"Lorg/kson/TokenType;")
            case TokenType.TRUE:
                return _access_static_field(b"org/kson/TokenType", b"TRUE", b"Lorg/kson/TokenType;")
            case TokenType.WHITESPACE:
                return _access_static_field(b"org/kson/TokenType", b"WHITESPACE", b"Lorg/kson/TokenType;")
            case TokenType.EOF:
                return _access_static_field(b"org/kson/TokenType", b"EOF", b"Lorg/kson/TokenType;")
    @staticmethod
    def _from_kotlin_enum(jni_ref):
        index = _call_method(b"org/kson/TokenType", jni_ref, b"ordinal", b"()I", "IntMethod", [])
        return TokenType(index)

    CURLY_BRACE_L = 0
    CURLY_BRACE_R = 1
    SQUARE_BRACKET_L = 2
    SQUARE_BRACKET_R = 3
    ANGLE_BRACKET_L = 4
    ANGLE_BRACKET_R = 5
    COLON = 6
    DOT = 7
    END_DASH = 8
    COMMA = 9
    COMMENT = 10
    EMBED_OPEN_DELIM = 11
    EMBED_CLOSE_DELIM = 12
    EMBED_TAG = 13
    EMBED_TAG_STOP = 14
    EMBED_METADATA = 15
    EMBED_PREAMBLE_NEWLINE = 16
    EMBED_CONTENT = 17
    FALSE = 18
    UNQUOTED_STRING = 19
    ILLEGAL_CHAR = 20
    LIST_DASH = 21
    NULL = 22
    NUMBER = 23
    STRING_OPEN_QUOTE = 24
    STRING_CLOSE_QUOTE = 25
    STRING_CONTENT = 26
    TRUE = 27
    WHITESPACE = 28
    EOF = 29


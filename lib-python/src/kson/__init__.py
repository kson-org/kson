from __future__ import annotations

import sys
from enum import Enum
from typing import Any, Dict, List, Optional, Type
from cffi import FFI
from pathlib import Path

CData = Any

ffi = FFI()

package_dir = Path(__file__).parent
with open(package_dir / "kson_api.h", "r") as f:
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
symbols: Any = (
    lib.libkson_symbols() if sys.platform in ["linux", "darwin"] else lib.kson_symbols()
)
kotlin_enum_type = (
    "libkson_kref_kotlin_Enum"
    if sys.platform in ["linux", "darwin"]
    else "kson_kref_kotlin_Enum"
)


def _cast_and_call(func: Any, args: List[Any]) -> Any:
    param_types = ffi.typeof(func).args

    casted_args: List[Any] = []
    for arg, param_type in zip(args, param_types):
        if isinstance(arg, ffi.CData):
            casted_args.append(_cast(param_type.cname, arg))
        else:
            casted_args.append(arg)

    return func(*casted_args)


def _cast(target_type_name: str, arg: CData) -> CData:
    addr = ffi.addressof(arg)
    return ffi.cast(f"{target_type_name} *", addr)[0]


def _init_wrapper(target_type: Type, ptr: CData) -> Any:
    ptr.pinned = ffi.gc(ptr.pinned, symbols.DisposeStablePointer)
    result: Any = object.__new__(target_type)
    result.ptr = ptr
    return result


def _init_enum_wrapper(target_type: Type, ptr: CData) -> Any:
    enum_helper_instance = symbols.kotlin.root.org.kson.EnumHelper._instance()
    ordinal = symbols.kotlin.root.org.kson.EnumHelper.ordinal(
        enum_helper_instance, _cast(kotlin_enum_type, ptr)
    )
    instance = target_type(ordinal)
    symbols.DisposeStablePointer(ptr.pinned)
    return instance


def _from_kotlin_string(ptr: CData) -> str:
    ffi_string: Any = ffi.string(ptr)
    python_string = ffi_string.decode("utf-8")
    symbols.DisposeString(ptr)
    return python_string


def _from_kotlin_list(
    list: CData, item_type: str, wrap_as: Optional[Type]
) -> List[Any]:
    python_list: List[Any] = []
    iterator = symbols.kotlin.root.org.kson.SimpleListIterator.SimpleListIterator(list)
    while True:
        item = symbols.kotlin.root.org.kson.SimpleListIterator.next(iterator)
        if item.pinned == ffi.NULL:
            break

        if wrap_as is not None:
            tmp = object.__new__(wrap_as)
            tmp.ptr = item
            item = tmp

        python_list.append(item)

    symbols.DisposeStablePointer(iterator.pinned)
    return python_list


def _from_kotlin_map(
    map: CData,
    key_type: str,
    value_type: str,
    key_wrap_as: Optional[Type],
    value_wrap_as: Optional[Type],
) -> Dict[Any, Any]:
    python_dict: Dict[Any, Any] = {}
    iterator = symbols.kotlin.root.org.kson.SimpleMapIterator.SimpleMapIterator(map)
    while True:
        entry = symbols.kotlin.root.org.kson.SimpleMapIterator.next(iterator)
        if entry.pinned == ffi.NULL:
            break

        key = symbols.kotlin.root.org.kson.SimpleMapEntry.get_key(entry)
        if key_wrap_as is not None:
            if key_wrap_as == str:
                # Special handling for string keys - use AnyHelper to convert kotlin.Any to String
                any_helper = symbols.kotlin.root.org.kson.AnyHelper._instance()
                string_ptr = symbols.kotlin.root.org.kson.AnyHelper.toString(
                    any_helper, key
                )
                key = _from_kotlin_string(string_ptr)
            else:
                tmp = object.__new__(key_wrap_as)
                tmp.ptr = key
                key = tmp

        value = symbols.kotlin.root.org.kson.SimpleMapEntry.get_value(entry)
        if value_wrap_as is not None:
            tmp = object.__new__(value_wrap_as)
            tmp.ptr = value
            value = tmp

        python_dict[key] = value
        symbols.DisposeStablePointer(entry.pinned)

    symbols.DisposeStablePointer(iterator.pinned)
    return python_dict


class Analysis:
    """The result of statically analyzing a Kson document."""

    ptr: CData

    def errors(self) -> List[Message]:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.Analysis.get_errors, [self.ptr]
        )
        result = _from_kotlin_list(result, "kson_kref_org_kson_Message", Message)
        return result

    def tokens(self) -> List[Token]:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.Analysis.get_tokens, [self.ptr]
        )
        result = _from_kotlin_list(result, "kson_kref_org_kson_Token", Token)
        return result

    def kson_value(self) -> Optional["KsonValue"]:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.Analysis.get_ksonValue, [self.ptr]
        )
        if result.pinned == ffi.NULL:
            return None
        value = _init_wrapper(KsonValue, result)
        return value._translate()


class Position:
    """A zero-based line/column position in a document.

    Args:
        line: The line number where the error occurred (0-based).
        column: The column number where the error occurred (0-based).
    """

    ptr: CData

    def line(self) -> int:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.Position.get_line, [self.ptr]
        )
        return result

    def column(self) -> int:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.Position.get_column, [self.ptr]
        )
        return result


class Result:
    """Result of a Kson conversion operation."""

    ptr: CData
    Success: Type
    Failure: Type

    def __init__(self) -> None:
        result = _cast_and_call(symbols.kotlin.root.org.kson.Result.Result, [])
        self.ptr = result

    def _translate(self) -> Result:
        subclass_type = symbols.kotlin.root.org.kson.Result.Success._type()
        if symbols.IsInstance(self.ptr.pinned, subclass_type):
            return _init_wrapper(Result.Success, self.ptr)
        subclass_type = symbols.kotlin.root.org.kson.Result.Failure._type()
        if symbols.IsInstance(self.ptr.pinned, subclass_type):
            return _init_wrapper(Result.Failure, self.ptr)
        raise RuntimeError("Unknown Result subtype")


class Success(Result):
    def output(self) -> str:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.Result.Success.get_output, [self.ptr]
        )
        result = _from_kotlin_string(result)
        return result


Result.Success = Success


class Failure(Result):
    def errors(self) -> List[Message]:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.Result.Failure.get_errors, [self.ptr]
        )
        result = _from_kotlin_list(result, "kson_kref_org_kson_Message", Message)
        return result


Result.Failure = Failure


class SchemaResult:
    """A parse_schema result."""

    ptr: CData
    Success: Type
    Failure: Type

    def __init__(self) -> None:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.SchemaResult.SchemaResult, []
        )
        self.ptr = result

    def _translate(self) -> SchemaResult:
        subclass_type = symbols.kotlin.root.org.kson.SchemaResult.Failure._type()
        if symbols.IsInstance(self.ptr.pinned, subclass_type):
            return _init_wrapper(SchemaResult.Failure, self.ptr)
        subclass_type = symbols.kotlin.root.org.kson.SchemaResult.Success._type()
        if symbols.IsInstance(self.ptr.pinned, subclass_type):
            return _init_wrapper(SchemaResult.Success, self.ptr)
        raise RuntimeError("Unknown SchemaResult subtype")


class SchemaResultSuccess(SchemaResult):
    def schema_validator(self) -> SchemaValidator:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.SchemaResult.Success.get_schemaValidator,
            [self.ptr],
        )
        result = _init_wrapper(SchemaValidator, result)
        return result


SchemaResult.Success = SchemaResultSuccess


class SchemaResultFailure(SchemaResult):
    def errors(self) -> List[Message]:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.SchemaResult.Failure.get_errors, [self.ptr]
        )
        result = _from_kotlin_list(result, "kson_kref_org_kson_Message", Message)
        return result


SchemaResult.Failure = SchemaResultFailure


class Message:
    """Represents a message logged during Kson processing."""

    ptr: CData

    def message(self) -> str:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.Message.get_message, [self.ptr]
        )
        result = _from_kotlin_string(result)
        return result

    def start(self) -> Position:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.Message.get_start, [self.ptr]
        )
        result = _init_wrapper(Position, result)
        return result

    def end(self) -> Position:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.Message.get_end, [self.ptr]
        )
        result = _init_wrapper(Position, result)
        return result


class Token:
    """Token produced by the lexing phase of a Kson parse."""

    ptr: CData

    def token_type(self) -> TokenType:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.Token.get_tokenType, [self.ptr]
        )
        result = _init_enum_wrapper(TokenType, result)
        return result

    def text(self) -> str:
        result = _cast_and_call(symbols.kotlin.root.org.kson.Token.get_text, [self.ptr])
        result = _from_kotlin_string(result)
        return result

    def start(self) -> Position:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.Token.get_start, [self.ptr]
        )
        result = _init_wrapper(Position, result)
        return result

    def end(self) -> Position:
        result = _cast_and_call(symbols.kotlin.root.org.kson.Token.get_end, [self.ptr])
        result = _init_wrapper(Position, result)
        return result


class SchemaValidator:
    """A validator that can check if Kson source conforms to a schema."""

    ptr: CData

    def validate(self, kson: str) -> List[Message]:
        """Validates the given Kson source against this validator's schema.

        Args:
            kson: The Kson source to validate.

        Returns:
            A list of validation error messages, or empty list if valid.
        """
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.SchemaValidator.validate,
            [self.ptr, kson.encode("utf-8")],
        )
        result = _from_kotlin_list(result, "kson_kref_org_kson_Message", Message)
        return result


class IndentType:
    """Options for indenting Kson Output."""

    ptr: CData
    Spaces: Type
    Tabs: Type

    def __init__(self) -> None:
        result = _cast_and_call(symbols.kotlin.root.org.kson.IndentType.IndentType, [])
        self.ptr = result

    def _translate(self) -> IndentType:
        subclass_type = symbols.kotlin.root.org.kson.IndentType.Spaces._type()
        if symbols.IsInstance(self.ptr.pinned, subclass_type):
            return _init_wrapper(IndentType.Spaces, self.ptr)
        subclass_type = symbols.kotlin.root.org.kson.IndentType.Tabs._type()
        if symbols.IsInstance(self.ptr.pinned, subclass_type):
            return _init_wrapper(IndentType.Tabs, self.ptr)
        raise RuntimeError("Unknown IndentType subtype")


class Spaces(IndentType):
    """Use spaces for indentation with the specified count."""

    ptr: CData

    def __init__(self, size: int) -> None:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.IndentType.Spaces.Spaces, [size]
        )
        self.ptr = result

    def size(self) -> int:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.IndentType.Spaces.get_size, [self.ptr]
        )
        return result


IndentType.Spaces = Spaces


class Tabs(IndentType):
    """Use tabs for indentation."""

    ptr: CData

    @staticmethod
    def get() -> Tabs:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.IndentType.Tabs._instance, []
        )
        result_obj = object.__new__(IndentType.Tabs)
        result_obj.ptr = result
        return result_obj


IndentType.Tabs = Tabs


class FormattingStyle(Enum):
    """FormattingStyle options for Kson Output."""

    def _to_kotlin_enum(self) -> CData:
        enum_helper_instance = symbols.kotlin.root.org.kson.EnumHelper._instance()
        match self:
            case FormattingStyle.PLAIN:
                result = symbols.kotlin.root.org.kson.FormattingStyle.PLAIN.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case FormattingStyle.DELIMITED:
                result = symbols.kotlin.root.org.kson.FormattingStyle.DELIMITED.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case FormattingStyle.COMPACT:
                result = symbols.kotlin.root.org.kson.FormattingStyle.COMPACT.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case FormattingStyle.CLASSIC:
                result = symbols.kotlin.root.org.kson.FormattingStyle.CLASSIC.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result

    PLAIN = 0
    DELIMITED = 1
    COMPACT = 2
    CLASSIC = 3


class FormatOptions:
    """Options for formatting Kson output."""

    ptr: CData

    def __init__(
        self, indent_type: IndentType, formatting_style: FormattingStyle
    ) -> None:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.FormatOptions.FormatOptions,
            [indent_type.ptr, formatting_style._to_kotlin_enum()],
        )
        self.ptr = result

    def indent_type(self) -> IndentType:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.FormatOptions.get_indentType, [self.ptr]
        )
        result = _init_wrapper(IndentType, result)
        result = result._translate()
        return result

    def formatting_style(self) -> FormattingStyle:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.FormatOptions.get_formattingStyle, [self.ptr]
        )
        result = _init_enum_wrapper(FormattingStyle, result)
        return result


class TokenType(Enum):
    def _to_kotlin_enum(self) -> CData:
        enum_helper_instance = symbols.kotlin.root.org.kson.EnumHelper._instance()
        match self:
            case TokenType.CURLY_BRACE_L:
                result = symbols.kotlin.root.org.kson.TokenType.CURLY_BRACE_L.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.CURLY_BRACE_R:
                result = symbols.kotlin.root.org.kson.TokenType.CURLY_BRACE_R.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.SQUARE_BRACKET_L:
                result = symbols.kotlin.root.org.kson.TokenType.SQUARE_BRACKET_L.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.SQUARE_BRACKET_R:
                result = symbols.kotlin.root.org.kson.TokenType.SQUARE_BRACKET_R.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.ANGLE_BRACKET_L:
                result = symbols.kotlin.root.org.kson.TokenType.ANGLE_BRACKET_L.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.ANGLE_BRACKET_R:
                result = symbols.kotlin.root.org.kson.TokenType.ANGLE_BRACKET_R.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.COLON:
                result = symbols.kotlin.root.org.kson.TokenType.COLON.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.DOT:
                result = symbols.kotlin.root.org.kson.TokenType.DOT.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.END_DASH:
                result = symbols.kotlin.root.org.kson.TokenType.END_DASH.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.COMMA:
                result = symbols.kotlin.root.org.kson.TokenType.COMMA.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.COMMENT:
                result = symbols.kotlin.root.org.kson.TokenType.COMMENT.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.EMBED_OPEN_DELIM:
                result = symbols.kotlin.root.org.kson.TokenType.EMBED_OPEN_DELIM.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.EMBED_CLOSE_DELIM:
                result = symbols.kotlin.root.org.kson.TokenType.EMBED_CLOSE_DELIM.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.EMBED_TAG:
                result = symbols.kotlin.root.org.kson.TokenType.EMBED_TAG.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.EMBED_TAG_STOP:
                result = symbols.kotlin.root.org.kson.TokenType.EMBED_TAG_STOP.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.EMBED_METADATA:
                result = symbols.kotlin.root.org.kson.TokenType.EMBED_METADATA.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.EMBED_PREAMBLE_NEWLINE:
                result = (
                    symbols.kotlin.root.org.kson.TokenType.EMBED_PREAMBLE_NEWLINE.get()
                )
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.EMBED_CONTENT:
                result = symbols.kotlin.root.org.kson.TokenType.EMBED_CONTENT.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.FALSE:
                result = symbols.kotlin.root.org.kson.TokenType.FALSE.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.UNQUOTED_STRING:
                result = symbols.kotlin.root.org.kson.TokenType.UNQUOTED_STRING.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.ILLEGAL_CHAR:
                result = symbols.kotlin.root.org.kson.TokenType.ILLEGAL_CHAR.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.LIST_DASH:
                result = symbols.kotlin.root.org.kson.TokenType.LIST_DASH.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.NULL:
                result = symbols.kotlin.root.org.kson.TokenType.NULL.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.NUMBER:
                result = symbols.kotlin.root.org.kson.TokenType.NUMBER.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.STRING_OPEN_QUOTE:
                result = symbols.kotlin.root.org.kson.TokenType.STRING_OPEN_QUOTE.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.STRING_CLOSE_QUOTE:
                result = symbols.kotlin.root.org.kson.TokenType.STRING_CLOSE_QUOTE.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.STRING_CONTENT:
                result = symbols.kotlin.root.org.kson.TokenType.STRING_CONTENT.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.TRUE:
                result = symbols.kotlin.root.org.kson.TokenType.TRUE.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.WHITESPACE:
                result = symbols.kotlin.root.org.kson.TokenType.WHITESPACE.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result
            case TokenType.EOF:
                result = symbols.kotlin.root.org.kson.TokenType.EOF.get()
                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                return result

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


class KsonValueType(Enum):
    """Type discriminator for KsonValue subclasses."""

    OBJECT = 0
    ARRAY = 1
    STRING = 2
    INTEGER = 3
    DECIMAL = 4
    BOOLEAN = 5
    NULL = 6
    EMBED = 7


class TranspileOptionsJson:
    """Options for transpiling Kson to JSON."""

    ptr: CData

    def __init__(self, retain_embed_tags: bool = True) -> None:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.TranspileOptions.Json.Json, [retain_embed_tags]
        )
        self.ptr = result


class TranspileOptionsYaml:
    """Options for transpiling Kson to YAML."""

    ptr: CData

    def __init__(self, retain_embed_tags: bool = True) -> None:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.TranspileOptions.Yaml.Yaml, [retain_embed_tags]
        )
        self.ptr = result


class KsonValue:
    """Base class for parsed Kson values."""

    ptr: CData

    # Type hints for nested classes (assigned later)
    KsonObject: Type["_KsonObject"]
    KsonArray: Type["_KsonArray"]
    KsonString: Type["_KsonString"]
    KsonBoolean: Type["_KsonBoolean"]
    KsonNull: Type["_KsonNull"]
    KsonEmbed: Type["_KsonEmbed"]
    KsonNumber: Type["_KsonNumber"]

    def value_type(self) -> KsonValueType:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.KsonValue.get_type, [self.ptr]
        )
        result = _init_enum_wrapper(KsonValueType, result)
        return result

    def start(self) -> Position:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.KsonValue.get_start, [self.ptr]
        )
        result = _init_wrapper(Position, result)
        return result

    def end(self) -> Position:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.KsonValue.get_end, [self.ptr]
        )
        result = _init_wrapper(Position, result)
        return result

    def _translate(self) -> "KsonValue":
        value_type = self.value_type()
        if value_type == KsonValueType.OBJECT:
            subclass_type = symbols.kotlin.root.org.kson.KsonValue.KsonObject._type()
            if symbols.IsInstance(self.ptr.pinned, subclass_type):
                return _init_wrapper(KsonValue.KsonObject, self.ptr)
        elif value_type == KsonValueType.ARRAY:
            subclass_type = symbols.kotlin.root.org.kson.KsonValue.KsonArray._type()
            if symbols.IsInstance(self.ptr.pinned, subclass_type):
                return _init_wrapper(KsonValue.KsonArray, self.ptr)
        elif value_type == KsonValueType.STRING:
            subclass_type = symbols.kotlin.root.org.kson.KsonValue.KsonString._type()
            if symbols.IsInstance(self.ptr.pinned, subclass_type):
                return _init_wrapper(KsonValue.KsonString, self.ptr)
        elif value_type == KsonValueType.INTEGER:
            subclass_type = (
                symbols.kotlin.root.org.kson.KsonValue.KsonNumber.Integer._type()
            )
            if symbols.IsInstance(self.ptr.pinned, subclass_type):
                return _init_wrapper(KsonValue.KsonNumber.Integer, self.ptr)
        elif value_type == KsonValueType.DECIMAL:
            subclass_type = (
                symbols.kotlin.root.org.kson.KsonValue.KsonNumber.Decimal._type()
            )
            if symbols.IsInstance(self.ptr.pinned, subclass_type):
                return _init_wrapper(KsonValue.KsonNumber.Decimal, self.ptr)
        elif value_type == KsonValueType.BOOLEAN:
            subclass_type = symbols.kotlin.root.org.kson.KsonValue.KsonBoolean._type()
            if symbols.IsInstance(self.ptr.pinned, subclass_type):
                return _init_wrapper(KsonValue.KsonBoolean, self.ptr)
        elif value_type == KsonValueType.NULL:
            subclass_type = symbols.kotlin.root.org.kson.KsonValue.KsonNull._type()
            if symbols.IsInstance(self.ptr.pinned, subclass_type):
                return _init_wrapper(KsonValue.KsonNull, self.ptr)
        elif value_type == KsonValueType.EMBED:
            subclass_type = symbols.kotlin.root.org.kson.KsonValue.KsonEmbed._type()
            if symbols.IsInstance(self.ptr.pinned, subclass_type):
                return _init_wrapper(KsonValue.KsonEmbed, self.ptr)
        raise RuntimeError("Unknown KsonValue subtype")

    pass


# Define nested classes after KsonValue is fully defined
class _KsonObject(KsonValue):
    """A Kson object with key-value pairs."""

    def properties(self) -> Dict[str, "KsonValue"]:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.KsonValue.KsonObject.get_properties,
            [self.ptr],
        )
        raw_dict = _from_kotlin_map(
            result,
            "kson_kref_kotlin_String",
            "kson_kref_org_kson_KsonValue",
            str,
            KsonValue,
        )
        # Translate values to their specific subtypes
        return {k: v._translate() for k, v in raw_dict.items()}

    def property_keys(self) -> Dict[str, "KsonValue"]:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.KsonValue.KsonObject.get_propertyKeys,
            [self.ptr],
        )
        raw_dict = _from_kotlin_map(
            result,
            "kson_kref_kotlin_String",
            "kson_kref_org_kson_KsonValue_KsonString",
            str,
            KsonValue,
        )
        # Translate values to their specific subtypes
        return {k: v._translate() for k, v in raw_dict.items()}


class _KsonArray(KsonValue):
    """A Kson array with elements."""

    def elements(self) -> List["KsonValue"]:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.KsonValue.KsonArray.get_elements,
            [self.ptr],
        )
        result = _from_kotlin_list(result, "kson_kref_org_kson_KsonValue", KsonValue)
        return [item._translate() for item in result]


class _KsonString(KsonValue):
    """A Kson string value."""

    def value(self) -> str:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.KsonValue.KsonString.get_value, [self.ptr]
        )
        result = _from_kotlin_string(result)
        return result


class _KsonNumberInteger(KsonValue):
    """A Kson integer number value."""

    def value(self) -> int:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.KsonValue.KsonNumber.Integer.get_value,
            [self.ptr],
        )
        return result


class _KsonNumberDecimal(KsonValue):
    """A Kson decimal number value."""

    def value(self) -> float:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.KsonValue.KsonNumber.Decimal.get_value,
            [self.ptr],
        )
        return result


class _KsonBoolean(KsonValue):
    """A Kson boolean value."""

    def value(self) -> bool:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.KsonValue.KsonBoolean.get_value, [self.ptr]
        )
        return bool(result)


class _KsonNull(KsonValue):
    """A Kson null value."""

    pass


class _KsonEmbed(KsonValue):
    """A Kson embed block."""

    def tag(self) -> Optional[str]:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.KsonValue.KsonEmbed.get_tag, [self.ptr]
        )
        if result == ffi.NULL:
            return None
        return _from_kotlin_string(result)

    def metadata(self) -> Optional[str]:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.KsonValue.KsonEmbed.get_metadata,
            [self.ptr],
        )
        if result == ffi.NULL:
            return None
        return _from_kotlin_string(result)

    def content(self) -> str:
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.KsonValue.KsonEmbed.get_content, [self.ptr]
        )
        result = _from_kotlin_string(result)
        return result


# Attach as nested classes
KsonValue.KsonObject = _KsonObject
KsonValue.KsonArray = _KsonArray
KsonValue.KsonString = _KsonString
KsonValue.KsonBoolean = _KsonBoolean
KsonValue.KsonNull = _KsonNull
KsonValue.KsonEmbed = _KsonEmbed


# Create KsonNumber namespace
class _KsonNumber:
    """Base class for Kson number values."""

    Integer = _KsonNumberInteger
    Decimal = _KsonNumberDecimal


KsonValue.KsonNumber = _KsonNumber


class Kson:
    """The Kson language (https://kson.org)."""

    ptr: CData

    @staticmethod
    def get() -> Kson:
        result = _cast_and_call(symbols.kotlin.root.org.kson.Kson._instance, [])
        result_obj = object.__new__(Kson)
        result_obj.ptr = result
        return result_obj

    @staticmethod
    def format(kson: str, format_options: FormatOptions) -> str:
        """Formats Kson source with the specified formatting options.

        Args:
            kson: The Kson source to format.
            format_options: The formatting options to apply.

        Returns:
            The formatted Kson source.
        """
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.Kson.format,
            [
                symbols.kotlin.root.org.kson.Kson._instance(),
                kson.encode("utf-8"),
                format_options.ptr,
            ],
        )
        result = _from_kotlin_string(result)
        return result

    @staticmethod
    def to_json(kson: str, options: Optional[TranspileOptionsJson] = None) -> Result:
        """Converts Kson to Json.

        Args:
            kson: The Kson source to convert.
            options: Options for the JSON transpilation.

        Returns:
            A Result containing either the Json output or error messages.
        """
        if options is None:
            options = TranspileOptionsJson()
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.Kson.toJson,
            [
                symbols.kotlin.root.org.kson.Kson._instance(),
                kson.encode("utf-8"),
                options.ptr,
            ],
        )
        result = _init_wrapper(Result, result)
        result = result._translate()
        return result

    @staticmethod
    def to_yaml(kson: str, options: Optional[TranspileOptionsYaml] = None) -> Result:
        """Converts Kson to Yaml, preserving comments.

        Args:
            kson: The Kson source to convert.
            options: Options for the YAML transpilation.

        Returns:
            A Result containing either the Yaml output or error messages.
        """
        if options is None:
            options = TranspileOptionsYaml()
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.Kson.toYaml,
            [
                symbols.kotlin.root.org.kson.Kson._instance(),
                kson.encode("utf-8"),
                options.ptr,
            ],
        )
        result = _init_wrapper(Result, result)
        result = result._translate()
        return result

    @staticmethod
    def analyze(kson: str) -> Analysis:
        """Statically analyze the given Kson and return an Analysis object.

        Contains any messages generated along with a tokenized version of the source.
        Useful for tooling/editor support.

        Args:
            kson: The Kson source to analyze.

        Returns:
            An Analysis object containing messages and tokens.
        """
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.Kson.analyze,
            [symbols.kotlin.root.org.kson.Kson._instance(), kson.encode("utf-8")],
        )
        result = _init_wrapper(Analysis, result)
        return result

    @staticmethod
    def parse_schema(schema_kson: str) -> SchemaResult:
        """Parses a Kson schema definition and returns a validator for that schema.

        Args:
            schema_kson: The Kson source defining a Json Schema.

        Returns:
            A SchemaValidator that can validate Kson documents against the schema.
        """
        result = _cast_and_call(
            symbols.kotlin.root.org.kson.Kson.parseSchema,
            [
                symbols.kotlin.root.org.kson.Kson._instance(),
                schema_kson.encode("utf-8"),
            ],
        )
        result = _init_wrapper(SchemaResult, result)
        result = result._translate()
        return result

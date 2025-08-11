import sys
from enum import Enum
from cffi import FFI
ffi = FFI()

with open('kson_api.h', 'r') as f:
   header = f.read()
ffi.cdef(header)

LIBRARY_NAMES = {
    'win32': 'kson.dll',
    'darwin': 'kson.dylib',
    'linux': 'libkson.so',
}

lib = ffi.dlopen(LIBRARY_NAMES.get(sys.platform))
symbols = lib.libkson_symbols() if sys.platform == 'linux' else lib.kson_symbols()
kotlin_enum_type = "libkson_kref_kotlin_Enum" if sys.platform == 'linux' else "kson_kref_kotlin_Enum"

def cast_and_call(func, args):
    param_types = ffi.typeof(func).args

    casted_args = []
    for (arg, param_type) in zip(args, param_types):
        if isinstance(arg, ffi.CData):
            casted_args.append(cast(param_type.cname, arg))
        else:
            casted_args.append(arg)

    return func(*casted_args)

def cast(target_type_name, arg):
    addr = ffi.addressof(arg)
    return ffi.cast(f"{target_type_name} *", addr)[0]

def init_wrapper(target_type, ptr):
    ptr.pinned = ffi.gc(ptr.pinned, symbols.DisposeStablePointer)
    result = object.__new__(target_type)
    result.ptr = ptr
    return result

def init_enum_wrapper(target_type, ptr):
    enum_helper_instance = symbols.kotlin.root.org.kson.EnumHelper._instance()
    ordinal = symbols.kotlin.root.org.kson.EnumHelper.ordinal(enum_helper_instance, cast(kotlin_enum_type, ptr))
    instance = target_type(ordinal)
    symbols.DisposeStablePointer(ptr.pinned)
    return instance

def from_kotlin_string(ptr):
    python_string = ffi.string(ptr).decode('utf-8')
    symbols.DisposeString(ptr)
    return python_string

def from_kotlin_list(list, item_type, wrap_as):
    python_list = []
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

class FormatOptions:
    """Options for formatting Kson output."""
    def __init__(self, indent_type, formatting_style):
        result = cast_and_call(symbols.kotlin.root.org.kson.FormatOptions.FormatOptions, [indent_type.ptr, formatting_style.to_kotlin_enum()])
        self.ptr = result

    def indent_type(self):
        result = cast_and_call(symbols.kotlin.root.org.kson.FormatOptions.get_indentType, [self.ptr])
        result = init_wrapper(IndentType, result)
        result = result._translate()
        return result

    def formatting_style(self):
        result = cast_and_call(symbols.kotlin.root.org.kson.FormatOptions.get_formattingStyle, [self.ptr])
        result = init_enum_wrapper(FormattingStyle, result)
        return result

class Analysis:
    """The result of statically analyzing a Kson document."""
    def errors(self):
        result = cast_and_call(symbols.kotlin.root.org.kson.Analysis.get_errors, [self.ptr])
        result = from_kotlin_list(result, "kson_kref_org_kson_Message", Message)
        return result

    def tokens(self):
        result = cast_and_call(symbols.kotlin.root.org.kson.Analysis.get_tokens, [self.ptr])
        result = from_kotlin_list(result, "kson_kref_org_kson_Token", Token)
        return result

class Position:
    """A zero-based line/column position in a document.

    Args:
        line: The line number where the error occurred (0-based).
        column: The column number where the error occurred (0-based).
    """
    def line(self):
        result = cast_and_call(symbols.kotlin.root.org.kson.Position.get_line, [self.ptr])
        return result

    def column(self):
        result = cast_and_call(symbols.kotlin.root.org.kson.Position.get_column, [self.ptr])
        return result

class Result:
    """Result of a Kson conversion operation."""
    def __init__(self):
        result = cast_and_call(symbols.kotlin.root.org.kson.Result.Result, [])
        self.ptr = result

    def _translate(self):
        subclass_type = symbols.kotlin.root.org.kson.Result.Success._type()
        if symbols.IsInstance(self.ptr.pinned, subclass_type):
            return init_wrapper(Result.Success, self.ptr)
        subclass_type = symbols.kotlin.root.org.kson.Result.Failure._type()
        if symbols.IsInstance(self.ptr.pinned, subclass_type):
            return init_wrapper(Result.Failure, self.ptr)

    class Success:
        def output(self):
            result = cast_and_call(symbols.kotlin.root.org.kson.Result.Success.get_output, [self.ptr])
            result = from_kotlin_string(result)
            return result

    class Failure:
        def errors(self):
            result = cast_and_call(symbols.kotlin.root.org.kson.Result.Failure.get_errors, [self.ptr])
            result = from_kotlin_list(result, "kson_kref_org_kson_Message", Message)
            return result

class SchemaResult:
    """A parse_schema result."""
    def __init__(self):
        result = cast_and_call(symbols.kotlin.root.org.kson.SchemaResult.SchemaResult, [])
        self.ptr = result

    def _translate(self):
        subclass_type = symbols.kotlin.root.org.kson.SchemaResult.Failure._type()
        if symbols.IsInstance(self.ptr.pinned, subclass_type):
            return init_wrapper(SchemaResult.Failure, self.ptr)
        subclass_type = symbols.kotlin.root.org.kson.SchemaResult.Success._type()
        if symbols.IsInstance(self.ptr.pinned, subclass_type):
            return init_wrapper(SchemaResult.Success, self.ptr)

    class Success:
        def schema_validator(self):
            result = cast_and_call(symbols.kotlin.root.org.kson.SchemaResult.Success.get_schemaValidator, [self.ptr])
            result = init_wrapper(SchemaValidator, result)
            return result

    class Failure:
        def errors(self):
            result = cast_and_call(symbols.kotlin.root.org.kson.SchemaResult.Failure.get_errors, [self.ptr])
            result = from_kotlin_list(result, "kson_kref_org_kson_Message", Message)
            return result

class Message:
    """Represents a message logged during Kson processing."""
    def message(self):
        result = cast_and_call(symbols.kotlin.root.org.kson.Message.get_message, [self.ptr])
        result = from_kotlin_string(result)
        return result

    def start(self):
        result = cast_and_call(symbols.kotlin.root.org.kson.Message.get_start, [self.ptr])
        result = init_wrapper(Position, result)
        return result

    def end(self):
        result = cast_and_call(symbols.kotlin.root.org.kson.Message.get_end, [self.ptr])
        result = init_wrapper(Position, result)
        return result

class Token:
    """Token produced by the lexing phase of a Kson parse."""
    def token_type(self):
        result = cast_and_call(symbols.kotlin.root.org.kson.Token.get_tokenType, [self.ptr])
        result = init_enum_wrapper(TokenType, result)
        return result

    def text(self):
        result = cast_and_call(symbols.kotlin.root.org.kson.Token.get_text, [self.ptr])
        result = from_kotlin_string(result)
        return result

    def start(self):
        result = cast_and_call(symbols.kotlin.root.org.kson.Token.get_start, [self.ptr])
        result = init_wrapper(Position, result)
        return result

    def end(self):
        result = cast_and_call(symbols.kotlin.root.org.kson.Token.get_end, [self.ptr])
        result = init_wrapper(Position, result)
        return result

class Kson:
    """The Kson language (https://kson.org)."""
    @staticmethod
    def get():
        result = cast_and_call(symbols.kotlin.root.org.kson.Kson._instance, [])
        result_obj = object.__new__(Kson)
        result_obj.ptr = result
        return result_obj

    @staticmethod
    def format(kson, format_options):
        """Formats Kson source with the specified formatting options.

        Args:
            kson: The Kson source to format.
            format_options: The formatting options to apply.

        Returns:
            The formatted Kson source.
        """
        result = cast_and_call(symbols.kotlin.root.org.kson.Kson.format, [symbols.kotlin.root.org.kson.Kson._instance(), kson.encode('utf-8'), format_options.ptr])
        result = from_kotlin_string(result)
        return result

    @staticmethod
    def to_json(kson):
        """Converts Kson to Json.

        Args:
            kson: The Kson source to convert.

        Returns:
            A Result containing either the Json output or error messages.
        """
        result = cast_and_call(symbols.kotlin.root.org.kson.Kson.toJson, [symbols.kotlin.root.org.kson.Kson._instance(), kson.encode('utf-8')])
        result = init_wrapper(Result, result)
        result = result._translate()
        return result

    @staticmethod
    def to_yaml(kson):
        """Converts Kson to Yaml, preserving comments.

        Args:
            kson: The Kson source to convert.

        Returns:
            A Result containing either the Yaml output or error messages.
        """
        result = cast_and_call(symbols.kotlin.root.org.kson.Kson.toYaml, [symbols.kotlin.root.org.kson.Kson._instance(), kson.encode('utf-8')])
        result = init_wrapper(Result, result)
        result = result._translate()
        return result

    @staticmethod
    def analyze(kson):
        """Statically analyze the given Kson and return an Analysis object.

        Contains any messages generated along with a tokenized version of the source.
        Useful for tooling/editor support.

        Args:
            kson: The Kson source to analyze.

        Returns:
            An Analysis object containing messages and tokens.
        """
        result = cast_and_call(symbols.kotlin.root.org.kson.Kson.analyze, [symbols.kotlin.root.org.kson.Kson._instance(), kson.encode('utf-8')])
        result = init_wrapper(Analysis, result)
        return result

    @staticmethod
    def parse_schema(schema_kson):
        """Parses a Kson schema definition and returns a validator for that schema.

        Args:
            schema_kson: The Kson source defining a Json Schema.

        Returns:
            A SchemaValidator that can validate Kson documents against the schema.
        """
        result = cast_and_call(symbols.kotlin.root.org.kson.Kson.parseSchema, [symbols.kotlin.root.org.kson.Kson._instance(), schema_kson.encode('utf-8')])
        result = init_wrapper(SchemaResult, result)
        result = result._translate()
        return result

class SchemaValidator:
    """A validator that can check if Kson source conforms to a schema."""
    def validate(self, kson):
        """Validates the given Kson source against this validator's schema.

        Args:
            kson: The Kson source to validate.

        Returns:
            A list of validation error messages, or empty list if valid.
        """
        result = cast_and_call(symbols.kotlin.root.org.kson.SchemaValidator.validate, [self.ptr, kson.encode('utf-8')])
        result = from_kotlin_list(result, "kson_kref_org_kson_Message", Message)
        return result

class IndentType:
    """Options for indenting Kson Output."""
    def __init__(self):
        result = cast_and_call(symbols.kotlin.root.org.kson.IndentType.IndentType, [])
        self.ptr = result

    def _translate(self):
        subclass_type = symbols.kotlin.root.org.kson.IndentType.Spaces._type()
        if symbols.IsInstance(self.ptr.pinned, subclass_type):
            return init_wrapper(IndentType.Spaces, self.ptr)
        subclass_type = symbols.kotlin.root.org.kson.IndentType.Tabs._type()
        if symbols.IsInstance(self.ptr.pinned, subclass_type):
            return init_wrapper(IndentType.Tabs, self.ptr)

    class Spaces:
        """Use spaces for indentation with the specified count."""
        def __init__(self, size):
            result = cast_and_call(symbols.kotlin.root.org.kson.IndentType.Spaces.Spaces, [size])
            self.ptr = result

        def size(self):
            result = cast_and_call(symbols.kotlin.root.org.kson.IndentType.Spaces.get_size, [self.ptr])
            return result

    class Tabs:
        """Use tabs for indentation."""
        @staticmethod
        def get():
            result = cast_and_call(symbols.kotlin.root.org.kson.IndentType.Tabs._instance, [])
            result_obj = object.__new__(IndentType.Tabs)
            result_obj.ptr = result
            return result_obj

class FormattingStyle(Enum):
    """FormattingStyle options for Kson Output."""
    def to_kotlin_enum(self):
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

    def name(self):
        enum_helper_instance = symbols.kotlin.root.org.kson.EnumHelper._instance()
        kotlin_enum = self.to_kotlin_enum()
        return from_kotlin_string(symbols.kotlin.root.org.kson.EnumHelper.name(enum_helper_instance, cast(kotlin_enum_type, kotlin_enum)))

    PLAIN = 0
    DELIMITED = 1
    COMPACT = 2

class TokenType(Enum):
    def to_kotlin_enum(self):
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
                result = symbols.kotlin.root.org.kson.TokenType.EMBED_PREAMBLE_NEWLINE.get()
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

    def name(self):
        enum_helper_instance = symbols.kotlin.root.org.kson.EnumHelper._instance()
        kotlin_enum = self.to_kotlin_enum()
        return from_kotlin_string(symbols.kotlin.root.org.kson.EnumHelper.name(enum_helper_instance, cast(kotlin_enum_type, kotlin_enum)))

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

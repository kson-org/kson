mod util;

#[cfg(test)]
mod test;

#[macro_use]
mod macros;

use crate::util::{FromKotlinObject, KsonPtr, OwnedKotlinPtr, ToKotlinObject};

#[cfg(any(feature = "dynamic-linking", target_os = "windows"))]
static KSON_LIB: std::sync::LazyLock<libloading::Library> = std::sync::LazyLock::new(|| unsafe {
    #[cfg(target_os = "windows")]
    let lib_name = "kson.dll";
    #[cfg(target_os = "linux")]
    let lib_name = "libkson.so";
    #[cfg(target_os = "macos")]
    let lib_name = "libkson.dylib";

    libloading::Library::new(lib_name).unwrap()
});

#[cfg(any(feature = "dynamic-linking", target_os = "windows"))]
pub(crate) static KSON_SYMBOLS: std::sync::LazyLock<kson_sys::kson_ExportedSymbols> =
    std::sync::LazyLock::new(|| unsafe {
        let kson_symbols: &[u8] = if cfg!(target_os = "windows") {
            b"kson_symbols"
        } else {
            b"libkson_symbols"
        };

        let func: libloading::Symbol<
            unsafe extern "C" fn() -> *mut kson_sys::kson_ExportedSymbols,
        > = KSON_LIB.get(kson_symbols).unwrap();
        *func()
    });

#[cfg(not(any(feature = "dynamic-linking", target_os = "windows")))]
unsafe extern "C" {
    fn libkson_symbols() -> *mut kson_sys::kson_ExportedSymbols;
}

#[cfg(not(any(feature = "dynamic-linking", target_os = "windows")))]
pub(crate) static KSON_SYMBOLS: std::sync::LazyLock<kson_sys::kson_ExportedSymbols> =
    std::sync::LazyLock::new(|| unsafe { *libkson_symbols() });

declare_kotlin_object!(
    /// Options for formatting Kson output.
    FormatOptions
);

impl FormatOptions {
    pub fn new(indent_type: &IndentType, formatting_style: &FormattingStyle) -> Self {
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .FormatOptions
            .FormatOptions
            .unwrap();
        let p0 = kson_sys::kson_kref_org_kson_IndentType {
            pinned: indent_type.to_kotlin_object(),
        };
        let p1 = kson_sys::kson_kref_org_kson_FormattingStyle {
            pinned: formatting_style.to_kotlin_object(),
        };
        let result = unsafe { f(p0, p1) };
        FormatOptions {
            kson_ref: KsonPtr {
                inner: std::sync::Arc::new(OwnedKotlinPtr {
                    inner: result.pinned,
                }),
            },
        }
    }

    pub fn indent_type(&self) -> IndentType {
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .FormatOptions
            .get_indentType
            .unwrap();
        let result = unsafe {
            f(kson_sys::kson_kref_org_kson_FormatOptions {
                pinned: self.kson_ref.inner.inner,
            })
        };
        IndentType::from_kotlin_object(result.pinned)
    }

    pub fn formatting_style(&self) -> FormattingStyle {
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .FormatOptions
            .get_formattingStyle
            .unwrap();
        let result = unsafe {
            f(kson_sys::kson_kref_org_kson_FormatOptions {
                pinned: self.kson_ref.inner.inner,
            })
        };
        FormattingStyle::from_kotlin_object(result.pinned)
    }
}

declare_kotlin_object! {
    /// The result of statically analyzing a Kson document
    Analysis
}

impl Analysis {
    pub fn errors(&self) -> Vec<Message> {
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .Analysis
            .get_errors
            .unwrap();
        let result = unsafe {
            f(kson_sys::kson_kref_org_kson_Analysis {
                pinned: self.kson_ref.inner.inner,
            })
        };
        util::from_kotlin_list(result)
    }

    pub fn tokens(&self) -> Vec<Token> {
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .Analysis
            .get_tokens
            .unwrap();
        let result = unsafe {
            f(kson_sys::kson_kref_org_kson_Analysis {
                pinned: self.kson_ref.inner.inner,
            })
        };
        util::from_kotlin_list(result)
    }

    pub fn kson_value(&self) -> Option<KsonValue> {
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .Analysis
            .get_ksonValue
            .unwrap();
        let result = unsafe {
            f(kson_sys::kson_kref_org_kson_Analysis {
                pinned: self.kson_ref.inner.inner,
            })
        };
        if result.pinned == std::ptr::null_mut() {
            None
        } else {
            Some(KsonValue::from_kotlin_object(result.pinned))
        }
    }
}

declare_kotlin_object! {
    /// A zero-based line/column position in a document
    ///
    /// @param line The line number where the error occurred (0-based)
    /// @param column The column number where the error occurred (0-based)
    Position
}

impl std::fmt::Debug for Position {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Position")
            .field("line", &self.line())
            .field("column", &self.column())
            .finish()
    }
}

impl Eq for Position {}
impl PartialEq for Position {
    fn eq(&self, other: &Position) -> bool {
        util::equals(self, other)
    }
}
impl std::hash::Hash for Position {
    fn hash<H>(&self, state: &mut H)
    where
        H: std::hash::Hasher,
    {
        util::apply_hash_code(self, state)
    }
}

impl Position {
    pub fn line(&self) -> i32 {
        let f = KSON_SYMBOLS.kotlin.root.org.kson.Position.get_line.unwrap();

        unsafe {
            f(kson_sys::kson_kref_org_kson_Position {
                pinned: self.kson_ref.inner.inner,
            })
        }
    }
    pub fn column(&self) -> i32 {
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .Position
            .get_column
            .unwrap();

        unsafe {
            f(kson_sys::kson_kref_org_kson_Position {
                pinned: self.kson_ref.inner.inner,
            })
        }
    }
}

declare_kotlin_object!(ResultFailure);

impl ResultFailure {
    pub fn errors(&self) -> Vec<Message> {
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .Result
            .Failure
            .get_errors
            .unwrap();
        let result = unsafe {
            f(kson_sys::kson_kref_org_kson_Result_Failure {
                pinned: self.kson_ref.inner.inner,
            })
        };
        util::from_kotlin_list(result)
    }
}

declare_kotlin_object!(ResultSuccess);

impl ResultSuccess {
    pub fn output(&self) -> String {
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .Result
            .Success
            .get_output
            .unwrap();
        let result = unsafe {
            f(kson_sys::kson_kref_org_kson_Result_Success {
                pinned: self.kson_ref.inner.inner,
            })
        };
        util::from_kotlin_string(result)
    }
}

impl_kotlin_object_for_enum!(
    Result<ResultSuccess, ResultFailure>,
    Ok where ResultSuccess = KSON_SYMBOLS.kotlin.root.org.kson.Result.Success,
    Err where ResultFailure = KSON_SYMBOLS.kotlin.root.org.kson.Result.Failure,
);

declare_kotlin_object!(SchemaResultFailure);

impl SchemaResultFailure {
    pub fn errors(&self) -> Vec<Message> {
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .SchemaResult
            .Failure
            .get_errors
            .unwrap();
        let result = unsafe {
            f(kson_sys::kson_kref_org_kson_SchemaResult_Failure {
                pinned: self.kson_ref.inner.inner,
            })
        };
        util::from_kotlin_list(result)
    }
}

declare_kotlin_object!(SchemaResultSuccess);

impl SchemaResultSuccess {
    pub fn schema_validator(&self) -> SchemaValidator {
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .SchemaResult
            .Success
            .get_schemaValidator
            .unwrap();
        let result = unsafe {
            f(kson_sys::kson_kref_org_kson_SchemaResult_Success {
                pinned: self.kson_ref.inner.inner,
            })
        };
        SchemaValidator {
            kson_ref: KsonPtr {
                inner: std::sync::Arc::new(OwnedKotlinPtr {
                    inner: result.pinned,
                }),
            },
        }
    }
}

impl_kotlin_object_for_enum!(
    Result<SchemaResultSuccess, SchemaResultFailure>,
    Ok where SchemaResultSuccess = KSON_SYMBOLS.kotlin.root.org.kson.SchemaResult.Success,
    Err where SchemaResultFailure = KSON_SYMBOLS.kotlin.root.org.kson.SchemaResult.Failure,
);

declare_kotlin_object! {
    /// Represents a message logged during Kson processing
    Message
}

impl Message {
    pub fn message(&self) -> String {
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .Message
            .get_message
            .unwrap();
        let result = unsafe {
            f(kson_sys::kson_kref_org_kson_Message {
                pinned: self.kson_ref.inner.inner,
            })
        };
        util::from_kotlin_string(result)
    }

    pub fn start(&self) -> Position {
        let f = KSON_SYMBOLS.kotlin.root.org.kson.Message.get_start.unwrap();
        let result = unsafe {
            f(kson_sys::kson_kref_org_kson_Message {
                pinned: self.kson_ref.inner.inner,
            })
        };
        Position {
            kson_ref: KsonPtr {
                inner: std::sync::Arc::new(OwnedKotlinPtr {
                    inner: result.pinned,
                }),
            },
        }
    }

    pub fn end(&self) -> Position {
        let f = KSON_SYMBOLS.kotlin.root.org.kson.Message.get_end.unwrap();
        let result = unsafe {
            f(kson_sys::kson_kref_org_kson_Message {
                pinned: self.kson_ref.inner.inner,
            })
        };
        Position {
            kson_ref: KsonPtr {
                inner: std::sync::Arc::new(OwnedKotlinPtr {
                    inner: result.pinned,
                }),
            },
        }
    }
}

declare_kotlin_object! {
    /// [Token] produced by the lexing phase of a Kson parse
    Token
}

impl Token {
    pub fn token_type(&self) -> TokenType {
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .Token
            .get_tokenType
            .unwrap();
        let result = unsafe {
            f(kson_sys::kson_kref_org_kson_Token {
                pinned: self.kson_ref.inner.inner,
            })
        };
        TokenType::from_kotlin_object(result.pinned)
    }

    pub fn text(&self) -> String {
        let f = KSON_SYMBOLS.kotlin.root.org.kson.Token.get_text.unwrap();
        let result = unsafe {
            f(kson_sys::kson_kref_org_kson_Token {
                pinned: self.kson_ref.inner.inner,
            })
        };
        util::from_kotlin_string(result)
    }

    pub fn start(&self) -> Position {
        let f = KSON_SYMBOLS.kotlin.root.org.kson.Token.get_start.unwrap();
        let result = unsafe {
            f(kson_sys::kson_kref_org_kson_Token {
                pinned: self.kson_ref.inner.inner,
            })
        };
        Position {
            kson_ref: KsonPtr {
                inner: std::sync::Arc::new(OwnedKotlinPtr {
                    inner: result.pinned,
                }),
            },
        }
    }

    pub fn end(&self) -> Position {
        let f = KSON_SYMBOLS.kotlin.root.org.kson.Token.get_end.unwrap();
        let result = unsafe {
            f(kson_sys::kson_kref_org_kson_Token {
                pinned: self.kson_ref.inner.inner,
            })
        };
        Position {
            kson_ref: KsonPtr {
                inner: std::sync::Arc::new(OwnedKotlinPtr {
                    inner: result.pinned,
                }),
            },
        }
    }
}

declare_kotlin_object! {
    /// The [Kson](https://kson.org) language
    Kson
}

impl Kson {
    pub fn get() -> Self {
        let f = KSON_SYMBOLS.kotlin.root.org.kson.Kson._instance.unwrap();
        let result = unsafe { f() };
        Kson {
            kson_ref: KsonPtr {
                inner: std::sync::Arc::new(OwnedKotlinPtr {
                    inner: result.pinned,
                }),
            },
        }
    }

    /// Formats Kson source with the specified formatting options.
    ///
    /// @param kson The Kson source to format
    /// @param format_options The formatting options to apply
    /// @return The formatted Kson source
    pub fn format(kson: &str, format_options: &FormatOptions) -> String {
        let f = KSON_SYMBOLS.kotlin.root.org.kson.Kson.format.unwrap();
        let p0 = util::to_kotlin_string(kson);
        let p0 = p0.as_ptr();
        let p1 = kson_sys::kson_kref_org_kson_FormatOptions {
            pinned: format_options.to_kotlin_object(),
        };
        let result = unsafe {
            f(
                KSON_SYMBOLS.kotlin.root.org.kson.Kson._instance.unwrap()(),
                p0,
                p1,
            )
        };
        util::from_kotlin_string(result)
    }

    /// Converts Kson to Json.
    ///
    /// @param kson The Kson source to convert
    /// @param retain_embed_tags Whether to retain the embed tags in the result (default: true)
    /// @return A Result containing either the Json output or error messages
    #[allow(clippy::wrong_self_convention)]
    pub fn to_json(kson: &str) -> Result<ResultSuccess, ResultFailure> {
        Self::to_json_with_options(kson, true)
    }

    /// Converts Kson to Json with options.
    ///
    /// @param kson The Kson source to convert
    /// @param retain_embed_tags Whether to retain the embed tags in the result
    /// @return A Result containing either the Json output or error messages
    #[allow(clippy::wrong_self_convention)]
    pub fn to_json_with_options(
        kson: &str,
        retain_embed_tags: bool,
    ) -> Result<ResultSuccess, ResultFailure> {
        let f = KSON_SYMBOLS.kotlin.root.org.kson.Kson.toJson.unwrap();
        let p0 = util::to_kotlin_string(kson);
        let p0 = p0.as_ptr();
        let result = unsafe {
            f(
                KSON_SYMBOLS.kotlin.root.org.kson.Kson._instance.unwrap()(),
                p0,
                retain_embed_tags,
            )
        };
        Result::from_kotlin_object(result.pinned)
    }

    /// Converts Kson to Yaml, preserving comments
    ///
    /// @param kson The Kson source to convert
    /// @param retain_embed_tags Whether to retain the embed tags in the result (default: true)
    /// @return A Result containing either the Yaml output or error messages
    #[allow(clippy::wrong_self_convention)]
    pub fn to_yaml(kson: &str) -> Result<ResultSuccess, ResultFailure> {
        Self::to_yaml_with_options(kson, true)
    }

    /// Converts Kson to Yaml with options, preserving comments
    ///
    /// @param kson The Kson source to convert
    /// @param retain_embed_tags Whether to retain the embed tags in the result
    /// @return A Result containing either the Yaml output or error messages
    #[allow(clippy::wrong_self_convention)]
    pub fn to_yaml_with_options(
        kson: &str,
        retain_embed_tags: bool,
    ) -> Result<ResultSuccess, ResultFailure> {
        let f = KSON_SYMBOLS.kotlin.root.org.kson.Kson.toYaml.unwrap();
        let p0 = util::to_kotlin_string(kson);
        let p0 = p0.as_ptr();
        let result = unsafe {
            f(
                KSON_SYMBOLS.kotlin.root.org.kson.Kson._instance.unwrap()(),
                p0,
                retain_embed_tags,
            )
        };
        Result::from_kotlin_object(result.pinned)
    }

    /// Statically analyze the given Kson and return an [Analysis] object containing any messages generated along with a
    /// tokenized version of the source.  Useful for tooling/editor support.
    pub fn analyze(kson: &str) -> Analysis {
        let f = KSON_SYMBOLS.kotlin.root.org.kson.Kson.analyze.unwrap();
        let p0 = util::to_kotlin_string(kson);
        let p0 = p0.as_ptr();
        let result = unsafe {
            f(
                KSON_SYMBOLS.kotlin.root.org.kson.Kson._instance.unwrap()(),
                p0,
            )
        };
        Analysis {
            kson_ref: KsonPtr {
                inner: std::sync::Arc::new(OwnedKotlinPtr {
                    inner: result.pinned,
                }),
            },
        }
    }

    /// Parses a Kson schema definition and returns a validator for that schema.
    ///
    /// @param schema_kson The Kson source defining a Json Schema
    /// @return A SchemaValidator that can validate Kson documents against the schema
    pub fn parse_schema(schema_kson: &str) -> Result<SchemaResultSuccess, SchemaResultFailure> {
        let f = KSON_SYMBOLS.kotlin.root.org.kson.Kson.parseSchema.unwrap();
        let p0 = util::to_kotlin_string(schema_kson);
        let p0 = p0.as_ptr();
        let result = unsafe {
            f(
                KSON_SYMBOLS.kotlin.root.org.kson.Kson._instance.unwrap()(),
                p0,
            )
        };
        Result::from_kotlin_object(result.pinned)
    }
}

declare_kotlin_object! {
    /// A validator that can check if Kson source conforms to a schema.
    SchemaValidator
}

impl SchemaValidator {
    /// Validates the given Kson source against this validator's schema.
    /// @param kson The Kson source to validate
    ///
    /// @return A list of validation error messages, or empty list if valid
    pub fn validate(&self, kson: &str) -> Vec<Message> {
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .SchemaValidator
            .validate
            .unwrap();
        let p0 = util::to_kotlin_string(kson);
        let p0 = p0.as_ptr();
        let result = unsafe {
            f(
                kson_sys::kson_kref_org_kson_SchemaValidator {
                    pinned: self.kson_ref.inner.inner,
                },
                p0,
            )
        };
        util::from_kotlin_list(result)
    }
}

declare_kotlin_object! {
    /// Use spaces for indentation with the specified count
    IndentTypeSpaces
}

impl IndentTypeSpaces {
    pub fn new(size: i32) -> Self {
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .IndentType
            .Spaces
            .Spaces
            .unwrap();
        let p0 = size;
        let result = unsafe { f(p0) };
        IndentTypeSpaces {
            kson_ref: KsonPtr {
                inner: std::sync::Arc::new(OwnedKotlinPtr {
                    inner: result.pinned,
                }),
            },
        }
    }
    pub fn size(&self) -> i32 {
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .IndentType
            .Spaces
            .get_size
            .unwrap();

        unsafe {
            f(kson_sys::kson_kref_org_kson_IndentType_Spaces {
                pinned: self.kson_ref.inner.inner,
            })
        }
    }
}

declare_kotlin_object! {
    /// Use tabs for indentation
    IndentTypeTabs
}

impl IndentTypeTabs {
    pub fn get() -> Self {
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .IndentType
            .Tabs
            ._instance
            .unwrap();
        let result = unsafe { f() };
        IndentTypeTabs {
            kson_ref: KsonPtr {
                inner: std::sync::Arc::new(OwnedKotlinPtr {
                    inner: result.pinned,
                }),
            },
        }
    }
}

/// Options for indenting Kson Output
pub enum IndentType {
    /// Use spaces for indentation with the specified count
    Spaces(IndentTypeSpaces),
    /// Use tabs for indentation
    Tabs(IndentTypeTabs),
}

impl_kotlin_object_for_enum!(
    IndentType,
    IndentType::Spaces where IndentTypeSpaces = KSON_SYMBOLS.kotlin.root.org.kson.IndentType.Spaces,
    IndentType::Tabs where IndentTypeTabs = KSON_SYMBOLS.kotlin.root.org.kson.IndentType.Tabs,
);

/// [FormattingStyle] options for Kson Output
#[derive(Copy, Clone)]
pub enum FormattingStyle {
    Plain,
    Delimited,
    Compact,
}

impl_kotlin_object_for_c_enum!(
    FormattingStyle,
    0 = FormattingStyle::Plain = KSON_SYMBOLS.kotlin.root.org.kson.FormattingStyle.PLAIN,
    1 = FormattingStyle::Delimited = KSON_SYMBOLS.kotlin.root.org.kson.FormattingStyle.DELIMITED,
    2 = FormattingStyle::Compact = KSON_SYMBOLS.kotlin.root.org.kson.FormattingStyle.COMPACT,
);

impl FormattingStyle {
    pub fn name(self) -> String {
        util::enum_name(self)
    }
}

#[derive(Copy, Clone)]
pub enum TokenType {
    CurlyBraceL,
    CurlyBraceR,
    SquareBracketL,
    SquareBracketR,
    AngleBracketL,
    AngleBracketR,
    Colon,
    Dot,
    EndDash,
    Comma,
    Comment,
    EmbedOpenDelim,
    EmbedCloseDelim,
    EmbedTag,
    EmbedTagStop,
    EmbedMetadata,
    EmbedPreambleNewline,
    EmbedContent,
    False,
    UnquotedString,
    IllegalChar,
    ListDash,
    Null,
    Number,
    StringOpenQuote,
    StringCloseQuote,
    StringContent,
    True,
    Whitespace,
    Eof,
}

impl TokenType {
    pub fn name(self) -> String {
        util::enum_name(self)
    }
}

impl_kotlin_object_for_c_enum!(
    TokenType,
    0 = TokenType::CurlyBraceL = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.CURLY_BRACE_L,
    1 = TokenType::CurlyBraceR = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.CURLY_BRACE_R,
    2 = TokenType::SquareBracketL = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.SQUARE_BRACKET_L,
    3 = TokenType::SquareBracketR = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.SQUARE_BRACKET_R,
    4 = TokenType::AngleBracketL = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.ANGLE_BRACKET_L,
    5 = TokenType::AngleBracketR = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.ANGLE_BRACKET_R,
    6 = TokenType::Colon = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.COLON,
    7 = TokenType::Dot = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.DOT,
    8 = TokenType::EndDash = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.END_DASH,
    9 = TokenType::Comma = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.COMMA,
    10 = TokenType::Comment = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.COMMENT,
    11 = TokenType::EmbedOpenDelim = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.EMBED_OPEN_DELIM,
    12 = TokenType::EmbedCloseDelim = KSON_SYMBOLS
        .kotlin
        .root
        .org
        .kson
        .TokenType
        .EMBED_CLOSE_DELIM,
    13 = TokenType::EmbedTag = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.EMBED_TAG,
    14 = TokenType::EmbedTagStop = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.EMBED_TAG_STOP,
    15 = TokenType::EmbedMetadata = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.EMBED_METADATA,
    16 = TokenType::EmbedPreambleNewline = KSON_SYMBOLS
        .kotlin
        .root
        .org
        .kson
        .TokenType
        .EMBED_PREAMBLE_NEWLINE,
    17 = TokenType::EmbedContent = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.EMBED_CONTENT,
    18 = TokenType::False = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.FALSE,
    19 = TokenType::UnquotedString = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.UNQUOTED_STRING,
    20 = TokenType::IllegalChar = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.ILLEGAL_CHAR,
    21 = TokenType::ListDash = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.LIST_DASH,
    22 = TokenType::Null = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.NULL,
    23 = TokenType::Number = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.NUMBER,
    24 = TokenType::StringOpenQuote = KSON_SYMBOLS
        .kotlin
        .root
        .org
        .kson
        .TokenType
        .STRING_OPEN_QUOTE,
    25 = TokenType::StringCloseQuote = KSON_SYMBOLS
        .kotlin
        .root
        .org
        .kson
        .TokenType
        .STRING_CLOSE_QUOTE,
    26 = TokenType::StringContent = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.STRING_CONTENT,
    27 = TokenType::True = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.TRUE,
    28 = TokenType::Whitespace = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.WHITESPACE,
    29 = TokenType::Eof = KSON_SYMBOLS.kotlin.root.org.kson.TokenType.EOF,
);

#[derive(Copy, Clone)]
pub enum KsonValueType {
    Object,
    Array,
    String,
    Integer,
    Decimal,
    Boolean,
    Null,
    Embed,
}

impl_kotlin_object_for_c_enum!(
    KsonValueType,
    0 = KsonValueType::Object = KSON_SYMBOLS.kotlin.root.org.kson.KsonValueType.OBJECT,
    1 = KsonValueType::Array = KSON_SYMBOLS.kotlin.root.org.kson.KsonValueType.ARRAY,
    2 = KsonValueType::String = KSON_SYMBOLS.kotlin.root.org.kson.KsonValueType.STRING,
    3 = KsonValueType::Integer = KSON_SYMBOLS.kotlin.root.org.kson.KsonValueType.INTEGER,
    4 = KsonValueType::Decimal = KSON_SYMBOLS.kotlin.root.org.kson.KsonValueType.DECIMAL,
    5 = KsonValueType::Boolean = KSON_SYMBOLS.kotlin.root.org.kson.KsonValueType.BOOLEAN,
    6 = KsonValueType::Null = KSON_SYMBOLS.kotlin.root.org.kson.KsonValueType.NULL,
    7 = KsonValueType::Embed = KSON_SYMBOLS.kotlin.root.org.kson.KsonValueType.EMBED,
);

impl KsonValueType {
    pub fn name(self) -> String {
        util::enum_name(self)
    }
}

/// A parsed Kson value
#[derive(Clone)]
pub enum KsonValue {
    KsonObject(KsonValueObject),
    KsonArray(KsonValueArray),
    KsonString(KsonValueString),
    KsonInteger(KsonValueInteger),
    KsonDecimal(KsonValueDecimal),
    KsonBoolean(KsonValueBoolean),
    KsonNull(KsonValueNull),
    KsonEmbed(KsonValueEmbed),
}

impl_kotlin_object_for_enum!(
    KsonValue,
    KsonValue::KsonObject where KsonValueObject = KSON_SYMBOLS.kotlin.root.org.kson.KsonValue.KsonObject,
    KsonValue::KsonArray where KsonValueArray = KSON_SYMBOLS.kotlin.root.org.kson.KsonValue.KsonArray,
    KsonValue::KsonString where KsonValueString = KSON_SYMBOLS.kotlin.root.org.kson.KsonValue.KsonString,
    KsonValue::KsonInteger where KsonValueInteger = KSON_SYMBOLS.kotlin.root.org.kson.KsonValue.KsonNumber.Integer,
    KsonValue::KsonDecimal where KsonValueDecimal = KSON_SYMBOLS.kotlin.root.org.kson.KsonValue.KsonNumber.Decimal,
    KsonValue::KsonBoolean where KsonValueBoolean = KSON_SYMBOLS.kotlin.root.org.kson.KsonValue.KsonBoolean,
    KsonValue::KsonNull where KsonValueNull = KSON_SYMBOLS.kotlin.root.org.kson.KsonValue.KsonNull,
    KsonValue::KsonEmbed where KsonValueEmbed = KSON_SYMBOLS.kotlin.root.org.kson.KsonValue.KsonEmbed,
);

impl std::fmt::Debug for KsonValue {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", util::to_string(self))
    }
}

impl Eq for KsonValue {}
impl PartialEq for KsonValue {
    fn eq(&self, other: &KsonValue) -> bool {
        util::equals(self, other)
    }
}
impl std::hash::Hash for KsonValue {
    fn hash<H>(&self, state: &mut H)
    where
        H: std::hash::Hasher,
    {
        util::apply_hash_code(self, state)
    }
}

impl KsonValue {
    pub fn value_type(&self) -> KsonValueType {
        let ptr = self.to_kotlin_object();
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .KsonValue
            .get_type
            .unwrap();
        let result = unsafe { f(kson_sys::kson_kref_org_kson_KsonValue { pinned: ptr }) };
        KsonValueType::from_kotlin_object(result.pinned)
    }

    pub fn start(&self) -> Position {
        Self::start_inner(self)
    }

    pub fn end(&self) -> Position {
        Self::end_inner(self)
    }

    fn start_inner<T: ToKotlinObject>(obj: T) -> Position {
        let ptr = obj.to_kotlin_object();
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .KsonValue
            .get_start
            .unwrap();
        let result = unsafe { f(kson_sys::kson_kref_org_kson_KsonValue { pinned: ptr }) };
        Position::from_kotlin_object(result.pinned)
    }

    fn end_inner<T: ToKotlinObject>(obj: T) -> Position {
        let ptr = obj.to_kotlin_object();
        let f = KSON_SYMBOLS.kotlin.root.org.kson.KsonValue.get_end.unwrap();
        let result = unsafe { f(kson_sys::kson_kref_org_kson_KsonValue { pinned: ptr }) };
        Position::from_kotlin_object(result.pinned)
    }
}

declare_kotlin_object! {
    KsonValueObject
}

impl KsonValueObject {
    pub fn properties(&self) -> std::collections::HashMap<KsonValue, KsonValue> {
        let ptr = self.to_kotlin_object();
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .KsonValue
            .KsonObject
            .get_properties
            .unwrap();
        let result =
            unsafe { f(kson_sys::kson_kref_org_kson_KsonValue_KsonObject { pinned: ptr }) };
        util::from_kotlin_value_map(result)
    }
}

declare_kotlin_object! {
    KsonValueArray
}

impl KsonValueArray {
    pub fn elements(&self) -> Vec<KsonValue> {
        let ptr = self.to_kotlin_object();
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .KsonValue
            .KsonArray
            .get_elements
            .unwrap();
        let result = unsafe { f(kson_sys::kson_kref_org_kson_KsonValue_KsonArray { pinned: ptr }) };
        util::from_kotlin_list(result)
    }

    pub fn start(&self) -> Position {
        KsonValue::start_inner(self)
    }

    pub fn end(&self) -> Position {
        KsonValue::end_inner(self)
    }
}

declare_kotlin_object! {
    KsonValueString
}

impl KsonValueString {
    pub fn value(&self) -> String {
        let ptr = self.to_kotlin_object();
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .KsonValue
            .KsonString
            .get_value
            .unwrap();
        let result =
            unsafe { f(kson_sys::kson_kref_org_kson_KsonValue_KsonString { pinned: ptr }) };
        util::from_kotlin_string(result)
    }

    pub fn start(&self) -> Position {
        KsonValue::start_inner(self)
    }

    pub fn end(&self) -> Position {
        KsonValue::end_inner(self)
    }
}

declare_kotlin_object! {
    KsonValueInteger
}

impl KsonValueInteger {
    pub fn value(&self) -> i32 {
        let ptr = self.to_kotlin_object();
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .KsonValue
            .KsonNumber
            .Integer
            .get_value
            .unwrap();
        unsafe { f(kson_sys::kson_kref_org_kson_KsonValue_KsonNumber_Integer { pinned: ptr }) }
    }

    pub fn start(&self) -> Position {
        KsonValue::start_inner(self)
    }

    pub fn end(&self) -> Position {
        KsonValue::end_inner(self)
    }
}

declare_kotlin_object! {
    KsonValueDecimal
}

impl KsonValueDecimal {
    pub fn value(&self) -> f64 {
        let ptr = self.to_kotlin_object();
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .KsonValue
            .KsonNumber
            .Decimal
            .get_value
            .unwrap();
        unsafe { f(kson_sys::kson_kref_org_kson_KsonValue_KsonNumber_Decimal { pinned: ptr }) }
    }

    pub fn start(&self) -> Position {
        KsonValue::start_inner(self)
    }

    pub fn end(&self) -> Position {
        KsonValue::end_inner(self)
    }
}

declare_kotlin_object! {
    KsonValueBoolean
}

declare_kotlin_object! {
    KsonValueNull
}

declare_kotlin_object! {
    KsonValueEmbed
}

impl KsonValueEmbed {
    pub fn tag(&self) -> Option<String> {
        let get_tag = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .KsonValue
            .KsonEmbed
            .get_tag
            .unwrap();
        let tag_result = unsafe {
            get_tag(kson_sys::kson_kref_org_kson_KsonValue_KsonEmbed {
                pinned: self.to_kotlin_object(),
            })
        };
        if tag_result == std::ptr::null_mut() {
            None
        } else {
            Some(util::from_kotlin_string(tag_result))
        }
    }

    pub fn metadata(&self) -> Option<String> {
        let get_metadata = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .KsonValue
            .KsonEmbed
            .get_metadata
            .unwrap();
        let metadata_result = unsafe {
            get_metadata(kson_sys::kson_kref_org_kson_KsonValue_KsonEmbed {
                pinned: self.to_kotlin_object(),
            })
        };
        if metadata_result == std::ptr::null_mut() {
            None
        } else {
            Some(util::from_kotlin_string(metadata_result))
        }
    }

    pub fn content(&self) -> String {
        let get_content = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .KsonValue
            .KsonEmbed
            .get_content
            .unwrap();
        let content_result = unsafe {
            get_content(kson_sys::kson_kref_org_kson_KsonValue_KsonEmbed {
                pinned: self.to_kotlin_object(),
            })
        };
        util::from_kotlin_string(content_result)
    }

    pub fn start(&self) -> Position {
        KsonValue::start_inner(self)
    }

    pub fn end(&self) -> Position {
        KsonValue::end_inner(self)
    }
}

mod util;

#[cfg(test)]
mod test;

#[macro_use]
mod macros;

use crate::util::{FromKotlinObject, KsonPtr, OwnedKotlinPtr, ToKotlinObject};
use kson_sys::kson_KNativePtr;
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

impl PartialEq for Position {
    fn eq(&self, other: &Self) -> bool {
        self.line() == other.line() && self.column() == other.column()
    }
}

impl Eq for Position {}

impl std::hash::Hash for Position {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.line().hash(state);
        self.column().hash(state);
    }
}

impl Clone for Position {
    fn clone(&self) -> Self {
        Position {
            kson_ref: crate::util::KsonPtr {
                inner: self.kson_ref.inner.clone(),
            },
        }
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

declare_kotlin_object! {
    /// Internal base type for KsonValue
    KsonValueBase
}

impl PartialEq for KsonValueBase {
    fn eq(&self, other: &Self) -> bool {
        self.kson_ref.inner.inner == other.kson_ref.inner.inner
    }
}

impl Eq for KsonValueBase {}

impl std::hash::Hash for KsonValueBase {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.kson_ref.inner.inner.hash(state);
    }
}

impl KsonValueBase {
    fn value_type(&self) -> KsonValueType {
        let f = KSON_SYMBOLS
            .kotlin
            .root
            .org
            .kson
            .KsonValue
            .get_type
            .unwrap();
        let result = unsafe {
            f(kson_sys::kson_kref_org_kson_KsonValue {
                pinned: self.kson_ref.inner.inner,
            })
        };
        KsonValueType::from_kotlin_object(result.pinned)
    }
}

/// Represents a parsed Kson value
#[derive(Clone)]
pub enum KsonValue {
    KsonObject {
        properties: std::collections::HashMap<KsonValue, KsonValue>,
        start: Position,
        end: Position,
    },
    KsonArray {
        elements: Vec<KsonValue>,
        start: Position,
        end: Position,
    },
    KsonString {
        value: String,
        start: Position,
        end: Position,
    },
    KsonInteger {
        value: i32,
        start: Position,
        end: Position,
    },
    KsonDecimal {
        value: f64,
        start: Position,
        end: Position,
    },
    KsonBoolean {
        value: bool,
        start: Position,
        end: Position,
    },
    KsonNull {
        start: Position,
        end: Position,
    },
    KsonEmbed {
        tag: Option<String>,
        metadata: Option<String>,
        content: String,
        start: Position,
        end: Position,
    },
}

impl std::fmt::Debug for KsonValue {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            KsonValue::KsonObject {
                properties,
                start,
                end,
            } => f
                .debug_struct("KsonObject")
                .field("properties", properties)
                .field("start", start)
                .field("end", end)
                .finish(),
            KsonValue::KsonArray {
                elements,
                start,
                end,
            } => f
                .debug_struct("KsonArray")
                .field("elements", elements)
                .field("start", start)
                .field("end", end)
                .finish(),
            KsonValue::KsonString { value, start, end } => f
                .debug_struct("KsonString")
                .field("value", value)
                .field("start", start)
                .field("end", end)
                .finish(),
            KsonValue::KsonInteger { value, start, end } => f
                .debug_struct("KsonInteger")
                .field("value", value)
                .field("start", start)
                .field("end", end)
                .finish(),
            KsonValue::KsonDecimal { value, start, end } => f
                .debug_struct("KsonDecimal")
                .field("value", value)
                .field("start", start)
                .field("end", end)
                .finish(),
            KsonValue::KsonBoolean { value, start, end } => f
                .debug_struct("KsonBoolean")
                .field("value", value)
                .field("start", start)
                .field("end", end)
                .finish(),
            KsonValue::KsonNull { start, end } => f
                .debug_struct("KsonNull")
                .field("start", start)
                .field("end", end)
                .finish(),
            KsonValue::KsonEmbed {
                tag,
                metadata,
                content,
                start,
                end,
            } => f
                .debug_struct("KsonEmbed")
                .field("tag", tag)
                .field("metadata", metadata)
                .field("content", content)
                .field("start", start)
                .field("end", end)
                .finish(),
        }
    }
}

impl PartialEq for KsonValue {
    fn eq(&self, other: &Self) -> bool {
        match (self, other) {
            (
                KsonValue::KsonObject {
                    properties: p1,
                    start: s1,
                    end: e1,
                },
                KsonValue::KsonObject {
                    properties: p2,
                    start: s2,
                    end: e2,
                },
            ) => p1 == p2 && s1 == s2 && e1 == e2,
            (
                KsonValue::KsonArray {
                    elements: el1,
                    start: s1,
                    end: e1,
                },
                KsonValue::KsonArray {
                    elements: el2,
                    start: s2,
                    end: e2,
                },
            ) => el1 == el2 && s1 == s2 && e1 == e2,
            (
                KsonValue::KsonString {
                    value: v1,
                    start: s1,
                    end: e1,
                },
                KsonValue::KsonString {
                    value: v2,
                    start: s2,
                    end: e2,
                },
            ) => v1 == v2 && s1 == s2 && e1 == e2,
            (
                KsonValue::KsonInteger {
                    value: v1,
                    start: s1,
                    end: e1,
                },
                KsonValue::KsonInteger {
                    value: v2,
                    start: s2,
                    end: e2,
                },
            ) => v1 == v2 && s1 == s2 && e1 == e2,
            (
                KsonValue::KsonDecimal {
                    value: v1,
                    start: s1,
                    end: e1,
                },
                KsonValue::KsonDecimal {
                    value: v2,
                    start: s2,
                    end: e2,
                },
            ) => v1.to_bits() == v2.to_bits() && s1 == s2 && e1 == e2,
            (
                KsonValue::KsonBoolean {
                    value: v1,
                    start: s1,
                    end: e1,
                },
                KsonValue::KsonBoolean {
                    value: v2,
                    start: s2,
                    end: e2,
                },
            ) => v1 == v2 && s1 == s2 && e1 == e2,
            (
                KsonValue::KsonNull { start: s1, end: e1 },
                KsonValue::KsonNull { start: s2, end: e2 },
            ) => s1 == s2 && e1 == e2,
            (
                KsonValue::KsonEmbed {
                    tag: t1,
                    metadata: m1,
                    content: c1,
                    start: s1,
                    end: e1,
                },
                KsonValue::KsonEmbed {
                    tag: t2,
                    metadata: m2,
                    content: c2,
                    start: s2,
                    end: e2,
                },
            ) => t1 == t2 && m1 == m2 && c1 == c2 && s1 == s2 && e1 == e2,
            _ => false,
        }
    }
}

impl Eq for KsonValue {}

impl std::hash::Hash for KsonValue {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        std::mem::discriminant(self).hash(state);
        match self {
            KsonValue::KsonObject {
                properties,
                start,
                end,
            } => {
                for (k, v) in properties {
                    k.hash(state);
                    v.hash(state);
                }
                start.hash(state);
                end.hash(state);
            }
            KsonValue::KsonArray {
                elements,
                start,
                end,
            } => {
                elements.hash(state);
                start.hash(state);
                end.hash(state);
            }
            KsonValue::KsonString { value, start, end } => {
                value.hash(state);
                start.hash(state);
                end.hash(state);
            }
            KsonValue::KsonInteger { value, start, end } => {
                value.hash(state);
                start.hash(state);
                end.hash(state);
            }
            KsonValue::KsonDecimal { value, start, end } => {
                value.to_bits().hash(state);
                start.hash(state);
                end.hash(state);
            }
            KsonValue::KsonBoolean { value, start, end } => {
                value.hash(state);
                start.hash(state);
                end.hash(state);
            }
            KsonValue::KsonNull { start, end } => {
                start.hash(state);
                end.hash(state);
            }
            KsonValue::KsonEmbed {
                tag,
                metadata,
                content,
                start,
                end,
            } => {
                tag.hash(state);
                metadata.hash(state);
                content.hash(state);
                start.hash(state);
                end.hash(state);
            }
        }
    }
}

impl KsonValue {
    pub fn start(&self) -> &Position {
        match self {
            KsonValue::KsonObject { start, .. } => start,
            KsonValue::KsonArray { start, .. } => start,
            KsonValue::KsonString { start, .. } => start,
            KsonValue::KsonInteger { start, .. } => start,
            KsonValue::KsonDecimal { start, .. } => start,
            KsonValue::KsonBoolean { start, .. } => start,
            KsonValue::KsonNull { start, .. } => start,
            KsonValue::KsonEmbed { start, .. } => start,
        }
    }

    pub fn end(&self) -> &Position {
        match self {
            KsonValue::KsonObject { end, .. } => end,
            KsonValue::KsonArray { end, .. } => end,
            KsonValue::KsonString { end, .. } => end,
            KsonValue::KsonInteger { end, .. } => end,
            KsonValue::KsonDecimal { end, .. } => end,
            KsonValue::KsonBoolean { end, .. } => end,
            KsonValue::KsonNull { end, .. } => end,
            KsonValue::KsonEmbed { end, .. } => end,
        }
    }
}

impl util::FromKotlinObject for KsonValue {
    fn from_kotlin_object(obj: kson_KNativePtr) -> Self {
        let base = KsonValueBase {
            kson_ref: KsonPtr {
                inner: std::sync::Arc::new(OwnedKotlinPtr { inner: obj }),
            },
        };

        let get_start = || {
            let f = KSON_SYMBOLS
                .kotlin
                .root
                .org
                .kson
                .KsonValue
                .get_start
                .unwrap();
            let result = unsafe {
                f(kson_sys::kson_kref_org_kson_KsonValue {
                    pinned: base.kson_ref.inner.inner,
                })
            };
            Position {
                kson_ref: KsonPtr {
                    inner: std::sync::Arc::new(OwnedKotlinPtr {
                        inner: result.pinned,
                    }),
                },
            }
        };

        let get_end = || {
            let f = KSON_SYMBOLS.kotlin.root.org.kson.KsonValue.get_end.unwrap();
            let result = unsafe {
                f(kson_sys::kson_kref_org_kson_KsonValue {
                    pinned: base.kson_ref.inner.inner,
                })
            };
            Position {
                kson_ref: KsonPtr {
                    inner: std::sync::Arc::new(OwnedKotlinPtr {
                        inner: result.pinned,
                    }),
                },
            }
        };

        match base.value_type() {
            KsonValueType::Object => {
                let f = KSON_SYMBOLS
                    .kotlin
                    .root
                    .org
                    .kson
                    .KsonValue
                    .KsonObject
                    .get_properties
                    .unwrap();
                let result = unsafe {
                    f(kson_sys::kson_kref_org_kson_KsonValue_KsonObject {
                        pinned: base.kson_ref.inner.inner,
                    })
                };
                KsonValue::KsonObject {
                    properties: util::from_kotlin_value_map(result),
                    start: get_start(),
                    end: get_end(),
                }
            }
            KsonValueType::Array => {
                let f = KSON_SYMBOLS
                    .kotlin
                    .root
                    .org
                    .kson
                    .KsonValue
                    .KsonArray
                    .get_elements
                    .unwrap();
                let result = unsafe {
                    f(kson_sys::kson_kref_org_kson_KsonValue_KsonArray {
                        pinned: base.kson_ref.inner.inner,
                    })
                };
                KsonValue::KsonArray {
                    elements: util::from_kotlin_list(result),
                    start: get_start(),
                    end: get_end(),
                }
            }
            KsonValueType::String => {
                let f = KSON_SYMBOLS
                    .kotlin
                    .root
                    .org
                    .kson
                    .KsonValue
                    .KsonString
                    .get_value
                    .unwrap();
                let result = unsafe {
                    f(kson_sys::kson_kref_org_kson_KsonValue_KsonString {
                        pinned: base.kson_ref.inner.inner,
                    })
                };
                KsonValue::KsonString {
                    value: util::from_kotlin_string(result),
                    start: get_start(),
                    end: get_end(),
                }
            }
            KsonValueType::Integer => {
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
                let result = unsafe {
                    f(kson_sys::kson_kref_org_kson_KsonValue_KsonNumber_Integer {
                        pinned: base.kson_ref.inner.inner,
                    })
                };
                KsonValue::KsonInteger {
                    value: result,
                    start: get_start(),
                    end: get_end(),
                }
            }
            KsonValueType::Decimal => {
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
                let result = unsafe {
                    f(kson_sys::kson_kref_org_kson_KsonValue_KsonNumber_Decimal {
                        pinned: base.kson_ref.inner.inner,
                    })
                };
                KsonValue::KsonDecimal {
                    value: result,
                    start: get_start(),
                    end: get_end(),
                }
            }
            KsonValueType::Boolean => {
                let f = KSON_SYMBOLS
                    .kotlin
                    .root
                    .org
                    .kson
                    .KsonValue
                    .KsonBoolean
                    .get_value
                    .unwrap();
                let result = unsafe {
                    f(kson_sys::kson_kref_org_kson_KsonValue_KsonBoolean {
                        pinned: base.kson_ref.inner.inner,
                    })
                };
                KsonValue::KsonBoolean {
                    value: result,
                    start: get_start(),
                    end: get_end(),
                }
            }
            KsonValueType::Null => KsonValue::KsonNull {
                start: get_start(),
                end: get_end(),
            },
            KsonValueType::Embed => {
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
                        pinned: base.kson_ref.inner.inner,
                    })
                };
                let tag = if tag_result == std::ptr::null_mut() {
                    None
                } else {
                    Some(util::from_kotlin_string(tag_result))
                };

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
                        pinned: base.kson_ref.inner.inner,
                    })
                };
                let metadata = if metadata_result == std::ptr::null_mut() {
                    None
                } else {
                    Some(util::from_kotlin_string(metadata_result))
                };

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
                        pinned: base.kson_ref.inner.inner,
                    })
                };
                let content = util::from_kotlin_string(content_result);

                KsonValue::KsonEmbed {
                    tag,
                    metadata,
                    content,
                    start: get_start(),
                    end: get_end(),
                }
            }
        }
    }
}

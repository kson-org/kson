#![allow(non_camel_case_types)]

#[macro_use]
mod util;
mod sys {
    pub use kson_sys::*;
}

use std::ffi::c_int;
use self::sys::jobject;
use self::util::{AsKotlinObject, FromKotlinObject, KotlinPtr, ToKotlinObject};


/// Options for formatting Kson output.
///
/// @param indentType The type of indentation to use (spaces or tabs)
/// @param formattingStyle The formatting style (PLAIN, DELIMITED, COMPACT, CLASSIC)
/// @param embedBlockRules Rules for formatting specific paths as embed blocks
#[derive(Clone)]
pub struct FormatOptions {
    kotlin_ptr: KotlinPtr,
}

impl FromKotlinObject for FormatOptions {
    fn from_kotlin_object(obj: self::sys::jobject) -> Self {
        let (env, _detach_guard) = util::attach_thread_to_java_vm();
        let kotlin_ptr = util::to_gc_global_ref(env, obj);
        Self { kotlin_ptr }
    }
}

impl ToKotlinObject for FormatOptions {
    fn to_kotlin_object(&self) -> KotlinPtr {
        self.kotlin_ptr.clone()
    }
}

impl AsKotlinObject for FormatOptions {
    fn as_kotlin_object(&self) -> self::sys::jobject {
        self.kotlin_ptr.inner.inner
    }
}

impl FormatOptions {
    pub fn new(
        indent_type: IndentType,
        formatting_style: FormattingStyle,
        embed_block_rules: &[EmbedRule],
    ) -> Self {
        let (env, _detach_guard) = util::attach_thread_to_java_vm();
        let class = util::get_class(env, c"org/kson/FormatOptions");
        let constructor = util::get_method(env, class.as_kotlin_object(), c"<init>", c"(Lorg/kson/IndentType;Lorg/kson/FormattingStyle;Ljava/util/List;)V");

        let indent_type_ptr = indent_type.to_kotlin_object();
        let indent_type = indent_type_ptr.as_kotlin_object();
        let formatting_style_ptr = formatting_style.to_kotlin_object();
        let formatting_style = formatting_style_ptr.as_kotlin_object();
        let embed_block_rules_ptr = util::to_kotlin_list(embed_block_rules);
        let embed_block_rules = embed_block_rules_ptr.as_kotlin_object();

        let jobject = unsafe { (**env).NewObject.unwrap()(env, class.as_kotlin_object(), constructor,
            indent_type,
            formatting_style,
            embed_block_rules,
        )};
        util::panic_upon_exception(env);
        Self {
            kotlin_ptr: util::to_gc_global_ref(env, jobject)
        }
    }
}


impl FormatOptions {


    pub fn indent_type(
        &self,
    ) -> IndentType {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();


        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/FormatOptions",
            c"getIndentType",
            c"()Lorg/kson/IndentType;",
            CallObjectMethod,
            self_obj,

        );

        FromKotlinObject::from_kotlin_object(result)
    }


    pub fn formatting_style(
        &self,
    ) -> FormattingStyle {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();


        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/FormatOptions",
            c"getFormattingStyle",
            c"()Lorg/kson/FormattingStyle;",
            CallObjectMethod,
            self_obj,

        );

        FromKotlinObject::from_kotlin_object(result)
    }


    pub fn embed_block_rules(
        &self,
    ) -> Vec<EmbedRule> {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();


        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/FormatOptions",
            c"getEmbedBlockRules",
            c"()Ljava/util/List;",
            CallObjectMethod,
            self_obj,

        );

        util::from_kotlin_list(result)
    }
}

impl std::fmt::Debug for FormatOptions {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let obj = self.to_kotlin_object();
        write!(f, "{}", util::call_to_string(c"org/kson/FormatOptions", &obj))
    }
}

impl Eq for FormatOptions {}
impl PartialEq for FormatOptions {
    fn eq(&self, other: &FormatOptions) -> bool {
        util::equals(self.to_kotlin_object(), other.to_kotlin_object())
    }
}
impl std::hash::Hash for FormatOptions {
    fn hash<H>(&self, state: &mut H)
    where
        H: std::hash::Hasher,
    {
        util::apply_hash_code(self.to_kotlin_object(), state)
    }
}


/// A zero-based line/column position in a document
///
/// @param line The line number where the error occurred (0-based)
/// @param column The column number where the error occurred (0-based)
#[derive(Clone)]
pub struct Position {
    kotlin_ptr: KotlinPtr,
}

impl FromKotlinObject for Position {
    fn from_kotlin_object(obj: self::sys::jobject) -> Self {
        let (env, _detach_guard) = util::attach_thread_to_java_vm();
        let kotlin_ptr = util::to_gc_global_ref(env, obj);
        Self { kotlin_ptr }
    }
}

impl ToKotlinObject for Position {
    fn to_kotlin_object(&self) -> KotlinPtr {
        self.kotlin_ptr.clone()
    }
}

impl AsKotlinObject for Position {
    fn as_kotlin_object(&self) -> self::sys::jobject {
        self.kotlin_ptr.inner.inner
    }
}

impl Position {
}


impl Position {


    pub fn line(
        &self,
    ) -> i32 {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();


        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/Position",
            c"getLine",
            c"()I",
            CallIntMethod,
            self_obj,

        );

        result
    }


    pub fn column(
        &self,
    ) -> i32 {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();


        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/Position",
            c"getColumn",
            c"()I",
            CallIntMethod,
            self_obj,

        );

        result
    }
}

impl std::fmt::Debug for Position {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let obj = self.to_kotlin_object();
        write!(f, "{}", util::call_to_string(c"org/kson/Position", &obj))
    }
}

impl Eq for Position {}
impl PartialEq for Position {
    fn eq(&self, other: &Position) -> bool {
        util::equals(self.to_kotlin_object(), other.to_kotlin_object())
    }
}
impl std::hash::Hash for Position {
    fn hash<H>(&self, state: &mut H)
    where
        H: std::hash::Hasher,
    {
        util::apply_hash_code(self.to_kotlin_object(), state)
    }
}


/// Represents a message logged during Kson processing
#[derive(Clone)]
pub struct Message {
    kotlin_ptr: KotlinPtr,
}

impl FromKotlinObject for Message {
    fn from_kotlin_object(obj: self::sys::jobject) -> Self {
        let (env, _detach_guard) = util::attach_thread_to_java_vm();
        let kotlin_ptr = util::to_gc_global_ref(env, obj);
        Self { kotlin_ptr }
    }
}

impl ToKotlinObject for Message {
    fn to_kotlin_object(&self) -> KotlinPtr {
        self.kotlin_ptr.clone()
    }
}

impl AsKotlinObject for Message {
    fn as_kotlin_object(&self) -> self::sys::jobject {
        self.kotlin_ptr.inner.inner
    }
}

impl Message {
}


impl Message {


    pub fn message(
        &self,
    ) -> String {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();


        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/Message",
            c"getMessage",
            c"()Ljava/lang/String;",
            CallObjectMethod,
            self_obj,

        );

        FromKotlinObject::from_kotlin_object(result)
    }


    pub fn severity(
        &self,
    ) -> MessageSeverity {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();


        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/Message",
            c"getSeverity",
            c"()Lorg/kson/MessageSeverity;",
            CallObjectMethod,
            self_obj,

        );

        FromKotlinObject::from_kotlin_object(result)
    }


    pub fn start(
        &self,
    ) -> Position {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();


        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/Message",
            c"getStart",
            c"()Lorg/kson/Position;",
            CallObjectMethod,
            self_obj,

        );

        FromKotlinObject::from_kotlin_object(result)
    }


    pub fn end(
        &self,
    ) -> Position {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();


        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/Message",
            c"getEnd",
            c"()Lorg/kson/Position;",
            CallObjectMethod,
            self_obj,

        );

        FromKotlinObject::from_kotlin_object(result)
    }
}

impl std::fmt::Debug for Message {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let obj = self.to_kotlin_object();
        write!(f, "{}", util::call_to_string(c"org/kson/Message", &obj))
    }
}

impl Eq for Message {}
impl PartialEq for Message {
    fn eq(&self, other: &Message) -> bool {
        util::equals(self.to_kotlin_object(), other.to_kotlin_object())
    }
}
impl std::hash::Hash for Message {
    fn hash<H>(&self, state: &mut H)
    where
        H: std::hash::Hasher,
    {
        util::apply_hash_code(self.to_kotlin_object(), state)
    }
}


/// [Token] produced by the lexing phase of a Kson parse
#[derive(Clone)]
pub struct Token {
    kotlin_ptr: KotlinPtr,
}

impl FromKotlinObject for Token {
    fn from_kotlin_object(obj: self::sys::jobject) -> Self {
        let (env, _detach_guard) = util::attach_thread_to_java_vm();
        let kotlin_ptr = util::to_gc_global_ref(env, obj);
        Self { kotlin_ptr }
    }
}

impl ToKotlinObject for Token {
    fn to_kotlin_object(&self) -> KotlinPtr {
        self.kotlin_ptr.clone()
    }
}

impl AsKotlinObject for Token {
    fn as_kotlin_object(&self) -> self::sys::jobject {
        self.kotlin_ptr.inner.inner
    }
}

impl Token {
}


impl Token {


    pub fn token_type(
        &self,
    ) -> TokenType {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();


        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/Token",
            c"getTokenType",
            c"()Lorg/kson/TokenType;",
            CallObjectMethod,
            self_obj,

        );

        FromKotlinObject::from_kotlin_object(result)
    }


    pub fn text(
        &self,
    ) -> String {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();


        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/Token",
            c"getText",
            c"()Ljava/lang/String;",
            CallObjectMethod,
            self_obj,

        );

        FromKotlinObject::from_kotlin_object(result)
    }


    pub fn start(
        &self,
    ) -> Position {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();


        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/Token",
            c"getStart",
            c"()Lorg/kson/Position;",
            CallObjectMethod,
            self_obj,

        );

        FromKotlinObject::from_kotlin_object(result)
    }


    pub fn end(
        &self,
    ) -> Position {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();


        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/Token",
            c"getEnd",
            c"()Lorg/kson/Position;",
            CallObjectMethod,
            self_obj,

        );

        FromKotlinObject::from_kotlin_object(result)
    }
}

impl std::fmt::Debug for Token {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let obj = self.to_kotlin_object();
        write!(f, "{}", util::call_to_string(c"org/kson/Token", &obj))
    }
}

impl Eq for Token {}
impl PartialEq for Token {
    fn eq(&self, other: &Token) -> bool {
        util::equals(self.to_kotlin_object(), other.to_kotlin_object())
    }
}
impl std::hash::Hash for Token {
    fn hash<H>(&self, state: &mut H)
    where
        H: std::hash::Hasher,
    {
        util::apply_hash_code(self.to_kotlin_object(), state)
    }
}

/// Represents a parsed [InternalKsonValue] in the public API
#[derive(Clone)]
pub enum KsonValue {
    KsonArray(kson_value::KsonArray),
    KsonBoolean(kson_value::KsonBoolean),
    KsonEmbed(kson_value::KsonEmbed),
    KsonNull(kson_value::KsonNull),
    KsonNumber(kson_value::KsonNumber),
    KsonObject(kson_value::KsonObject),
    KsonString(kson_value::KsonString),
}

pub mod kson_value {
    use super::*;


    /// A Kson array with elements
    #[derive(Clone)]
    pub struct KsonArray {
        kotlin_ptr: KotlinPtr,
    }

    impl FromKotlinObject for KsonArray {
        fn from_kotlin_object(obj: self::sys::jobject) -> Self {
            let (env, _detach_guard) = util::attach_thread_to_java_vm();
            let kotlin_ptr = util::to_gc_global_ref(env, obj);
            Self { kotlin_ptr }
        }
    }

    impl ToKotlinObject for KsonArray {
        fn to_kotlin_object(&self) -> KotlinPtr {
            self.kotlin_ptr.clone()
        }
    }

    impl AsKotlinObject for KsonArray {
        fn as_kotlin_object(&self) -> self::sys::jobject {
            self.kotlin_ptr.inner.inner
        }
    }

    impl KsonArray {
    }


    impl KsonArray {


        pub fn elements(
            &self,
        ) -> Vec<KsonValue> {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue$KsonArray",
                c"getElements",
                c"()Ljava/util/List;",
                CallObjectMethod,
                self_obj,

            );

            util::from_kotlin_list(result)
        }


        pub fn type_(
            &self,
        ) -> KsonValueType {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue$KsonArray",
                c"getType",
                c"()Lorg/kson/KsonValueType;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }


        pub fn start(
            &self,
        ) -> Position {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue",
                c"getStart",
                c"()Lorg/kson/Position;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }


        pub fn end(
            &self,
        ) -> Position {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue",
                c"getEnd",
                c"()Lorg/kson/Position;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }
    }

    impl std::fmt::Debug for KsonArray {
        fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            let obj = self.to_kotlin_object();
            write!(f, "{}", util::call_to_string(c"org/kson/KsonValue$KsonArray", &obj))
        }
    }

    impl Eq for KsonArray {}
    impl PartialEq for KsonArray {
        fn eq(&self, other: &KsonArray) -> bool {
            util::equals(self.to_kotlin_object(), other.to_kotlin_object())
        }
    }
    impl std::hash::Hash for KsonArray {
        fn hash<H>(&self, state: &mut H)
        where
            H: std::hash::Hasher,
        {
            util::apply_hash_code(self.to_kotlin_object(), state)
        }
    }

    /// A Kson boolean value
    #[derive(Clone)]
    pub struct KsonBoolean {
        kotlin_ptr: KotlinPtr,
    }

    impl FromKotlinObject for KsonBoolean {
        fn from_kotlin_object(obj: self::sys::jobject) -> Self {
            let (env, _detach_guard) = util::attach_thread_to_java_vm();
            let kotlin_ptr = util::to_gc_global_ref(env, obj);
            Self { kotlin_ptr }
        }
    }

    impl ToKotlinObject for KsonBoolean {
        fn to_kotlin_object(&self) -> KotlinPtr {
            self.kotlin_ptr.clone()
        }
    }

    impl AsKotlinObject for KsonBoolean {
        fn as_kotlin_object(&self) -> self::sys::jobject {
            self.kotlin_ptr.inner.inner
        }
    }

    impl KsonBoolean {
    }


    impl KsonBoolean {


        pub fn value(
            &self,
        ) -> bool {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue$KsonBoolean",
                c"getValue",
                c"()Z",
                CallBooleanMethod,
                self_obj,

            );

            result != 0
        }


        pub fn type_(
            &self,
        ) -> KsonValueType {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue$KsonBoolean",
                c"getType",
                c"()Lorg/kson/KsonValueType;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }


        pub fn start(
            &self,
        ) -> Position {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue",
                c"getStart",
                c"()Lorg/kson/Position;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }


        pub fn end(
            &self,
        ) -> Position {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue",
                c"getEnd",
                c"()Lorg/kson/Position;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }
    }

    impl std::fmt::Debug for KsonBoolean {
        fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            let obj = self.to_kotlin_object();
            write!(f, "{}", util::call_to_string(c"org/kson/KsonValue$KsonBoolean", &obj))
        }
    }

    impl Eq for KsonBoolean {}
    impl PartialEq for KsonBoolean {
        fn eq(&self, other: &KsonBoolean) -> bool {
            util::equals(self.to_kotlin_object(), other.to_kotlin_object())
        }
    }
    impl std::hash::Hash for KsonBoolean {
        fn hash<H>(&self, state: &mut H)
        where
            H: std::hash::Hasher,
        {
            util::apply_hash_code(self.to_kotlin_object(), state)
        }
    }

    /// A Kson embed block
    #[derive(Clone)]
    pub struct KsonEmbed {
        kotlin_ptr: KotlinPtr,
    }

    impl FromKotlinObject for KsonEmbed {
        fn from_kotlin_object(obj: self::sys::jobject) -> Self {
            let (env, _detach_guard) = util::attach_thread_to_java_vm();
            let kotlin_ptr = util::to_gc_global_ref(env, obj);
            Self { kotlin_ptr }
        }
    }

    impl ToKotlinObject for KsonEmbed {
        fn to_kotlin_object(&self) -> KotlinPtr {
            self.kotlin_ptr.clone()
        }
    }

    impl AsKotlinObject for KsonEmbed {
        fn as_kotlin_object(&self) -> self::sys::jobject {
            self.kotlin_ptr.inner.inner
        }
    }

    impl KsonEmbed {
    }


    impl KsonEmbed {


        pub fn tag(
            &self,
        ) -> Option<String> {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue$KsonEmbed",
                c"getTag",
                c"()Ljava/lang/String;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }


        pub fn content(
            &self,
        ) -> String {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue$KsonEmbed",
                c"getContent",
                c"()Ljava/lang/String;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }


        pub fn type_(
            &self,
        ) -> KsonValueType {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue$KsonEmbed",
                c"getType",
                c"()Lorg/kson/KsonValueType;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }


        pub fn start(
            &self,
        ) -> Position {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue",
                c"getStart",
                c"()Lorg/kson/Position;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }


        pub fn end(
            &self,
        ) -> Position {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue",
                c"getEnd",
                c"()Lorg/kson/Position;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }
    }

    impl std::fmt::Debug for KsonEmbed {
        fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            let obj = self.to_kotlin_object();
            write!(f, "{}", util::call_to_string(c"org/kson/KsonValue$KsonEmbed", &obj))
        }
    }

    impl Eq for KsonEmbed {}
    impl PartialEq for KsonEmbed {
        fn eq(&self, other: &KsonEmbed) -> bool {
            util::equals(self.to_kotlin_object(), other.to_kotlin_object())
        }
    }
    impl std::hash::Hash for KsonEmbed {
        fn hash<H>(&self, state: &mut H)
        where
            H: std::hash::Hasher,
        {
            util::apply_hash_code(self.to_kotlin_object(), state)
        }
    }

    /// A Kson null value
    #[derive(Clone)]
    pub struct KsonNull {
        kotlin_ptr: KotlinPtr,
    }

    impl FromKotlinObject for KsonNull {
        fn from_kotlin_object(obj: self::sys::jobject) -> Self {
            let (env, _detach_guard) = util::attach_thread_to_java_vm();
            let kotlin_ptr = util::to_gc_global_ref(env, obj);
            Self { kotlin_ptr }
        }
    }

    impl ToKotlinObject for KsonNull {
        fn to_kotlin_object(&self) -> KotlinPtr {
            self.kotlin_ptr.clone()
        }
    }

    impl AsKotlinObject for KsonNull {
        fn as_kotlin_object(&self) -> self::sys::jobject {
            self.kotlin_ptr.inner.inner
        }
    }

    impl KsonNull {
    }


    impl KsonNull {


        pub fn type_(
            &self,
        ) -> KsonValueType {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue$KsonNull",
                c"getType",
                c"()Lorg/kson/KsonValueType;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }


        pub fn start(
            &self,
        ) -> Position {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue",
                c"getStart",
                c"()Lorg/kson/Position;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }


        pub fn end(
            &self,
        ) -> Position {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue",
                c"getEnd",
                c"()Lorg/kson/Position;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }
    }

    impl std::fmt::Debug for KsonNull {
        fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            let obj = self.to_kotlin_object();
            write!(f, "{}", util::call_to_string(c"org/kson/KsonValue$KsonNull", &obj))
        }
    }

    impl Eq for KsonNull {}
    impl PartialEq for KsonNull {
        fn eq(&self, other: &KsonNull) -> bool {
            util::equals(self.to_kotlin_object(), other.to_kotlin_object())
        }
    }
    impl std::hash::Hash for KsonNull {
        fn hash<H>(&self, state: &mut H)
        where
            H: std::hash::Hasher,
        {
            util::apply_hash_code(self.to_kotlin_object(), state)
        }
    }
    /// A Kson number value.
    #[derive(Clone)]
    pub enum KsonNumber {
        Decimal(kson_value::kson_number::Decimal),
        Integer(kson_value::kson_number::Integer),
    }

    pub mod kson_number {
        use super::*;



        #[derive(Clone)]
        pub struct Decimal {
            kotlin_ptr: KotlinPtr,
        }

        impl FromKotlinObject for Decimal {
            fn from_kotlin_object(obj: self::sys::jobject) -> Self {
                let (env, _detach_guard) = util::attach_thread_to_java_vm();
                let kotlin_ptr = util::to_gc_global_ref(env, obj);
                Self { kotlin_ptr }
            }
        }

        impl ToKotlinObject for Decimal {
            fn to_kotlin_object(&self) -> KotlinPtr {
                self.kotlin_ptr.clone()
            }
        }

        impl AsKotlinObject for Decimal {
            fn as_kotlin_object(&self) -> self::sys::jobject {
                self.kotlin_ptr.inner.inner
            }
        }

        impl Decimal {
        }


        impl Decimal {


            pub fn value(
                &self,
            ) -> f64 {
                let self_ptr = self.to_kotlin_object();
                let self_obj = self_ptr.as_kotlin_object();


                let (_, _detach_guard) = util::attach_thread_to_java_vm();
                let result = call_jvm_function!(
                    util,
                    c"org/kson/KsonValue$KsonNumber$Decimal",
                    c"getValue",
                    c"()D",
                    CallDoubleMethod,
                    self_obj,

                );

                result
            }


            pub fn type_(
                &self,
            ) -> KsonValueType {
                let self_ptr = self.to_kotlin_object();
                let self_obj = self_ptr.as_kotlin_object();


                let (_, _detach_guard) = util::attach_thread_to_java_vm();
                let result = call_jvm_function!(
                    util,
                    c"org/kson/KsonValue$KsonNumber$Decimal",
                    c"getType",
                    c"()Lorg/kson/KsonValueType;",
                    CallObjectMethod,
                    self_obj,

                );

                FromKotlinObject::from_kotlin_object(result)
            }


            pub fn start(
                &self,
            ) -> Position {
                let self_ptr = self.to_kotlin_object();
                let self_obj = self_ptr.as_kotlin_object();


                let (_, _detach_guard) = util::attach_thread_to_java_vm();
                let result = call_jvm_function!(
                    util,
                    c"org/kson/KsonValue",
                    c"getStart",
                    c"()Lorg/kson/Position;",
                    CallObjectMethod,
                    self_obj,

                );

                FromKotlinObject::from_kotlin_object(result)
            }


            pub fn end(
                &self,
            ) -> Position {
                let self_ptr = self.to_kotlin_object();
                let self_obj = self_ptr.as_kotlin_object();


                let (_, _detach_guard) = util::attach_thread_to_java_vm();
                let result = call_jvm_function!(
                    util,
                    c"org/kson/KsonValue",
                    c"getEnd",
                    c"()Lorg/kson/Position;",
                    CallObjectMethod,
                    self_obj,

                );

                FromKotlinObject::from_kotlin_object(result)
            }
        }

        impl std::fmt::Debug for Decimal {
            fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
                let obj = self.to_kotlin_object();
                write!(f, "{}", util::call_to_string(c"org/kson/KsonValue$KsonNumber$Decimal", &obj))
            }
        }

        impl Eq for Decimal {}
        impl PartialEq for Decimal {
            fn eq(&self, other: &Decimal) -> bool {
                util::equals(self.to_kotlin_object(), other.to_kotlin_object())
            }
        }
        impl std::hash::Hash for Decimal {
            fn hash<H>(&self, state: &mut H)
            where
                H: std::hash::Hasher,
            {
                util::apply_hash_code(self.to_kotlin_object(), state)
            }
        }


        #[derive(Clone)]
        pub struct Integer {
            kotlin_ptr: KotlinPtr,
        }

        impl FromKotlinObject for Integer {
            fn from_kotlin_object(obj: self::sys::jobject) -> Self {
                let (env, _detach_guard) = util::attach_thread_to_java_vm();
                let kotlin_ptr = util::to_gc_global_ref(env, obj);
                Self { kotlin_ptr }
            }
        }

        impl ToKotlinObject for Integer {
            fn to_kotlin_object(&self) -> KotlinPtr {
                self.kotlin_ptr.clone()
            }
        }

        impl AsKotlinObject for Integer {
            fn as_kotlin_object(&self) -> self::sys::jobject {
                self.kotlin_ptr.inner.inner
            }
        }

        impl Integer {
        }


        impl Integer {


            pub fn value(
                &self,
            ) -> i32 {
                let self_ptr = self.to_kotlin_object();
                let self_obj = self_ptr.as_kotlin_object();


                let (_, _detach_guard) = util::attach_thread_to_java_vm();
                let result = call_jvm_function!(
                    util,
                    c"org/kson/KsonValue$KsonNumber$Integer",
                    c"getValue",
                    c"()I",
                    CallIntMethod,
                    self_obj,

                );

                result
            }


            pub fn internal_start(
                &self,
            ) -> Position {
                let self_ptr = self.to_kotlin_object();
                let self_obj = self_ptr.as_kotlin_object();


                let (_, _detach_guard) = util::attach_thread_to_java_vm();
                let result = call_jvm_function!(
                    util,
                    c"org/kson/KsonValue$KsonNumber$Integer",
                    c"getInternalStart",
                    c"()Lorg/kson/Position;",
                    CallObjectMethod,
                    self_obj,

                );

                FromKotlinObject::from_kotlin_object(result)
            }


            pub fn internal_end(
                &self,
            ) -> Position {
                let self_ptr = self.to_kotlin_object();
                let self_obj = self_ptr.as_kotlin_object();


                let (_, _detach_guard) = util::attach_thread_to_java_vm();
                let result = call_jvm_function!(
                    util,
                    c"org/kson/KsonValue$KsonNumber$Integer",
                    c"getInternalEnd",
                    c"()Lorg/kson/Position;",
                    CallObjectMethod,
                    self_obj,

                );

                FromKotlinObject::from_kotlin_object(result)
            }


            pub fn type_(
                &self,
            ) -> KsonValueType {
                let self_ptr = self.to_kotlin_object();
                let self_obj = self_ptr.as_kotlin_object();


                let (_, _detach_guard) = util::attach_thread_to_java_vm();
                let result = call_jvm_function!(
                    util,
                    c"org/kson/KsonValue$KsonNumber$Integer",
                    c"getType",
                    c"()Lorg/kson/KsonValueType;",
                    CallObjectMethod,
                    self_obj,

                );

                FromKotlinObject::from_kotlin_object(result)
            }


            pub fn start(
                &self,
            ) -> Position {
                let self_ptr = self.to_kotlin_object();
                let self_obj = self_ptr.as_kotlin_object();


                let (_, _detach_guard) = util::attach_thread_to_java_vm();
                let result = call_jvm_function!(
                    util,
                    c"org/kson/KsonValue",
                    c"getStart",
                    c"()Lorg/kson/Position;",
                    CallObjectMethod,
                    self_obj,

                );

                FromKotlinObject::from_kotlin_object(result)
            }


            pub fn end(
                &self,
            ) -> Position {
                let self_ptr = self.to_kotlin_object();
                let self_obj = self_ptr.as_kotlin_object();


                let (_, _detach_guard) = util::attach_thread_to_java_vm();
                let result = call_jvm_function!(
                    util,
                    c"org/kson/KsonValue",
                    c"getEnd",
                    c"()Lorg/kson/Position;",
                    CallObjectMethod,
                    self_obj,

                );

                FromKotlinObject::from_kotlin_object(result)
            }
        }

        impl std::fmt::Debug for Integer {
            fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
                let obj = self.to_kotlin_object();
                write!(f, "{}", util::call_to_string(c"org/kson/KsonValue$KsonNumber$Integer", &obj))
            }
        }

        impl Eq for Integer {}
        impl PartialEq for Integer {
            fn eq(&self, other: &Integer) -> bool {
                util::equals(self.to_kotlin_object(), other.to_kotlin_object())
            }
        }
        impl std::hash::Hash for Integer {
            fn hash<H>(&self, state: &mut H)
            where
                H: std::hash::Hasher,
            {
                util::apply_hash_code(self.to_kotlin_object(), state)
            }
        }
    }
    impl FromKotlinObject for KsonNumber {
        fn from_kotlin_object(obj: jobject) -> Self {
            match util::class_name(obj).as_str() {
                "org.kson.KsonValue$KsonNumber$Decimal" => kson_value::KsonNumber::Decimal(kson_value::kson_number::Decimal::from_kotlin_object(obj)),
                "org.kson.KsonValue$KsonNumber$Integer" => kson_value::KsonNumber::Integer(kson_value::kson_number::Integer::from_kotlin_object(obj)),
                _ => unreachable!(),
            }
        }
    }

    impl ToKotlinObject for KsonNumber {
        fn to_kotlin_object(&self) -> KotlinPtr {
            match self {
                Self::Decimal(inner) => inner.to_kotlin_object(),
                Self::Integer(inner) => inner.to_kotlin_object(),
            }
        }
    }

    impl KsonNumber {
        pub fn name(self) -> String {
            let obj = self.to_kotlin_object();
            util::enum_name(&obj)
        }
    }


    impl KsonNumber {


        pub fn start(
            &self,
        ) -> Position {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue",
                c"getStart",
                c"()Lorg/kson/Position;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }


        pub fn end(
            &self,
        ) -> Position {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue",
                c"getEnd",
                c"()Lorg/kson/Position;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }

        /// Type discriminator for easier type checking in TypeScript/JavaScript
        pub fn type_(
            &self,
        ) -> KsonValueType {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue",
                c"getType",
                c"()Lorg/kson/KsonValueType;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }
    }

    impl std::fmt::Debug for KsonNumber {
        fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            let obj = self.to_kotlin_object();
            write!(f, "{}", util::call_to_string(c"org/kson/KsonValue$KsonNumber", &obj))
        }
    }

    impl Eq for KsonNumber {}
    impl PartialEq for KsonNumber {
        fn eq(&self, other: &KsonNumber) -> bool {
            util::equals(self.to_kotlin_object(), other.to_kotlin_object())
        }
    }
    impl std::hash::Hash for KsonNumber {
        fn hash<H>(&self, state: &mut H)
        where
            H: std::hash::Hasher,
        {
            util::apply_hash_code(self.to_kotlin_object(), state)
        }
    }

    /// A Kson object with key-value pairs
    #[derive(Clone)]
    pub struct KsonObject {
        kotlin_ptr: KotlinPtr,
    }

    impl FromKotlinObject for KsonObject {
        fn from_kotlin_object(obj: self::sys::jobject) -> Self {
            let (env, _detach_guard) = util::attach_thread_to_java_vm();
            let kotlin_ptr = util::to_gc_global_ref(env, obj);
            Self { kotlin_ptr }
        }
    }

    impl ToKotlinObject for KsonObject {
        fn to_kotlin_object(&self) -> KotlinPtr {
            self.kotlin_ptr.clone()
        }
    }

    impl AsKotlinObject for KsonObject {
        fn as_kotlin_object(&self) -> self::sys::jobject {
            self.kotlin_ptr.inner.inner
        }
    }

    impl KsonObject {
    }


    impl KsonObject {


        pub fn properties(
            &self,
        ) -> std::collections::HashMap<String, KsonValue> {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue$KsonObject",
                c"getProperties",
                c"()Ljava/util/Map;",
                CallObjectMethod,
                self_obj,

            );

            util::from_kotlin_value_map(result)
        }


        pub fn property_keys(
            &self,
        ) -> std::collections::HashMap<String, kson_value::KsonString> {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue$KsonObject",
                c"getPropertyKeys",
                c"()Ljava/util/Map;",
                CallObjectMethod,
                self_obj,

            );

            util::from_kotlin_value_map(result)
        }


        pub fn type_(
            &self,
        ) -> KsonValueType {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue$KsonObject",
                c"getType",
                c"()Lorg/kson/KsonValueType;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }


        pub fn start(
            &self,
        ) -> Position {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue",
                c"getStart",
                c"()Lorg/kson/Position;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }


        pub fn end(
            &self,
        ) -> Position {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue",
                c"getEnd",
                c"()Lorg/kson/Position;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }
    }

    impl std::fmt::Debug for KsonObject {
        fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            let obj = self.to_kotlin_object();
            write!(f, "{}", util::call_to_string(c"org/kson/KsonValue$KsonObject", &obj))
        }
    }

    impl Eq for KsonObject {}
    impl PartialEq for KsonObject {
        fn eq(&self, other: &KsonObject) -> bool {
            util::equals(self.to_kotlin_object(), other.to_kotlin_object())
        }
    }
    impl std::hash::Hash for KsonObject {
        fn hash<H>(&self, state: &mut H)
        where
            H: std::hash::Hasher,
        {
            util::apply_hash_code(self.to_kotlin_object(), state)
        }
    }

    /// A Kson string value
    #[derive(Clone)]
    pub struct KsonString {
        kotlin_ptr: KotlinPtr,
    }

    impl FromKotlinObject for KsonString {
        fn from_kotlin_object(obj: self::sys::jobject) -> Self {
            let (env, _detach_guard) = util::attach_thread_to_java_vm();
            let kotlin_ptr = util::to_gc_global_ref(env, obj);
            Self { kotlin_ptr }
        }
    }

    impl ToKotlinObject for KsonString {
        fn to_kotlin_object(&self) -> KotlinPtr {
            self.kotlin_ptr.clone()
        }
    }

    impl AsKotlinObject for KsonString {
        fn as_kotlin_object(&self) -> self::sys::jobject {
            self.kotlin_ptr.inner.inner
        }
    }

    impl KsonString {
    }


    impl KsonString {


        pub fn value(
            &self,
        ) -> String {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue$KsonString",
                c"getValue",
                c"()Ljava/lang/String;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }


        pub fn type_(
            &self,
        ) -> KsonValueType {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue$KsonString",
                c"getType",
                c"()Lorg/kson/KsonValueType;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }


        pub fn start(
            &self,
        ) -> Position {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue",
                c"getStart",
                c"()Lorg/kson/Position;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }


        pub fn end(
            &self,
        ) -> Position {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/KsonValue",
                c"getEnd",
                c"()Lorg/kson/Position;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }
    }

    impl std::fmt::Debug for KsonString {
        fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            let obj = self.to_kotlin_object();
            write!(f, "{}", util::call_to_string(c"org/kson/KsonValue$KsonString", &obj))
        }
    }

    impl Eq for KsonString {}
    impl PartialEq for KsonString {
        fn eq(&self, other: &KsonString) -> bool {
            util::equals(self.to_kotlin_object(), other.to_kotlin_object())
        }
    }
    impl std::hash::Hash for KsonString {
        fn hash<H>(&self, state: &mut H)
        where
            H: std::hash::Hasher,
        {
            util::apply_hash_code(self.to_kotlin_object(), state)
        }
    }
}
impl FromKotlinObject for KsonValue {
    fn from_kotlin_object(obj: jobject) -> Self {
        match util::class_name(obj).as_str() {
            "org.kson.KsonValue$KsonArray" => KsonValue::KsonArray(kson_value::KsonArray::from_kotlin_object(obj)),
            "org.kson.KsonValue$KsonBoolean" => KsonValue::KsonBoolean(kson_value::KsonBoolean::from_kotlin_object(obj)),
            "org.kson.KsonValue$KsonEmbed" => KsonValue::KsonEmbed(kson_value::KsonEmbed::from_kotlin_object(obj)),
            "org.kson.KsonValue$KsonNull" => KsonValue::KsonNull(kson_value::KsonNull::from_kotlin_object(obj)),
            "org.kson.KsonValue$KsonNumber" => KsonValue::KsonNumber(kson_value::KsonNumber::from_kotlin_object(obj)),
            "org.kson.KsonValue$KsonObject" => KsonValue::KsonObject(kson_value::KsonObject::from_kotlin_object(obj)),
            "org.kson.KsonValue$KsonString" => KsonValue::KsonString(kson_value::KsonString::from_kotlin_object(obj)),
            "org.kson.KsonValue$KsonNumber$Decimal" => KsonValue::KsonNumber(kson_value::KsonNumber::Decimal(kson_value::kson_number::Decimal::from_kotlin_object(obj))),
            "org.kson.KsonValue$KsonNumber$Integer" => KsonValue::KsonNumber(kson_value::KsonNumber::Integer(kson_value::kson_number::Integer::from_kotlin_object(obj))),
            _ => unreachable!(),
        }
    }
}

impl ToKotlinObject for KsonValue {
    fn to_kotlin_object(&self) -> KotlinPtr {
        match self {
            Self::KsonArray(inner) => inner.to_kotlin_object(),
            Self::KsonBoolean(inner) => inner.to_kotlin_object(),
            Self::KsonEmbed(inner) => inner.to_kotlin_object(),
            Self::KsonNull(inner) => inner.to_kotlin_object(),
            Self::KsonNumber(inner) => inner.to_kotlin_object(),
            Self::KsonObject(inner) => inner.to_kotlin_object(),
            Self::KsonString(inner) => inner.to_kotlin_object(),
        }
    }
}

impl KsonValue {
    pub fn name(self) -> String {
        let obj = self.to_kotlin_object();
        util::enum_name(&obj)
    }
}


impl KsonValue {


    pub fn start(
        &self,
    ) -> Position {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();


        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/KsonValue",
            c"getStart",
            c"()Lorg/kson/Position;",
            CallObjectMethod,
            self_obj,

        );

        FromKotlinObject::from_kotlin_object(result)
    }


    pub fn end(
        &self,
    ) -> Position {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();


        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/KsonValue",
            c"getEnd",
            c"()Lorg/kson/Position;",
            CallObjectMethod,
            self_obj,

        );

        FromKotlinObject::from_kotlin_object(result)
    }

    /// Type discriminator for easier type checking in TypeScript/JavaScript
    pub fn type_(
        &self,
    ) -> KsonValueType {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();


        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/KsonValue",
            c"getType",
            c"()Lorg/kson/KsonValueType;",
            CallObjectMethod,
            self_obj,

        );

        FromKotlinObject::from_kotlin_object(result)
    }
}

impl std::fmt::Debug for KsonValue {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let obj = self.to_kotlin_object();
        write!(f, "{}", util::call_to_string(c"org/kson/KsonValue", &obj))
    }
}

impl Eq for KsonValue {}
impl PartialEq for KsonValue {
    fn eq(&self, other: &KsonValue) -> bool {
        util::equals(self.to_kotlin_object(), other.to_kotlin_object())
    }
}
impl std::hash::Hash for KsonValue {
    fn hash<H>(&self, state: &mut H)
    where
        H: std::hash::Hasher,
    {
        util::apply_hash_code(self.to_kotlin_object(), state)
    }
}


/// A validator that can check if Kson source conforms to a schema.
#[derive(Clone)]
pub struct SchemaValidator {
    kotlin_ptr: KotlinPtr,
}

impl FromKotlinObject for SchemaValidator {
    fn from_kotlin_object(obj: self::sys::jobject) -> Self {
        let (env, _detach_guard) = util::attach_thread_to_java_vm();
        let kotlin_ptr = util::to_gc_global_ref(env, obj);
        Self { kotlin_ptr }
    }
}

impl ToKotlinObject for SchemaValidator {
    fn to_kotlin_object(&self) -> KotlinPtr {
        self.kotlin_ptr.clone()
    }
}

impl AsKotlinObject for SchemaValidator {
    fn as_kotlin_object(&self) -> self::sys::jobject {
        self.kotlin_ptr.inner.inner
    }
}

impl SchemaValidator {
}


impl SchemaValidator {

    /// Validates the given Kson source against this validator's schema.
    /// @param kson The Kson source to validate
    /// @param filepath Optional filepath of the document being validated, used by validators to determine which rules to apply
    ///
    /// @return A list of validation error messages, or empty list if valid
    pub fn validate(
        &self,
        kson: &str,
        filepath: Option<&str>,
    ) -> Vec<Message> {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();
        let kson_ptr = kson.to_kotlin_object();
        let kson = kson_ptr.as_kotlin_object();
        let filepath_ptr = filepath.to_kotlin_object();
        let filepath = filepath_ptr.as_kotlin_object();

        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/SchemaValidator",
            c"validate",
            c"(Ljava/lang/String;Ljava/lang/String;)Ljava/util/List;",
            CallObjectMethod,
            self_obj,
            kson,
            filepath,
        );

        util::from_kotlin_list(result)
    }
}

impl std::fmt::Debug for SchemaValidator {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let obj = self.to_kotlin_object();
        write!(f, "{}", util::call_to_string(c"org/kson/SchemaValidator", &obj))
    }
}

impl Eq for SchemaValidator {}
impl PartialEq for SchemaValidator {
    fn eq(&self, other: &SchemaValidator) -> bool {
        util::equals(self.to_kotlin_object(), other.to_kotlin_object())
    }
}
impl std::hash::Hash for SchemaValidator {
    fn hash<H>(&self, state: &mut H)
    where
        H: std::hash::Hasher,
    {
        util::apply_hash_code(self.to_kotlin_object(), state)
    }
}


/// The result of statically analyzing a Kson document
#[derive(Clone)]
pub struct Analysis {
    kotlin_ptr: KotlinPtr,
}

impl FromKotlinObject for Analysis {
    fn from_kotlin_object(obj: self::sys::jobject) -> Self {
        let (env, _detach_guard) = util::attach_thread_to_java_vm();
        let kotlin_ptr = util::to_gc_global_ref(env, obj);
        Self { kotlin_ptr }
    }
}

impl ToKotlinObject for Analysis {
    fn to_kotlin_object(&self) -> KotlinPtr {
        self.kotlin_ptr.clone()
    }
}

impl AsKotlinObject for Analysis {
    fn as_kotlin_object(&self) -> self::sys::jobject {
        self.kotlin_ptr.inner.inner
    }
}

impl Analysis {
}


impl Analysis {


    pub fn errors(
        &self,
    ) -> Vec<Message> {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();


        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/Analysis",
            c"getErrors",
            c"()Ljava/util/List;",
            CallObjectMethod,
            self_obj,

        );

        util::from_kotlin_list(result)
    }


    pub fn tokens(
        &self,
    ) -> Vec<Token> {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();


        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/Analysis",
            c"getTokens",
            c"()Ljava/util/List;",
            CallObjectMethod,
            self_obj,

        );

        util::from_kotlin_list(result)
    }


    pub fn kson_value(
        &self,
    ) -> Option<KsonValue> {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();


        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/Analysis",
            c"getKsonValue",
            c"()Lorg/kson/KsonValue;",
            CallObjectMethod,
            self_obj,

        );

        FromKotlinObject::from_kotlin_object(result)
    }
}

impl std::fmt::Debug for Analysis {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let obj = self.to_kotlin_object();
        write!(f, "{}", util::call_to_string(c"org/kson/Analysis", &obj))
    }
}

impl Eq for Analysis {}
impl PartialEq for Analysis {
    fn eq(&self, other: &Analysis) -> bool {
        util::equals(self.to_kotlin_object(), other.to_kotlin_object())
    }
}
impl std::hash::Hash for Analysis {
    fn hash<H>(&self, state: &mut H)
    where
        H: std::hash::Hasher,
    {
        util::apply_hash_code(self.to_kotlin_object(), state)
    }
}

/// Result of a Kson conversion operation
#[derive(Clone)]
pub enum Result {
    Failure(result::Failure),
    Success(result::Success),
}

pub mod result {
    use super::*;



    #[derive(Clone)]
    pub struct Failure {
        kotlin_ptr: KotlinPtr,
    }

    impl FromKotlinObject for Failure {
        fn from_kotlin_object(obj: self::sys::jobject) -> Self {
            let (env, _detach_guard) = util::attach_thread_to_java_vm();
            let kotlin_ptr = util::to_gc_global_ref(env, obj);
            Self { kotlin_ptr }
        }
    }

    impl ToKotlinObject for Failure {
        fn to_kotlin_object(&self) -> KotlinPtr {
            self.kotlin_ptr.clone()
        }
    }

    impl AsKotlinObject for Failure {
        fn as_kotlin_object(&self) -> self::sys::jobject {
            self.kotlin_ptr.inner.inner
        }
    }

    impl Failure {
        pub fn new(
            errors: &[Message],
        ) -> Self {
            let (env, _detach_guard) = util::attach_thread_to_java_vm();
            let class = util::get_class(env, c"org/kson/Result$Failure");
            let constructor = util::get_method(env, class.as_kotlin_object(), c"<init>", c"(Ljava/util/List;)V");

            let errors_ptr = util::to_kotlin_list(errors);
            let errors = errors_ptr.as_kotlin_object();

            let jobject = unsafe { (**env).NewObject.unwrap()(env, class.as_kotlin_object(), constructor,
                errors,
            )};
            util::panic_upon_exception(env);
            Self {
                kotlin_ptr: util::to_gc_global_ref(env, jobject)
            }
        }
    }


    impl Failure {


        pub fn errors(
            &self,
        ) -> Vec<Message> {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/Result$Failure",
                c"getErrors",
                c"()Ljava/util/List;",
                CallObjectMethod,
                self_obj,

            );

            util::from_kotlin_list(result)
        }
    }

    impl std::fmt::Debug for Failure {
        fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            let obj = self.to_kotlin_object();
            write!(f, "{}", util::call_to_string(c"org/kson/Result$Failure", &obj))
        }
    }

    impl Eq for Failure {}
    impl PartialEq for Failure {
        fn eq(&self, other: &Failure) -> bool {
            util::equals(self.to_kotlin_object(), other.to_kotlin_object())
        }
    }
    impl std::hash::Hash for Failure {
        fn hash<H>(&self, state: &mut H)
        where
            H: std::hash::Hasher,
        {
            util::apply_hash_code(self.to_kotlin_object(), state)
        }
    }


    #[derive(Clone)]
    pub struct Success {
        kotlin_ptr: KotlinPtr,
    }

    impl FromKotlinObject for Success {
        fn from_kotlin_object(obj: self::sys::jobject) -> Self {
            let (env, _detach_guard) = util::attach_thread_to_java_vm();
            let kotlin_ptr = util::to_gc_global_ref(env, obj);
            Self { kotlin_ptr }
        }
    }

    impl ToKotlinObject for Success {
        fn to_kotlin_object(&self) -> KotlinPtr {
            self.kotlin_ptr.clone()
        }
    }

    impl AsKotlinObject for Success {
        fn as_kotlin_object(&self) -> self::sys::jobject {
            self.kotlin_ptr.inner.inner
        }
    }

    impl Success {
        pub fn new(
            output: &str,
        ) -> Self {
            let (env, _detach_guard) = util::attach_thread_to_java_vm();
            let class = util::get_class(env, c"org/kson/Result$Success");
            let constructor = util::get_method(env, class.as_kotlin_object(), c"<init>", c"(Ljava/lang/String;)V");

            let output_ptr = output.to_kotlin_object();
            let output = output_ptr.as_kotlin_object();

            let jobject = unsafe { (**env).NewObject.unwrap()(env, class.as_kotlin_object(), constructor,
                output,
            )};
            util::panic_upon_exception(env);
            Self {
                kotlin_ptr: util::to_gc_global_ref(env, jobject)
            }
        }
    }


    impl Success {


        pub fn output(
            &self,
        ) -> String {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/Result$Success",
                c"getOutput",
                c"()Ljava/lang/String;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }
    }

    impl std::fmt::Debug for Success {
        fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            let obj = self.to_kotlin_object();
            write!(f, "{}", util::call_to_string(c"org/kson/Result$Success", &obj))
        }
    }

    impl Eq for Success {}
    impl PartialEq for Success {
        fn eq(&self, other: &Success) -> bool {
            util::equals(self.to_kotlin_object(), other.to_kotlin_object())
        }
    }
    impl std::hash::Hash for Success {
        fn hash<H>(&self, state: &mut H)
        where
            H: std::hash::Hasher,
        {
            util::apply_hash_code(self.to_kotlin_object(), state)
        }
    }
}
impl FromKotlinObject for Result {
    fn from_kotlin_object(obj: jobject) -> Self {
        match util::class_name(obj).as_str() {
            "org.kson.Result$Failure" => Result::Failure(result::Failure::from_kotlin_object(obj)),
            "org.kson.Result$Success" => Result::Success(result::Success::from_kotlin_object(obj)),
            _ => unreachable!(),
        }
    }
}

impl ToKotlinObject for Result {
    fn to_kotlin_object(&self) -> KotlinPtr {
        match self {
            Self::Failure(inner) => inner.to_kotlin_object(),
            Self::Success(inner) => inner.to_kotlin_object(),
        }
    }
}

impl Result {
    pub fn name(self) -> String {
        let obj = self.to_kotlin_object();
        util::enum_name(&obj)
    }
}


impl Result {
}

impl std::fmt::Debug for Result {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let obj = self.to_kotlin_object();
        write!(f, "{}", util::call_to_string(c"org/kson/Result", &obj))
    }
}

impl Eq for Result {}
impl PartialEq for Result {
    fn eq(&self, other: &Result) -> bool {
        util::equals(self.to_kotlin_object(), other.to_kotlin_object())
    }
}
impl std::hash::Hash for Result {
    fn hash<H>(&self, state: &mut H)
    where
        H: std::hash::Hasher,
    {
        util::apply_hash_code(self.to_kotlin_object(), state)
    }
}

/// A [parseSchema] result
#[derive(Clone)]
pub enum SchemaResult {
    Failure(schema_result::Failure),
    Success(schema_result::Success),
}

pub mod schema_result {
    use super::*;



    #[derive(Clone)]
    pub struct Failure {
        kotlin_ptr: KotlinPtr,
    }

    impl FromKotlinObject for Failure {
        fn from_kotlin_object(obj: self::sys::jobject) -> Self {
            let (env, _detach_guard) = util::attach_thread_to_java_vm();
            let kotlin_ptr = util::to_gc_global_ref(env, obj);
            Self { kotlin_ptr }
        }
    }

    impl ToKotlinObject for Failure {
        fn to_kotlin_object(&self) -> KotlinPtr {
            self.kotlin_ptr.clone()
        }
    }

    impl AsKotlinObject for Failure {
        fn as_kotlin_object(&self) -> self::sys::jobject {
            self.kotlin_ptr.inner.inner
        }
    }

    impl Failure {
        pub fn new(
            errors: &[Message],
        ) -> Self {
            let (env, _detach_guard) = util::attach_thread_to_java_vm();
            let class = util::get_class(env, c"org/kson/SchemaResult$Failure");
            let constructor = util::get_method(env, class.as_kotlin_object(), c"<init>", c"(Ljava/util/List;)V");

            let errors_ptr = util::to_kotlin_list(errors);
            let errors = errors_ptr.as_kotlin_object();

            let jobject = unsafe { (**env).NewObject.unwrap()(env, class.as_kotlin_object(), constructor,
                errors,
            )};
            util::panic_upon_exception(env);
            Self {
                kotlin_ptr: util::to_gc_global_ref(env, jobject)
            }
        }
    }


    impl Failure {


        pub fn errors(
            &self,
        ) -> Vec<Message> {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/SchemaResult$Failure",
                c"getErrors",
                c"()Ljava/util/List;",
                CallObjectMethod,
                self_obj,

            );

            util::from_kotlin_list(result)
        }
    }

    impl std::fmt::Debug for Failure {
        fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            let obj = self.to_kotlin_object();
            write!(f, "{}", util::call_to_string(c"org/kson/SchemaResult$Failure", &obj))
        }
    }

    impl Eq for Failure {}
    impl PartialEq for Failure {
        fn eq(&self, other: &Failure) -> bool {
            util::equals(self.to_kotlin_object(), other.to_kotlin_object())
        }
    }
    impl std::hash::Hash for Failure {
        fn hash<H>(&self, state: &mut H)
        where
            H: std::hash::Hasher,
        {
            util::apply_hash_code(self.to_kotlin_object(), state)
        }
    }


    #[derive(Clone)]
    pub struct Success {
        kotlin_ptr: KotlinPtr,
    }

    impl FromKotlinObject for Success {
        fn from_kotlin_object(obj: self::sys::jobject) -> Self {
            let (env, _detach_guard) = util::attach_thread_to_java_vm();
            let kotlin_ptr = util::to_gc_global_ref(env, obj);
            Self { kotlin_ptr }
        }
    }

    impl ToKotlinObject for Success {
        fn to_kotlin_object(&self) -> KotlinPtr {
            self.kotlin_ptr.clone()
        }
    }

    impl AsKotlinObject for Success {
        fn as_kotlin_object(&self) -> self::sys::jobject {
            self.kotlin_ptr.inner.inner
        }
    }

    impl Success {
        pub fn new(
            schema_validator: SchemaValidator,
        ) -> Self {
            let (env, _detach_guard) = util::attach_thread_to_java_vm();
            let class = util::get_class(env, c"org/kson/SchemaResult$Success");
            let constructor = util::get_method(env, class.as_kotlin_object(), c"<init>", c"(Lorg/kson/SchemaValidator;)V");

            let schema_validator_ptr = schema_validator.to_kotlin_object();
            let schema_validator = schema_validator_ptr.as_kotlin_object();

            let jobject = unsafe { (**env).NewObject.unwrap()(env, class.as_kotlin_object(), constructor,
                schema_validator,
            )};
            util::panic_upon_exception(env);
            Self {
                kotlin_ptr: util::to_gc_global_ref(env, jobject)
            }
        }
    }


    impl Success {


        pub fn schema_validator(
            &self,
        ) -> SchemaValidator {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/SchemaResult$Success",
                c"getSchemaValidator",
                c"()Lorg/kson/SchemaValidator;",
                CallObjectMethod,
                self_obj,

            );

            FromKotlinObject::from_kotlin_object(result)
        }
    }

    impl std::fmt::Debug for Success {
        fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            let obj = self.to_kotlin_object();
            write!(f, "{}", util::call_to_string(c"org/kson/SchemaResult$Success", &obj))
        }
    }

    impl Eq for Success {}
    impl PartialEq for Success {
        fn eq(&self, other: &Success) -> bool {
            util::equals(self.to_kotlin_object(), other.to_kotlin_object())
        }
    }
    impl std::hash::Hash for Success {
        fn hash<H>(&self, state: &mut H)
        where
            H: std::hash::Hasher,
        {
            util::apply_hash_code(self.to_kotlin_object(), state)
        }
    }
}
impl FromKotlinObject for SchemaResult {
    fn from_kotlin_object(obj: jobject) -> Self {
        match util::class_name(obj).as_str() {
            "org.kson.SchemaResult$Failure" => SchemaResult::Failure(schema_result::Failure::from_kotlin_object(obj)),
            "org.kson.SchemaResult$Success" => SchemaResult::Success(schema_result::Success::from_kotlin_object(obj)),
            _ => unreachable!(),
        }
    }
}

impl ToKotlinObject for SchemaResult {
    fn to_kotlin_object(&self) -> KotlinPtr {
        match self {
            Self::Failure(inner) => inner.to_kotlin_object(),
            Self::Success(inner) => inner.to_kotlin_object(),
        }
    }
}

impl SchemaResult {
    pub fn name(self) -> String {
        let obj = self.to_kotlin_object();
        util::enum_name(&obj)
    }
}


impl SchemaResult {
}

impl std::fmt::Debug for SchemaResult {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let obj = self.to_kotlin_object();
        write!(f, "{}", util::call_to_string(c"org/kson/SchemaResult", &obj))
    }
}

impl Eq for SchemaResult {}
impl PartialEq for SchemaResult {
    fn eq(&self, other: &SchemaResult) -> bool {
        util::equals(self.to_kotlin_object(), other.to_kotlin_object())
    }
}
impl std::hash::Hash for SchemaResult {
    fn hash<H>(&self, state: &mut H)
    where
        H: std::hash::Hasher,
    {
        util::apply_hash_code(self.to_kotlin_object(), state)
    }
}

/// Core interface for transpilation options shared across all output formats.
#[derive(Clone)]
pub enum TranspileOptions {
    Json(transpile_options::Json),
    Yaml(transpile_options::Yaml),
}

pub mod transpile_options {
    use super::*;


    /// Options for transpiling Kson to JSON.
    #[derive(Clone)]
    pub struct Json {
        kotlin_ptr: KotlinPtr,
    }

    impl FromKotlinObject for Json {
        fn from_kotlin_object(obj: self::sys::jobject) -> Self {
            let (env, _detach_guard) = util::attach_thread_to_java_vm();
            let kotlin_ptr = util::to_gc_global_ref(env, obj);
            Self { kotlin_ptr }
        }
    }

    impl ToKotlinObject for Json {
        fn to_kotlin_object(&self) -> KotlinPtr {
            self.kotlin_ptr.clone()
        }
    }

    impl AsKotlinObject for Json {
        fn as_kotlin_object(&self) -> self::sys::jobject {
            self.kotlin_ptr.inner.inner
        }
    }

    impl Json {
        pub fn new(
            retain_embed_tags: bool,
        ) -> Self {
            let (env, _detach_guard) = util::attach_thread_to_java_vm();
            let class = util::get_class(env, c"org/kson/TranspileOptions$Json");
            let constructor = util::get_method(env, class.as_kotlin_object(), c"<init>", c"(Z)V");

            let retain_embed_tags = retain_embed_tags as c_int;

            let jobject = unsafe { (**env).NewObject.unwrap()(env, class.as_kotlin_object(), constructor,
                retain_embed_tags,
            )};
            util::panic_upon_exception(env);
            Self {
                kotlin_ptr: util::to_gc_global_ref(env, jobject)
            }
        }
    }


    impl Json {


        pub fn retain_embed_tags(
            &self,
        ) -> bool {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/TranspileOptions$Json",
                c"getRetainEmbedTags",
                c"()Z",
                CallBooleanMethod,
                self_obj,

            );

            result != 0
        }
    }

    impl std::fmt::Debug for Json {
        fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            let obj = self.to_kotlin_object();
            write!(f, "{}", util::call_to_string(c"org/kson/TranspileOptions$Json", &obj))
        }
    }

    impl Eq for Json {}
    impl PartialEq for Json {
        fn eq(&self, other: &Json) -> bool {
            util::equals(self.to_kotlin_object(), other.to_kotlin_object())
        }
    }
    impl std::hash::Hash for Json {
        fn hash<H>(&self, state: &mut H)
        where
            H: std::hash::Hasher,
        {
            util::apply_hash_code(self.to_kotlin_object(), state)
        }
    }

    /// Options for transpiling Kson to YAML.
    #[derive(Clone)]
    pub struct Yaml {
        kotlin_ptr: KotlinPtr,
    }

    impl FromKotlinObject for Yaml {
        fn from_kotlin_object(obj: self::sys::jobject) -> Self {
            let (env, _detach_guard) = util::attach_thread_to_java_vm();
            let kotlin_ptr = util::to_gc_global_ref(env, obj);
            Self { kotlin_ptr }
        }
    }

    impl ToKotlinObject for Yaml {
        fn to_kotlin_object(&self) -> KotlinPtr {
            self.kotlin_ptr.clone()
        }
    }

    impl AsKotlinObject for Yaml {
        fn as_kotlin_object(&self) -> self::sys::jobject {
            self.kotlin_ptr.inner.inner
        }
    }

    impl Yaml {
        pub fn new(
            retain_embed_tags: bool,
        ) -> Self {
            let (env, _detach_guard) = util::attach_thread_to_java_vm();
            let class = util::get_class(env, c"org/kson/TranspileOptions$Yaml");
            let constructor = util::get_method(env, class.as_kotlin_object(), c"<init>", c"(Z)V");

            let retain_embed_tags = retain_embed_tags as c_int;

            let jobject = unsafe { (**env).NewObject.unwrap()(env, class.as_kotlin_object(), constructor,
                retain_embed_tags,
            )};
            util::panic_upon_exception(env);
            Self {
                kotlin_ptr: util::to_gc_global_ref(env, jobject)
            }
        }
    }


    impl Yaml {


        pub fn retain_embed_tags(
            &self,
        ) -> bool {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/TranspileOptions$Yaml",
                c"getRetainEmbedTags",
                c"()Z",
                CallBooleanMethod,
                self_obj,

            );

            result != 0
        }
    }

    impl std::fmt::Debug for Yaml {
        fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            let obj = self.to_kotlin_object();
            write!(f, "{}", util::call_to_string(c"org/kson/TranspileOptions$Yaml", &obj))
        }
    }

    impl Eq for Yaml {}
    impl PartialEq for Yaml {
        fn eq(&self, other: &Yaml) -> bool {
            util::equals(self.to_kotlin_object(), other.to_kotlin_object())
        }
    }
    impl std::hash::Hash for Yaml {
        fn hash<H>(&self, state: &mut H)
        where
            H: std::hash::Hasher,
        {
            util::apply_hash_code(self.to_kotlin_object(), state)
        }
    }
}
impl FromKotlinObject for TranspileOptions {
    fn from_kotlin_object(obj: jobject) -> Self {
        match util::class_name(obj).as_str() {
            "org.kson.TranspileOptions$Json" => TranspileOptions::Json(transpile_options::Json::from_kotlin_object(obj)),
            "org.kson.TranspileOptions$Yaml" => TranspileOptions::Yaml(transpile_options::Yaml::from_kotlin_object(obj)),
            _ => unreachable!(),
        }
    }
}

impl ToKotlinObject for TranspileOptions {
    fn to_kotlin_object(&self) -> KotlinPtr {
        match self {
            Self::Json(inner) => inner.to_kotlin_object(),
            Self::Yaml(inner) => inner.to_kotlin_object(),
        }
    }
}

impl TranspileOptions {
    pub fn name(self) -> String {
        let obj = self.to_kotlin_object();
        util::enum_name(&obj)
    }
}


impl TranspileOptions {


    pub fn retain_embed_tags(
        &self,
    ) -> bool {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();


        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/TranspileOptions",
            c"getRetainEmbedTags",
            c"()Z",
            CallBooleanMethod,
            self_obj,

        );

        result != 0
    }
}

impl std::fmt::Debug for TranspileOptions {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let obj = self.to_kotlin_object();
        write!(f, "{}", util::call_to_string(c"org/kson/TranspileOptions", &obj))
    }
}

impl Eq for TranspileOptions {}
impl PartialEq for TranspileOptions {
    fn eq(&self, other: &TranspileOptions) -> bool {
        util::equals(self.to_kotlin_object(), other.to_kotlin_object())
    }
}
impl std::hash::Hash for TranspileOptions {
    fn hash<H>(&self, state: &mut H)
    where
        H: std::hash::Hasher,
    {
        util::apply_hash_code(self.to_kotlin_object(), state)
    }
}


/// The [Kson](https://kson.org) language
#[derive(Clone)]
pub struct Kson {
    kotlin_ptr: KotlinPtr,
}

impl FromKotlinObject for Kson {
    fn from_kotlin_object(obj: self::sys::jobject) -> Self {
        let (env, _detach_guard) = util::attach_thread_to_java_vm();
        let kotlin_ptr = util::to_gc_global_ref(env, obj);
        Self { kotlin_ptr }
    }
}

impl ToKotlinObject for Kson {
    fn to_kotlin_object(&self) -> KotlinPtr {
        self.kotlin_ptr.clone()
    }
}

impl AsKotlinObject for Kson {
    fn as_kotlin_object(&self) -> self::sys::jobject {
        self.kotlin_ptr.inner.inner
    }
}

impl Kson {
}


impl Kson {

    /// Formats Kson source with the specified formatting options.
    ///
    /// @param kson The Kson source to format
    /// @param formatOptions The formatting options to apply
    /// @return The formatted Kson source
    pub fn format(
        kson: &str,
        format_options: FormatOptions,
    ) -> String {
        let self_ptr = util::access_static_field(c"org/kson/Kson", c"INSTANCE", c"Lorg/kson/Kson;");
        let self_obj = self_ptr.as_kotlin_object();
        let kson_ptr = kson.to_kotlin_object();
        let kson = kson_ptr.as_kotlin_object();
        let format_options_ptr = format_options.to_kotlin_object();
        let format_options = format_options_ptr.as_kotlin_object();

        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/Kson",
            c"format",
            c"(Ljava/lang/String;Lorg/kson/FormatOptions;)Ljava/lang/String;",
            CallObjectMethod,
            self_obj,
            kson,
            format_options,
        );

        FromKotlinObject::from_kotlin_object(result)
    }

    /// Converts Kson to Json.
    ///
    /// @param kson The Kson source to convert
    /// @param options Options for the JSON transpilation
    /// @return A Result containing either the Json output or error messages
    pub fn to_json(
        kson: &str,
        options: transpile_options::Json,
    ) -> std::result::Result<result::Success, result::Failure> {
        let self_ptr = util::access_static_field(c"org/kson/Kson", c"INSTANCE", c"Lorg/kson/Kson;");
        let self_obj = self_ptr.as_kotlin_object();
        let kson_ptr = kson.to_kotlin_object();
        let kson = kson_ptr.as_kotlin_object();
        let options_ptr = options.to_kotlin_object();
        let options = options_ptr.as_kotlin_object();

        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/Kson",
            c"toJson",
            c"(Ljava/lang/String;Lorg/kson/TranspileOptions$Json;)Lorg/kson/Result;",
            CallObjectMethod,
            self_obj,
            kson,
            options,
        );

        crate::kson_result_into_rust_result(FromKotlinObject::from_kotlin_object(result))
    }

    /// Converts Kson to Yaml, preserving comments
    ///
    /// @param kson The Kson source to convert
    /// @param options Options for the YAML transpilation
    /// @return A Result containing either the Yaml output or error messages
    pub fn to_yaml(
        kson: &str,
        options: transpile_options::Yaml,
    ) -> std::result::Result<result::Success, result::Failure> {
        let self_ptr = util::access_static_field(c"org/kson/Kson", c"INSTANCE", c"Lorg/kson/Kson;");
        let self_obj = self_ptr.as_kotlin_object();
        let kson_ptr = kson.to_kotlin_object();
        let kson = kson_ptr.as_kotlin_object();
        let options_ptr = options.to_kotlin_object();
        let options = options_ptr.as_kotlin_object();

        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/Kson",
            c"toYaml",
            c"(Ljava/lang/String;Lorg/kson/TranspileOptions$Yaml;)Lorg/kson/Result;",
            CallObjectMethod,
            self_obj,
            kson,
            options,
        );

        crate::kson_result_into_rust_result(FromKotlinObject::from_kotlin_object(result))
    }

    /// Statically analyze the given Kson and return an [Analysis] object containing any messages generated along with a
    /// tokenized version of the source.  Useful for tooling/editor support.
    /// @param kson The Kson source to analyze
    /// @param filepath Filepath of the document being analyzed
    pub fn analyze(
        kson: &str,
        filepath: Option<&str>,
    ) -> Analysis {
        let self_ptr = util::access_static_field(c"org/kson/Kson", c"INSTANCE", c"Lorg/kson/Kson;");
        let self_obj = self_ptr.as_kotlin_object();
        let kson_ptr = kson.to_kotlin_object();
        let kson = kson_ptr.as_kotlin_object();
        let filepath_ptr = filepath.to_kotlin_object();
        let filepath = filepath_ptr.as_kotlin_object();

        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/Kson",
            c"analyze",
            c"(Ljava/lang/String;Ljava/lang/String;)Lorg/kson/Analysis;",
            CallObjectMethod,
            self_obj,
            kson,
            filepath,
        );

        FromKotlinObject::from_kotlin_object(result)
    }

    /// Parses a Kson schema definition and returns a validator for that schema.
    ///
    /// @param schemaKson The Kson source defining a Json Schema
    /// @return A SchemaValidator that can validate Kson documents against the schema
    pub fn parse_schema(
        schema_kson: &str,
    ) -> std::result::Result<schema_result::Success, schema_result::Failure> {
        let self_ptr = util::access_static_field(c"org/kson/Kson", c"INSTANCE", c"Lorg/kson/Kson;");
        let self_obj = self_ptr.as_kotlin_object();
        let schema_kson_ptr = schema_kson.to_kotlin_object();
        let schema_kson = schema_kson_ptr.as_kotlin_object();

        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/Kson",
            c"parseSchema",
            c"(Ljava/lang/String;)Lorg/kson/SchemaResult;",
            CallObjectMethod,
            self_obj,
            schema_kson,
        );

        crate::kson_schema_result_into_rust_result(FromKotlinObject::from_kotlin_object(result))
    }
}

impl std::fmt::Debug for Kson {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let obj = self.to_kotlin_object();
        write!(f, "{}", util::call_to_string(c"org/kson/Kson", &obj))
    }
}

impl Eq for Kson {}
impl PartialEq for Kson {
    fn eq(&self, other: &Kson) -> bool {
        util::equals(self.to_kotlin_object(), other.to_kotlin_object())
    }
}
impl std::hash::Hash for Kson {
    fn hash<H>(&self, state: &mut H)
    where
        H: std::hash::Hasher,
    {
        util::apply_hash_code(self.to_kotlin_object(), state)
    }
}


/// A rule for formatting string values at specific paths as embed blocks.
///
/// When formatting KSON, strings at paths matching [pathPattern] will be rendered
/// as embed blocks instead of regular strings.
///
/// **Warning:** JsonPointerGlob syntax is experimental and may change in future versions.
///
/// @param pathPattern A JsonPointerGlob pattern (e.g., "/scripts/ *", "/queries/ **")
/// @param tag Optional embed tag to include (e.g., "yaml", "sql", "bash")
/// @throws IllegalArgumentException if [pathPattern] is not a valid JsonPointerGlob
///
/// Example:
/// ```kotlin
/// EmbedRule("/scripts/ *", tag = "bash")  // Match all values under "scripts"
/// EmbedRule("/config/description")       // Match exact path, no tag
/// ```
#[derive(Clone)]
pub struct EmbedRule {
    kotlin_ptr: KotlinPtr,
}

impl FromKotlinObject for EmbedRule {
    fn from_kotlin_object(obj: self::sys::jobject) -> Self {
        let (env, _detach_guard) = util::attach_thread_to_java_vm();
        let kotlin_ptr = util::to_gc_global_ref(env, obj);
        Self { kotlin_ptr }
    }
}

impl ToKotlinObject for EmbedRule {
    fn to_kotlin_object(&self) -> KotlinPtr {
        self.kotlin_ptr.clone()
    }
}

impl AsKotlinObject for EmbedRule {
    fn as_kotlin_object(&self) -> self::sys::jobject {
        self.kotlin_ptr.inner.inner
    }
}

impl EmbedRule {
    pub fn new(
        path_pattern: &str,
        tag: Option<&str>,
    ) -> Self {
        let (env, _detach_guard) = util::attach_thread_to_java_vm();
        let class = util::get_class(env, c"org/kson/EmbedRule");
        let constructor = util::get_method(env, class.as_kotlin_object(), c"<init>", c"(Ljava/lang/String;Ljava/lang/String;)V");

        let path_pattern_ptr = path_pattern.to_kotlin_object();
        let path_pattern = path_pattern_ptr.as_kotlin_object();
        let tag_ptr = tag.to_kotlin_object();
        let tag = tag_ptr.as_kotlin_object();

        let jobject = unsafe { (**env).NewObject.unwrap()(env, class.as_kotlin_object(), constructor,
            path_pattern,
            tag,
        )};
        util::panic_upon_exception(env);
        Self {
            kotlin_ptr: util::to_gc_global_ref(env, jobject)
        }
    }
}


impl EmbedRule {


    pub fn path_pattern(
        &self,
    ) -> String {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();


        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/EmbedRule",
            c"getPathPattern",
            c"()Ljava/lang/String;",
            CallObjectMethod,
            self_obj,

        );

        FromKotlinObject::from_kotlin_object(result)
    }


    pub fn tag(
        &self,
    ) -> Option<String> {
        let self_ptr = self.to_kotlin_object();
        let self_obj = self_ptr.as_kotlin_object();


        let (_, _detach_guard) = util::attach_thread_to_java_vm();
        let result = call_jvm_function!(
            util,
            c"org/kson/EmbedRule",
            c"getTag",
            c"()Ljava/lang/String;",
            CallObjectMethod,
            self_obj,

        );

        FromKotlinObject::from_kotlin_object(result)
    }
}

impl std::fmt::Debug for EmbedRule {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let obj = self.to_kotlin_object();
        write!(f, "{}", util::call_to_string(c"org/kson/EmbedRule", &obj))
    }
}

impl Eq for EmbedRule {}
impl PartialEq for EmbedRule {
    fn eq(&self, other: &EmbedRule) -> bool {
        util::equals(self.to_kotlin_object(), other.to_kotlin_object())
    }
}
impl std::hash::Hash for EmbedRule {
    fn hash<H>(&self, state: &mut H)
    where
        H: std::hash::Hasher,
    {
        util::apply_hash_code(self.to_kotlin_object(), state)
    }
}

/// Options for indenting Kson Output
#[derive(Clone)]
pub enum IndentType {
    Spaces(indent_type::Spaces),
    Tabs(indent_type::Tabs),
}

pub mod indent_type {
    use super::*;


    /// Use spaces for indentation with the specified count
    #[derive(Clone)]
    pub struct Spaces {
        kotlin_ptr: KotlinPtr,
    }

    impl FromKotlinObject for Spaces {
        fn from_kotlin_object(obj: self::sys::jobject) -> Self {
            let (env, _detach_guard) = util::attach_thread_to_java_vm();
            let kotlin_ptr = util::to_gc_global_ref(env, obj);
            Self { kotlin_ptr }
        }
    }

    impl ToKotlinObject for Spaces {
        fn to_kotlin_object(&self) -> KotlinPtr {
            self.kotlin_ptr.clone()
        }
    }

    impl AsKotlinObject for Spaces {
        fn as_kotlin_object(&self) -> self::sys::jobject {
            self.kotlin_ptr.inner.inner
        }
    }

    impl Spaces {
        pub fn new(
            size: i32,
        ) -> Self {
            let (env, _detach_guard) = util::attach_thread_to_java_vm();
            let class = util::get_class(env, c"org/kson/IndentType$Spaces");
            let constructor = util::get_method(env, class.as_kotlin_object(), c"<init>", c"(I)V");



            let jobject = unsafe { (**env).NewObject.unwrap()(env, class.as_kotlin_object(), constructor,
                size,
            )};
            util::panic_upon_exception(env);
            Self {
                kotlin_ptr: util::to_gc_global_ref(env, jobject)
            }
        }
    }


    impl Spaces {


        pub fn size(
            &self,
        ) -> i32 {
            let self_ptr = self.to_kotlin_object();
            let self_obj = self_ptr.as_kotlin_object();


            let (_, _detach_guard) = util::attach_thread_to_java_vm();
            let result = call_jvm_function!(
                util,
                c"org/kson/IndentType$Spaces",
                c"getSize",
                c"()I",
                CallIntMethod,
                self_obj,

            );

            result
        }
    }

    impl std::fmt::Debug for Spaces {
        fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            let obj = self.to_kotlin_object();
            write!(f, "{}", util::call_to_string(c"org/kson/IndentType$Spaces", &obj))
        }
    }

    impl Eq for Spaces {}
    impl PartialEq for Spaces {
        fn eq(&self, other: &Spaces) -> bool {
            util::equals(self.to_kotlin_object(), other.to_kotlin_object())
        }
    }
    impl std::hash::Hash for Spaces {
        fn hash<H>(&self, state: &mut H)
        where
            H: std::hash::Hasher,
        {
            util::apply_hash_code(self.to_kotlin_object(), state)
        }
    }

    /// Use tabs for indentation
    #[derive(Clone)]
    pub struct Tabs {
        kotlin_ptr: KotlinPtr,
    }

    impl FromKotlinObject for Tabs {
        fn from_kotlin_object(obj: self::sys::jobject) -> Self {
            let (env, _detach_guard) = util::attach_thread_to_java_vm();
            let kotlin_ptr = util::to_gc_global_ref(env, obj);
            Self { kotlin_ptr }
        }
    }

    impl ToKotlinObject for Tabs {
        fn to_kotlin_object(&self) -> KotlinPtr {
            self.kotlin_ptr.clone()
        }
    }

    impl AsKotlinObject for Tabs {
        fn as_kotlin_object(&self) -> self::sys::jobject {
            self.kotlin_ptr.inner.inner
        }
    }

    impl Tabs {
    }


    impl Tabs {
    }

    impl std::fmt::Debug for Tabs {
        fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            let obj = self.to_kotlin_object();
            write!(f, "{}", util::call_to_string(c"org/kson/IndentType$Tabs", &obj))
        }
    }

    impl Eq for Tabs {}
    impl PartialEq for Tabs {
        fn eq(&self, other: &Tabs) -> bool {
            util::equals(self.to_kotlin_object(), other.to_kotlin_object())
        }
    }
    impl std::hash::Hash for Tabs {
        fn hash<H>(&self, state: &mut H)
        where
            H: std::hash::Hasher,
        {
            util::apply_hash_code(self.to_kotlin_object(), state)
        }
    }
}
impl FromKotlinObject for IndentType {
    fn from_kotlin_object(obj: jobject) -> Self {
        match util::class_name(obj).as_str() {
            "org.kson.IndentType$Spaces" => IndentType::Spaces(indent_type::Spaces::from_kotlin_object(obj)),
            "org.kson.IndentType$Tabs" => IndentType::Tabs(indent_type::Tabs::from_kotlin_object(obj)),
            _ => unreachable!(),
        }
    }
}

impl ToKotlinObject for IndentType {
    fn to_kotlin_object(&self) -> KotlinPtr {
        match self {
            Self::Spaces(inner) => inner.to_kotlin_object(),
            Self::Tabs(inner) => inner.to_kotlin_object(),
        }
    }
}

impl IndentType {
    pub fn name(self) -> String {
        let obj = self.to_kotlin_object();
        util::enum_name(&obj)
    }
}


impl IndentType {
}

impl std::fmt::Debug for IndentType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let obj = self.to_kotlin_object();
        write!(f, "{}", util::call_to_string(c"org/kson/IndentType", &obj))
    }
}

impl Eq for IndentType {}
impl PartialEq for IndentType {
    fn eq(&self, other: &IndentType) -> bool {
        util::equals(self.to_kotlin_object(), other.to_kotlin_object())
    }
}
impl std::hash::Hash for IndentType {
    fn hash<H>(&self, state: &mut H)
    where
        H: std::hash::Hasher,
    {
        util::apply_hash_code(self.to_kotlin_object(), state)
    }
}

/// Represents the severity of a [Message]
#[derive(Copy, Clone)]
pub enum MessageSeverity {
    Error,
    Warning,
}

impl FromKotlinObject for MessageSeverity {
    fn from_kotlin_object(obj: jobject) -> Self {
        match util::enum_ordinal(c"org/kson/MessageSeverity", obj) {
            0 => MessageSeverity::Error,
            1 => MessageSeverity::Warning,
            _ => unreachable!(),
        }
    }
}

impl ToKotlinObject for MessageSeverity {
    fn to_kotlin_object(&self) -> KotlinPtr {
        match self {
            MessageSeverity::Error => util::access_static_field(c"org/kson/MessageSeverity", c"ERROR", c"Lorg/kson/MessageSeverity;"),
            MessageSeverity::Warning => util::access_static_field(c"org/kson/MessageSeverity", c"WARNING", c"Lorg/kson/MessageSeverity;"),
        }
    }
}

impl MessageSeverity {
    pub fn name(self) -> String {
        let obj = self.to_kotlin_object();
        util::enum_name(&obj)
    }
}
/// Type discriminator for KsonValue subclasses
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

impl FromKotlinObject for KsonValueType {
    fn from_kotlin_object(obj: jobject) -> Self {
        match util::enum_ordinal(c"org/kson/KsonValueType", obj) {
            0 => KsonValueType::Object,
            1 => KsonValueType::Array,
            2 => KsonValueType::String,
            3 => KsonValueType::Integer,
            4 => KsonValueType::Decimal,
            5 => KsonValueType::Boolean,
            6 => KsonValueType::Null,
            7 => KsonValueType::Embed,
            _ => unreachable!(),
        }
    }
}

impl ToKotlinObject for KsonValueType {
    fn to_kotlin_object(&self) -> KotlinPtr {
        match self {
            KsonValueType::Object => util::access_static_field(c"org/kson/KsonValueType", c"OBJECT", c"Lorg/kson/KsonValueType;"),
            KsonValueType::Array => util::access_static_field(c"org/kson/KsonValueType", c"ARRAY", c"Lorg/kson/KsonValueType;"),
            KsonValueType::String => util::access_static_field(c"org/kson/KsonValueType", c"STRING", c"Lorg/kson/KsonValueType;"),
            KsonValueType::Integer => util::access_static_field(c"org/kson/KsonValueType", c"INTEGER", c"Lorg/kson/KsonValueType;"),
            KsonValueType::Decimal => util::access_static_field(c"org/kson/KsonValueType", c"DECIMAL", c"Lorg/kson/KsonValueType;"),
            KsonValueType::Boolean => util::access_static_field(c"org/kson/KsonValueType", c"BOOLEAN", c"Lorg/kson/KsonValueType;"),
            KsonValueType::Null => util::access_static_field(c"org/kson/KsonValueType", c"NULL", c"Lorg/kson/KsonValueType;"),
            KsonValueType::Embed => util::access_static_field(c"org/kson/KsonValueType", c"EMBED", c"Lorg/kson/KsonValueType;"),
        }
    }
}

impl KsonValueType {
    pub fn name(self) -> String {
        let obj = self.to_kotlin_object();
        util::enum_name(&obj)
    }
}
/// [FormattingStyle] options for Kson Output
#[derive(Copy, Clone)]
pub enum FormattingStyle {
    Plain,
    Delimited,
    Compact,
    Classic,
}

impl FromKotlinObject for FormattingStyle {
    fn from_kotlin_object(obj: jobject) -> Self {
        match util::enum_ordinal(c"org/kson/FormattingStyle", obj) {
            0 => FormattingStyle::Plain,
            1 => FormattingStyle::Delimited,
            2 => FormattingStyle::Compact,
            3 => FormattingStyle::Classic,
            _ => unreachable!(),
        }
    }
}

impl ToKotlinObject for FormattingStyle {
    fn to_kotlin_object(&self) -> KotlinPtr {
        match self {
            FormattingStyle::Plain => util::access_static_field(c"org/kson/FormattingStyle", c"PLAIN", c"Lorg/kson/FormattingStyle;"),
            FormattingStyle::Delimited => util::access_static_field(c"org/kson/FormattingStyle", c"DELIMITED", c"Lorg/kson/FormattingStyle;"),
            FormattingStyle::Compact => util::access_static_field(c"org/kson/FormattingStyle", c"COMPACT", c"Lorg/kson/FormattingStyle;"),
            FormattingStyle::Classic => util::access_static_field(c"org/kson/FormattingStyle", c"CLASSIC", c"Lorg/kson/FormattingStyle;"),
        }
    }
}

impl FormattingStyle {
    pub fn name(self) -> String {
        let obj = self.to_kotlin_object();
        util::enum_name(&obj)
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

impl FromKotlinObject for TokenType {
    fn from_kotlin_object(obj: jobject) -> Self {
        match util::enum_ordinal(c"org/kson/TokenType", obj) {
            0 => TokenType::CurlyBraceL,
            1 => TokenType::CurlyBraceR,
            2 => TokenType::SquareBracketL,
            3 => TokenType::SquareBracketR,
            4 => TokenType::AngleBracketL,
            5 => TokenType::AngleBracketR,
            6 => TokenType::Colon,
            7 => TokenType::Dot,
            8 => TokenType::EndDash,
            9 => TokenType::Comma,
            10 => TokenType::Comment,
            11 => TokenType::EmbedOpenDelim,
            12 => TokenType::EmbedCloseDelim,
            13 => TokenType::EmbedTag,
            14 => TokenType::EmbedPreambleNewline,
            15 => TokenType::EmbedContent,
            16 => TokenType::False,
            17 => TokenType::UnquotedString,
            18 => TokenType::IllegalChar,
            19 => TokenType::ListDash,
            20 => TokenType::Null,
            21 => TokenType::Number,
            22 => TokenType::StringOpenQuote,
            23 => TokenType::StringCloseQuote,
            24 => TokenType::StringContent,
            25 => TokenType::True,
            26 => TokenType::Whitespace,
            27 => TokenType::Eof,
            _ => unreachable!(),
        }
    }
}

impl ToKotlinObject for TokenType {
    fn to_kotlin_object(&self) -> KotlinPtr {
        match self {
            TokenType::CurlyBraceL => util::access_static_field(c"org/kson/TokenType", c"CURLY_BRACE_L", c"Lorg/kson/TokenType;"),
            TokenType::CurlyBraceR => util::access_static_field(c"org/kson/TokenType", c"CURLY_BRACE_R", c"Lorg/kson/TokenType;"),
            TokenType::SquareBracketL => util::access_static_field(c"org/kson/TokenType", c"SQUARE_BRACKET_L", c"Lorg/kson/TokenType;"),
            TokenType::SquareBracketR => util::access_static_field(c"org/kson/TokenType", c"SQUARE_BRACKET_R", c"Lorg/kson/TokenType;"),
            TokenType::AngleBracketL => util::access_static_field(c"org/kson/TokenType", c"ANGLE_BRACKET_L", c"Lorg/kson/TokenType;"),
            TokenType::AngleBracketR => util::access_static_field(c"org/kson/TokenType", c"ANGLE_BRACKET_R", c"Lorg/kson/TokenType;"),
            TokenType::Colon => util::access_static_field(c"org/kson/TokenType", c"COLON", c"Lorg/kson/TokenType;"),
            TokenType::Dot => util::access_static_field(c"org/kson/TokenType", c"DOT", c"Lorg/kson/TokenType;"),
            TokenType::EndDash => util::access_static_field(c"org/kson/TokenType", c"END_DASH", c"Lorg/kson/TokenType;"),
            TokenType::Comma => util::access_static_field(c"org/kson/TokenType", c"COMMA", c"Lorg/kson/TokenType;"),
            TokenType::Comment => util::access_static_field(c"org/kson/TokenType", c"COMMENT", c"Lorg/kson/TokenType;"),
            TokenType::EmbedOpenDelim => util::access_static_field(c"org/kson/TokenType", c"EMBED_OPEN_DELIM", c"Lorg/kson/TokenType;"),
            TokenType::EmbedCloseDelim => util::access_static_field(c"org/kson/TokenType", c"EMBED_CLOSE_DELIM", c"Lorg/kson/TokenType;"),
            TokenType::EmbedTag => util::access_static_field(c"org/kson/TokenType", c"EMBED_TAG", c"Lorg/kson/TokenType;"),
            TokenType::EmbedPreambleNewline => util::access_static_field(c"org/kson/TokenType", c"EMBED_PREAMBLE_NEWLINE", c"Lorg/kson/TokenType;"),
            TokenType::EmbedContent => util::access_static_field(c"org/kson/TokenType", c"EMBED_CONTENT", c"Lorg/kson/TokenType;"),
            TokenType::False => util::access_static_field(c"org/kson/TokenType", c"FALSE", c"Lorg/kson/TokenType;"),
            TokenType::UnquotedString => util::access_static_field(c"org/kson/TokenType", c"UNQUOTED_STRING", c"Lorg/kson/TokenType;"),
            TokenType::IllegalChar => util::access_static_field(c"org/kson/TokenType", c"ILLEGAL_CHAR", c"Lorg/kson/TokenType;"),
            TokenType::ListDash => util::access_static_field(c"org/kson/TokenType", c"LIST_DASH", c"Lorg/kson/TokenType;"),
            TokenType::Null => util::access_static_field(c"org/kson/TokenType", c"NULL", c"Lorg/kson/TokenType;"),
            TokenType::Number => util::access_static_field(c"org/kson/TokenType", c"NUMBER", c"Lorg/kson/TokenType;"),
            TokenType::StringOpenQuote => util::access_static_field(c"org/kson/TokenType", c"STRING_OPEN_QUOTE", c"Lorg/kson/TokenType;"),
            TokenType::StringCloseQuote => util::access_static_field(c"org/kson/TokenType", c"STRING_CLOSE_QUOTE", c"Lorg/kson/TokenType;"),
            TokenType::StringContent => util::access_static_field(c"org/kson/TokenType", c"STRING_CONTENT", c"Lorg/kson/TokenType;"),
            TokenType::True => util::access_static_field(c"org/kson/TokenType", c"TRUE", c"Lorg/kson/TokenType;"),
            TokenType::Whitespace => util::access_static_field(c"org/kson/TokenType", c"WHITESPACE", c"Lorg/kson/TokenType;"),
            TokenType::Eof => util::access_static_field(c"org/kson/TokenType", c"EOF", c"Lorg/kson/TokenType;"),
        }
    }
}

impl TokenType {
    pub fn name(self) -> String {
        let obj = self.to_kotlin_object();
        util::enum_name(&obj)
    }
}

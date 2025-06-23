# Bindings

For a configuration language such as Kson, it's crucial to support multiple programming languages.
The more interoperable Kson is across languages, the more useful it will be to people!

### Kotlin Multiplatform targets

The Kson library is written on top of Kotlin Multiplatform. Out-of-the-box, Kotlin Multiplatform can
compile down to JVM bytecode, Javascript and native code. This means we have can easily support
JVM-based languages (Kotlin, Java, etc), Javascript (including type declarations for Typescript),
and C (through a very low-level API).

### Bindings for other languages

There are plenty of languages out there without Kotlin Multiplatform support. However, Kotlin
exposes native library functions and types through the C ABI, which means we can call those
functions from any programming language that can call C functions (i.e., every programming language
under the sun).

We use a built-in gradle task to compile Kson as a native shared library (i.e., a `.dll` or `.so`
file with its associated C header file). This shared library can be loaded by a program, which then
becomes able to call the library's functions.

Note that the functions and types provided by the native library are too low-level for human
consumption. For that reason, we generate a user-friendly wrapper around the native library for each
programming language we are interested in. Internally, the wrapper acts as a bridge between the
programming language in question and the C functions, translating types and taking care of memory
management. The wrapper fully hides the fact that we are calling C functions under the hood! In
fact, it replicates the Kson API from the original Kotlin code as much as the language allows.

Generating the wrapper happens in two stages:

- Collect metadata about Kson's public API (see `bindings/src/main/kotlin/org/kson/ksp`)
- Based on the metadata, generate wrappers for different programming languages (see `buildSrc/src/main/kotlin/GenerateBindingsTask.kt`)

### What about static linking?

Using a shared library requires distributing the library's files, which is slightly annoying. In
theory, we could get around this limitation by using static linking for the languages that support
it (most compiled languages). That means the Kson library would become part of the compiled binary
instead of needing to be distributed alongside it.

We evaluated static linking for our Rust wrapper, but it turns out that Kotlin Multiplatform makes
that pretty much impossible on Windows. The gist of the issue is that the compiled library requires
a C++ runtime to properly function, and on Windows it gets built on top of the MinGW toolchain.
However, most compiled programming languages nowadays (including Rust) are using Microsoft's
official toolchain (MSVC), which provides a C++ runtime that is incompatible with MinGW's. As far as
I know, static linking gets very complicated when there is a mismatch between the C++ runtime of the
library and the C++ runtime of the application. It might even be impossible, but I'm not 100% sure.
In any case, my attempts at it failed and we settled on dynamic linking (i.e., using a shared
library) even for compiled languages. Note that we _could_ use static linking on Linux and MacOS,
but IMO it's not worth the hassle.

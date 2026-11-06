# Rust bindings for kson-lib API

The Rust bindings for KSON are split into two crates, following the convention in the Rust
ecosystem:

- `kson-sys`: the low-level interface to the native library (you probably don't need to use it directly)
- `kson-rs`: the idiomatic wrapper around kson (what you are probably looking for, see the example below)

## Example usage

Add the library to your dependencies:

```bash
cargo add kson-rs
```

Write some code:

```rust
use kson_rs::{indent_type, FormatOptions, FormattingStyle, IndentType, Kson};
use std::io::Read;

fn main() {
    let mut source = String::new();
    std::io::stdin().read_to_string(&mut source).unwrap();

    let indent = IndentType::Spaces(indent_type::Spaces::new(2));
    let options = FormatOptions::new(indent, FormattingStyle::Plain, &[]);
    println!("{}", Kson::format(&source, options));
}
```

KSON is a superset of JSON, so this accepts either. Piping JSON in:

```bash
echo '{"key": [1, 2, 3, 4], "nested": {"a": true}}' | cargo run
```

prints the equivalent document in KSON's plain format:

```
key:
  - 1
  - 2
  - 3
  - 4
nested:
  a: true
```

## Obtaining kson-lib binaries

The `kson-sys` crate requires linking to the `kson-lib` binary. Our `build.rs` automatically
downloads a suitable binary from the [kson-binaries
repository](https://github.com/kson-org/kson-binaries), if it can be found. In case no pre-built
binary is available for your platform, you need to manually specify how to obtain it through one of
the following environment variables:

* `KSON_ROOT_SOURCE_DIR`: if set to the root of a KSON source tree, we will attempt to build and use the necessary binaries from there.
* `KSON_PREBUILT_BIN_DIR`: use pre-built KSON binaries from the specified directory (with the
  `static` feature, a directory holding `libkson.a` and `libkson.link-libs`, as built by
  `./gradlew :kson-lib:buildGraalVmStaticLibrary`).

## Shipping a binary that uses kson

By default `kson-sys` links `kson-lib` as a shared library (`libkson.dylib`, `libkson.so` or
`kson.dll`), which then has to be found at runtime. `cargo run` and `cargo test` take care of that
for you, but a binary you copy elsewhere or bundle into an app does not. You have two options.

### Static linking (macOS and Linux)

Turn on the `static` feature and `kson-lib` is linked into your binary, with nothing to ship
beside it:

```toml
[dependencies]
kson-rs = { version = "...", features = ["static"] }
```

The static library holds the same compiled code as the shared one, so it adds about as much to
your binary as `libkson` would take on disk (around 18 MB). It needs only libraries every system
already has (`libz`, `libdl`, `libpthread`, and the Foundation framework on macOS), which
`kson-sys` links for you. The `static` feature is not available on Windows.

The static library keeps only `kson-lib`'s API global. A few dozen C variables from the JDK's
native code it is built from stay global too (names like `ia_class` and `IO_fd_fdID`), which
only matters if your program also links the JDK's native libraries statically.

### Shipping the shared library

The shared library identifies itself by name only (`@rpath/libkson.dylib` on macOS, the SONAME
`libkson.so` on Linux), so your binary has to tell the loader where to look, through an rpath.
Build with `KSON_COPY_SHARED_LIBRARY_TO_DIR` to get a copy of the library somewhere predictable,
ship it with your binary, and add an rpath pointing at it, for instance in your crate's `build.rs`:

```rust
// libkson next to the executable (macOS); use `$ORIGIN` instead of `@executable_path` on Linux
println!("cargo:rustc-link-arg=-Wl,-rpath,@executable_path");
// or, in a macOS app bundle with the library in Contents/Frameworks
println!("cargo:rustc-link-arg=-Wl,-rpath,@executable_path/../Frameworks");
```

This has to be in the crate that builds the binary: link arguments that `kson-sys` emits don't
reach the binaries of crates that depend on it. On Windows, place `kson.dll` next to the
executable.

## Troubleshooting

* If the KSON library is not found at runtime, see [Shipping a binary that uses
  kson](#shipping-a-binary-that-uses-kson). For a quick local run you can instead add the
  directory holding it to your loader path (`DYLD_LIBRARY_PATH` on macOS, `LD_LIBRARY_PATH` on
  Linux).
* If macOS reports `library load disallowed by system policy` for a library you supplied yourself,
  clear its quarantine flag: `xattr -c /path/to/libkson.dylib`.

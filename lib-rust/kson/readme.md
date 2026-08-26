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
* `KSON_PREBUILT_BIN_DIR`: use pre-built KSON binaries from the specified directory.

## Troubleshooting

* If the KSON library is not found at runtime, build with `KSON_COPY_SHARED_LIBRARY_TO_DIR=target/debug`
  to place it next to your binary, and add that directory to your loader path
  (`DYLD_LIBRARY_PATH` on macOS, `LD_LIBRARY_PATH` on Linux).
* If macOS reports `library load disallowed by system policy` for a library you supplied yourself,
  clear its quarantine flag: `xattr -c /path/to/libkson.dylib`.

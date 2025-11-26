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

Tell the build where to put the KSON shared library (see [below](#a-note-on-dynamic-linking) for
details):

```bash
export KSON_COPY_SHARED_LIBRARY_TO_DIR=target/debug
```

Write some code:

```rust
use kson_rs::Kson;

fn main() {
    let json = Kson::to_json("key: [1, 2, 3, 4]", false)
        .map_err(|_| "unreachable: kson input is guaranteed to be valid!")
        .unwrap();
    println!("{}", json.output());
}
```

Running this with `cargo run` should print the following to stdout:

```json
{
  "key": [
    1,
    2,
    3,
    4
  ]
}
```

## Obtaining kson-lib binaries

The `kson-sys` crate requires linking to the `kson-lib` binary. Our `build.rs` automatically
downloads a suitable binary from the [kson-binaries
repository](https://github.com/kson-org/kson-binaries), if it can be found. In case no pre-built
binary is available for your platform, you need to manually specify how to obtain it through one of
the following environment variables:

* `KSON_ROOT_SOURCE_DIR`: if set to the root of a KSON source tree, we will attempt to build and use the necessary binaries from there.
* `KSON_PREBUILT_BIN_DIR`: use pre-built KSON binaries from the specified directory.

## A note on dynamic linking

The KSON bindings use dynamic linking, so you need to make sure the operating system can find the
KSON library at runtime. Hence the `KSON_COPY_SHARED_LIBRARY_TO_DIR` trick, to put the library next
to your binary.

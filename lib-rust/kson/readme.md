# Rust bindings for kson-lib API

## Example usage

Add the library to your dependencies:

```bash
cargo add kson-rs
```

Write some code:

```rust
use kson_rs::Kson;

fn main() {
    let json = Kson::to_json("key: [1, 2, 3, 4]")
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

## A note on Windows and dynamic linking

By default we use static linking under the hood, which means you can use the `kson` crate without
further setup. On Windows, however, we have no choice but to use dynamic linking (due to limitations
in kotlin-native). This requires extra work from your side so the operating system can find
`kson.dll` when your program runs. Unfold the section below if you'd like to know more.

<details>
<summary>Placing `kson.dll` where Windows can find it</summary>

If you `cargo add kson-sys` to your dependencies, it becomes possible to automatically place the
`kson.dll` file next to your compiled binary through the following build script:

```rust
// build.rs
use std::path::Path;
use std::{env, fs};

fn main() {
    let lib_bin_path = env::var("DEP_KSON_LIB_BINARY").expect("DEP_KSON_LIB_BINARY not set");
    let lib_bin_path = Path::new(&lib_bin_path);

    let profile = env::var("PROFILE").unwrap();
    let target_root = Path::new(&env::var("CARGO_MANIFEST_DIR").unwrap()).join("target");
    let dest_dir = target_root.join(&profile);

    fs::create_dir_all(&dest_dir).unwrap();
    fs::copy(
        lib_bin_path,
        dest_dir.join(lib_bin_path.file_name().unwrap()),
    )
    .expect("failed to copy kson binary");

    // Re-run if the source library changes
    println!("cargo:rerun-if-changed={}", lib_bin_path.display());
}
```
</details>

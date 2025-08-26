# Rust bindings for Kson's public API

Only local installation is supported at the moment. A crate on crates.io is coming soon!

## Example usage

> [!NOTE]
> This example assumes you are on macOS or Linux. For Windows, see below.

Clone the kson repository:

```bash
git clone https://github.com/kson-org/kson.git
```

Install `lib-rust` to an existing Rust project:

```bash
cargo add --path ../kson/lib-rust/kson
```

Write some code:

```rust
use kson::Kson;

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

## Dynamic linking on Windows

The example above uses static linking under the hood, which means the user can depend on the `kson`
crate without further setup. On Windows, however, a slightly more involved setup is necessary
because kson-native requires dynamic linking on that platform.

Clone the kson repository and install `lib-rust` to your existing Rust project, as shown in the
example above.

After that, you can build `kson.dll` with the following command from the kson repository's root
directory:

```bash
./gradlew :lib-kotlin:nativeKsonMainBinaries
```

Once the build finishes, copy the file at `lib-kotlin/build/bin/nativeKson/releaseShared/kson.dll`
to a suitable place. For the purposes of this readme, we will copy `kson.dll` to the directory where
our final Rust binary will be located: the Rust project's `target/debug` directory (sidenote:
Windows automatically checks for `.dll` files next to the program you are running). If the
`target/debug` directory doesn't exist yet, you can run `cargo build` and it will be created
automatically.

With the `.dll` file in place, you can `cargo run` the project and see the exact same output as the
one from the example above.

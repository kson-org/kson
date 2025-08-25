# Rust bindings for Kson's public API

Only local installation is supported at the moment. A crate on crates.io is coming soon!

## Example usage

Clone the kson repository:

```bash
git clone https://github.com/kson-org/kson.git
```

Install `lib-rust` to an existing Rust project:

```bash
cargo add --path ../kson/lib-rust
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

This should print the following to stdout:

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

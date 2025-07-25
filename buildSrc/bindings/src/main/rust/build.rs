use bindgen::callbacks::ParseCallbacks;
use std::env;
use std::path::PathBuf;

#[derive(Debug)]
struct CustomRenamer;

impl ParseCallbacks for CustomRenamer {
    // Necessary to get rid of the `libkson` vs. `kson` difference depending on the target OS
    fn item_name(&self, original_item_name: &str) -> Option<String> {
        if original_item_name.starts_with("libkson_") {
            Some(original_item_name.strip_prefix("lib").unwrap().to_string())
        } else {
            None
        }
    }
}

fn main() {
    let bindings = bindgen::Builder::default()
        .header("libkson/kson.h")
        .parse_callbacks(Box::new(CustomRenamer))
        .generate()
        .expect("Unable to generate bindings");

    // Write the bindings to $OUT_DIR/bindings.rs
    let out_path = PathBuf::from(env::var("OUT_DIR").unwrap());
    bindings
        .write_to_file(out_path.join("bindings.rs"))
        .expect("Couldn't write bindings!");
}

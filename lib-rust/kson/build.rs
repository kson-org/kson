use std::path::Path;
use std::{env, fs};

fn main() {
    let use_dynamic_linking = cfg!(feature = "dynamic-linking") || cfg!(target_os = "windows");
    if use_dynamic_linking {
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
}

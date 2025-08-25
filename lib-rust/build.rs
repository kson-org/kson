use bindgen::callbacks::ParseCallbacks;
use std::env;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

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

// Build kotlin-native artifacts and put them under `artifacts`
fn get_kotlin_artifacts(use_dynamic_linking: bool) -> Result<(), Box<dyn std::error::Error>> {
    let manifest_dir = env::var("CARGO_MANIFEST_DIR")?;
    let rust_root = Path::new(&manifest_dir);
    let kotlin_root = rust_root.join("kotlin");
    let artifacts_dir = Path::new(&manifest_dir).join("artifacts");

    // Build kotlin-native artifacts
    let gradle_script = if cfg!(target_os = "windows") {
        kotlin_root.join("gradlew.bat")
    } else {
        kotlin_root.join("gradlew")
    };

    let status = Command::new(&gradle_script)
        .arg(":lib-kotlin:nativeKsonMainBinaries")
        .arg("--no-daemon")
        .current_dir(&kotlin_root)
        .stdout(Stdio::inherit())
        .stderr(Stdio::inherit())
        .status()?;

    if !status.success() {
        return Err(format!("Kotlin build failed with exit code: {:?}", status.code()).into());
    }

    // Copy built artifacts
    fs::create_dir_all(&artifacts_dir)?;
    let release_path = if use_dynamic_linking {
        "lib-kotlin/build/bin/nativeKson/releaseShared"
    } else {
        "lib-kotlin/build/bin/nativeKson/releaseStatic"
    };
    let source_dir = kotlin_root.join(release_path);
    for entry in fs::read_dir(&source_dir)? {
        let entry = entry?;
        let source_path = entry.path();
        let file_name = source_path.file_name().unwrap();
        let dest_path = artifacts_dir.join(file_name);
        fs::copy(&source_path, &dest_path)?;
        println!("cargo:rerun-if-changed={}", source_path.display());
    }

    // Copy the C headers, preprocessed (will overwrite existing header files)
    let gradle_task = if use_dynamic_linking {
        ":lib-rust:copyHeaderFileDynamic"
    } else {
        ":lib-rust:copyHeaderFileStatic"
    };
    let status = Command::new(&gradle_script)
        .arg(gradle_task)
        .arg("--no-daemon")
        .current_dir(&rust_root)
        .stdout(Stdio::inherit())
        .stderr(Stdio::inherit())
        .status()?;

    if !status.success() {
        return Err(format!(
            "gradle {gradle_task} failed with exit code: {:?}",
            status.code()
        )
        .into());
    }

    Ok(())
}

fn main() {
    let use_dynamic_linking = cfg!(feature = "dynamic-linking") || cfg!(target_os = "windows");
    get_kotlin_artifacts(use_dynamic_linking).expect("Failed to copy Kotlin artifacts");

    let bindings = bindgen::Builder::default()
        .header("artifacts/kson_api.h")
        .parse_callbacks(Box::new(CustomRenamer))
        .generate()
        .expect("Unable to generate bindings");

    // Write the bindings to $OUT_DIR/bindings.rs
    let out_path = PathBuf::from(env::var("OUT_DIR").unwrap());
    bindings
        .write_to_file(out_path.join("bindings.rs"))
        .expect("Couldn't write bindings!");

    // Configure static linking if enabled
    if !use_dynamic_linking {
        let dir = env::var("CARGO_MANIFEST_DIR").unwrap();
        let artifacts = std::path::Path::new(&dir).join("artifacts");
        println!("cargo:rustc-link-search=native={}", artifacts.display());
        println!("cargo:rustc-link-lib=static=kson");

        // Note: our kotlin-native binary relies on the C++ runtime, which we don't want to
        // statically link. Below we explicitly configure the runtime to be dynamically linked.
        #[cfg(target_os = "linux")]
        println!("cargo:rustc-link-lib=dylib=stdc++");

        #[cfg(target_os = "macos")]
        println!("cargo:rustc-link-lib=dylib=c++");
    }
}

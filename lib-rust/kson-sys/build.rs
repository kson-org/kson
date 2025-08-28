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
fn get_kotlin_artifacts(
    use_dynamic_linking: bool,
    out_dir: &Path,
) -> Result<(), Box<dyn std::error::Error>> {
    let manifest_dir = env::var("CARGO_MANIFEST_DIR")?;
    let kotlin_root = Path::new(&manifest_dir).join("kotlin");

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
    let release_path = if use_dynamic_linking {
        "lib-kotlin/build/bin/nativeKson/releaseShared"
    } else {
        "lib-kotlin/build/bin/nativeKson/releaseStatic"
    };
    let source_dir = kotlin_root.join(release_path);
    for entry in fs::read_dir(&source_dir)? {
        let entry = entry?;
        let source_path = entry.path();
        if source_path.is_file() && source_path.extension() != Some(std::ffi::OsStr::new("h")) {
            let file_name = source_path.file_name().unwrap();
            let dest_path = out_dir.join(file_name);
            fs::copy(&source_path, &dest_path)?;
            println!("cargo:rerun-if-changed={}", source_path.display());
        }
    }

    // Copy the C headers, preprocessed (will overwrite existing header files)
    let gradle_task = if use_dynamic_linking {
        ":lib-kotlin:copyNativeHeaderDynamic"
    } else {
        ":lib-kotlin:copyNativeHeaderStatic"
    };
    let status = Command::new(&gradle_script)
        .arg(gradle_task)
        .arg("--no-daemon")
        .current_dir(&kotlin_root)
        .arg("--outputDir")
        .arg(out_dir.display().to_string())
        .stdout(Stdio::null())
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
    let out_dir = PathBuf::from(env::var("OUT_DIR").unwrap());
    let use_dynamic_linking = cfg!(feature = "dynamic-linking") || cfg!(target_os = "windows");

    // Compile kotlin code
    get_kotlin_artifacts(use_dynamic_linking, &out_dir).expect("Failed to copy Kotlin artifacts");

    // Generate bindings
    let bindings = bindgen::Builder::default()
        .header(out_dir.join("kson_api.h").display().to_string())
        .parse_callbacks(Box::new(CustomRenamer))
        .generate()
        .expect("Unable to generate bindings");

    bindings
        .write_to_file(out_dir.join("bindings.rs"))
        .expect("Couldn't write bindings!");

    // Deal with static vs. dynamic linking
    if use_dynamic_linking {
        // Let users of the library know the path to the compiled binary, so they can copy it
        let shared_name = if cfg!(target_os = "windows") {
            format!("kson.dll")
        } else if cfg!(target_os = "macos") {
            format!("libkson.dylib")
        } else {
            format!("libkson.so")
        };
        let built_lib = out_dir.join(&shared_name);
        println!("cargo:lib-binary={}", built_lib.display());
    } else {
        // Tell the compiler where to find the static library
        println!("cargo:rustc-link-search=native={}", out_dir.display());
        println!("cargo:rustc-link-lib=static=kson");

        // Note: our kotlin-native binary relies on the C++ runtime, which we don't want to
        // statically link. Below we explicitly configure the runtime to be dynamically linked.
        #[cfg(target_os = "linux")]
        println!("cargo:rustc-link-lib=dylib=stdc++");
        #[cfg(target_os = "macos")]
        {
            println!("cargo:rustc-link-lib=dylib=c++");
            println!("cargo:rustc-link-lib=framework=CoreFoundation");
            println!("cargo:rustc-link-lib=framework=Foundation");
        }
    }
}

use bindgen::callbacks::ParseCallbacks;
use std::env;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

#[derive(Debug)]
struct CustomRenamer;

static KSON_LIB_VERSION: &str = "0.2.0";

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

fn get_kson_artifacts(
    use_dynamic_linking: bool,
    out_dir: &Path,
) -> Result<(), Box<dyn std::error::Error>> {
    let kson_root_env_var = "KSON_ROOT_SOURCE_DIR";
    let kson_prebuild_env_var = "KSON_PREBUILT_BIN_DIR";
    if let Ok(kson_root) = env::var(kson_root_env_var) {
        build_kson_from_source(Path::new(&kson_root), use_dynamic_linking, out_dir)
    } else {
        if let Ok(prebuilt_root) = env::var(kson_prebuild_env_var) {
            for entry in fs::read_dir(&prebuilt_root)? {
                let entry = entry?;
                let source_path = entry.path();
                if source_path.is_file() {
                    let file_name = source_path.file_name().unwrap();
                    let dest_path = out_dir.join(file_name);
                    fs::copy(&source_path, &dest_path)?;
                    println!("cargo:rerun-if-changed={}", source_path.display());
                }
            }
        } else if let Err(e) = download_prebuilt_kson(use_dynamic_linking, out_dir) {
            panic!(
                "failed to download prebuilt kson: {e}\nset the `{kson_prebuild_env_var}` variable to the path of compatible kson binaries, or the `{kson_root_env_var}` variable to the path of a compatible kson source tree (if you prefer to build kson from source)"
            );
        }

        Ok(())
    }
}

fn download_prebuilt_kson(
    use_dynamic_linking: bool,
    out_dir: &Path,
) -> Result<(), Box<dyn std::error::Error>> {
    let cpu_arch = match env::var("CARGO_CFG_TARGET_ARCH")?.as_str() {
        "aarch64" => "arm64",
        "x86_64" => "amd64",
        arch => panic!("unsupported CPU architecture: {arch}"),
    };
    let os = env::var("CARGO_CFG_TARGET_OS")?;
    if !["windows", "macos", "linux"].contains(&os.as_str()) {
        panic!("unsupported operating system: {os}");
    }

    let shared_or_static = if use_dynamic_linking {
        "shared"
    } else {
        "static"
    };

    fs::create_dir_all(out_dir)?;
    let url = format!(
        "https://github.com/kson-org/kson-binaries/releases/download/kson-lib-{KSON_LIB_VERSION}/kson-lib-{shared_or_static}-{cpu_arch}-{os}.tar.gz"
    );
    let archive = ureq::get(url).call()?.body_mut().read_to_vec()?;
    let decoder = flate2::read::GzDecoder::new(archive.as_slice());
    let mut archive = tar::Archive::new(decoder);
    archive.unpack(out_dir)?;

    Ok(())
}

fn build_kson_from_source(
    kson_root: &Path,
    use_dynamic_linking: bool,
    out_dir: &Path,
) -> Result<(), Box<dyn std::error::Error>> {
    // Build kotlin-native artifacts
    let gradle_script = if cfg!(target_os = "windows") {
        kson_root.join("gradlew.bat")
    } else {
        kson_root.join("gradlew")
    };

    let status = Command::new(&gradle_script)
        .arg(":kson-lib:nativeRelease")
        .arg("--no-daemon")
        .current_dir(&kson_root)
        .stdout(Stdio::inherit())
        .stderr(Stdio::inherit())
        .status()?;

    if !status.success() {
        return Err(format!("Kotlin build failed with exit code: {:?}", status.code()).into());
    }

    // Copy built artifacts
    let release_path = if use_dynamic_linking {
        "kson-lib/build/bin/nativeKson/releaseShared"
    } else {
        "kson-lib/build/bin/nativeKson/releaseStatic"
    };
    let source_dir = kson_root.join(release_path);
    for entry in fs::read_dir(&source_dir)? {
        let entry = entry?;
        let source_path = entry.path();
        if source_path.is_file() {
            let file_name = source_path.file_name().unwrap();
            let dest_path = out_dir.join(file_name);
            fs::copy(&source_path, &dest_path)?;
            println!("cargo:rerun-if-changed={}", source_path.display());
        }
    }

    Ok(())
}

fn main() {
    let out_dir = PathBuf::from(env::var("OUT_DIR").unwrap());
    let use_dynamic_linking = cfg!(feature = "dynamic-linking") || cfg!(target_os = "windows");

    // Obtain kotlin artifacts (TODO: allow compiling from source)
    get_kson_artifacts(use_dynamic_linking, &out_dir).expect("Failed to copy Kotlin artifacts");

    // Generate bindings
    let bindings = bindgen::Builder::default()
        .header(
            out_dir
                .join("kson_api_preprocessed.h")
                .display()
                .to_string(),
        )
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

        // Note: our kotlin-native binary relies on platform-specific libraries, which we don't want
        // to statically link. Below we explicitly configure them to be dynamically linked.
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

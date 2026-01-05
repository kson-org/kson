use anyhow::{bail, Context};
use std::env;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

// [[kson-version-num]]
static KSON_LIB_VERSION: &str = "0.3.0-dev";

fn get_kson_artifacts(
    out_dir: &Path,
) -> anyhow::Result<()> {
    let kson_root_env_var = "KSON_ROOT_SOURCE_DIR";
    let kson_prebuild_env_var = "KSON_PREBUILT_BIN_DIR";
    if let Ok(kson_root) = env::var(kson_root_env_var) {
        build_kson_from_source(Path::new(&kson_root), out_dir)
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
        } else if let Err(e) = download_prebuilt_kson(true, out_dir) {
            bail!(
                "failed to download prebuilt kson: {e}\nset the `{kson_prebuild_env_var}` variable to the path of compatible kson binaries, or the `{kson_root_env_var}` variable to the path of a compatible kson source tree (if you prefer to build kson from source)"
            );
        }

        Ok(())
    }
}

fn download_prebuilt_kson(
    use_dynamic_linking: bool,
    out_dir: &Path,
) -> anyhow::Result<()> {
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
    out_dir: &Path,
) -> anyhow::Result<()> {
    // Build kotlin-native artifacts
    let gradle_script = if cfg!(target_os = "windows") {
        kson_root.join("gradlew.bat")
    } else {
        kson_root.join("gradlew")
    };

    let status = Command::new(&gradle_script)
        .arg(":kson-lib:buildWithGraalVmNativeImage")
        .arg("--no-daemon")
        .current_dir(&kson_root)
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status()?;

    if !status.success() {
        bail!("Kotlin build failed with exit code: {:?}", status.code());
    }

    // Copy built artifacts
    let release_path = "kson-lib/build/kotlin/compileGraalVmNativeImage";
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

fn main() -> anyhow::Result<()> {
    let out_dir = PathBuf::from(env::var("OUT_DIR").unwrap());

    // Obtain kotlin artifacts
    get_kson_artifacts(&out_dir).context("Failed to copy Kotlin artifacts")?;

    // Generate bindings
    let bindings = bindgen::Builder::default()
        .header(
            out_dir
                .join("jni_simplified.h")
                .display()
                .to_string(),
        )
        .generate()
        .context("Unable to generate bindings")?;

    bindings
        .write_to_file(out_dir.join("bindings.rs"))
        .context("Couldn't write bindings!")?;

    // Tell the compiler where to find the dynamic library
    println!("cargo:rustc-link-search=native={}", out_dir.display());
    println!("cargo:rustc-link-lib=dylib=kson");

    #[cfg(not(target_os = "windows"))]
    println!("cargo:rustc-link-lib=dylib=z");

    // Let users of the library know the path to the compiled binary
    let shared_name = if cfg!(target_os = "windows") {
        format!("kson.dll")
    } else if cfg!(target_os = "macos") {
        format!("libkson.dylib")
    } else {
        format!("libkson.so")
    };
    let built_lib = out_dir.join(&shared_name);
    println!("cargo:lib-binary={}", built_lib.display());

    // Copy the library to a specific directory, if requested
    if let Ok(copy_dir) = env::var("KSON_COPY_SHARED_LIBRARY_TO_DIR") {
        fs::create_dir_all(&copy_dir).with_context(|| format!("failed to copy the shared library to the provided directory (the directory at `{copy_dir}` does not exist and could not be created)"))?;
        fs::copy(built_lib, Path::new(&copy_dir).join(shared_name)).with_context(|| format!("failed to copy the shared library to the provided directory (the directory was `{copy_dir}`)"))?;
    }

    Ok(())
}

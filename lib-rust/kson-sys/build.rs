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

/// bindgen finds libclang via clang-sys, which on Windows matches only the exact filenames
/// `clang.dll` and `libclang.dll` — it has no versioned-name search there (the `libclang.so.*`
/// globs are Linux/BSD-only). conda-forge ships the runtime as `libclang-13.dll` (13 is libclang's
/// C API soversion, unchanged since LLVM 13), so nothing in the pixi environment matches and
/// bindgen panics with "Unable to find libclang".
///
/// Copy it to the name clang-sys wants, beside the original so libclang's own dependent DLLs still
/// resolve, then point clang-sys at that directory.
///
/// The directory comes from `CONDA_PREFIX`, a real environment variable pixi exports. Writing
/// `$CONDA_PREFIX` into pixi.toml's `[activation.env]` would not work: pixi does not expand it on
/// Windows (it does on Linux, hence the linux-* blocks there).
///
/// A no-op off Windows: every detail here (the `.dll` names, conda's `Library/bin` layout,
/// clang-sys's lookup rules) is Windows-specific, so the guard lives here rather than in the
/// caller — nowhere else has to remember it.
fn alias_libclang_for_clang_sys() -> anyhow::Result<()> {
    if !cfg!(target_os = "windows") {
        return Ok(());
    }

    println!("cargo:rerun-if-env-changed=LIBCLANG_PATH");
    println!("cargo:rerun-if-env-changed=CONDA_PREFIX");

    // Someone has already pointed clang-sys at a libclang (e.g. a system LLVM): nothing to do.
    if env::var_os("LIBCLANG_PATH").is_some() {
        return Ok(());
    }
    // Not running under pixi, so there is no conda libclang to alias.
    let Ok(conda_prefix) = env::var("CONDA_PREFIX") else {
        return Ok(());
    };

    let dir = PathBuf::from(conda_prefix).join("Library/bin");
    let alias = dir.join("libclang.dll");

    if !alias.exists() {
        // 13 is libclang's C API soversion, not an LLVM version: it has been 13 since LLVM 13, and
        // pixi pins libclang to 20.*. If it ever changes, this copy fails naming the file it wanted.
        let versioned = dir.join("libclang-13.dll");
        fs::copy(&versioned, &alias).with_context(|| {
            format!(
                "failed to copy {} to {}; is the `libclang` conda package installed?",
                versioned.display(),
                alias.display()
            )
        })?;
    }

    // SAFETY: build scripts are single-threaded at this point, so no other thread can observe the
    // environment while it is being mutated.
    unsafe { env::set_var("LIBCLANG_PATH", &dir) };

    Ok(())
}

fn main() -> anyhow::Result<()> {
    let out_dir = PathBuf::from(env::var("OUT_DIR").unwrap());

    // Obtain kotlin artifacts
    get_kson_artifacts(&out_dir).context("Failed to copy Kotlin artifacts")?;

    alias_libclang_for_clang_sys().context("Failed to make libclang discoverable for bindgen")?;

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

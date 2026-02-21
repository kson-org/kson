"""
Custom build backend for kson Python package.
This backend ensures native artifacts are built when creating source distributions.
"""

import os
import subprocess
import shutil
import sys
from pathlib import Path
from setuptools import build_meta as _orig
from setuptools.build_meta import *

# See also LIBRARY_NAMES in src/kson/__init__.py
PLATFORM_NATIVE_LIBRARIES = {
    "win32": "kson.dll",
    "darwin": "libkson.dylib",
    "linux": "libkson.so",
}


def _platform_native_library():
    """Return the native library filename for the current platform."""
    lib = PLATFORM_NATIVE_LIBRARIES.get(sys.platform)
    if lib is None:
        raise RuntimeError(f"Unsupported platform: {sys.platform}")
    return lib


def _required_artifacts():
    """Return the filenames that must be present for a working installation."""
    return [_platform_native_library(), "jni_simplified.h"]


def _ensure_native_artifacts():
    """Build native artifacts using the bundled Gradle setup."""
    lib_python_dir = Path(__file__).parent
    kson_copy_dir = lib_python_dir / "kson-sdist"
    src_dir = lib_python_dir / "src"
    src_kson_dir = src_dir / "kson"

    # Only check for the native library needed on *this* platform
    required = _required_artifacts()
    artifacts_exist = all((src_kson_dir / f).exists() for f in required)

    if not artifacts_exist and kson_copy_dir.exists():
        print(f"Building native artifacts for {sys.platform} with bundled Gradle setup...")

        gradlew = "./gradlew" if os.name != "nt" else "gradlew.bat"
        result = subprocess.run(
            [gradlew, "lib-python:build"],
            capture_output=True,
            text=True,
            cwd=kson_copy_dir,
        )

        if result.returncode != 0:
            print(f"Gradle build stdout:\n{result.stdout}")
            print(f"Gradle build stderr:\n{result.stderr}")
            raise RuntimeError("Failed to build native artifacts")

        print("Native artifacts built successfully")

        # Replace the entire src directory with the one from kson-sdist
        kson_copy_src = kson_copy_dir / "lib-python" / "src"
        if kson_copy_src.exists():
            print("Replacing src directory with built artifacts...")
            # Save _marker.c if it exists
            marker_c = src_kson_dir / "_marker.c"
            marker_c_content = None
            if marker_c.exists():
                marker_c_content = marker_c.read_bytes()

            shutil.rmtree(src_dir, ignore_errors=True)
            shutil.copytree(kson_copy_src, src_dir)

            # Restore _marker.c if it existed
            if marker_c_content is not None:
                marker_c_new = src_kson_dir / "_marker.c"
                marker_c_new.parent.mkdir(parents=True, exist_ok=True)
                marker_c_new.write_bytes(marker_c_content)

        # Clean up kson-sdist after successful build
        print("Cleaning up build files...")
        shutil.rmtree(kson_copy_dir, ignore_errors=True)

    # Post-condition: verify all required artifacts are present
    missing = [f for f in required if not (src_kson_dir / f).exists()]
    if missing:
        raise RuntimeError(
            f"Required native artifacts missing for {sys.platform}: {', '.join(missing)}. "
            f"Install from a pre-built wheel instead, or ensure a JDK is available "
            f"so the Gradle build can produce them."
        )


def build_sdist(sdist_directory, config_settings=None):
    """Build source distribution."""
    # Note: When creating sdist, we keep kson-sdist for later use
    # The kson-sdist directory should be prepared beforehand by release process
    return _orig.build_sdist(sdist_directory, config_settings)


def build_wheel(wheel_directory, config_settings=None, metadata_directory=None):
    """Build wheel with native artifacts."""
    _ensure_native_artifacts()
    # kson-sdist will be deleted after building artifacts, so it won't be in the wheel
    return _orig.build_wheel(wheel_directory, config_settings, metadata_directory)

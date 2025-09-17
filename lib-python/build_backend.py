"""
Custom build backend for kson Python package.
This backend ensures native artifacts are built when creating source distributions.
"""

import os
import subprocess
import shutil
from pathlib import Path
from setuptools import build_meta as _orig
from setuptools.build_meta import *


def _ensure_native_artifacts():
    """Build native artifacts using the bundled Gradle setup."""
    lib_python_dir = Path(__file__).parent
    kson_copy_dir = lib_python_dir / "kson-sdist"
    src_dir = lib_python_dir / "src"
    src_kson_dir = src_dir / "kson"

    # Check if native artifacts already exist
    native_files = ["kson.dll", "libkson.dylib", "libkson.so", "kson_api.h"]
    artifacts_exist = any((src_kson_dir / f).exists() for f in native_files)

    if not artifacts_exist and kson_copy_dir.exists():
        print("Building native artifacts with bundled Gradle setup...")
        # Run gradle from the kson-sdist directory
        original_dir = os.getcwd()
        try:
            os.chdir(kson_copy_dir)

            # Run the Gradle build
            gradlew = "./gradlew" if os.name != "nt" else "gradlew.bat"
            result = subprocess.run(
                [gradlew, "lib-python:build"], capture_output=True, text=True
            )

            if result.returncode != 0:
                print(f"Gradle build failed:\n{result.stderr}")
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

        finally:
            os.chdir(original_dir)

        # Clean up kson-sdist after successful build
        print("Cleaning up build files...")
        shutil.rmtree(kson_copy_dir, ignore_errors=True)


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

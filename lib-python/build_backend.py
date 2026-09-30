"""
Custom build backend for the kson Python package.

The native library is Gradle output from a full checkout, so no wheel build can conjure one:
this backend's whole job is to refuse rather than emit a wheel that imports and then fails on
the first call.  The sdist is source only and therefore always lands in that refusal.
"""

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
    """Raise unless this platform's native artifacts already sit in src/kson."""
    src_kson_dir = Path(__file__).parent / "src" / "kson"
    missing = [f for f in _required_artifacts() if not (src_kson_dir / f).exists()]
    if missing:
        raise RuntimeError(
            f"Cannot build kson-lang for {sys.platform}: {', '.join(missing)} missing from "
            f"{src_kson_dir}.\n"
            f"Those are Gradle output. kson-lang ships prebuilt wheels carrying them, so either "
            f"none matched this platform and Python version and pip fell back to the source "
            f"distribution, or this is a checkout where the Gradle build has not run.\n"
            f"Either way, \"Build from source\" in the readme is the way to get them:\n"
            f"    git clone https://github.com/kson-org/kson.git\n"
            f"    cd kson && ./gradlew :lib-python:build"
        )


def build_wheel(wheel_directory, config_settings=None, metadata_directory=None):
    """Build a wheel, refusing unless the native artifacts to put in it are already there."""
    _ensure_native_artifacts()
    return _orig.build_wheel(wheel_directory, config_settings, metadata_directory)

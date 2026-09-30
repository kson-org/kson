"""Tests that the sdist's exclusions keep step with the wheel's package data.

`[tool.setuptools.package-data]` lists what a *wheel* must carry, and setuptools offers the same
files to the sdist.  Only the `exclude` commands in MANIFEST.in take them back out again, so a
name added to one list and not the other quietly puts a platform binary in a source archive -
which is how the sdist came to ship `libkson.so` in the first place.  Opening the tarball is a
release-branch job; this runs on every build.
"""

import fnmatch
import tomllib
from pathlib import Path

LIB_PYTHON = Path(__file__).parent.parent

# what a wheel carries and an sdist must not: the native library, and the headers generated
# beside it.  Kept in step with the `grep` guarding `test-python-sdist` in .circleci/config.kson
BUILD_OUTPUT_SUFFIXES = (".so", ".dylib", ".dll", ".pyd", ".h")


def _setuptools_config():
    with (LIB_PYTHON / "pyproject.toml").open("rb") as pyproject:
        return tomllib.load(pyproject)["tool"]["setuptools"]


def _package_data_paths():
    """every `package-data` entry as the path MANIFEST.in patterns are written against"""
    config = _setuptools_config()
    source_root = config["package-dir"][""]
    return [
        f"{source_root}/{package}/{filename}"
        for package, filenames in config["package-data"].items()
        for filename in filenames
    ]


def _manifest_excludes():
    """the patterns MANIFEST.in's `exclude` commands take back out of the sdist"""
    patterns = []
    for line in (LIB_PYTHON / "MANIFEST.in").read_text().splitlines():
        command, *rest = line.split() or [""]
        if command == "exclude":
            patterns.extend(rest)
    return patterns


def test_every_build_artifact_in_package_data_is_excluded_from_the_sdist():
    artifacts = [p for p in _package_data_paths() if p.endswith(BUILD_OUTPUT_SUFFIXES)]
    assert artifacts, "package-data names no build output, so this test proves nothing"

    excludes = _manifest_excludes()
    unexcluded = [
        artifact
        for artifact in artifacts
        if not any(fnmatch.fnmatch(artifact, pattern) for pattern in excludes)
    ]
    assert unexcluded == [], (
        f"package-data puts {unexcluded} in the sdist and no MANIFEST.in `exclude` in {excludes} "
        f"takes them out"
    )

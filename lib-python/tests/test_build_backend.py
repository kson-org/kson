"""Tests for the custom build backend.

The backend cannot produce the native library - only a Gradle build in a full checkout can - so
all it decides is who gets through, and what it tells whoever does not.
"""

import sys
from unittest.mock import patch

import pytest

import build_backend


@pytest.fixture
def src_kson(tmp_path):
    """An empty `src/kson`, with the backend pointed at the source tree holding it."""
    src_kson_dir = tmp_path / "src" / "kson"
    src_kson_dir.mkdir(parents=True)
    with patch.object(build_backend, "__file__", str(tmp_path / "build_backend.py")):
        yield src_kson_dir


def _refusal_message():
    """What `_ensure_native_artifacts` refuses with, given whatever the `src_kson` fixture holds."""
    with pytest.raises(RuntimeError) as refused:
        build_backend._ensure_native_artifacts()
    return str(refused.value)


@pytest.mark.parametrize(
    "platform, library",
    [("darwin", "libkson.dylib"), ("linux", "libkson.so"), ("win32", "kson.dll")],
)
def test_platform_native_library(platform, library):
    with patch.object(sys, "platform", platform):
        assert build_backend._platform_native_library() == library


def test_unsupported_platform_is_named_in_the_error():
    with patch.object(sys, "platform", "freebsd"):
        with pytest.raises(RuntimeError, match="Unsupported platform: freebsd"):
            build_backend._platform_native_library()


def test_passes_when_this_platforms_artifacts_are_present(src_kson):
    for artifact in build_backend._required_artifacts():
        (src_kson / artifact).touch()

    build_backend._ensure_native_artifacts()


def test_refuses_when_only_another_platforms_library_is_present(src_kson):
    """A wheel around a foreign library imports fine and dies on the first call."""
    ours = build_backend._platform_native_library()
    for library in build_backend.PLATFORM_NATIVE_LIBRARIES.values():
        if library != ours:
            (src_kson / library).touch()
    (src_kson / "jni_simplified.h").touch()

    assert ours in _refusal_message()


def test_refuses_when_the_jni_header_is_missing(src_kson):
    """`kson/__init__.py` reads the header at import time, so the library alone is not enough."""
    (src_kson / build_backend._platform_native_library()).touch()

    assert "jni_simplified.h" in _refusal_message()


def test_refusal_says_what_the_reader_needs(src_kson):
    """This message is the whole user-facing contract of the source distribution, and its wording
    is load-bearing twice over: `test-python-sdist` greps CI's copy of it, and missing artifacts
    mean either that no wheel matched or that Gradle has not run in this checkout - only the
    reader can tell which, so naming one cause sends the other kind of reader after the wrong
    problem."""
    refusal = _refusal_message()

    # what CI greps for, and which machine it is talking about
    assert "Cannot build kson-lang" in refusal
    assert sys.platform in refusal

    # both ways of arriving here, neither one claimed as *the* cause
    assert "prebuilt wheels" in refusal
    assert "fell back" in refusal
    assert "checkout" in refusal

    # where to go next
    assert "git clone https://github.com/kson-org/kson.git" in refusal
    assert "./gradlew :lib-python:build" in refusal


def test_build_wheel_refuses_before_setuptools_gets_a_look(src_kson):
    """The check is only worth having if the hook a frontend calls actually runs it."""
    with pytest.raises(RuntimeError, match="Cannot build kson-lang"):
        build_backend.build_wheel(str(src_kson))


def test_backend_exposes_every_pep_517_hook():
    """Only `build_wheel` is ours; the rest ride in on the `setuptools.build_meta` star import,
    and a frontend that cannot find them refuses to build at all."""
    hooks = [
        "build_sdist",
        "build_wheel",
        "get_requires_for_build_sdist",
        "get_requires_for_build_wheel",
        "prepare_metadata_for_build_wheel",
    ]
    assert [hook for hook in hooks if not callable(getattr(build_backend, hook, None))] == []
    assert build_backend.build_wheel.__module__ == "build_backend"

"""Tests for the custom build backend's platform-aware native artifact detection."""

import subprocess
import sys
from unittest.mock import patch

import pytest

import build_backend


class TestPlatformNativeLibrary:
    def test_darwin(self):
        with patch.object(sys, "platform", "darwin"):
            assert build_backend._platform_native_library() == "libkson.dylib"

    def test_linux(self):
        with patch.object(sys, "platform", "linux"):
            assert build_backend._platform_native_library() == "libkson.so"

    def test_windows(self):
        with patch.object(sys, "platform", "win32"):
            assert build_backend._platform_native_library() == "kson.dll"

    def test_unsupported_platform(self):
        with patch.object(sys, "platform", "freebsd"):
            with pytest.raises(RuntimeError, match="Unsupported platform: freebsd"):
                build_backend._platform_native_library()


class TestEnsureNativeArtifacts:
    """Verify that _ensure_native_artifacts only considers the current platform's library."""

    def _place_all_required_artifacts(self, directory):
        """Place all required artifacts for the current platform."""
        for f in build_backend._required_artifacts():
            (directory / f).touch()

    def test_skips_build_when_current_platform_artifacts_exist(self, tmp_path):
        """Build is skipped when all required artifacts for *this* platform are present."""
        src_kson = tmp_path / "src" / "kson"
        src_kson.mkdir(parents=True)
        kson_sdist = tmp_path / "kson-sdist"
        kson_sdist.mkdir()

        self._place_all_required_artifacts(src_kson)

        with patch.object(build_backend, "__file__", str(tmp_path / "build_backend.py")):
            with patch.object(build_backend.subprocess, "run") as mock_run:
                build_backend._ensure_native_artifacts()
                mock_run.assert_not_called()

    def test_triggers_build_when_only_foreign_artifact_exists(self, tmp_path):
        """Build is triggered when only a *different* platform's library is present."""
        src_kson = tmp_path / "src" / "kson"
        src_kson.mkdir(parents=True)
        kson_sdist = tmp_path / "kson-sdist"
        kson_sdist.mkdir()

        # Place foreign platform libraries (but not the current one)
        current_lib = build_backend._platform_native_library()
        for lib in build_backend.PLATFORM_NATIVE_LIBRARIES.values():
            if lib != current_lib:
                (src_kson / lib).touch()
        (src_kson / "jni_simplified.h").touch()

        with patch.object(build_backend, "__file__", str(tmp_path / "build_backend.py")):
            with patch.object(build_backend.subprocess, "run") as mock_run:
                mock_run.return_value = subprocess.CompletedProcess(
                    args=[], returncode=1, stdout="", stderr="fail",
                )
                with pytest.raises(RuntimeError, match="Failed to build native artifacts"):
                    build_backend._ensure_native_artifacts()
                mock_run.assert_called_once()

    def test_triggers_build_when_header_missing(self, tmp_path):
        """Build is triggered when the native lib exists but jni_simplified.h is missing."""
        src_kson = tmp_path / "src" / "kson"
        src_kson.mkdir(parents=True)
        kson_sdist = tmp_path / "kson-sdist"
        kson_sdist.mkdir()

        current_lib = build_backend._platform_native_library()
        (src_kson / current_lib).touch()

        with patch.object(build_backend, "__file__", str(tmp_path / "build_backend.py")):
            with patch.object(build_backend.subprocess, "run") as mock_run:
                mock_run.return_value = subprocess.CompletedProcess(
                    args=[], returncode=1, stdout="", stderr="fail",
                )
                with pytest.raises(RuntimeError, match="Failed to build native artifacts"):
                    build_backend._ensure_native_artifacts()
                mock_run.assert_called_once()

    def test_errors_when_artifacts_missing_and_no_kson_sdist(self, tmp_path):
        """Raises when artifacts are missing and there's no kson-sdist to build from."""
        src_kson = tmp_path / "src" / "kson"
        src_kson.mkdir(parents=True)

        with patch.object(build_backend, "__file__", str(tmp_path / "build_backend.py")):
            with pytest.raises(RuntimeError, match="Required native artifacts missing"):
                build_backend._ensure_native_artifacts()

    def test_successful_build_replaces_src_and_preserves_marker(self, tmp_path):
        """On successful build, src is replaced with kson-sdist output and _marker.c is preserved."""
        src_kson = tmp_path / "src" / "kson"
        src_kson.mkdir(parents=True)
        kson_sdist = tmp_path / "kson-sdist"

        # Set up the kson-sdist build output that Gradle would produce
        native_lib = build_backend._platform_native_library()
        kson_sdist_src = kson_sdist / "lib-python" / "src" / "kson"
        kson_sdist_src.mkdir(parents=True)
        (kson_sdist_src / "__init__.py").write_text("# built")
        (kson_sdist_src / native_lib).write_bytes(b"built-lib")
        (kson_sdist_src / "jni_simplified.h").write_text("/* header */")

        # Place _marker.c in the original src (should be preserved)
        marker_content = b"/* platform marker */"
        (src_kson / "_marker.c").write_bytes(marker_content)

        with patch.object(build_backend, "__file__", str(tmp_path / "build_backend.py")):
            with patch.object(build_backend.subprocess, "run") as mock_run:
                mock_run.return_value = subprocess.CompletedProcess(
                    args=[], returncode=0, stdout="", stderr="",
                )
                build_backend._ensure_native_artifacts()

        # src was replaced with kson-sdist output
        assert (tmp_path / "src" / "kson" / "__init__.py").read_text() == "# built"
        assert (tmp_path / "src" / "kson" / native_lib).read_bytes() == b"built-lib"

        # _marker.c was preserved
        assert (tmp_path / "src" / "kson" / "_marker.c").read_bytes() == marker_content

        # kson-sdist was cleaned up
        assert not kson_sdist.exists()

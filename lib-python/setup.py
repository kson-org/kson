# Minimal setup.py - tells setuptools this package has compiled code
from setuptools import setup, Extension

setup(
    ext_modules=[
        Extension(
            "kson._marker",
            ["src/kson/_marker.c"],
            py_limited_api=True,  # Use stable ABI for Python 3.x compatibility
            define_macros=[("Py_LIMITED_API", "0x030A0000")],  # Python 3.10+ stable ABI
        )
    ]
)

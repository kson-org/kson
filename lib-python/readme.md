# Python bindings for Kson's public API

[KSON](https://kson.org) is available on PyPI for Linux, macOS and Windows.

## Installation

Install from PyPI:

```bash
pip install kson
```

### Build from source

```bash
git clone https://github.com/kson-org/kson.git
cd kson && ./gradlew :lib-python:build
pip install ./lib-python
```

## Example usage


```python
from kson import Kson, Success
result = Kson.to_json("key: [1, 2, 3, 4]")
assert isinstance(result, Success)
print(result.output())
```

This should print the following to stdout:

```json
{
  "key": [
    1,
    2,
    3,
    4
  ]
}
```

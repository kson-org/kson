# Python bindings for Kson's public API

Only local installation is supported at the moment. PyPI packages for Linux, macOS and Windows are
coming soon!

## Example usage

Build `lib-python` from source:

```bash
git clone https://github.com/kson-org/kson.git
cd kson && ./gradlew :lib-python:build
```

Install `lib-python` to an existing python project:

```bash
pip install ../kson/lib-python
```

Write some code:

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

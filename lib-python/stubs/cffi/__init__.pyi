# This stub exists because otherwise type checking fails. The root cause is that `pyright` ships
# with a buggy stub for the `cffi` library. A fix is ready, but it will not be available until the
# next release.
#
# This code can be deleted once we upgrade `pyright` to a version higher than `1.1.408`.

from typing import Any
def __getattr__(name: str) -> Any: ...

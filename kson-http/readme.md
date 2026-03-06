# kson-http

A reimplementation of KSON's public API (see [kson-lib](../kson-lib)) based on
HTTP requests to a server that respects the [kson-api-schema](./kson-api-schema.json).
This code is meant to facilitate testing, so users of KSON should ignore it
and rely on `kson-lib` for all their KSON needs. We might implement a
user-facing, HTTP-based KSON API in the future.

> [!WARNING]
> The JSON Schema is highly experimental, possibly inaccurate in some places, and
> subject to change without notice

### Testing KSON implementations

If you have an implementation of the KSON public API that you would like to
test, all you need to do is create an HTTP server that handles KSON requests
according to the [schema](./kson-api-schema.json) and internally dispatches
them to your actual KSON implementation. This sounds complicated, but a good
LLM can easily one-shot server creation based on the schema and your
implementation's source code.

With that in place, you then need to instruct Gradle to run the `kson-lib`
tests against your server. For reference, see the [test project](../lib-python/kson-lib-tests/)
we use to run the `kson-lib` tests against our Python bindings.

# KSON CLI

A command-line interface for working with KSON files.

## Building

To build a native executable, run:

```bash
./gradlew :tooling:cli:buildNativeImage
```

The native binary will be generated in `tooling/cli/build/native/nativeCompile/`.

## Usage

The CLI provides several commands for working with KSON files:

- `format` - Format KSON files
- `analyze` - Analyze KSON files for issues
- `json` or `yaml` - Convert KSON files to other formats

Run the CLI with `--help` for more information on available commands and options.
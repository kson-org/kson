# Schema Configuration Examples

This directory contains examples of how to configure JSON schemas for KSON files.

## Overview

The KSON language server supports associating JSON schemas with KSON files using a workspace configuration file. This enables:
- Code completion based on schema definitions
- Hover information showing property descriptions
- Validation against the schema

## Configuration File

Create a `.kson-schema.kson` file in your workspace root with the following format:

```json
{
  "schemas": [
    {
      "fileMatch": ["config/*.kson", "**/*.config.kson"],
      "schema": "schemas/config.schema.kson"
    }
  ]
}
```

### Schema Mapping Properties

- **fileMatch**: Array of glob patterns to match KSON files
  - `*.kson` - matches all .kson files in the current directory
  - `config/*.kson` - matches all .kson files in the config directory
  - `**/*.config.kson` - matches all .config.kson files in any subdirectory

- **schema**: Workspace-relative path to the JSON schema file
  - Paths are resolved relative to the workspace root
  - Must be a local file path (URLs not supported yet)

## Example Structure

```
workspace/
├── .kson-schema.kson          # Schema configuration
├── schemas/                    # Schema definitions
│   ├── config.schema.kson
│   └── settings.schema.kson
├── config/                     # KSON files
│   └── app.config.kson
└── settings.kson
```

## Pattern Matching

When a KSON file is opened, the language server:
1. Checks for `.kson-schema.kson` in the workspace root
2. Matches the file path against each `fileMatch` pattern
3. Loads the corresponding schema file
4. Uses the schema for completions and hover information

If multiple patterns match a file, the first matching schema is used.

## Fallback Behavior

If no schema configuration is found or no pattern matches a file, the language server falls back to a built-in test schema for backward compatibility.

## Reloading Configuration

Currently, changes to `.kson-schema.kson` require restarting the language server. Dynamic reloading is planned for a future release.

## Schema File Format

Schema files should be valid JSON Schema (draft-07 or compatible):

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": {
    "propertyName": {
      "type": "string",
      "description": "Description shown in hover",
      "title": "Display Name"
    }
  }
}
```

See the `schemas/` directory for complete examples.

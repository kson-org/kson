# Bundled Schema Files

This directory contains bundled schema files for KSON dialects.

## Adding a bundled schema for a language

1. Add the schema file here (e.g., `my-dialect.schema.kson`)
2. Update `package.json` to add a `bundledSchema` field to the language contribution:
   ```json
   {
     "id": "my-dialect",
     "extensions": [".md"],
     "bundledSchema": "./dist/extension/schemas/my-dialect.schema.kson"
   }
   ```
3. The schema will be automatically bundled and loaded for that language ID.

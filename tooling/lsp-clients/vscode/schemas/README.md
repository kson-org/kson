# Bundled Schema Files

This directory contains bundled schema files for KSON languages.

## Adding a bundled schema for a language

1. Add the schema file here (e.g., `example.schema.kson`)
2. Update `package.json` to add a `bundledSchema` field to the language contribution:
   ```json
   {
     "id": "example",
     "extensions": [".md"],
     "bundledSchema": "./dist/extension/schemas/example.schema.kson"
   }
   ```
3. The schema will be automatically bundled and loaded for that language ID.

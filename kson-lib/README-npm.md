# JavaScript/TypeScript bindings for kson-lib API

KSON (Kson Structured Object Notation) combines the best of JSON and YAML—robust and efficient like JSON, clean and readable like YAML.

## Example Usage

Write some code:

```javascript
import { Kson, Result } from '@kson_org/kson';

const kson = Kson.getInstance();

// Convert KSON to JSON
const result = kson.toJson('key: [1, 2, 3, 4]');
if (result instanceof Result.Success) {
  console.log(result.output);
}
```

Running this should print the following:

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

### TypeScript Usage

This package includes TypeScript definitions out of the box:

```typescript
import { Kson, Result, FormatOptions, IndentType, FormattingStyle } from 'kson';

const kson = Kson.getInstance();

const ksonString = `
  person:
    name: 'Leonardo Bonacci'
    nickname: Fibonacci
    age: 42
`;

// Convert KSON to JSON
const jsonResult: Result = kson.toJson(ksonString);
if (jsonResult instanceof Result.Success) {
  const data = JSON.parse(jsonResult.output);
  console.log(data);
} else if (jsonResult instanceof Result.Failure) {
  console.error('Errors:', jsonResult.errors);
}

// Format KSON (returns string directly)
const formatted: string = kson.format(ksonString);
console.log(formatted);

// Format with options
const options = new FormatOptions(
  new IndentType.Spaces(2),
  FormattingStyle.PLAIN
);
const formattedWithOptions = kson.format(ksonString, options);
console.log(formattedWithOptions);

// Convert to YAML
const yamlResult = kson.toYaml(ksonString);
if (yamlResult instanceof Result.Success) {
  console.log(yamlResult.output);
}
```

## API Reference

### Core Methods

- **`toJson(kson: string, retainEmbedTags?: boolean): Result`**
  Converts KSON to formatted JSON. Returns a `Result` which can be either:
  - `Result.Success` with an `output` property containing the JSON string
  - `Result.Failure` with an `errors` property containing a list of error messages

- **`toYaml(kson: string, retainEmbedTags?: boolean): Result`**
  Converts KSON to YAML format. Returns a `Result` with Success/Failure variants.

- **`format(kson: string, formatOptions?: FormatOptions): string`**
  Formats KSON string according to specified style. Returns the formatted string directly.

- **`analyze(kson: string): Analysis`**
  Analyzes KSON and returns diagnostic information including tokens and any errors.

- **`parseSchema(schemaKson: string): SchemaResult`**
  Parses and validates a KSON schema. Returns a `SchemaResult` which can be:
  - `SchemaResult.Success` with a `schemaValidator` for validating KSON documents
  - `SchemaResult.Failure` with error messages

### Helper Types

- **`FormatOptions`**: Configuration for formatting
  - `indentType`: Either `IndentType.Spaces(size)` or `IndentType.Tabs`
  - `formattingStyle`: One of `FormattingStyle.PLAIN`, `FormattingStyle.DELIMITED`, or `FormattingStyle.COMPACT`

- **`Result`**: Success/Failure union type for operations that can fail
  - `Result.Success`: Contains `output` property with the result string
  - `Result.Failure`: Contains `errors` property with error messages

- **`SchemaResult`**: Success/Failure for schema parsing
  - `SchemaResult.Success`: Contains `schemaValidator` for validating documents
  - `SchemaResult.Failure`: Contains error messages

## KSON Syntax Highlights

```kson
# Comments are supported
person:
  name: 'Leonardo Bonacci'
  nickname: Fibonacci  # Quotes are optional for simple strings
  age: 42

  # Arrays can be written in multiple styles
  favorite_books:
    - title: Elements
      author: Euclid
    - title: Metaphysics
      author: Aristotle

  # Nested arrays and complex values
  favorite_numbers: [0, 1, 1, 2, 3, 5, 8]
  golden_ratio: '(1 + √5)/2'
```

## Environment Support

This package uses conditional exports to provide optimized builds for different environments:

- **Node.js**: ES modules optimized for server-side use
- **Browser**: ES modules optimized for client-side use
- **TypeScript**: Full type definitions included

The correct version is automatically selected based on your environment.

## Platform Compatibility

The JavaScript bindings are compiled from Kotlin/JS and work across all modern JavaScript environments. The package includes:

- Pre-compiled ES modules for both browser and Node.js
- TypeScript type definitions
- Source maps for debugging

No additional setup or native binaries are required - everything works out of the box on all platforms that support JavaScript.

## Links

- [KSON Documentation](https://github.com/kson-org/kson/blob/main/docs/readme.md)
- [GitHub Repository](https://github.com/kson-org/kson)
- [Report Issues](https://github.com/kson-org/kson/issues)

## License

Apache-2.0
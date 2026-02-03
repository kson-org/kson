# KSON Documentation

KSON is a configuration language designed to be an **improved interface on plain text data**, particularly at the
human/machine boundary. KSON is:

- Robust and efficient like JSON
- Clean and readable like YAML
- Flexibly formatted with built-in pretty-printing
- Clear and helpful, with user-centric errors
- Richly supported across VS Code, JetBrains IDEs, and any LSP-compatible editor
- Type-safe with JSON Schema support
- Ready to run everywhere with multi-platform compatibility

KSON is a **superset of JSON**. Some key ways KSON extends JSON's syntax:

- KSON includes a "plain" syntax for [objects](#plain-objects) and [lists](#plain-dash-lists) allowing
  its [default format](#plain-format-example) to look a lot like YAML, without relying on significant whitespace
- KSON introduces the [Embed Block](#embed-blocks), a structure for ergonomically embedding complex content such as code
  blocks
- KSON adds flexibility to [Strings](#strings), allowing unquoted "simple" strings and supporting single-quotes as an
  alternative to double-quotes

A KSON document is a document containing exactly one [KSON Value](#kson-values)

# Kson Values

Kson defines the following value types:

- [Nulls](#nulls)
- [Booleans](#booleans)
- [Numbers](#numbers)
- [Strings](#strings)
- [Lists](#lists)
- [Objects](#objects)
- [Embed blocks](#embed-blocks)

## Nulls

A KSON document representing `null` is simply:

```kson
null
```

## Booleans

Booleans are written `true` or `false`. Here is a KSON document representing a boolean value:

```kson
true
```

## Numbers

Numbers in KSON are identical to [numbers in JSON](https://datatracker.ietf.org/doc/html/rfc8259#section-6), with the
exception that they may have leading zeros. Here is a KSON document representing a number:

```kson
5
```

Here is a KSON [list](#lists) demonstrating a variety of supported numbers:

```kson
- 5
- 3.14
- 2.998e8
- 025
```

## Strings

Strings in KSON are identical to [strings in JSON](https://datatracker.ietf.org/doc/html/rfc8259#section-7), except:

- KSON strings may be unquoted if they are 'simple', i.e. they only contain letters (from any alphabet), numbers or
  underscores, and they do not start with a number

```kson
an_unqu0t3d_striπg
```

- KSON strings may be quoted using a single quote rather than a double quote. Here is a [list](#lists) of strings
  demonstrating the difference:

```kson
- 'A "string", enclosed in single quotes'
- "A 'string', enclosed in double quotes"
- 'use slash to escape \'internal\' quotes'
- "use slash to escape \"internal\" quotes"
```

- KSON strings may contain raw, unescaped whitespace (though an [Embed Block](#embed-blocks) will usually be a better
  choice for multiline strings due to their editing ergonomics and indent-stripping)

```
'A string with
a raw newline.
	Tabs are also A-OK 👌'
```

## Lists

A list in Kson is a sequence of [Kson Values](#kson-values).

KSON supports three list styles:

- [Plain Dash Lists](#plain-dash-lists), a clean YAML-like list style useful for clarity and readability
- [Delimited Dash Lists](#delimited-dash-lists), a delimited version of [Plain Dash Lists](#plain-dash-lists) useful
  when editing or refactoring complex nested lists
- [Bracket Lists](#bracket-lists) a JSON-like list style useful for compacting lists and required for JSON compatibility

### Plain Dash Lists

A Plain Dash List is a clean YAML-like un-delimited list useful for clarity and readability. This is the style used in
Kson's [plain format](#plain-format-example).

A plain dash list is un-delimited, denotes each list element with a `- `.

```kson
- element_one
- element_two
- element_three
```

A empty list is expressed as: `<>`

Plain dash lists may be nested like so:

```kson
- outer_element
-
  - nested_element_1
  - nested_element_2
```

Important: unlike YAML, **whitespace is not significant in KSON**, so to add more outer elements to the above list, we
must explicitly end the nested list with `=`, the [end-dash](#the-end-dash-):

```kson
- outer_element
-
  - nested_element_1
  - nested_element_2
  =
- outer_element_2
```

#### The end-dash `=`

The end-dash `=` explicitly declares the end of a [Plain Dash List](#plain-dash-lists). The end-dash is only required
when a nested [Plain Dash List](#plain-dash-lists) needs to explicitly denote the end of the items that belong to it:

```kson
- outer_element_1
-
  - nested_element_1
  - nested_element_2
  =
# note that without the end-dash, `outer_element_2` would belong to the nested list
- outer_element_2
```

The [end-dot `.`](#the-end-dot-) works analogously for [Plain Objects](#plain-objects).

### Delimited Dash Lists

A Delimited Dash List is a delimited version of a [Plain Dash List](#plain-dash-lists), useful when editing or
refactoring complex nested lists&mdash;for instance, these lists can be safely and unambiguously copy/pasted around.

Delimited Dash Lists are delimited with angle brackets `<...>`:

```kson
<
  - outer_element1
  - <
      - nested_element_1
      - nested_element_2
    >
  - outer_element_2
>
```

An empty list is expressed as: `<>`

This style is used in Kson's [delimited format](#delimited-format-example)

## Bracket Lists

A Bracket List is a JSON-like list useful for supporting [compact formatted](#compact-format-example) lists and required
for JSON compatibility.

Bracket Lists are delimited with square brackets `[...]`:

```kson
[
  outer_element1
  [
      nested_element_1
      nested_element_2
  ]
  outer_element_2
]
```

> [!NOTE]
> Commas are supported between elements, but they are unnecessary and generally formatted away.
> See [Commas in Kson](#commas-in-kson) for detail

## Objects

Object properties in KSON are `key: value` pairs where `key` is a [String](#strings) and `value` is
any [Kson Value](#kson-values).

KSON supports two object styles:

- [Plain Objects](#plain-objects), a clean YAML-like un-delimited object style useful for clarity and readability
- [Delimited Objects](#delimited-objects), a JSON-like delimited version of [Plain Objects](#plain-objects) useful when
  editing or refactoring complex nested objects&mdash;for instance, these objects can be safely and unambiguously
  copy/pasted around

### Plain Objects

A plain object in KSON is a clean YAML-like un-delimited object style useful for clarity and readability.

```kson
property_one: 1
property_two: 2
property_three: 3
```

An empty object is expressed as: `{}`

Plain objects may be nested like so:

```kson
outer_property:
  nested_property_1: 1
  nested_property_2: 2
```

[//]: # (Discuss: add the same edit as we made in list on the call with Adolfo)
Important: unlike YAML, **whitespace is not significant in KSON**, so to add more outer properties to the above object,
we must explicitly end the nested object with `.`, the [end-dot](#the-end-dot-):

```kson
outer_property:
  nested_property_1: 1
  nested_property_2: 2
  .
outer_property_2: 'not part of the nested object'
```

#### The end-dot `.`

The end-dot `.` explicitly declares the end of a [Plain Object](#plain-objects). The end-dot is only required when
a [Plain Object](#plain-objects) needs to explicitly denote the end of the properties that belong to it, i.e.:

```kson
outer_property_1:
  nested_property_1: x
  nested_element_2: y
  .
# note that without the end-dot, `outer_element_2` would belong to the nested object
outer_element_2: 'a value'
```

The [end-dash `=`](#the-end-dash-) works analogously for [Plain Dash Lists](#plain-dash-lists).

### Delimited Objects

A Delimited Object is a a JSON-like delimited version of [Plain Objects](#plain-objects) useful when editing or
refactoring complex nested
objects.

Delimited Objects are delimited with curly braces `{...}`:

```kson
{
  outer_property_1: {
      nested_property_1: x
      nested_element_2: y
    }
  outer_element_2: 'a value'
}
```

An empty object is expressed as: `{}`

This style is used in Kson's [delimited format](#delimited-format-example)

> [!NOTE]
> Commas are supported between properties, but they are unnecessary and generally formatted away.
> See [Commas in Kson](#commas-in-kson) for detail

## Embed Blocks

The KSON Embed Block is designed for ergonomically embedding complex content such as code
blocks. These blocks start on the first newline after the opening `%`, run all the way up
to the closing `%%` and always strip their minimum indent:

```kson
embed_block: %
  Free form,
  multi-line,
  indent-stripped
  embedded text block!
  %%
```

[Embed blocks](#embed-blocks) often feature an [Embed Tag](#embed-tags) denoting the type of text being embedded. This
is particularly useful for embedded code so that editors and tooling can key off this value and provide
language-specific features for a given embed block

```
%typescript
function fibonacci(n: number): number {
  if (n <= 0) return 0;
  if (n === 1) return 1;
    return fibonacci(n - 1) + fibonacci(n - 2);
  }
%%
```

See [Embed Preamble](#the-embed-preamble) for full details on the metadata supported by [Embed Blocks](#embed-blocks)

### Escaping Embed Delimiters
The rules for escaping embed block delimiters are as follows:
```kson
embed_escapes: %
  Embed end-delimiters are escaped by putting a slash inside them: %\%

  Note that this moves the escaping goalpost since we also need to allow "%\%"
  literally inside of embeds.  So: when evaluating escaped embed delimiters,
  we allow arbitrary `\`s between `%`s, and consume one of them.  Thus, %\\%
  gives %\% in the output, %\\\% gives %\\% in the output, etc forever until
  we hit an uninterrupted end-delimiter:
  %%
```

### Alternative Embed Block Delimiter

The alternative embed delimiter `$` works identically to `%` and can be used when convenient to minimize
escaping

```
alternate_embed: $kson
  This embed block is equivalent to a %/%%-delimited block, but here "$/$"
  must be escaped rather than "%%"
$$
```


### The Embed Preamble

The Embed Preamble may be provided to annotate the embedded content. An Embed Preamble is made of two parts:

- the [Embed Tag](#embed-tags), which denotes the content type
- the [Embed Metadata](#embed-metadata), arbitrary metadata given after the first colon `:` in
  the [Embed Preamble](#the-embed-preamble)

```
%text: sample block
This is a sample block of type `text`, as noted in its Embed Preamble.
%% 
```

An empty [Embed Preamble](#the-embed-preamble) simply indicates an embed of raw text. When appropriate, it's recommended to include a tag or metadata to provide context about the embedded content.

#### Embed Tags

[Embed Blocks](#embed-blocks) can denote the type of content being embedded using an Embed Tag. Embed Tags appear at the
beginning of the [Embed Preamble](#the-embed-preamble) and may not contain newlines or colons.

```kson
%sql
SELECT first_name, last_name, hire_date
FROM employees
WHERE department = 'Sales' 
  AND hire_date < '2020-01-01'
ORDER BY hire_date ASC;
%%
```

While KSON does not specify which tags are legal, KSON does specify that [Embed Tags](#embed-tags) are intended to
denote the type of embedded content, and they intended to enable tooling enhancements for the embedded content&mdash;for
instance to properly highlight the embedded content, or to automatically inject
a [full-on embedded editor](https://www.jetbrains.com/help/idea/using-language-injections.html#language_annotation).
See [Embed Metadata](#embed-metadata) for syntax to arbitrarily embellish this tag.

#### Embed Metadata

[Embed Blocks](#embed-blocks) may have a string of arbitrary metadata associated with them. Embed Metadata is given
after the first colon in the [Embed Preamble](#the-embed-preamble):

```kson
%:Captain's log, Stardate 4523.3 
Deep Space Station K-7 has issued a priority one call. More than an emergency,
it signals near or total disaster. We can only assume the Klingons have attacked
the station. We're going in armed for battle.
%%
```

[//]: # (Discuss: the preamble can contain both an embed tag and embed metadata?)
[Embed Metadata](#embed-metadata) is often used in conjunction with an [Embed Tag](#embed-tags):

```kson
%sql: "server=10.0.1.174;uid=root;database=company"
SELECT first_name, last_name, hire_date
  FROM employees
 WHERE department = 'Sales'
   AND hire_date < '2020-01-01'
 ORDER BY hire_date ASC;
%%
```

## Formatting Styles

KSON has a built-in auto-formatter that supports **three opinionated styles**. These three "views" on the same data
serve
different roles:

- [Plain format](#plain-format-example), a lean format reminiscent of YAML. This is KSON's default format, useful for
  clarity
  and
  readability
- [Delimited format](#delimited-format-example), an explicit format reminiscent of JSON, useful when editing or
  refactoring
  complex
  nested data
- [Compact format](#compact-format-example), a minified format to facilitate compression and transport

### Plain Format Example

```kson
person:
  name: 'Leonardo Bonacci'
  nickname: Fibonacci
  favorite_books:
    - title: Elements
      author: Euclid

    - title: Metaphysics
      author: Aristotle
      .
  favorite_numbers:
    - 
      - 0
      - 1
      - 1
      - 2
      - '...'
      =
    - '(1 + √5)/2'
    - π
  # A Kson "embed block" containing Kotlin code
  favorite_function: %kotlin
      /**
       * Calculates the nth number in the Fibonacci sequence using recursion
       */
      fun fibonacci(n:
        Int): Long {
                if (n < 0) throw IllegalArgumentException("Input must be non-negative")
                return when (n) {
                0 -> 0
                1 -> 1
                else -> fibonacci(n - 1) + fibonacci(n - 2)
        }
    }
    %%
```

### Delimited Format Example

```kson
{
  person: {
    name: 'Leonardo Bonacci'
    nickname: Fibonacci
    favorite_books: <
      - {
          title: Elements
          author: Euclid
        }
      - {
          title: Metaphysics
          author: Aristotle
        }
    >
    favorite_numbers: <
      - <
          - 0
          - 1
          - 1
          - 2
          - '...'
        >
      - '(1 + √5)/2'
      - π
    >
    # A Kson "embed block" containing Kotlin code
    favorite_function: %kotlin
        /**
         * Calculates the nth number in the Fibonacci sequence using recursion
         */
        fun fibonacci(n:
          Int): Long {
                  if (n < 0) throw IllegalArgumentException("Input must be non-negative")
                  return when (n) {
                  0 -> 0
                  1 -> 1
                  else -> fibonacci(n - 1) + fibonacci(n - 2)
          }
      }
      %%
  }
}
```

### Compact Format Example

```kson
person:name:'Leonardo Bonacci'nickname:Fibonacci favorite_books:[{title:Elements author:Euclid}title:Metaphysics author:Aristotle.]favorite_numbers:[[0 1 1 2 '...']'(1 + √5)/2' π]
# A Kson "embed block" containing Kotlin code
favorite_function:%kotlin
  /**
   * Calculates the nth number in the Fibonacci sequence using recursion
   */
  fun fibonacci(n:
    Int): Long {
            if (n < 0) throw IllegalArgumentException("Input must be non-negative")
            return when (n) {
            0 -> 0
            1 -> 1
            else -> fibonacci(n - 1) + fibonacci(n - 2)
    }
}
%%
```

## JSON Compatibility Notes

#### KSON is a superset of JSON

KSON's status as a superset of JSON is verified
by [JsonSuiteTest.kt](../src/commonTest/kotlin/org/kson/parser/json/generated/JsonSuiteTest.kt), which is generated
  from [JSONTestSuite](https://github.com/nst/JSONTestSuite)

#### JSON Schema support

Kson is compatible with JsonSchema (Draft7, more to come), verified
  by  the [SchemaDraft7SuiteTest*.kt files here](../src/commonTest/kotlin/org/kson/parser/json/generated), generated
  from [JSON-Schema-Test-Suite](https://github.com/json-schema-org/JSON-Schema-Test-Suite)

#### Commas in Kson

JSON compatibility requires that [Objects](#objects) and [Bracket Lists](#bracket-lists) in KSON allow commas. However,
since every [KSON value](#kson-values) has a definite beginning and ending, the **commas are unnecessary and generally
formatted away**.

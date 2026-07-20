# KSON Documentation

KSON and its tooling are designed to be a holistic interface layer for JSON-shaped data. KSON is equivalent to JSON: in addition to any RFC 8259 JSON document being a legal KSON document (KSON is an RFC 8259-compliant **superset of JSON**, see [JSON Compatibility Notes](#json-compatibility-notes)), any KSON document is a single formatter pass away from its JSON representation. Some key ways KSON extends JSON's syntax:

- KSON includes a "plain" syntax for [objects](#plain-objects) and [lists](#plain-dash-lists) allowing its [default format](#plain-format-example) to look a lot like YAML, without relying on significant whitespace
- KSON introduces the [Embed Block](#embed-blocks), a structure for ergonomically embedding complex content such as code blocks
- KSON adds flexibility to [Strings](#strings), allowing unquoted "simple" strings and supporting single-quotes as an alternative to double-quotes

A KSON document is a document containing exactly one [KSON Value](#kson-values)

# KSON Values

KSON defines the following value types:

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

Numbers in KSON are identical to [numbers in JSON](https://datatracker.ietf.org/doc/html/rfc8259#section-6), with the exception that they may have leading zeros. Here is a KSON document representing a number:

```kson
5
```

Here is a KSON [list](#lists) demonstrating a variety of supported numbers:

```kson
- 5
- 3.14
- 2.998e8
- 025
- -273.15
```

## Strings

Strings in KSON are identical to [strings in JSON](https://datatracker.ietf.org/doc/html/rfc8259#section-7), except:

- KSON strings may be unquoted if they are 'simple', i.e. they only contain letters (from any alphabet), numbers, underscores, or dashes. They do not start with a number or a dash.

```kson
an_unqu0t3d_striπg
```

One consequence of unquoted strings: a bare `true`, `false` or `null` is always its literal value, exactly as in JSON&mdash;quote it (`'true'`) when the string is intended. Likewise, a string that is not 'simple' must be quoted: `The Great Gatsby` (spaces), `v1.2` (a dot) and `2020-01-01` (a leading number) all require quotes&mdash;unlike in YAML, `title: The Great Gatsby` is not valid.

- KSON strings may be quoted using a single quote rather than a double quote. Here is a [list](#lists) of strings demonstrating the difference:

```kson
- 'A "string", enclosed in single quotes'
- "A 'string', enclosed in double quotes"
- 'use slash to escape \'internal\' quotes'
- "use slash to escape \"internal\" quotes"
```

- KSON strings may contain raw, unescaped whitespace (though an [Embed Block](#embed-blocks) will usually be a better choice for multiline strings due to their editing ergonomics and indent-stripping)

```kson
'A string with
a raw newline.
	Tabs are also A-OK 👌'
```

## Lists

A list in KSON is a sequence of [KSON Values](#kson-values).

KSON supports three list styles:

- [Plain Dash Lists](#plain-dash-lists), a clean YAML-like list style useful for clarity and readability
- [Delimited Dash Lists](#delimited-dash-lists), a `<>`-delimited version of [Plain Dash Lists](#plain-dash-lists) useful when editing or refactoring complex nested lists
- [Bracket Lists](#bracket-lists), a JSON-like `[]`-delimited list style useful for compacting lists and required for JSON compatibility

### Plain Dash Lists

A Plain Dash List is a clean YAML-like un-delimited list useful for clarity and readability. This is the style used in KSON's [plain format](#plain-format-example).

A plain dash list is un-delimited, denoting each list element with a dash followed by whitespace (`- `)&mdash;the whitespace is what distinguishes a list dash from a negative number like `-5`.

```kson
- element_one
- element_two
- element_three
```

Plain dash lists may be nested like so:

```kson
- outer_element
-
  - nested_element_1
  - nested_element_2
```

Important: unlike YAML, **whitespace is not significant in KSON**, so to add more outer elements to the above list, we must explicitly end the nested list with `=`, the [end-dash](#the-end-dash-):

```kson
- outer_element
-
  - nested_element_1
  - nested_element_2
  =
- outer_element_2
```

#### The end-dash `=`

The end-dash `=` explicitly declares the end of a [Plain Dash List](#plain-dash-lists). Dash list parsing is **greedy**, absorbing every list item it can, so the end-dash `=` is only required when a nested [Plain Dash List](#plain-dash-lists) needs to explicitly unnest a subsequent list item:

```kson
- outer_element_1
-
  - nested_element_1
  - nested_element_2
  =
# note that without the end-dash, `outer_element_2` would belong to the nested list
- outer_element_2
```

See [How Plain Structures End](#how-plain-structures-end) for more color on this.

The [end-dot `.`](#the-end-dot-) works analogously for [Plain Objects](#plain-objects).

### Delimited Dash Lists

A Delimited Dash List is a delimited version of a [Plain Dash List](#plain-dash-lists), useful when editing or refactoring complex nested lists&mdash;for instance, these lists can be safely and unambiguously copy/pasted around.

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

This style is used in KSON's [delimited format](#delimited-format-example)

### Bracket Lists

A Bracket List is a JSON-like list useful for supporting [compact formatted](#compact-format-example) lists and required for JSON compatibility. Their data is naturally equivalent to the list types above.

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
> Commas are supported between elements, but they are unnecessary and generally formatted away. See [Commas in KSON](#commas-in-kson) for detail

### Empty Lists

Empty lists may be expressed as either `<>` or `[]`

## Objects

Object properties in KSON are `key: value` pairs where `key` is a [String](#strings) and `value` is any [KSON Value](#kson-values).

KSON supports two object styles:

- [Plain Objects](#plain-objects), a clean YAML-like un-delimited object style useful for clarity and readability
- [Delimited Objects](#delimited-objects), a JSON-like delimited version of [Plain Objects](#plain-objects) useful when editing or refactoring complex nested objects&mdash;for instance, these objects can be safely and unambiguously copy/pasted around

### Plain Objects

A plain object in KSON is a clean YAML-like un-delimited object style useful for clarity and readability.

```kson
property_one: 1
property_two: 2
property_three: 3
```

Plain objects may be nested like so:

```kson
outer_property:
  nested_property_1: 1
  nested_property_2: 2
```

Important: unlike YAML, **whitespace is not significant in KSON**, so to add more outer properties to the above object, we must explicitly end the nested object with `.`, the [end-dot](#the-end-dot-):

```kson
outer_property:
  nested_property_1: 1
  nested_property_2: 2
  .
outer_property_2: 'not part of the nested object'
```

#### The end-dot `.`

The end-dot `.` explicitly declares the end of a [Plain Object](#plain-objects). Object parsing is **greedy**, absorbing every property it can, so the end-dot `.` is only required when a nested [Plain Object](#plain-objects) needs to explicitly unnest a subsequent property:

```kson
outer_property_1:
  nested_property_1: x
  nested_property_2: y
  .
# note that without the end-dot, `outer_property_2` would belong to the nested object
outer_property_2: 'a value'
```

See [How Plain Structures End](#how-plain-structures-end) for more color on this.

The [end-dash `=`](#the-end-dash-) works analogously for [Plain Dash Lists](#plain-dash-lists).

### Delimited Objects

A Delimited Object is a JSON-like delimited version of [Plain Objects](#plain-objects) useful when editing or refactoring complex nested objects.

Delimited Objects are delimited with curly braces `{...}`:

```kson
{
  outer_property_1: {
    nested_property_1: x
    nested_property_2: y
  }
  outer_property_2: 'a value'
}
```

This style is used in KSON's [delimited format](#delimited-format-example)

> [!NOTE]
> Commas are supported between properties, but they are unnecessary and generally formatted away. See [Commas in KSON](#commas-in-kson) for detail

### Empty Objects

An empty object is expressed as: `{}`

## How Plain Structures End

[Plain Dash Lists](#plain-dash-lists) and [Plain Objects](#plain-objects) are un-delimited, and indentation/whitespace is not what determines their nesting, so it is important to internalize the rule that guides how they end: *their parsing is **greedy**, absorbing every element/property they can*.

Stated concretely for each structure:

- A plain dash list absorbs every `- `-denoted list element it can, ending at the first thing which is not a `- ` element (or at an explicit [end-dash `=`](#the-end-dash-))
- A plain object absorbs every `key: value` property it can, ending at the first thing which cannot be a property (or at an explicit [end-dot `.`](#the-end-dot-))

This rule means plain structures often end naturally with no terminator needed. For instance, a dash list given as a property value ends on its own, since a `key:` cannot be a list element:

```kson
lucky_numbers:
  - 7
  - 13
lucky_color: blue
```

A terminator is needed exactly when the greedy parse would otherwise absorb something meant for an enclosing structure. A common case: an object inside a dash list, followed by a property meant for an enclosing object. Here, the first book object ends naturally at the `- ` (a dash cannot be a property), but the second book needs an end-dot `.` to keep `favorite_movie:` out of the book object:

```kson
favorite_books:
  - title: Elements
    author: Euclid
  - title: Metaphysics
    author: Aristotle
    .
favorite_movie: 'The Rock'
```

Explicitly closing a plain object or list is always legal, even when not required. The formatter cleans up unneeded end-dots and end-dashes.

## Embed Blocks

The KSON Embed Block is designed for ergonomically embedding complex content such as code blocks. See [Embed Block JSON Compatibility](#embed-block-json-compatibility) for details on how the KSON/JSON equivalence is handled for Embed Blocks

An embed block opens with `%`, optionally followed on the same line by an [Embed Tag](#embed-tags). The block's content starts on the next line, runs all the way up to the **first** `%%`, and always strips its minimum indent. When `%%` is on its own line, the newline preceding it is not part of the content&mdash;this is a formatting choice that keeps the closing delimiter visually distinct without affecting the value:

```kson
embed_block: %
  Free form,
  multi-line,
  indent-stripped
  embedded text block!
  %%
```

[Embed blocks](#embed-blocks) often feature an [Embed Tag](#embed-tags) denoting the type of text being embedded. This is particularly useful for embedded code so that editors and tooling can key off this value and provide language-specific features for a given embed block

```kson
%typescript
function fibonacci(n: number): number {
  if (n <= 0) return 0;
  if (n === 1) return 1;
    return fibonacci(n - 1) + fibonacci(n - 2);
  }
%%
```

### Embed Tags

An [Embed Tag](#embed-tags) adds metadata to an [Embed Block](#embed-blocks). An embed tag is a KSON [String](#strings) closed by the first raw newline rather than by a quote: quotes, punctuation and trailing whitespace are all ordinary tag characters&mdash;even a `%%` is welcome in a tag, since content begins only after the tag's newline. The one thing a tag cannot contain is a raw newline, which always ends the tag.

```kson
%this is an "embed tag": it is arbitrary metadata related to this content. In this case, the content is empty.
%%
```

[Embed Tags](#embed-tags) are often used to denote the embed content's type, which is particularly useful to enable tooling enhancements for the embedded content&mdash;for instance to properly highlight the embedded content, or to automatically inject a [full-on embedded editor](https://www.jetbrains.com/help/idea/using-language-injections.html#language_annotation).  Examples:

```kson
%sql
SELECT first_name, last_name, hire_date
FROM employees
WHERE department = 'Sales' 
  AND hire_date < '2020-01-01'
ORDER BY hire_date ASC;
%%
```

The whole line is the tag&mdash;here, the single string `sql "server=10.0.1.174;uid=root;database=company"`, quotes and all:

```kson
%sql "server=10.0.1.174;uid=root;database=company"
SELECT first_name, last_name, hire_date
  FROM employees
 WHERE department = 'Sales'
   AND hire_date < '2020-01-01'
 ORDER BY hire_date ASC;
%%
```

```kson
%Captain's log, Stardate 4523.3
Deep Space Station K-7 has issued a priority one call. More than an emergency,
it signals near or total disaster. We can only assume the Klingons have attacked
the station. We're going in armed for battle.
%%
```

### Escaping Embed Delimiters

An embed block's content **always** ends at the first raw occurrence of the end-delimiter: `%%` ends it every time, without exception&mdash;even a naive regex search for the next `%%` is guaranteed to be correct. The two-character terminator, escaped by interrupting it with a slash, gives content a way to *express* the end-delimiter without ever *containing* it:

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

The alternative embed delimiter `$` works identically to `%` and can be used when convenient to minimize escaping

```kson
alternate_embed: $text
  This embed block is equivalent to a %-delimited block, but here the
  end-delimiter to escape is "$\$" rather than "%%"
$$
```

# Comments

KSON supports line comments: a `#` begins a comment which runs to the end of the line. Comments may appear anywhere whitespace may appear (so a `#` inside a [String](#strings) or an [Embed Block](#embed-blocks)'s content is just part of that value):

```kson
# a comment about this object
key: value
```

Note that comments trailing a value are supported, but discouraged. KSON's [formatter](#formatting-styles) moves trailing comments onto their own line above the item they document.

## Comment preservation

The KSON compiler preserves comments as metadata, which not only enables the formatter to be comment-aware, but also allows comments to be preserved in operations such as KSON's built-in YAML transpilation: the output YAML has the source comments properly embedded in it.

# Formatting Styles

KSON has a built-in auto-formatter that supports **three opinionated styles**. These three "views" on the same data serve different roles:

- [Plain format](#plain-format-example), a lean format reminiscent of YAML. This is KSON's default format, useful for clarity and readability
- [Delimited format](#delimited-format-example), an explicit format reminiscent of JSON, useful when editing or refactoring complex nested data
- [Compact format](#compact-format-example), a minified format to facilitate compression and transport

## Plain Format Example

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
  # A KSON "embed block" containing Kotlin code
  favorite_function: %kotlin
      /**
       * Calculates the nth number in the Fibonacci sequence using recursion
       */
      fun fibonacci(n: Int): Long {
        if (n < 0) throw IllegalArgumentException("Input must be non-negative")
        return when (n) {
        0 -> 0
        1 -> 1
        else -> fibonacci(n - 1) + fibonacci(n - 2)
      }
    }
    %%
```

## Delimited Format Example

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
    # A KSON "embed block" containing Kotlin code
    favorite_function: %kotlin
      /**
       * Calculates the nth number in the Fibonacci sequence using recursion
       */
      fun fibonacci(n: Int): Long {
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

## Compact Format Example

Compact format is fully data-faithful: everything KSON syntactically controls is compressed to the bare minimum, while the embed block's content&mdash;data, not syntax&mdash;is preserved byte-for-byte, whitespace and all:

```kson
person:name:'Leonardo Bonacci'nickname:Fibonacci favorite_books:[{title:Elements author:Euclid}title:Metaphysics author:Aristotle.]favorite_numbers:[[0 1 1 2 '...']'(1 + √5)/2' π]
# A KSON "embed block" containing Kotlin code
favorite_function:%kotlin
    /**
     * Calculates the nth number in the Fibonacci sequence using recursion
     */
    fun fibonacci(n: Int): Long {
      if (n < 0) throw IllegalArgumentException("Input must be non-negative")
      return when (n) {
      0 -> 0
      1 -> 1
      else -> fibonacci(n - 1) + fibonacci(n - 2)
    }
  }
%%
```

# JSON Compatibility Notes

## KSON is a superset of JSON

KSON's status as a superset of JSON is verified by [JsonSuiteTest.kt](../src/commonTest/kotlin/org/kson/parser/json/generated/JsonSuiteTest.kt), which is generated from [JSONTestSuite](https://github.com/nst/JSONTestSuite)

## JSON Schema support

KSON is compatible with JsonSchema (Draft7, more to come), verified by  the [SchemaDraft7SuiteTest*.kt files here](../src/commonTest/kotlin/org/kson/parser/json/generated), generated from [JSON-Schema-Test-Suite](https://github.com/json-schema-org/JSON-Schema-Test-Suite)

## Embed Block JSON Compatibility

Embed Blocks are a user- and tooling-centric formatting of objects with exactly two string properties, `embedTag:` and `embedContent:`, allowing embedded content ergonomics while still being fully equivalent to JSON.

When using KSON as an interface on legacy data by transpiling to JSON or YAML, there is an option to not "preserve" embed tags, transpiling embed blocks down to their `embedContent:` string value. This is useful for facilitating a better editing experience on legacy embedded code, allowing the user to tag the code with its type (say, `sql`, or `typescript`) to enable IDEs to inject better tooling, while still transpiling down to the bare string the underlying legacy data format requires.

## Commas in KSON

JSON compatibility requires that [Objects](#objects) and [Bracket Lists](#bracket-lists) in KSON allow commas. However, since every [KSON value](#kson-values) has a definite beginning and ending, the **commas are unnecessary and generally formatted away**.

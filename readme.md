# KSON: Kson Structured Object Notation

<img src="assets/logo/kson_logo_blue.svg" alt="drawing" width="150"/>

KSON combines the best aspects of JSON and YAML&mdash;robust and efficient like JSON, clean and readable like YAML. KSON
is designed to be [toolable](tooling/readme.md) and has a [flexible syntax](docs/readme.md#formatting-styles) that is
usually auto-formatted to look like this:

```kson
# Kson syntax note: whitespace is NOT significant!
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
    - - 0
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

### Why KSON?

One of the key things KSON wants to say is: let's keep everything that's great about YAML and JSON as "Configuration User Interfaces", and let's make those interfaces more toolable, reliable, and fun.  Here are some of the ways we do that:

- KSON is a [verified superset of JSON](https://github.com/kson-org/kson/pull/72), has [native JSON Schema support](https://github.com/kson-org/kson/pull/186), transpiles reliably to [YAML (with comments preserved!)](https://github.com/kson-org/kson/pull/80), and is likely available wherever you want it&mdash;current supported platforms: [JS](https://www.npmjs.com/package/@kson_org/kson), [Python](https://pypi.org/project/kson-lang/), [Rust](https://crates.io/crates/kson-rs), [JVM](https://central.sonatype.com/artifact/org.kson/kson-jvm), and [Kotlin Multiplatform](https://central.sonatype.com/artifact/org.kson/kson)

- KSON is also widely available in developer tools, with support for [VS Code](https://marketplace.visualstudio.com/items?itemName=kson.kson), [Jetbrains IDEs](todo add link when publishing completes), and anywhere you can plug in an [LSP](https://github.com/kson-org/kson/tree/main/tooling/language-server-protocol).

Learn more at _**[kson.org](https://kson.org)**_, try it out now in our [online playground](https://kson.org/playground/), and come join our community on [Zulip](https://kson-org.zulipchat.com/)

### Developer setup

We welcome you to dive in this project to explore, experiment, or contribute. A lot of care, craft, attention and joy went into making this project understandable and approachable. Hopefully some of that shines through when you dive in. Please feel free to ask questions on [Zulip](https://kson-org.zulipchat.com/) if you want any support finding your way around the code here.  

* Clone this repo, then run `./gradlew check` in its root dir to validate everything builds and runs correctly.
  * There should be no other dependencies needed (even [the JDK is defined and managed by the build](jdk.properties)). See [Troubleshooting setup](#troubleshooting-setup) below if you run into any issues
  * Note that the build depends on the [embedded `buildSrc/` project](buildSrc/readme.md), which is [built](buildSrc/build.gradle.kts) and [tested](buildSrc/src/test) as a prerequisite for this build

* **IntelliJ setup:** 
  * Ensure you have the Kotlin and Gradle plugins installed
  * Open the root [`build.gradle.kts`](build.gradle.kts) file directly and select "Open as Project" when prompted
  * In Settings, under `Build, Execution, Deployment -> Build Tools -> Gradle`, for `Gradle JVM`:
    * choose "Add JDK..." and select the `Contents/Home` folder of the JDK under `gradle/jdk` (this JDK is installed the first time you run `./gradlew check` from the command line)

#### Project structure

- The [root build](build.gradle.kts) of this project contains the **core Kson implementation**
  - [kson-lib/](kson-lib) defines the public interface for the Kson project. This should be the only module that depends on the root build
  - [lib-python/](lib-python) defines an idiomatic Python interface on [kson-lib/](kson-lib)
  - [lib-rust/](lib-rust) defines an idiomatic Rust interface on [kson-lib/](kson-lib)
  - [tooling/](tooling) contains tooling/editor/IDE support for Kson
- The [buildSrc/](buildSrc/build.gradle.kts) project implements non-trivial custom build components needed by this project.  It is developed as a separate, independent project. See the [buildSrc readme](buildSrc/readme.md) for details.

#### Some useful gradle commands:

```sh
# build and test
./gradlew build

# run all tests
./gradlew allTests

# build (if necessary) and run jvm tests
./gradlew jvmTest
./gradlew jvmTest --tests "org.kson.parser.*" 

# Run a test and rerun each time a source file changes.
./gradlew jvmTest --continuous --tests "org.kson.parser.LexerTest" 
```

#### Troubleshooting setup:

* **Ubuntu setup:** if an error is reported by gradle similar to `error while loading shared libraries: libtinfo.so.5`, install `libncurses-dev` with `apt install libncurses-dev`. See also [Kotlin/Native Setup instructions](https://github.com/JetBrains/kotlin-native/blob/27232bca5f2fb0164f1aa465d38e5042c6d7d55b/README.md), and [this similar issue in the Ktor project](https://youtrack.jetbrains.com/issue/KTOR-7909/Contribution-Installation-Instructions-Replace-libncurses5-with-libncurses6-for-Ubuntu-20.04-and-Later) along with [its fix, here](https://github.com/ktorio/ktor/pull/4529)

### Kson Editors and Tooling
KSON can be used in VS Code or IntelliJ with a simple gradle command.

To run either IDE you can run the following gradlew task from the root directory: 
```shell
./gradlew :tooling:lsp-clients:npm_run_vscode # starts VS Code IDE
./gradlew runIde # starts IntelliJ IDE 
```

For more information on our tooling see [tooling/](tooling/readme.md)

### Notes
Many thanks to [@munificent](https://github.com/munificent) for making all of this more accessible by writing the wonderful [**Crafting Interpeters**](https://craftinginterpreters.com/) &mdash; [Part I](https://craftinginterpreters.com/welcome.html) and [Part II](https://craftinginterpreters.com/a-tree-walk-interpreter.html) in particular are strongly recommended reading for anyone exploring this code

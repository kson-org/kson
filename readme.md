# Kson: Kson Structured Object Notion

<img src="assets/logo/kson_logo_blue.svg" alt="drawing" width="150"/>

TODO document the language, remembering to clearly note:
- string parsing: we base our string parsing closely on [Json's rules for strings](https://www.rfc-editor.org/rfc/rfc8259.html#section-7), except we allow whitespace control characters to be embedded in our strings
- embed block escapes
- list semantics: list may be bracketed or dash-delimited.  A sub-list in a dash-delimited list must be a bracketed list to avoid ambiguity, but otherwise these may be used interchangeably
- commas are optional between elements in objects and lists and may be leading or trailing.  The formatter is likely to encourage leading commas for bracket lists and no commas for objects (so we'll always have either a comma or a list dash or a keyword starting/denoting an entry in these compound elements, so each has a leading indicator of what type of element it is)
- ... todo other stuff

### Development setup

* Clone this repo, then run `./gradlew check` in its root dir to validate everything builds and runs correctly.
  * There should be no other dependencies needed (even [the JDK is defined and managed by the build](jdk.properties)). See [Troubleshooting setup](#troubleshooting-setup) below if you run into any issues
  * Note that the build depends on the [embedded `buildSrc/` project](buildSrc/readme.md), which is [built](buildSrc/build.gradle.kts) and [tested](buildSrc/src/test) as a prerequisite for this build

* **IntelliJ setup:** 
  * Ensure you have the Kotlin and Gradle plugins installed
  * Open the root [`build.gradle.kts`](build.gradle.kts) file directly and select "Open as Project" when prompted
  * In Settings, under `Build, Execution, Deployment -> Build Tools -> Gradle`, for `Gradle JVM`:
    * choose "Add JDK..." and select the `Contents/Home` folder of the JDK under `gradle/jdk` (this JDK is installed the first time you run `./gradlew check` from the command line)


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

* **Ubuntu setup:** if an error is reported by gradle similar to `error while loading shared libraries: libtinfo.so.5`, install libncurses5 with `apt install libncurses5`. See also [Kotlin/Native Setup instructions](https://github.com/JetBrains/kotlin-native/blob/27232bca5f2fb0164f1aa465d38e5042c6d7d55b/README.md).

### Kson Editors and Tooling

See [tooling/](tooling/readme.md)

### Notes
Many thanks to [@munificent](https://github.com/munificent) for making all of this more accessible by writing the wonderful [**Crafting Interpeters**](https://craftinginterpreters.com/) &mdash; [Part I](https://craftinginterpreters.com/welcome.html) and [Part II](https://craftinginterpreters.com/a-tree-walk-interpreter.html) in particular are strongly recommended reading for anyone exploring this code

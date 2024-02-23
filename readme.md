# Kson: Kson Structured Object Notion

<img src="assets/logo/kson_logo_blue.svg" alt="drawing" width="150"/>

TODO document the language, remembering to clearly note:
- string escapes
- embed block escapes
- list semantics
- ... todo other stuff

### Development setup

* Clone this repo, then run `./gradlew check` in its root dir to validate everything builds and runs correctly. 
  * There should be no other dependencies needed (even [the JDK is defined and managed by the build](https://github.com/kson-org/kson/commit/1b15a72e88759c1476a18fc2d23ef318ddf5ddda)).  See [Troubleshooting setup](#troubleshooting-setup) below if you run into any issues. 
  * Note that the build depends on the [embedded `buildSrc/` project](buildSrc/readme.md), which is [built](buildSrc/build.gradle.kts) and [tested](buildSrc/src/test) as a prerequisite for this build

* **IntelliJ setup:** ensure you have the Kotlin and Gradle plugins installed in Intellij (default in recent versions), then simply open the root [`build.gradle.kts`](build.gradle.kts) file "as a Project". Tests can be run by right-clicking the org.kson package in `commonTest` folder and selecting "Run tests in 'org.kson'".


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
* Please file a bug for all other setup/build issues

### Kson Editors and Tooling

See [tooling/](tooling/readme.md)

### Notes
Many thanks to [@munificent](https://github.com/munificent) for making all of this more accessible by writing the wonderful [**Crafting Interpeters**](https://craftinginterpreters.com/) &mdash; [Part I](https://craftinginterpreters.com/welcome.html) and [Part II](https://craftinginterpreters.com/a-tree-walk-interpreter.html) in particular are strongly recommended reading for anyone exploring this code

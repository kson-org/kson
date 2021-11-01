# Kson: Kson Structured Object Notion

### Development setup

* Clone this repo, then run `./gradlew check` in its root dir to validate everything builds and runs correctly.

* **IntelliJ setup:** ensure you have the Kotlin and Gradle plugins installed in Intellij, then simply open the root [`build.gradle.kts`](build.gradle.kts) file "as a Project"

* **Ubuntu setup:** if an error is reported by gradle similar to `error while loading shared libraries: libtinfo.so.5`, install libncurses5 with `apt install libcurses5`. See also [Kotlin/Native Setup instructions](https://github.com/JetBrains/kotlin-native/blob/27232bca5f2fb0164f1aa465d38e5042c6d7d55b/README.md).

### Notes
Many thanks to [@munificent](https://github.com/munificent) for making all of this more accessible by writing the wonderful [**Crafting Interpeters**](https://craftinginterpreters.com/) &mdash; [Part I](https://craftinginterpreters.com/welcome.html) and [Part II](https://craftinginterpreters.com/a-tree-walk-interpreter.html) in particular are strongly recommended reading for anyone exploring this code 
# Kson: Kson Structured Object Notion

TODO document the language, remembering to clearly note:
- string escapes
- embed block escapes
- ... todo other stuff

### Development setup

* Clone this repo, then run `./gradlew check` in its root dir to validate everything builds and runs correctly.

* **IntelliJ setup:** ensure you have the Kotlin and Gradle plugins installed in Intellij, then simply open the root [`build.gradle.kts`](build.gradle.kts) file "as a Project"

### Notes
Many thanks to [@munificent](https://github.com/munificent) for making all of this more accessible by writing the wonderful [**Crafting Interpeters**](https://craftinginterpreters.com/) &mdash; [Part I](https://craftinginterpreters.com/welcome.html) and [Part II](https://craftinginterpreters.com/a-tree-walk-interpreter.html) in particular are strongly recommended reading for anyone exploring this code 
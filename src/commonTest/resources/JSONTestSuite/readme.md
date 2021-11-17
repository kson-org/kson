## Embedding tests from JSONTestSuite

As discussed in https://seriot.ch/projects/parsing_json.html, striving to be a good JSON parser involves being aware of 
and correctly handling numerous specific data edge cases.

In this folder, we include the contents of [nst/JSONTestSuite](https://github.com/nst/JSONTestSuite).

Latest update is synced from commit [d64aefb](https://github.com/nst/JSONTestSuite/commit/d64aefb55228d9584d3e5b2433f720ea8fd00c82).

### Update procedure

* Acquire a recent commit of the repo as specified above (i.e., clone, pull, download .tgz).
* Update your local `test_parsing` folder and `parsing-test-cases-list.txt` file:
```sh
# cwd in this folder
> rm -rf ./test_parsing
> cp -Rp $PATH_TO_JSONTestSuite_REPO/test_parsing ./test_parsing
>  ls -1 test_parsing/*.json | grep --invert-match --file ./parsing-test-cases-list-skipped.txt --fixed-strings --line-regexp - > parsing-test-cases-list.txt
```
* Update the SHA of the tests you were using above in this file.

### Skipping new parsing tests

There are some tests that we must skip initially and/or perpetually. This should be done by including the path to the test file in `parsing-test-cases-list-skipped.txt` and rerunning the above script. A comment should proceed each skipped test to explain why this is intentionally skipped and whether there is an associated tracking issue to fix it going forward.

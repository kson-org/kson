// Minimal C extension to mark wheel as platform-specific
#include <Python.h>
static struct PyModuleDef m = {PyModuleDef_HEAD_INIT, "_marker", "", -1, NULL};
PyMODINIT_FUNC PyInit__marker(void) {return PyModule_Create(&m);}
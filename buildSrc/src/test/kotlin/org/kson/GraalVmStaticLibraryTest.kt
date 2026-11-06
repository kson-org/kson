package org.kson

import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GraalVmStaticLibraryTest {

    @Test
    fun readsTheLinkInputsNativeImageEmbedsInTheObject() {
        // the strings sit among binary data, each terminated by a NUL
        val imageObject = (
            "\u0000\u0001junk" +
                "com.oracle.svm.core.VM.Target.Libraries=dl|pthread|-framework Foundation|z\u0000" +
                "ÿmore junk" +
                "com.oracle.svm.core.VM.Target.StaticLibraries=liblibchelper.a|libjvm.a\u0000tail"
            ).toByteArray(Charsets.ISO_8859_1)

        assertEquals(
            GraalVmStaticLibrary.TargetLibraries(
                staticLibraries = listOf("liblibchelper.a", "libjvm.a"),
                libraries = listOf("dl", "pthread", "-framework Foundation", "z"),
            ),
            GraalVmStaticLibrary.targetLibraries(imageObject)
        )
    }

    @Test
    fun failsLoudlyWhenNativeImageStopsRecordingItsLinkInputs() {
        assertFailsWith<GradleException> {
            GraalVmStaticLibrary.targetLibraries("no markers here".toByteArray())
        }
    }

    @Test
    fun translatesLibrariesIntoCargoLinkLibSyntax() {
        assertEquals("z", GraalVmStaticLibrary.cargoLinkLib("z"))
        assertEquals("framework=Foundation", GraalVmStaticLibrary.cargoLinkLib("-framework Foundation"))
    }

    @Test
    fun readsExportedSymbolsFromAMacOsSymbolList() {
        assertEquals(
            listOf("_graal_create_isolate", "_JNI_CreateJavaVM"),
            GraalVmStaticLibrary.exportedSymbols("_graal_create_isolate\n_JNI_CreateJavaVM\n\n")
        )
    }

    @Test
    fun readsExportedSymbolsFromALinuxVersionScript() {
        // as native-image writes it: names quoted, one per line
        assertEquals(
            listOf("graal_create_isolate", "JNI_CreateJavaVM"),
            GraalVmStaticLibrary.exportedSymbols("{\nglobal:\n\"graal_create_isolate\";\n\"JNI_CreateJavaVM\";\nlocal: *;\n};\n")
        )
    }
}

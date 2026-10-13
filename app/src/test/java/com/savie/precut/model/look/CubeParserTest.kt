package com.savie.precut.model.look

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CubeParserTest {

    private fun identity2(header: String = "") = """
        # A comment
        TITLE "Identity"
        $header
        LUT_3D_SIZE 2
        0 0 0
        1 0 0
        0 1 0
        1 1 0
        0 0 1
        1 0 1
        0 1 1
        1 1 1
    """.trimIndent()

    @Test
    fun parsesValidCube() {
        val cube = CubeParser.parse(identity2("DOMAIN_MIN 0 0 0\nDOMAIN_MAX 1 1 1")).getOrThrow()
        assertEquals(2, cube.size)
        assertEquals(1f, cube.lookup(1f, 0f, 0f).r, 1e-6f)
        assertEquals(0.5f, cube.lookup(0.5f, 0.5f, 0.5f).g, 1e-6f)
    }

    @Test
    fun rejectsBadFiles() {
        assertTrue(CubeParser.parse("LUT_1D_SIZE 2\n0 0 0\n1 1 1").isFailure)
        assertTrue(CubeParser.parse(identity2().removeSuffix("1 1 1")).isFailure) // too few rows
        assertTrue(CubeParser.parse(identity2().replace("LUT_3D_SIZE 2", "LUT_3D_SIZE 3")).isFailure)
        assertTrue(CubeParser.parse(identity2("DOMAIN_MAX 2 2 2")).isFailure)
        assertTrue(CubeParser.parse("hello world").isFailure)
        assertTrue(CubeParser.parse("").isFailure)
    }
}

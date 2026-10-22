package com.savie.precut.media

import com.savie.precut.media.effect.EffectShaders
import com.savie.precut.media.layer.LayerShaders
import com.savie.precut.media.look.LookShaders
import com.savie.precut.media.motion.TransitionShaders
import com.savie.precut.media.motion.uniforms
import com.savie.precut.model.effect.EffectParams
import com.savie.precut.model.effect.EffectSettings
import com.savie.precut.model.effect.EffectType
import com.savie.precut.model.motion.MotionMath
import com.savie.precut.model.motion.TransitionType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every GLSL program PreCut builds per effect and per overlapping transition is well-formed, and
 * is written with the values the app feeds it to `build/shaders`, where `tools/shader_check.py`
 * compiles and renders each one on the computer's own GPU.
 */
class ShaderSourcesTest {

    private val out = File("build/shaders").apply { mkdirs() }

    @Test
    fun everyEffect_hasItsOwnWellFormedProgram() {
        EffectType.entries.forEach { type ->
            val source = EffectShaders.fragment(type)
            assertWellFormed(type.name, source)
            File(out, "effect_${type.name}.frag").writeText(source)
        }
        // Each program holds one body, so two effects never share a source.
        assertEquals(EffectType.entries.size - 3, EffectType.entries.map(EffectShaders::fragment).toSet().size) // the four split screens share one
    }

    @Test
    fun everyOverlappingTransition_hasItsOwnWellFormedProgram() {
        val overlapping = TransitionType.entries.filter { it.overlapping }
        overlapping.forEach { type ->
            val source = LayerShaders.transition(type)
            assertWellFormed(type.name, source)
            File(out, "transition_${type.name}.frag").writeText(source)
        }
        assertEquals(overlapping.size, overlapping.map(LayerShaders::transition).toSet().size)
    }

    @Test
    fun writesTheSharedShadersAndTheirValues() {
        assertWellFormed("edge", TransitionShaders.FRAGMENT)
        File(out, "edge.frag").writeText(TransitionShaders.FRAGMENT)
        File(out, "edge.vert").writeText(TransitionShaders.VERTEX)
        File(out, "effect.vert").writeText(LookShaders.VERTEX)
        File(out, "layer.vert").writeText(LayerShaders.VERTEX)
        val effects = EffectType.entries.associate { type ->
            type.name to floats(EffectParams.of(EffectSettings(type)))
        }
        val edges = TransitionType.entries.filter { !it.overlapping }.associate { type ->
            type.name to JsonObject(
                EDGE_PROGRESS.associate { progress ->
                    val look = MotionMath.transitionLook(type, progress)
                    // The app changes the glitch seed every 1/15 s; any fixed one shows the look.
                    val uniforms = look.uniforms() + ("uGlitch" to floatArrayOf(look.glitch, 7f))
                    progress.toString() to JsonObject(uniforms.mapValues { (_, v) -> floats(v) })
                },
            )
        }
        File(out, "values.json").writeText(JsonObject(mapOf("effects" to JsonObject(effects), "edges" to JsonObject(edges))).toString())
    }

    private fun floats(values: FloatArray) = JsonArray(values.map { JsonPrimitive(it) })

    private fun assertWellFormed(name: String, source: String) {
        assertTrue(name, source.startsWith("#version 100\n"))
        assertTrue(name, "void main()" in source)
        assertEquals("$name: braces", source.count { it == '{' }, source.count { it == '}' })
        assertEquals("$name: parentheses", source.count { it == '(' }, source.count { it == ')' })
        // Kotlin templates never reach the GPU.
        assertFalse(name, '$' in source)
    }

    private companion object {
        val EDGE_PROGRESS = listOf(0.2f, 0.4f, 0.5f, 0.6f, 0.8f)
    }
}

package com.savie.precut.model.ai.edit

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.util.Locale
import kotlin.math.roundToLong

/** A tool call that can't be done; the message goes back to Claude. */
internal class ToolError(message: String) : Exception(message)

internal fun fail(message: String): Nothing = throw ToolError(message)

/** A tool call's input, read defensively (the API checks the schema, but not ranges or ids). */
internal class ToolInput(private val json: JsonObject) {

    fun string(name: String): String = stringOrNull(name) ?: fail("$name is missing")

    fun stringOrNull(name: String): String? = when (val value = json[name]) {
        null, JsonNull -> null
        is JsonPrimitive -> if (value.isString) value.content else fail("$name must be text")
        else -> fail("$name must be text")
    }

    fun number(name: String): Double = numberOrNull(name) ?: fail("$name is missing")

    fun numberOrNull(name: String): Double? = when (val value = json[name]) {
        null, JsonNull -> null
        is JsonPrimitive -> value.takeUnless { it.isString }?.doubleOrNull?.takeIf { it.isFinite() } ?: fail("$name must be a number")
        else -> fail("$name must be a number")
    }

    fun booleanOrNull(name: String): Boolean? = when (val value = json[name]) {
        null, JsonNull -> null
        is JsonPrimitive -> value.takeUnless { it.isString }?.booleanOrNull ?: fail("$name must be true or false")
        else -> fail("$name must be true or false")
    }

    fun strings(name: String): List<String> {
        val array = json[name] as? JsonArray ?: fail("$name must be a list")
        return array.map { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content ?: fail("$name must hold text ids") }
    }
}

/** Seconds as whole milliseconds. */
internal fun ms(seconds: Double): Long = (seconds * 1_000).roundToLong()

/** [value] when it's within [range], else a message naming [name] and the range. */
internal fun inRange(name: String, value: Double, range: ClosedFloatingPointRange<Double>): Double =
    if (value in range) value else fail("$name must be from ${plain(range.start)} to ${plain(range.endInclusive)}")

/** 2.0 as "2", 0.1f widened to a Double as "0.1": at most three decimals, no trailing zeros. */
internal fun plain(value: Double): String = String.format(Locale.ROOT, "%.3f", value).trimEnd('0').trimEnd('.')

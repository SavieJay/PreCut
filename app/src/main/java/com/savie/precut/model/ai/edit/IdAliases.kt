package com.savie.precut.model.ai.edit

/** What an alias names; its letter starts the alias. */
enum class AliasKind(val prefix: Char) { CLIP('c'), OVERLAY('o'), TEXT('t'), AUDIO('a') }

/**
 * Short ids ("c1", "t2") for the project's long ones, so the brief and Claude's tool calls stay
 * small and easy to copy. Grows as tools make new items, and never forgets one, so a change can be
 * run again (for review) with the same ids.
 */
class IdAliases {
    private val reals = HashMap<String, String>()
    private val aliases = HashMap<String, String>()
    private val counts = IntArray(AliasKind.entries.size)

    /** The alias of [realId], made on first use. */
    fun alias(realId: String, kind: AliasKind): String = aliases.getOrPut(realId) {
        val alias = "${kind.prefix}${++counts[kind.ordinal]}"
        reals[alias] = realId
        alias
    }

    /** The real id behind [alias] (an unknown alias, or a real id passed by mistake, gives null). */
    fun real(alias: String): String? = reals[alias]
}

package com.savie.precut.model.onboarding

import com.savie.precut.model.AspectRatio
import com.savie.precut.model.template.TemplateCategory
import kotlinx.serialization.Serializable

/** Onboarding's "What will you create?" (Phase 24). Saved by name: never rename. */
@Serializable
enum class CreationGoal { SOCIAL, YOUTUBE, MEMORIES, BUSINESS }

/** Onboarding's "Your editing experience". */
@Serializable
enum class Experience { NEW, SOME, PRO }

/**
 * The aspect ratio new projects start in for [goals]: social, family and travel clips are mostly
 * filmed and watched upright, YouTube is wide, and brand posts are square. Social wins a mix, as
 * the most common; null keeps the current default.
 */
fun defaultAspectRatio(goals: Set<CreationGoal>): AspectRatio? = when {
    CreationGoal.SOCIAL in goals -> AspectRatio.RATIO_9_16
    CreationGoal.YOUTUBE in goals -> AspectRatio.RATIO_16_9
    CreationGoal.BUSINESS in goals -> AspectRatio.RATIO_1_1
    CreationGoal.MEMORIES in goals -> AspectRatio.RATIO_9_16
    else -> null
}

/** Template categories to show first for [goals], most relevant first, without repeats. */
fun preferredTemplateCategories(goals: Set<CreationGoal>): List<TemplateCategory> =
    CreationGoal.entries.filter { it in goals }.flatMap { goal ->
        when (goal) {
            CreationGoal.SOCIAL -> listOf(TemplateCategory.SOCIAL, TemplateCategory.VLOG)
            CreationGoal.YOUTUBE -> listOf(TemplateCategory.VLOG)
            CreationGoal.MEMORIES -> listOf(TemplateCategory.TRAVEL, TemplateCategory.CELEBRATION, TemplateCategory.SLIDESHOW)
            CreationGoal.BUSINESS -> listOf(TemplateCategory.BUSINESS)
        }
    }.distinct()

/** [items] with those in [preferred] categories first (in that order), the rest after; stable otherwise. */
fun <T> List<T>.preferringCategories(preferred: List<TemplateCategory>, category: (T) -> TemplateCategory): List<T> =
    sortedBy { item -> preferred.indexOf(category(item)).let { if (it < 0) preferred.size else it } }

/** New editors start in Simple mode: fewer tools on the toolbar, the rest under More. */
fun simpleModeFor(experience: Experience): Boolean = experience == Experience.NEW

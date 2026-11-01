package com.savie.precut.ui.editor

import com.savie.precut.data.AppSettings
import com.savie.precut.model.Project
import com.savie.precut.model.brand.isBranded
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.pro.ProAccess
import com.savie.precut.model.pro.ProFeature

/** The brand kit (Phase 26B, Pro) in the editor: one tap applies it; new projects get it when set to. */
class EditorBrand(private val host: EditorHost, private val pro: EditorPro, private val settings: AppSettings) {

    fun onApply() = pro.gate(ProFeature.BRAND_KIT) {
        val kit = settings.values.value.brandKit
        if (kit.isEmpty) {
            host.send(EditorEvent.BrandKitEmpty)
            return@gate
        }
        host.applyEdit(EditAction.ApplyBrandKit(kit, host.newId().take(8), settings.values.value.photoDurationMs))
        host.send(EditorEvent.BrandApplied)
    }

    /** A project opened fresh (never edited) gets the kit when "Apply to new projects" is on, for Pro. */
    fun onOpened(project: Project) {
        val kit = settings.values.value.brandKit
        val fresh = project.createdAt == project.updatedAt && !project.isBranded()
        if (kit.applyToNew && !kit.isEmpty && fresh && pro.access(ProFeature.BRAND_KIT) == ProAccess.Unlocked) {
            host.applyEdit(EditAction.ApplyBrandKit(kit, host.newId().take(8), settings.values.value.photoDurationMs))
        }
    }
}

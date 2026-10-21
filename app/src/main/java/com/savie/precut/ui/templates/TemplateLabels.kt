package com.savie.precut.ui.templates

import androidx.annotation.StringRes
import com.savie.precut.R
import com.savie.precut.model.template.TemplateCategory

val TemplateCategory.label: Int
    @StringRes get() = when (this) {
        TemplateCategory.TRAVEL -> R.string.template_category_travel
        TemplateCategory.SOCIAL -> R.string.template_category_social
        TemplateCategory.BUSINESS -> R.string.template_category_business
        TemplateCategory.CELEBRATION -> R.string.template_category_celebration
        TemplateCategory.SLIDESHOW -> R.string.template_category_slideshow
        TemplateCategory.VLOG -> R.string.template_category_vlog
        TemplateCategory.OTHER -> R.string.template_category_other
    }

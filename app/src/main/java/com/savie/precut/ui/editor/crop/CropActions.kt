package com.savie.precut.ui.editor.crop

import com.savie.precut.model.frame.CropRatio
import com.savie.precut.model.frame.CropRect

/** What crop mode (T-12b) can do with its working copy. */
interface CropActions {
    fun onChange(crop: CropRect)
    fun onRatio(ratio: CropRatio)
    fun onReset()
    fun onMirror()
    fun onRotate()
    fun onCancel()
    fun onApply()
}

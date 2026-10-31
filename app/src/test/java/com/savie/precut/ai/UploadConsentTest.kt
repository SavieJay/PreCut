package com.savie.precut.ai

import com.savie.precut.model.ai.AiFeature
import com.savie.precut.model.ai.UploadSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadConsentTest {

    @Test
    fun oneUpload_onceAndOnlyOfThatSize() {
        val consent = UploadConsent.givenFor(UploadSummary(AiFeature.CAPTIONS, 2_000L, 100L))
        assertFalse(consent.spendOn(99L))
        assertTrue(consent.spendOn(100L))
        assertFalse(consent.spendOn(100L))
    }

    @Test
    fun parts_inOrderAndNoMore() {
        val consent = UploadConsent.givenFor(UploadSummary(AiFeature.CAPTIONS, 1_200_000L, 300L, partBytes = listOf(200L, 100L)))
        assertFalse(consent.spendOn(100L))
        assertTrue(consent.spendOn(200L))
        assertTrue(consent.spendOn(100L))
        assertFalse(consent.spendOn(100L))
    }
}

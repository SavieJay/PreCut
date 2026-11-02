package com.savie.precut.model.growth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstallReferrerTest {
    @Test
    fun `a template page's install referrer names the template`() {
        assertEquals("AbCd1234", templateFromReferrer("utm_source=website&utm_medium=template&utm_campaign=template&template=AbCd1234"))
        assertEquals("AbCd1234", templateFromReferrer("template=AbCd1234"))
    }

    @Test
    fun `other referrers, odd ids and none open nothing`() {
        assertNull(templateFromReferrer(null))
        assertNull(templateFromReferrer(""))
        assertNull(templateFromReferrer("utm_source=google-play&utm_medium=organic"))
        assertNull(templateFromReferrer("utm_source=website&utm_medium=hero&utm_campaign=hero"))
        assertNull(templateFromReferrer("template=abc"))
        assertNull(templateFromReferrer("template=AbCd1234%2F..%2F"))
        assertNull(templateFromReferrer("template=%zz"))
        assertNull(templateFromReferrer("mytemplate=AbCd1234"))
    }
}

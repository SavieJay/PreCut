package com.savie.precut.model.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 30C: every way the phone's copy and the server's can meet. */
class SyncPlanTest {

    private fun local(revision: Int?, dirty: Boolean, deleted: Boolean = false) = LocalCopy("p", revision, dirty, deleted)
    private fun remote(revision: Int, deleted: Boolean = false) = RemoteCopy("p", revision, deleted)

    @Test
    fun `new on the server - taken, unless it's a deletion`() {
        assertEquals(Incoming.Take, planIncoming(null, remote(1)))
        assertEquals(Incoming.Ignore, planIncoming(null, remote(2, deleted = true)))
    }

    @Test
    fun `our own save coming back, or an older change, is ignored`() {
        assertEquals(Incoming.Ignore, planIncoming(local(3, dirty = false), remote(3)))
        assertEquals(Incoming.Ignore, planIncoming(local(3, dirty = true), remote(2)))
    }

    @Test
    fun `unchanged here - the server's change wins, deletions too`() {
        assertEquals(Incoming.Take, planIncoming(local(1, dirty = false), remote(2)))
        assertEquals(Incoming.DropLocal, planIncoming(local(1, dirty = false), remote(2, deleted = true)))
    }

    @Test
    fun `changed on both sides - both copies are kept`() {
        assertEquals(Incoming.KeepBoth, planIncoming(local(1, dirty = true), remote(2)))
        // Never uploaded here, yet the server has one with that id (another phone of the account).
        assertEquals(Incoming.KeepBoth, planIncoming(local(null, dirty = true), remote(1)))
    }

    @Test
    fun `an edit and a deletion - the edit wins, whichever side made it`() {
        assertEquals(Incoming.KeepLocal(2), planIncoming(local(1, dirty = true), remote(2, deleted = true)))
        assertEquals(Incoming.Take, planIncoming(local(1, dirty = true, deleted = true), remote(2)))
        assertEquals(Incoming.DropLocal, planIncoming(local(1, dirty = true, deleted = true), remote(2, deleted = true)))
    }

    @Test
    fun `sending - saves build on the revision the phone has, deletions too`() {
        assertEquals(Outgoing.Upload(null), planOutgoing(local(null, dirty = true)))
        assertEquals(Outgoing.Upload(4), planOutgoing(local(4, dirty = true)))
        assertEquals(Outgoing.DeleteRemote(4), planOutgoing(local(4, dirty = true, deleted = true)))
        assertEquals(Outgoing.Forget, planOutgoing(local(null, dirty = true, deleted = true)))
    }

    @Test
    fun `the phone's copy is named after the project`() {
        assertEquals("Trip (this phone)", keptCopyName("Trip", "this phone"))
    }
}

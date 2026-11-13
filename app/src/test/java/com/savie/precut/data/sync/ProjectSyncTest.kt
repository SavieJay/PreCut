package com.savie.precut.data.sync

import com.savie.precut.auth.AccessTokens
import com.savie.precut.data.ProjectSerializer
import com.savie.precut.model.Project
import com.savie.precut.model.account.AuthFailure
import com.savie.precut.model.account.AuthResult
import com.savie.precut.model.sync.LocalCopy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 30C: the sync engine against an in-memory server and phone. */
class ProjectSyncTest {

    private val tokens = Tokens()
    private val server = Server()
    private val phone = Phone()
    private val cursors = Cursors()
    private var ids = 0
    private val sync = ProjectSync(tokens, server, phone, cursors, conflictLabel = { "this phone" }, newId = { "copy-${++ids}" })

    private fun project(id: String, name: String, updatedAt: Long = 1_000) =
        Project(id = id, name = name, clips = emptyList(), createdAt = 0, updatedAt = updatedAt)

    @Test
    fun `projects made before signing in join the account and upload`() = runBlocking {
        phone.put(project("a", "Trip"), owner = null)
        assertEquals(SyncOutcome.Done, sync.run())
        assertEquals("user-1", phone.rows.getValue("a").owner)
        assertEquals(false, phone.rows.getValue("a").dirty)
        assertEquals(1, phone.rows.getValue("a").revision)
        assertEquals("Trip", server.projects.getValue("a").name)
        assertEquals(null, server.saves.single().second.baseRevision)
    }

    @Test
    fun `projects from another phone come down, and the cursor moves on`() = runBlocking {
        server.put(project("b", "Beach"), revision = 3)
        assertEquals(SyncOutcome.Done, sync.run())
        val row = phone.rows.getValue("b")
        assertEquals("Beach", row.project?.name)
        assertEquals(3, row.revision)
        assertEquals(false, row.dirty)
        // Started a minute before the last change, so a late one isn't missed.
        assertEquals("2026-11-13T09:59:00Z", cursors.saved?.since)
        assertTrue(server.saves.isEmpty())
    }

    @Test
    fun `an edit here goes up from the revision the phone has`() = runBlocking {
        server.put(project("c", "Cafe"), revision = 1)
        sync.run()
        phone.edit("c", "Cafe 2", updatedAt = 2_000)
        sync.run()
        assertEquals(1, server.saves.last().second.baseRevision)
        assertEquals("Cafe 2", server.projects.getValue("c").name)
        assertEquals(2, phone.rows.getValue("c").revision)
        assertEquals(false, phone.rows.getValue("c").dirty)
    }

    @Test
    fun `changed on two phones - both copies are kept`() = runBlocking {
        server.put(project("d", "Dance"), revision = 1)
        sync.run()
        phone.edit("d", "Dance (mine)", updatedAt = 2_000)
        server.put(project("d", "Dance (theirs)"), revision = 2) // another phone saved first
        sync.run()
        assertEquals("Dance (theirs)", phone.rows.getValue("d").project?.name)
        assertEquals(false, phone.rows.getValue("d").dirty)
        val copy = phone.rows.getValue("copy-1")
        assertEquals("Dance (mine) (this phone)", copy.project?.name)
        // The copy is a new project and went up too.
        assertEquals("Dance (mine) (this phone)", server.projects.getValue("copy-1").name)
    }

    @Test
    fun `a stale save refused by the server keeps both copies too`() = runBlocking {
        server.put(project("e", "Eve"), revision = 1)
        sync.run()
        phone.edit("e", "Eve (mine)", updatedAt = 2_000)
        server.hideChanges = true // the change isn't in the pull, only in the refusal
        server.put(project("e", "Eve (theirs)"), revision = 2)
        sync.run()
        assertEquals("Eve (theirs)", phone.rows.getValue("e").project?.name)
        assertEquals("Eve (mine) (this phone)", phone.rows.getValue("copy-1").project?.name)
    }

    @Test
    fun `deletions travel both ways`() = runBlocking {
        server.put(project("f", "Fair"), revision = 1)
        server.put(project("g", "Garden"), revision = 1)
        sync.run()
        phone.delete("f")
        server.delete("g")
        sync.run()
        assertNull(phone.rows["f"])
        assertTrue(server.projects.getValue("f").deleted)
        assertNull(phone.rows["g"])
        // A project deleted before it ever went up just goes.
        phone.put(project("h", "Hill"), owner = "user-1")
        phone.delete("h")
        sync.run()
        assertNull(phone.rows["h"])
        assertNull(server.projects["h"])
    }

    @Test
    fun `an edit here beats a deletion elsewhere`() = runBlocking {
        server.put(project("i", "Island"), revision = 1)
        sync.run()
        phone.edit("i", "Island 2", updatedAt = 2_000)
        server.delete("i") // revision 2
        sync.run()
        assertEquals("Island 2", server.projects.getValue("i").name)
        assertEquals(false, server.projects.getValue("i").deleted)
        assertEquals(2, server.saves.last().second.baseRevision)
    }

    @Test
    fun `offline stops for a retry, and nothing is lost`() = runBlocking {
        phone.put(project("j", "Jazz"), owner = "user-1")
        server.offline = true
        assertEquals(SyncOutcome.Retry(AuthFailure.Offline), sync.run())
        assertEquals(true, phone.rows.getValue("j").dirty)
        server.offline = false
        assertEquals(SyncOutcome.Done, sync.run())
        assertEquals(false, phone.rows.getValue("j").dirty)
    }

    @Test
    fun `signed out, nothing happens`() = runBlocking {
        tokens.userId = null
        phone.put(project("k", "Kite"), owner = null)
        assertEquals(SyncOutcome.NotSignedIn, sync.run())
        assertNull(phone.rows.getValue("k").owner)
    }

    private class Tokens : AccessTokens {
        override var userId: String? = "user-1"
        override suspend fun <T> withToken(call: suspend (String) -> AuthResult<T>): AuthResult<T> = call("token")
    }

    private class Cursors : SyncCursors {
        var saved: Cursor? = null
        override fun get(userId: String) = saved
        override fun set(userId: String, cursor: Cursor?) {
            saved = cursor
        }
    }

    /** The server: revisions, deletions, and changes in order. */
    private class Server : ProjectSyncApi {
        val projects = mutableMapOf<String, RemoteProject>()
        val saves = mutableListOf<Pair<String, ProjectUpload>>()
        private val order = mutableListOf<String>()
        var offline = false
        var hideChanges = false

        fun put(project: Project, revision: Int) {
            projects[project.id] = RemoteProject(project.id, revision, project.name, json(project))
            touch(project.id)
        }

        fun delete(id: String) {
            val current = projects.getValue(id)
            projects[id] = current.copy(revision = current.revision + 1, project = null, deleted = true)
            touch(id)
        }

        private fun touch(id: String) {
            order.remove(id)
            order.add(id)
        }

        override suspend fun changes(token: String, cursor: Cursor?): AuthResult<ChangesPage> {
            if (offline) return AuthResult.Failed(AuthFailure.Offline)
            if (hideChanges) return AuthResult.Ok(ChangesPage())
            val list = order.map { projects.getValue(it) }
            return AuthResult.Ok(ChangesPage(list, if (list.isEmpty()) null else Cursor("2026-11-13T10:00:00Z", list.last().id), more = false))
        }

        override suspend fun save(token: String, id: String, upload: ProjectUpload): AuthResult<SaveReply> {
            if (offline) return AuthResult.Failed(AuthFailure.Offline)
            val current = projects[id]
            if (current?.revision != upload.baseRevision) return AuthResult.Ok(SaveReply.Conflict(current))
            saves += id to upload
            val revision = (current?.revision ?: 0) + 1
            projects[id] = RemoteProject(id, revision, upload.name, upload.project)
            touch(id)
            return AuthResult.Ok(SaveReply.Saved(revision))
        }

        override suspend fun delete(token: String, id: String, base: Int): AuthResult<SaveReply> {
            val current = projects[id] ?: return AuthResult.Ok(SaveReply.Saved(0))
            if (current.revision != base) return AuthResult.Ok(SaveReply.Conflict(current))
            delete(id)
            return AuthResult.Ok(SaveReply.Saved(projects.getValue(id).revision))
        }

        private fun json(project: Project) = Json.parseToJsonElement(ProjectSerializer.encode(project)) as JsonObject
    }

    private data class Row(val project: Project?, val owner: String?, val revision: Int?, val dirty: Boolean, val updatedAt: Long)

    /** The phone's projects, as Room keeps them. */
    private class Phone : ProjectSyncStore {
        val rows = mutableMapOf<String, Row>()

        fun put(project: Project, owner: String?) {
            rows[project.id] = Row(project, owner, null, true, project.updatedAt)
        }

        fun edit(id: String, name: String, updatedAt: Long) {
            val row = rows.getValue(id)
            rows[id] = row.copy(project = row.project?.copy(name = name, updatedAt = updatedAt), dirty = true, updatedAt = updatedAt)
        }

        fun delete(id: String) {
            rows[id] = rows.getValue(id).copy(project = null, dirty = true)
        }

        override suspend fun claim(owner: String) {
            rows.replaceAll { _, row -> if (row.owner == null) row.copy(owner = owner, dirty = true) else row }
        }

        override suspend fun localCopy(id: String) = rows[id]?.let { LocalCopy(id, it.revision, it.dirty, it.project == null) }

        override suspend fun dirty(owner: String) = rows.filter { it.value.dirty && it.value.owner == owner }
            .map { (id, row) -> DirtyProject(id, row.project, row.revision, row.updatedAt) }

        override suspend fun takeRemote(owner: String, project: Project, revision: Int) {
            rows[project.id] = Row(project, owner, revision, false, project.updatedAt)
        }

        override suspend fun keepAsNew(id: String, newId: String, rename: (String) -> String) {
            val row = rows.getValue(id)
            val project = checkNotNull(row.project)
            rows[newId] = Row(project.copy(id = newId, name = rename(project.name)), row.owner, null, true, row.updatedAt)
        }

        override suspend fun markSynced(id: String, revision: Int, updatedAt: Long) {
            val row = rows.getValue(id)
            rows[id] = row.copy(revision = revision, dirty = row.updatedAt != updatedAt)
        }

        override suspend fun rebase(id: String, revision: Int?) {
            rows[id] = rows.getValue(id).copy(revision = revision)
        }

        override suspend fun remove(id: String) {
            rows.remove(id)
        }
    }
}

package com.savie.precut.model.sync

/**
 * Cloud project sync (Phase 30C): what to do with each project, from the phone's copy and the
 * server's. Pure, so every case is unit-tested. Nothing is ever lost: when both sides changed,
 * both copies are kept.
 */

/** The phone's copy as sync sees it. [serverRevision] is null for one the server never had. */
data class LocalCopy(val id: String, val serverRevision: Int?, val dirty: Boolean, val deleted: Boolean)

/** A change the server sent (or answered a refused save with). */
data class RemoteCopy(val id: String, val revision: Int, val deleted: Boolean)

sealed interface Incoming {
    /** Already have it (our own save coming back, or an older change). */
    data object Ignore : Incoming

    /** Take the server's copy (a new project, or a newer version of an unchanged one). */
    data object Take : Incoming

    /** Deleted on the server and unchanged here: delete it here too. */
    data object DropLocal : Incoming

    /** Changed on both sides: the phone's copy moves to a new project, then take the server's. */
    data object KeepBoth : Incoming

    /** Edited here but deleted elsewhere: the edit wins and brings it back ([revision] is the deletion's). */
    data class KeepLocal(val revision: Int) : Incoming
}

fun planIncoming(local: LocalCopy?, remote: RemoteCopy): Incoming {
    if (local == null) return if (remote.deleted) Incoming.Ignore else Incoming.Take
    val known = local.serverRevision
    if (known != null && remote.revision <= known) return Incoming.Ignore
    return when {
        !local.dirty -> if (remote.deleted) Incoming.DropLocal else Incoming.Take
        // Deleted here, changed elsewhere: the change wins (deleting it here is undone).
        local.deleted -> if (remote.deleted) Incoming.DropLocal else Incoming.Take
        remote.deleted -> Incoming.KeepLocal(remote.revision)
        else -> Incoming.KeepBoth
    }
}

sealed interface Outgoing {
    /** Save it, from the revision the phone has ([base], null for a new one). */
    data class Upload(val base: Int?) : Outgoing

    /** Delete it on the server, from [base]. */
    data class DeleteRemote(val base: Int) : Outgoing

    /** Deleted before the server ever had it: just forget it here. */
    data object Forget : Outgoing
}

fun planOutgoing(local: LocalCopy): Outgoing = when {
    !local.deleted -> Outgoing.Upload(local.serverRevision)
    local.serverRevision == null -> Outgoing.Forget
    else -> Outgoing.DeleteRemote(local.serverRevision)
}

/** The name of the phone's copy when both sides changed, e.g. "Trip (this phone)". */
fun keptCopyName(name: String, label: String): String = "$name ($label)"

/** The next sync starts a minute before where the last one ended: a change a slow save committed late isn't missed. */
const val CURSOR_OVERLAP_MS = 60_000L

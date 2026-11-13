package com.savie.precut.auth

import com.savie.precut.ai.cloud.CloudConfig
import com.savie.precut.ai.cloud.InstallStore
import com.savie.precut.model.account.AuthFailure
import com.savie.precut.model.account.AuthResult
import com.savie.precut.model.account.AuthState
import com.savie.precut.model.account.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/** Phase 30: signing in, the session's life, and the account routes, against a local server. */
class AccountRepositoryTest {

    private val server = MockWebServer()
    private val store = MemorySessions()
    private val installs = Installs()
    private val now = Instant.ofEpochSecond(1_000_000)

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun config(key: String = "sb_publishable_test") =
        AuthConfig.fromCloudUrl(server.url("/functions/v1/cloud").toString(), key, "web-client")

    private fun repository(config: AuthConfig = config()): AccountRepository {
        val http = OkHttpClient()
        val cloud = CloudConfig(server.url("/functions/v1/cloud").toString(), "", 1L)
        return AccountRepository(
            config,
            AuthClient(config, http, Dispatchers.IO),
            GoogleSignIn(config),
            AccountApi(cloud, http, Dispatchers.IO),
            store,
            installs,
            Clock.fixed(now, ZoneOffset.UTC),
            CoroutineScope(Dispatchers.Unconfined),
        )
    }

    private fun reply(code: Int, body: String = "{}") =
        server.enqueue(MockResponse(code, Headers.headersOf("Content-Type", "application/json"), body))

    private fun next() = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "no request was made" }

    private fun session(expiresAt: Long, access: String = "access-1", refresh: String = "refresh-1") =
        """{"access_token":"$access","refresh_token":"$refresh","expires_at":$expiresAt,"expires_in":3600,"token_type":"bearer","user":{"id":"user-1","email":"ana@example.com","aud":"authenticated"}}"""

    @Test
    fun `a build without account keys never asks to sign in`() {
        assertEquals(AuthState.NotSetUp, repository(config(key = "")).state.value)
        assertEquals(AuthState.SignedOut, repository().state.value)
        assertEquals("http://${server.hostName}:${server.port}", config().supabaseUrl)
    }

    @Test
    fun `an email code signs in, keeps the session and links this install`() = runBlocking {
        installs.token = "install-token"
        val account = repository()
        reply(200)
        assertEquals(AuthResult.Ok(Unit), account.sendCode(" ana@example.com "))
        val otp = next()
        assertEquals("/auth/v1/otp", otp.url.encodedPath)
        assertEquals("sb_publishable_test", otp.headers["apikey"])
        assertEquals("""{"email":"ana@example.com","create_user":true}""", otp.body?.utf8())

        reply(200, session(now.epochSecond + 3600))
        reply(200, """{"linked":true}""")
        assertEquals(AuthResult.Ok(Unit), account.verifyCode("ana@example.com", "123456"))
        assertEquals("""{"type":"email","email":"ana@example.com","token":"123456"}""", next().body?.utf8())
        assertEquals(AuthState.SignedIn("user-1", "ana@example.com"), account.state.value)
        assertEquals("access-1", store.saved?.accessToken)
        val link = next()
        assertEquals("/functions/v1/cloud/account/link", link.url.encodedPath)
        assertEquals("Bearer access-1", link.headers["Authorization"])
        assertEquals("install-token", link.headers["X-Install-Token"])
        // A new launch starts signed in.
        assertEquals(AuthState.SignedIn("user-1", "ana@example.com"), repository().state.value)
    }

    @Test
    fun `a wrong code says so and stays signed out`() = runBlocking {
        val account = repository()
        reply(403, """{"code":403,"error_code":"otp_expired","msg":"Token has expired or is invalid"}""")
        assertEquals(AuthResult.Failed(AuthFailure.BadCode), account.verifyCode("ana@example.com", "000000"))
        assertEquals(AuthState.SignedOut, account.state.value)
        assertNull(store.saved)
    }

    @Test
    fun `a token about to end is refreshed first`() = runBlocking {
        store.saved = Session("old", "refresh-1", now.epochSecond + 30, "user-1", "ana@example.com")
        val account = repository()
        reply(200, session(now.epochSecond + 3600, access = "access-2", refresh = "refresh-2"))
        assertEquals(AuthResult.Ok("access-2"), account.accessToken())
        val refresh = next()
        assertEquals("/auth/v1/token", refresh.url.encodedPath)
        assertEquals("grant_type=refresh_token", refresh.url.encodedQuery)
        assertEquals("""{"refresh_token":"refresh-1"}""", refresh.body?.utf8())
        assertEquals("refresh-2", store.saved?.refreshToken)
        // Still good: no second refresh.
        assertEquals(AuthResult.Ok("access-2"), account.accessToken())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `offline a stored session stays, and a refused refresh token ends it`() = runBlocking {
        store.save(Session("old", "refresh-1", now.epochSecond - 10, "user-1", "ana@example.com"))
        val account = repository()
        reply(503)
        assertEquals(AuthResult.Failed(AuthFailure.Busy), account.accessToken())
        assertEquals(AuthState.SignedIn("user-1", "ana@example.com"), account.state.value)
        reply(400, """{"error_code":"refresh_token_not_found"}""")
        assertEquals(AuthResult.Failed(AuthFailure.SessionEnded), account.accessToken())
        assertEquals(AuthState.Expired("ana@example.com"), account.state.value)
        assertNull(store.saved)
        assertEquals("ana@example.com", store.lastEmail)
    }

    @Test
    fun `the account routes carry the token, and a refused token signs out`() = runBlocking {
        store.saved = Session("access-1", "refresh-1", now.epochSecond + 3600, "user-1", "ana@example.com")
        val account = repository()
        reply(200, """{"email":"ana@example.com","pro":true,"storageUsed":10,"storageLimit":100,"maxFileBytes":5,"projects":2}""")
        assertEquals(AuthResult.Ok(CloudAccount("ana@example.com", true, 10, 100, 5, 2)), account.account())
        assertEquals("1", next().headers["X-PreCut-Api"])
        reply(401, """{"error":{"code":"signed_out"}}""")
        assertEquals(AuthResult.Failed(AuthFailure.SessionEnded), account.account())
        assertEquals(AuthState.Expired("ana@example.com"), account.state.value)
    }

    @Test
    fun `an install the server doesn't know doesn't end the session`() = runBlocking {
        store.saved = Session("access-1", "refresh-1", now.epochSecond + 3600, "user-1", null)
        val account = repository()
        installs.token = "stale"
        reply(401, """{"error":{"code":"not_registered"}}""")
        account.linkInstall()
        next()
        assertEquals(AuthState.SignedIn("user-1", null), account.state.value)
    }

    @Test
    fun `deleting the account signs out, and signing out tells the server`() = runBlocking {
        store.saved = Session("access-1", "refresh-1", now.epochSecond + 3600, "user-1", "ana@example.com")
        val account = repository()
        reply(503)
        assertEquals(AuthResult.Failed(AuthFailure.Busy), account.deleteAccount())
        assertEquals(AuthState.SignedIn("user-1", "ana@example.com"), account.state.value)
        reply(200, """{"deleted":true}""")
        assertEquals(AuthResult.Ok(Unit), account.deleteAccount())
        next()
        assertEquals("DELETE", next().method)
        assertEquals(AuthState.SignedOut, account.state.value)

        store.saved = Session("access-3", "refresh-3", now.epochSecond + 3600, "user-1", null)
        val again = repository()
        reply(204, "")
        again.signOut()
        assertEquals(AuthState.SignedOut, again.state.value)
        val logout = next()
        assertEquals("/auth/v1/logout", logout.url.encodedPath)
        assertEquals("Bearer access-3", logout.headers["Authorization"])
    }

    private class MemorySessions : SessionStore {
        var saved: Session? = null
        override fun load(): Session? = saved
        override fun save(session: Session?) {
            saved = session
            session?.email?.let { lastEmail = it }
        }
        override var lastEmail: String? = null
    }

    private class Installs : InstallStore {
        override fun installId(): String = "install-1"
        override var token: String? = null
        override fun reset() = Unit
    }
}

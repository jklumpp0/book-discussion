package com.octoberdiscussion.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerTest {
    companion object {
        // A file unique to this test run — schema.sql only CREATEs TABLE IF NOT EXISTS, so a
        // shared path would accumulate rows (and users) across runs and skew login/lookup tests.
        private val dbFile = "${System.getProperty("java.io.tmpdir")}/october-discussion-test-${UUID.randomUUID()}.db"

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { "jdbc:sqlite:$dbFile" }
        }

        // Cost 4 (the library's minimum) keeps seeding/login fast; production hashing uses a higher cost.
        private const val TEST_BCRYPT_COST = 4
    }

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var webTestClient: WebTestClient

    lateinit var client: WebTestClient

    @BeforeEach
    fun setUp() {
        client = webTestClient.mutate().responseTimeout(Duration.ofSeconds(30)).build()
        jdbcTemplate.update("DELETE FROM session")
        jdbcTemplate.update("DELETE FROM user")
    }

    private fun seedUser(
        displayName: String,
        accessCode: String,
        role: String = "member",
        avatarKey: String = "fox",
    ): Int {
        val hash = BCrypt.withDefaults().hashToString(TEST_BCRYPT_COST, accessCode.toCharArray())
        jdbcTemplate.update(
            "INSERT INTO user (display_name, avatar_key, access_code_hash, role) VALUES (?, ?, ?, ?)",
            displayName,
            avatarKey,
            hash,
            role,
        )
        return jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Int::class.java) ?: error("no generated id")
    }

    private fun login(accessCode: String): String {
        val result =
            client
                .post()
                .uri("/api/session")
                .bodyValue(LoginRequest(accessCode))
                .exchange()
                .expectStatus()
                .isOk
                .returnResult(UserResponse::class.java)
        return result.responseCookies.getFirst(SESSION_COOKIE_NAME)?.value ?: error("no session cookie in response")
    }

    @Test
    fun `valid access code logs in and sets a secure cookie`() {
        seedUser("Jane", "correct-code")

        val result =
            client
                .post()
                .uri("/api/session")
                .bodyValue(LoginRequest("correct-code"))
                .exchange()
                .expectStatus()
                .isOk
                .expectCookie()
                .httpOnly(SESSION_COOKIE_NAME, true)
                .expectCookie()
                .sameSite(SESSION_COOKIE_NAME, "Lax")
                .expectBody(UserResponse::class.java)
                .returnResult()

        assertEquals("Jane", result.responseBody?.displayName)
        assertEquals("member", result.responseBody?.role)
        val cookie = result.responseCookies.getFirst(SESSION_COOKIE_NAME)?.value
        assertEquals(true, cookie != null && cookie.isNotBlank())
    }

    @Test
    fun `invalid access code is rejected`() {
        seedUser("Jane", "correct-code")

        client
            .post()
            .uri("/api/session")
            .bodyValue(LoginRequest("wrong-code"))
            .exchange()
            .expectStatus()
            .isUnauthorized
    }

    @Test
    fun `me without a session cookie is unauthorized`() {
        client
            .get()
            .uri("/api/me")
            .exchange()
            .expectStatus()
            .isUnauthorized
    }

    @Test
    fun `an expired session is not accepted`() {
        val userId = seedUser("Jane", "correct-code")
        val cookie = login("correct-code")
        jdbcTemplate.update("UPDATE session SET expires_at = '2000-01-01 00:00:00' WHERE user_id = ?", userId)

        client
            .get()
            .uri("/api/me")
            .cookie(SESSION_COOKIE_NAME, cookie)
            .exchange()
            .expectStatus()
            .isUnauthorized
    }

    @Test
    fun `logout clears the session and the cookie stops working`() {
        seedUser("Jane", "correct-code")
        val cookie = login("correct-code")

        client
            .delete()
            .uri("/api/session")
            .cookie(SESSION_COOKIE_NAME, cookie)
            .exchange()
            .expectStatus()
            .isNoContent
            .expectCookie()
            .maxAge(SESSION_COOKIE_NAME, Duration.ZERO)

        client
            .get()
            .uri("/api/me")
            .cookie(SESSION_COOKIE_NAME, cookie)
            .exchange()
            .expectStatus()
            .isUnauthorized
    }

    @Test
    fun `patch me updates persist across a re-get`() {
        seedUser("Pat", "pat-code")
        val cookie = login("pat-code")

        client
            .patch()
            .uri("/api/me")
            .cookie(SESSION_COOKIE_NAME, cookie)
            .bodyValue(UpdateMeRequest(displayName = "Patricia", avatarKey = "owl"))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(UserResponse::class.java)
            .value { assertEquals("Patricia", it.displayName) }

        client
            .get()
            .uri("/api/me")
            .cookie(SESSION_COOKIE_NAME, cookie)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(UserResponse::class.java)
            .value {
                assertEquals("Patricia", it.displayName)
                assertEquals("owl", it.avatarKey)
            }
    }

    @Test
    fun `patch me rejects an avatar key outside the preset list`() {
        seedUser("Pat", "pat-code")
        val cookie = login("pat-code")

        client
            .patch()
            .uri("/api/me")
            .cookie(SESSION_COOKIE_NAME, cookie)
            .bodyValue(UpdateMeRequest(avatarKey = "dragon"))
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `non-admin is forbidden from admin endpoints`() {
        seedUser("Pat", "pat-code", role = "member")
        val cookie = login("pat-code")

        client
            .post()
            .uri("/api/admin/users")
            .cookie(SESSION_COOKIE_NAME, cookie)
            .bodyValue(CreateUserRequest(displayName = "New Person", avatarKey = "bear"))
            .exchange()
            .expectStatus()
            .isForbidden
    }

    @Test
    fun `GET admin users is forbidden for a non-admin`() {
        seedUser("Pat", "pat-code", role = "member")
        val cookie = login("pat-code")

        client
            .get()
            .uri("/api/admin/users")
            .cookie(SESSION_COOKIE_NAME, cookie)
            .exchange()
            .expectStatus()
            .isForbidden
    }

    @Test
    fun `GET admin users lists users without exposing access code hashes`() {
        seedUser("Admin", "admin-code", role = "admin")
        val adminCookie = login("admin-code")
        seedUser("Jane", "jane-code")

        client
            .get()
            .uri("/api/admin/users")
            .cookie(SESSION_COOKIE_NAME, adminCookie)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(2)
            .jsonPath("$[?(@.displayName == 'Jane')]")
            .exists()
            .jsonPath("$..accessCodeHash")
            .doesNotExist()
    }

    @Test
    fun `admin can create a user, and reset-code invalidates the old code and prior sessions`() {
        seedUser("Admin", "admin-code", role = "admin")
        val adminCookie = login("admin-code")

        val created =
            client
                .post()
                .uri("/api/admin/users")
                .cookie(SESSION_COOKIE_NAME, adminCookie)
                .bodyValue(CreateUserRequest(displayName = "New Person", avatarKey = "bear", role = "member"))
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody(CreateUserResponse::class.java)
                .returnResult()
                .responseBody ?: error("no body")

        val firstLogin =
            client
                .post()
                .uri("/api/session")
                .bodyValue(LoginRequest(created.accessCode))
                .exchange()
                .expectStatus()
                .isOk
                .returnResult(UserResponse::class.java)
        val newUserCookie = firstLogin.responseCookies.getFirst(SESSION_COOKIE_NAME)?.value ?: error("no cookie")

        client
            .get()
            .uri("/api/me")
            .cookie(SESSION_COOKIE_NAME, newUserCookie)
            .exchange()
            .expectStatus()
            .isOk

        val reset =
            client
                .post()
                .uri("/api/admin/users/${created.id}/reset-code")
                .cookie(SESSION_COOKIE_NAME, adminCookie)
                .exchange()
                .expectStatus()
                .isOk
                .expectBody(ResetCodeResponse::class.java)
                .returnResult()
                .responseBody ?: error("no body")

        client
            .post()
            .uri("/api/session")
            .bodyValue(LoginRequest(created.accessCode))
            .exchange()
            .expectStatus()
            .isUnauthorized

        client
            .post()
            .uri("/api/session")
            .bodyValue(LoginRequest(reset.accessCode))
            .exchange()
            .expectStatus()
            .isOk

        client
            .get()
            .uri("/api/me")
            .cookie(SESSION_COOKIE_NAME, newUserCookie)
            .exchange()
            .expectStatus()
            .isUnauthorized
    }

    @Test
    fun `admin can delete a user and their sessions`() {
        seedUser("Admin", "admin-code", role = "admin")
        val adminCookie = login("admin-code")
        val targetId = seedUser("Doomed", "doomed-code")
        val targetCookie = login("doomed-code")

        client
            .delete()
            .uri("/api/admin/users/$targetId")
            .cookie(SESSION_COOKIE_NAME, adminCookie)
            .exchange()
            .expectStatus()
            .isNoContent

        client
            .get()
            .uri("/api/me")
            .cookie(SESSION_COOKIE_NAME, targetCookie)
            .exchange()
            .expectStatus()
            .isUnauthorized
    }
}

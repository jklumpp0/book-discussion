package com.octoberdiscussion.message

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(MessageTestAuthConfig::class)
class MessageControllerTest {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun sqliteProps(registry: DynamicPropertyRegistry) {
            val dir = Files.createTempDirectory("october-discussion-message-test")
            val dbFile = dir.resolve("test.db")
            registry.add("spring.datasource.url") { "jdbc:sqlite:$dbFile" }
        }
    }

    private fun nextId(): Int = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long::class.java)!!.toInt()

    private fun seedUser(displayName: String): Int {
        jdbcTemplate.update(
            "INSERT INTO user (display_name, avatar_key, access_code_hash) VALUES (?, 'default', 'hash')",
            displayName,
        )
        return nextId()
    }

    private fun seedTopic(isClosed: Boolean = false): Int {
        jdbcTemplate.update("INSERT INTO book (title) VALUES ('Test Book')")
        val bookId = nextId()
        jdbcTemplate.update(
            "INSERT INTO topic (book_id, title, is_closed) VALUES (?, 'Test Topic', ?)",
            bookId,
            if (isClosed) 1 else 0,
        )
        return nextId()
    }

    private fun get(
        topicId: Int,
        userId: Int?,
    ): WebTestClient.ResponseSpec {
        val spec = webTestClient.get().uri("/api/topics/{topicId}/messages", topicId)
        return (if (userId != null) spec.header("X-Test-User-Id", userId.toString()) else spec).exchange()
    }

    private fun post(
        topicId: Int,
        userId: Int?,
        request: CreateMessageRequest,
    ): WebTestClient.ResponseSpec {
        val spec =
            webTestClient
                .post()
                .uri("/api/topics/{topicId}/messages", topicId)
                .contentType(MediaType.APPLICATION_JSON)
        return (if (userId != null) spec.header("X-Test-User-Id", userId.toString()) else spec)
            .bodyValue(request)
            .exchange()
    }

    private fun patch(
        messageId: Int,
        userId: Int?,
        request: UpdateMessageRequest,
    ): WebTestClient.ResponseSpec {
        val spec =
            webTestClient
                .patch()
                .uri("/api/messages/{id}", messageId)
                .contentType(MediaType.APPLICATION_JSON)
        return (if (userId != null) spec.header("X-Test-User-Id", userId.toString()) else spec)
            .bodyValue(request)
            .exchange()
    }

    private fun delete(
        messageId: Int,
        userId: Int?,
    ): WebTestClient.ResponseSpec {
        val spec = webTestClient.delete().uri("/api/messages/{id}", messageId)
        return (if (userId != null) spec.header("X-Test-User-Id", userId.toString()) else spec).exchange()
    }

    @Test
    fun `GET without a session returns 401`() {
        val topicId = seedTopic()
        get(topicId, userId = null).expectStatus().isUnauthorized
    }

    @Test
    fun `POST without a session returns 401`() {
        val topicId = seedTopic()
        post(topicId, userId = null, CreateMessageRequest(body = "hi")).expectStatus().isUnauthorized
    }

    @Test
    fun `GET on a nonexistent topic returns 404`() {
        val userId = seedUser("Alice")
        get(topicId = 999_999, userId = userId).expectStatus().isNotFound
    }

    @Test
    fun `POST to a nonexistent topic returns 404`() {
        val userId = seedUser("Alice")
        post(topicId = 999_999, userId = userId, CreateMessageRequest(body = "hi")).expectStatus().isNotFound
    }

    @Test
    fun `POST to a closed topic returns 409`() {
        val userId = seedUser("Alice")
        val topicId = seedTopic(isClosed = true)
        post(topicId, userId, CreateMessageRequest(body = "hi")).expectStatus().isEqualTo(409)
    }

    @Test
    fun `posting a top-level message returns it and it shows up in the flat list`() {
        val userId = seedUser("Alice")
        val topicId = seedTopic()

        val created =
            post(topicId, userId, CreateMessageRequest(body = "hello there"))
                .expectStatus()
                .isCreated
                .expectBody(MessageResponse::class.java)
                .returnResult()
                .responseBody!!

        assertEquals(topicId, created.topicId)
        assertEquals(userId, created.authorId)
        assertEquals("Alice", created.authorName)
        assertNull(created.parentId)
        assertEquals("hello there", created.body)
        assertNull(created.editedAt)
        assertNull(created.deletedAt)

        val list =
            get(topicId, userId)
                .expectStatus()
                .isOk
                .expectBodyList(MessageResponse::class.java)
                .returnResult()
                .responseBody!!

        assertEquals(1, list.size)
        assertEquals(created.id, list[0].id)
    }

    @Test
    fun `replying to a top-level message is accepted`() {
        val userId = seedUser("Alice")
        val topicId = seedTopic()
        val parent =
            post(topicId, userId, CreateMessageRequest(body = "top level"))
                .expectBody(MessageResponse::class.java)
                .returnResult()
                .responseBody!!

        val reply =
            post(topicId, userId, CreateMessageRequest(body = "a reply", parentId = parent.id))
                .expectStatus()
                .isCreated
                .expectBody(MessageResponse::class.java)
                .returnResult()
                .responseBody!!

        assertEquals(parent.id, reply.parentId)
    }

    @Test
    fun `replying to a reply is rejected with 400, not silently re-parented`() {
        val userId = seedUser("Alice")
        val topicId = seedTopic()
        val parent =
            post(topicId, userId, CreateMessageRequest(body = "top level"))
                .expectBody(MessageResponse::class.java)
                .returnResult()
                .responseBody!!
        val reply =
            post(topicId, userId, CreateMessageRequest(body = "a reply", parentId = parent.id))
                .expectBody(MessageResponse::class.java)
                .returnResult()
                .responseBody!!

        post(topicId, userId, CreateMessageRequest(body = "reply to a reply", parentId = reply.id))
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `replying to a message from a different topic is rejected with 400`() {
        val userId = seedUser("Alice")
        val topicId = seedTopic()
        val otherTopicId = seedTopic()
        val parentInOtherTopic =
            post(otherTopicId, userId, CreateMessageRequest(body = "top level elsewhere"))
                .expectBody(MessageResponse::class.java)
                .returnResult()
                .responseBody!!

        post(topicId, userId, CreateMessageRequest(body = "cross-topic reply", parentId = parentInOtherTopic.id))
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `editing your own message updates body, bodyHtml and editedAt`() {
        val userId = seedUser("Alice")
        val topicId = seedTopic()
        val created =
            post(topicId, userId, CreateMessageRequest(body = "original"))
                .expectBody(MessageResponse::class.java)
                .returnResult()
                .responseBody!!

        val updated =
            patch(created.id, userId, UpdateMessageRequest(body = "edited"))
                .expectStatus()
                .isOk
                .expectBody(MessageResponse::class.java)
                .returnResult()
                .responseBody!!

        assertEquals("edited", updated.body)
        assertNotNull(updated.bodyHtml)
        assertTrue(updated.bodyHtml!!.contains("edited"))
        assertNotNull(updated.editedAt)
    }

    @Test
    fun `editing another user's message returns 403`() {
        val author = seedUser("Alice")
        val other = seedUser("Bob")
        val topicId = seedTopic()
        val created =
            post(topicId, author, CreateMessageRequest(body = "original"))
                .expectBody(MessageResponse::class.java)
                .returnResult()
                .responseBody!!

        patch(created.id, other, UpdateMessageRequest(body = "hijacked")).expectStatus().isForbidden
    }

    @Test
    fun `editing an already deleted message returns 409`() {
        val userId = seedUser("Alice")
        val topicId = seedTopic()
        val created =
            post(topicId, userId, CreateMessageRequest(body = "original"))
                .expectBody(MessageResponse::class.java)
                .returnResult()
                .responseBody!!

        delete(created.id, userId).expectStatus().isNoContent
        patch(created.id, userId, UpdateMessageRequest(body = "too late")).expectStatus().isEqualTo(409)
    }

    @Test
    fun `deleting another user's message returns 403`() {
        val author = seedUser("Alice")
        val other = seedUser("Bob")
        val topicId = seedTopic()
        val created =
            post(topicId, author, CreateMessageRequest(body = "original"))
                .expectBody(MessageResponse::class.java)
                .returnResult()
                .responseBody!!

        delete(created.id, other).expectStatus().isForbidden
    }

    @Test
    fun `deleting your own message soft-deletes it, hiding body and bodyHtml on subsequent GET`() {
        val userId = seedUser("Alice")
        val topicId = seedTopic()
        val created =
            post(topicId, userId, CreateMessageRequest(body = "to be deleted"))
                .expectBody(MessageResponse::class.java)
                .returnResult()
                .responseBody!!

        delete(created.id, userId).expectStatus().isNoContent

        val list =
            get(topicId, userId)
                .expectStatus()
                .isOk
                .expectBodyList(MessageResponse::class.java)
                .returnResult()
                .responseBody!!
        val fetched = list.single { it.id == created.id }

        assertNull(fetched.body)
        assertNull(fetched.bodyHtml)
        assertNotNull(fetched.deletedAt)
    }

    @Test
    fun `markdown is actually rendered, not just passed through inert`() {
        val userId = seedUser("Alice")
        val topicId = seedTopic()

        val created =
            post(topicId, userId, CreateMessageRequest(body = "**bold**"))
                .expectBody(MessageResponse::class.java)
                .returnResult()
                .responseBody!!

        assertNotNull(created.bodyHtml)
        assertTrue(created.bodyHtml!!.contains("<strong>bold</strong>"))
    }

    @Test
    fun `raw HTML in a message body is escaped and never rendered as executable markup`() {
        val userId = seedUser("Alice")
        val topicId = seedTopic()
        val payload = "<img src=x onerror=alert(1)>"

        val created =
            post(topicId, userId, CreateMessageRequest(body = payload))
                .expectStatus()
                .isCreated
                .expectBody(MessageResponse::class.java)
                .returnResult()
                .responseBody!!

        val html = assertNotNull(created.bodyHtml)
        assertFalse(html.contains("<img"), "an unescaped <img tag must never appear in rendered HTML")
        assertTrue(html.contains("&lt;img"), "the tag should be present only as escaped literal text")
        assertTrue(html.contains("onerror=alert(1)"), "the inert text content should still be visible")

        val fetched =
            get(topicId, userId)
                .expectStatus()
                .isOk
                .expectBodyList(MessageResponse::class.java)
                .returnResult()
                .responseBody!!
                .single { it.id == created.id }

        val fetchedHtml = assertNotNull(fetched.bodyHtml)
        assertFalse(fetchedHtml.contains("<img"))
        assertTrue(fetchedHtml.contains("&lt;img"))
    }
}

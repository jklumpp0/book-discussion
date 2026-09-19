package com.octoberdiscussion.book

import com.octoberdiscussion.auth.CURRENT_USER_ATTRIBUTE
import com.octoberdiscussion.auth.CurrentUser
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.server.WebFilter
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookControllerTest {
    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private val client by lazy { WebTestClient.bindToServer().baseUrl("http://localhost:$port").build() }

    // Nested so it is only picked up by this test's context, not component-scanned into
    // other groups' @SpringBootTest runs after merge.
    @TestConfiguration
    class TestAuthConfig {
        @Bean
        fun testRoleFilter(): WebFilter =
            WebFilter { exchange, chain ->
                exchange.request.headers.getFirst("X-Test-Role")?.let { role ->
                    exchange.attributes[CURRENT_USER_ATTRIBUTE] =
                        CurrentUser(id = 1, displayName = "Test User", avatarKey = "default", role = role)
                }
                chain.filter(exchange)
            }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun dataSourceProps(registry: DynamicPropertyRegistry) {
            val dir = Files.createTempDirectory("october-discussion-book-test")
            val dbFile = dir.resolve("test.db")
            registry.add("spring.datasource.url") { "jdbc:sqlite:$dbFile" }
        }
    }

    @BeforeEach
    fun cleanDb() {
        jdbcTemplate.update("DELETE FROM topic")
        jdbcTemplate.update("DELETE FROM book")
    }

    private fun asMember(headers: HttpHeaders) = headers.set("X-Test-Role", "member")

    private fun asAdmin(headers: HttpHeaders) = headers.set("X-Test-Role", "admin")

    private fun insertBook(
        title: String,
        author: String?,
        isCurrent: Boolean,
    ): Int {
        jdbcTemplate.update(
            "INSERT INTO book (title, author, is_current) VALUES (?, ?, ?)",
            title,
            author,
            if (isCurrent) 1 else 0,
        )
        return jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Int::class.java)!!
    }

    private fun insertTopic(
        bookId: Int,
        title: String,
        position: Int,
        isClosed: Boolean = false,
    ): Int {
        jdbcTemplate.update(
            "INSERT INTO topic (book_id, title, position, is_closed) VALUES (?, ?, ?, ?)",
            bookId,
            title,
            position,
            if (isClosed) 1 else 0,
        )
        return jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Int::class.java)!!
    }

    @Test
    fun `GET current book requires a session`() {
        client
            .get()
            .uri("/api/book/current")
            .exchange()
            .expectStatus()
            .isUnauthorized
    }

    @Test
    fun `GET current book returns 404 when no book is active`() {
        insertBook(title = "Not Current", author = null, isCurrent = false)

        client
            .get()
            .uri("/api/book/current")
            .headers(::asMember)
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    fun `GET current book returns exactly the current book with topics ordered by position`() {
        insertBook(title = "Other Book", author = "Other Author", isCurrent = false)
        val bookId = insertBook(title = "Current Book", author = "Some Author", isCurrent = true)
        val thirdId = insertTopic(bookId, "Third", position = 2)
        val firstId = insertTopic(bookId, "First", position = 0)
        val secondId = insertTopic(bookId, "Second", position = 1)
        val closedId = insertTopic(bookId, "Closed Topic", position = 3, isClosed = true)

        val expected =
            """
            {
              "id": $bookId,
              "title": "Current Book",
              "author": "Some Author",
              "topics": [
                { "id": $firstId, "title": "First", "position": 0, "isClosed": false },
                { "id": $secondId, "title": "Second", "position": 1, "isClosed": false },
                { "id": $thirdId, "title": "Third", "position": 2, "isClosed": false },
                { "id": $closedId, "title": "Closed Topic", "position": 3, "isClosed": true }
              ]
            }
            """.trimIndent()

        client
            .get()
            .uri("/api/book/current")
            .headers(::asMember)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .json(expected, JsonCompareMode.STRICT)
    }

    @Test
    fun `POST admin books is forbidden for a non-admin`() {
        client
            .post()
            .uri("/api/admin/books")
            .headers(::asMember)
            .bodyValue(mapOf("title" to "New Book"))
            .exchange()
            .expectStatus()
            .isForbidden
    }

    @Test
    fun `POST admin books creates a book that is not current by default`() {
        client
            .post()
            .uri("/api/admin/books")
            .headers(::asAdmin)
            .bodyValue(mapOf("title" to "New Book", "author" to "New Author"))
            .exchange()
            .expectStatus()
            .isCreated
            .expectBody()
            .jsonPath("$.title")
            .isEqualTo("New Book")
            .jsonPath("$.author")
            .isEqualTo("New Author")
            .jsonPath("$.isCurrent")
            .isEqualTo(false)

        val currentCount =
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM book WHERE is_current = 1", Int::class.java)
        assertEquals(0, currentCount)
    }

    @Test
    fun `POST admin books activate flips is_current atomically with no double-current state`() {
        val bookA = insertBook(title = "Book A", author = null, isCurrent = true)
        val bookB = insertBook(title = "Book B", author = null, isCurrent = false)

        client
            .post()
            .uri("/api/admin/books/$bookB/activate")
            .headers(::asAdmin)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(bookB)
            .jsonPath("$.isCurrent")
            .isEqualTo(true)

        val currentIds =
            jdbcTemplate.queryForList("SELECT id FROM book WHERE is_current = 1", Int::class.java)
        assertEquals(listOf(bookB), currentIds)

        val bookAIsCurrent =
            jdbcTemplate.queryForObject("SELECT is_current FROM book WHERE id = ?", Int::class.java, bookA)
        assertEquals(0, bookAIsCurrent)
    }

    @Test
    fun `POST admin books activate returns 404 for an unknown book`() {
        client
            .post()
            .uri("/api/admin/books/999/activate")
            .headers(::asAdmin)
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    fun `POST admin topics creates a topic`() {
        val bookId = insertBook(title = "Book", author = null, isCurrent = true)

        client
            .post()
            .uri("/api/admin/topics")
            .headers(::asAdmin)
            .bodyValue(mapOf("bookId" to bookId, "title" to "Chapters 1-5", "position" to 0))
            .exchange()
            .expectStatus()
            .isCreated
            .expectBody()
            .jsonPath("$.bookId")
            .isEqualTo(bookId)
            .jsonPath("$.title")
            .isEqualTo("Chapters 1-5")
            .jsonPath("$.position")
            .isEqualTo(0)
            .jsonPath("$.isClosed")
            .isEqualTo(false)
    }

    @Test
    fun `PATCH admin topics updates a subset of fields and closing persists`() {
        val bookId = insertBook(title = "Book", author = null, isCurrent = true)
        val topicId = insertTopic(bookId, title = "Chapters 1-5", position = 0)

        client
            .patch()
            .uri("/api/admin/topics/$topicId")
            .headers(::asAdmin)
            .bodyValue(mapOf("isClosed" to true))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.title")
            .isEqualTo("Chapters 1-5")
            .jsonPath("$.position")
            .isEqualTo(0)
            .jsonPath("$.isClosed")
            .isEqualTo(true)

        val row =
            jdbcTemplate.queryForMap("SELECT title, position, is_closed FROM topic WHERE id = ?", topicId)
        assertEquals("Chapters 1-5", row["title"])
        assertEquals(0L, (row["position"] as Number).toLong())
        assertTrue((row["is_closed"] as Number).toInt() != 0)
    }

    @Test
    fun `PATCH admin topics returns 404 for an unknown topic`() {
        client
            .patch()
            .uri("/api/admin/topics/999")
            .headers(::asAdmin)
            .bodyValue(mapOf("title" to "Nope"))
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    fun `GET admin books is forbidden for a non-admin`() {
        client
            .get()
            .uri("/api/admin/books")
            .headers(::asMember)
            .exchange()
            .expectStatus()
            .isForbidden
    }

    @Test
    fun `GET admin books lists every book regardless of current state`() {
        insertBook(title = "Old Book", author = null, isCurrent = false)
        insertBook(title = "Current Book", author = "Author", isCurrent = true)

        client
            .get()
            .uri("/api/admin/books")
            .headers(::asAdmin)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(2)
    }

    @Test
    fun `GET admin books topics lists topics for that book`() {
        val bookId = insertBook(title = "Book", author = null, isCurrent = true)
        val otherBookId = insertBook(title = "Other Book", author = null, isCurrent = false)
        insertTopic(otherBookId, "Not this book", position = 0)
        val firstId = insertTopic(bookId, "First", position = 0)
        val secondId = insertTopic(bookId, "Second", position = 1)

        client
            .get()
            .uri("/api/admin/books/$bookId/topics")
            .headers(::asAdmin)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(2)
            .jsonPath("$[0].id")
            .isEqualTo(firstId)
            .jsonPath("$[1].id")
            .isEqualTo(secondId)
    }

    @Test
    fun `GET admin books topics returns 404 for an unknown book`() {
        client
            .get()
            .uri("/api/admin/books/999/topics")
            .headers(::asAdmin)
            .exchange()
            .expectStatus()
            .isNotFound
    }
}

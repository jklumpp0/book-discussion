package com.octoberdiscussion.book

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Statement
import java.sql.Types

@Component
class BookRepository(
    private val jdbcTemplate: JdbcTemplate,
    transactionManager: PlatformTransactionManager,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    suspend fun findCurrentBookWithTopics(): CurrentBookResponse? =
        withContext(Dispatchers.IO) {
            val book =
                jdbcTemplate
                    .query(
                        "SELECT id, title, author FROM book WHERE is_current = 1 LIMIT 1",
                        bookSummaryRowMapper,
                    ).firstOrNull() ?: return@withContext null
            val topics =
                jdbcTemplate.query(
                    "SELECT id, title, position, is_closed FROM topic WHERE book_id = ? ORDER BY position",
                    topicSummaryRowMapper,
                    book.id,
                )
            CurrentBookResponse(id = book.id, title = book.title, author = book.author, topics = topics)
        }

    suspend fun findAllBooks(): List<Book> =
        withContext(Dispatchers.IO) {
            jdbcTemplate.query(
                "SELECT id, title, author, is_current, created_at FROM book ORDER BY created_at DESC, id DESC",
                bookRowMapper,
            )
        }

    suspend fun findTopicsByBookId(bookId: Int): List<Topic>? =
        withContext(Dispatchers.IO) {
            val bookExists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM book WHERE id = ?", Int::class.java, bookId) ?: 0
            if (bookExists == 0) return@withContext null
            jdbcTemplate.query(
                "SELECT id, book_id, title, position, is_closed, created_at FROM topic WHERE book_id = ? ORDER BY position",
                topicRowMapper,
                bookId,
            )
        }

    suspend fun createBook(
        title: String,
        author: String?,
    ): Book =
        withContext(Dispatchers.IO) {
            val id = insertBook(title = title, author = author)
            requireNotNull(findBookById(id)) { "book $id missing immediately after insert" }
        }

    // Both UPDATEs run inside a single JDBC transaction so no reader can ever observe
    // zero or two books with is_current = 1.
    suspend fun activateBook(id: Int): Book? =
        withContext(Dispatchers.IO) {
            transactionTemplate.execute { status ->
                val exists =
                    jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM book WHERE id = ?",
                        Int::class.java,
                        id,
                    ) ?: 0
                if (exists == 0) {
                    status.setRollbackOnly()
                    return@execute null
                }
                jdbcTemplate.update("UPDATE book SET is_current = 0 WHERE is_current = 1")
                jdbcTemplate.update("UPDATE book SET is_current = 1 WHERE id = ?", id)
                findBookById(id)
            }
        }

    suspend fun createTopic(
        bookId: Int,
        title: String,
        position: Int,
    ): Topic =
        withContext(Dispatchers.IO) {
            val id = insertTopic(bookId = bookId, title = title, position = position)
            requireNotNull(findTopicById(id)) { "topic $id missing immediately after insert" }
        }

    suspend fun updateTopic(
        id: Int,
        title: String?,
        position: Int?,
        isClosed: Boolean?,
    ): Topic? =
        withContext(Dispatchers.IO) {
            val existing = findTopicById(id) ?: return@withContext null
            val updated =
                existing.copy(
                    title = title ?: existing.title,
                    position = position ?: existing.position,
                    isClosed = isClosed ?: existing.isClosed,
                )
            jdbcTemplate.update(
                "UPDATE topic SET title = ?, position = ?, is_closed = ? WHERE id = ?",
                updated.title,
                updated.position,
                if (updated.isClosed) 1 else 0,
                id,
            )
            updated
        }

    private fun findBookById(id: Int): Book? =
        jdbcTemplate
            .query(
                "SELECT id, title, author, is_current, created_at FROM book WHERE id = ?",
                bookRowMapper,
                id,
            ).firstOrNull()

    private fun findTopicById(id: Int): Topic? =
        jdbcTemplate
            .query(
                "SELECT id, book_id, title, position, is_closed, created_at FROM topic WHERE id = ?",
                topicRowMapper,
                id,
            ).firstOrNull()

    private fun insertBook(
        title: String,
        author: String?,
    ): Int {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            { connection ->
                connection
                    .prepareStatement(
                        "INSERT INTO book (title, author) VALUES (?, ?)",
                        Statement.RETURN_GENERATED_KEYS,
                    ).apply {
                        setString(1, title)
                        if (author != null) setString(2, author) else setNull(2, Types.VARCHAR)
                    }
            },
            keyHolder,
        )
        return keyHolder.key?.toInt() ?: error("no generated key returned for book insert")
    }

    private fun insertTopic(
        bookId: Int,
        title: String,
        position: Int,
    ): Int {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            { connection ->
                connection
                    .prepareStatement(
                        "INSERT INTO topic (book_id, title, position) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS,
                    ).apply {
                        setInt(1, bookId)
                        setString(2, title)
                        setInt(3, position)
                    }
            },
            keyHolder,
        )
        return keyHolder.key?.toInt() ?: error("no generated key returned for topic insert")
    }

    private data class BookSummaryRow(
        val id: Int,
        val title: String,
        val author: String?,
    )

    companion object {
        private val bookSummaryRowMapper =
            RowMapper { rs, _ ->
                BookSummaryRow(id = rs.getInt("id"), title = rs.getString("title"), author = rs.getString("author"))
            }

        private val bookRowMapper =
            RowMapper { rs, _ ->
                Book(
                    id = rs.getInt("id"),
                    title = rs.getString("title"),
                    author = rs.getString("author"),
                    isCurrent = rs.getInt("is_current") != 0,
                    createdAt = rs.getString("created_at"),
                )
            }

        private val topicRowMapper =
            RowMapper { rs, _ ->
                Topic(
                    id = rs.getInt("id"),
                    bookId = rs.getInt("book_id"),
                    title = rs.getString("title"),
                    position = rs.getInt("position"),
                    isClosed = rs.getInt("is_closed") != 0,
                    createdAt = rs.getString("created_at"),
                )
            }

        private val topicSummaryRowMapper =
            RowMapper { rs, _ ->
                TopicSummary(
                    id = rs.getInt("id"),
                    title = rs.getString("title"),
                    position = rs.getInt("position"),
                    isClosed = rs.getInt("is_closed") != 0,
                )
            }
    }
}

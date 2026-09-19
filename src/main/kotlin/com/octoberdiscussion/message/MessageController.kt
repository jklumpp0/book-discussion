package com.octoberdiscussion.message

import com.octoberdiscussion.auth.requireCurrentUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import java.sql.ResultSet
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val SQLITE_TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun String.sqliteTimestampToIso(): String =
    LocalDateTime
        .parse(this, SQLITE_TIMESTAMP_FORMAT)
        .atZone(ZoneOffset.UTC)
        .toInstant()
        .toString()

private fun ResultSet.getIntOrNull(column: String): Int? {
    val value = getInt(column)
    return if (wasNull()) null else value
}

private const val SELECT_MESSAGE_SQL = """
    SELECT m.id, m.topic_id, m.author_id, m.parent_id, m.body, m.created_at, m.edited_at, m.deleted_at,
           u.display_name AS author_name, u.avatar_key AS author_avatar
    FROM message m
    JOIN user u ON u.id = m.author_id
"""

private data class MessageOwnership(
    val authorId: Int,
    val deletedAt: String?,
)

// Wraps a nullable parent_id so firstOrNull() can distinguish "no row for that id" (empty list)
// from "row found, and its own parent_id happens to be null" (a box containing null).
private data class ParentLookup(
    val parentId: Int?,
)

@RestController
class MessageController(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val messageRowMapper =
        RowMapper { rs: ResultSet, _: Int ->
            val deletedAt = rs.getString("deleted_at")
            val body = if (deletedAt != null) null else rs.getString("body")
            MessageResponse(
                id = rs.getInt("id"),
                topicId = rs.getInt("topic_id"),
                authorId = rs.getInt("author_id"),
                authorName = rs.getString("author_name"),
                authorAvatar = rs.getString("author_avatar"),
                parentId = rs.getIntOrNull("parent_id"),
                body = body,
                bodyHtml = body?.let { renderMessageMarkdown(it) },
                createdAt = rs.getString("created_at").sqliteTimestampToIso(),
                editedAt = rs.getString("edited_at")?.sqliteTimestampToIso(),
                deletedAt = deletedAt?.sqliteTimestampToIso(),
            )
        }

    @GetMapping("/api/topics/{topicId}/messages")
    suspend fun listMessages(
        @PathVariable topicId: Int,
        exchange: ServerWebExchange,
    ): List<MessageResponse> {
        exchange.requireCurrentUser()
        return withContext(Dispatchers.IO) {
            if (!topicExists(topicId)) throw ResponseStatusException(HttpStatus.NOT_FOUND)
            jdbcTemplate.query(
                "$SELECT_MESSAGE_SQL WHERE m.topic_id = ? ORDER BY m.created_at ASC, m.id ASC",
                messageRowMapper,
                topicId,
            )
        }
    }

    @PostMapping("/api/topics/{topicId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createMessage(
        @PathVariable topicId: Int,
        @RequestBody request: CreateMessageRequest,
        exchange: ServerWebExchange,
    ): MessageResponse {
        val user = exchange.requireCurrentUser()
        return withContext(Dispatchers.IO) {
            val isClosed = topicIsClosed(topicId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
            if (isClosed) throw ResponseStatusException(HttpStatus.CONFLICT)

            val parentId = request.parentId
            if (parentId != null) {
                val parent =
                    jdbcTemplate
                        .query(
                            "SELECT parent_id FROM message WHERE id = ? AND topic_id = ?",
                            RowMapper { rs, _ -> ParentLookup(rs.getIntOrNull("parent_id")) },
                            parentId,
                            topicId,
                        ).firstOrNull()
                        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST)
                if (parent.parentId != null) throw ResponseStatusException(HttpStatus.BAD_REQUEST)
            }

            jdbcTemplate.update(
                "INSERT INTO message (topic_id, author_id, parent_id, body) VALUES (?, ?, ?, ?)",
                topicId,
                user.id,
                parentId,
                request.body,
            )
            val id = checkNotNull(jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long::class.java)).toInt()
            findMessageById(id) ?: error("message $id was inserted but could not be re-read")
        }
    }

    @PatchMapping("/api/messages/{id}")
    suspend fun updateMessage(
        @PathVariable id: Int,
        @RequestBody request: UpdateMessageRequest,
        exchange: ServerWebExchange,
    ): MessageResponse {
        val user = exchange.requireCurrentUser()
        return withContext(Dispatchers.IO) {
            val ownership = findOwnership(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
            if (ownership.authorId != user.id) throw ResponseStatusException(HttpStatus.FORBIDDEN)
            if (ownership.deletedAt != null) throw ResponseStatusException(HttpStatus.CONFLICT)

            jdbcTemplate.update(
                "UPDATE message SET body = ?, edited_at = datetime('now') WHERE id = ?",
                request.body,
                id,
            )
            findMessageById(id) ?: error("message $id was updated but could not be re-read")
        }
    }

    @DeleteMapping("/api/messages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun deleteMessage(
        @PathVariable id: Int,
        exchange: ServerWebExchange,
    ) {
        val user = exchange.requireCurrentUser()
        withContext(Dispatchers.IO) {
            val ownership = findOwnership(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
            if (ownership.authorId != user.id) throw ResponseStatusException(HttpStatus.FORBIDDEN)
            if (ownership.deletedAt == null) {
                // body is NOT NULL in the frozen schema, so soft-delete clears it to '' at rest;
                // the row mapper always nulls body/bodyHtml in the API response once deletedAt is set.
                jdbcTemplate.update(
                    "UPDATE message SET deleted_at = datetime('now'), body = '' WHERE id = ?",
                    id,
                )
            }
        }
    }

    private fun topicExists(topicId: Int): Boolean =
        jdbcTemplate
            .query("SELECT 1 FROM topic WHERE id = ?", RowMapper { _, _ -> true }, topicId)
            .firstOrNull() ?: false

    private fun topicIsClosed(topicId: Int): Boolean? =
        jdbcTemplate
            .query(
                "SELECT is_closed FROM topic WHERE id = ?",
                RowMapper { rs, _ -> rs.getInt("is_closed") != 0 },
                topicId,
            ).firstOrNull()

    private fun findOwnership(messageId: Int): MessageOwnership? =
        jdbcTemplate
            .query(
                "SELECT author_id, deleted_at FROM message WHERE id = ?",
                RowMapper { rs, _ -> MessageOwnership(rs.getInt("author_id"), rs.getString("deleted_at")) },
                messageId,
            ).firstOrNull()

    private fun findMessageById(id: Int): MessageResponse? =
        jdbcTemplate.query("$SELECT_MESSAGE_SQL WHERE m.id = ?", messageRowMapper, id).firstOrNull()
}

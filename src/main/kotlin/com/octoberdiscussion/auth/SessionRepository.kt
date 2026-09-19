package com.octoberdiscussion.auth

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

// Matches the format SQLite's own datetime('now') produces, so expires_at is directly comparable in SQL.
private val SQLITE_DATETIME_FORMAT =
    DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneOffset.UTC)

@Component
class SessionRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun create(userId: Int): String {
        val sessionId = UUID.randomUUID().toString()
        val expiresAt = SQLITE_DATETIME_FORMAT.format(Instant.now().plus(SESSION_TTL))
        jdbcTemplate.update(
            "INSERT INTO session (id, user_id, expires_at) VALUES (?, ?, ?)",
            sessionId,
            userId,
            expiresAt,
        )
        return sessionId
    }

    fun findValidByCookie(sessionId: String): CurrentUser? =
        jdbcTemplate
            .query(
                """
                SELECT user.id, user.display_name, user.avatar_key, user.role
                FROM session
                JOIN user ON user.id = session.user_id
                WHERE session.id = ? AND session.expires_at > datetime('now')
                """.trimIndent(),
                { rs, _ ->
                    CurrentUser(
                        id = rs.getInt("id"),
                        displayName = rs.getString("display_name"),
                        avatarKey = rs.getString("avatar_key"),
                        role = rs.getString("role"),
                    )
                },
                sessionId,
            ).firstOrNull()

    fun deleteById(sessionId: String) {
        jdbcTemplate.update("DELETE FROM session WHERE id = ?", sessionId)
    }

    fun deleteAllForUser(userId: Int) {
        jdbcTemplate.update("DELETE FROM session WHERE user_id = ?", userId)
    }
}

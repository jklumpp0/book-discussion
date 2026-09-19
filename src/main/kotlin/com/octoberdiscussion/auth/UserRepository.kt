package com.octoberdiscussion.auth

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Component
import java.sql.Statement

data class UserRow(
    val id: Int,
    val displayName: String,
    val avatarKey: String,
    val accessCodeHash: String,
    val role: String,
)

private val USER_COLUMNS = "id, display_name, avatar_key, access_code_hash, role"

private fun rowMapper(
    rs: java.sql.ResultSet,
    rowNum: Int,
) = UserRow(
    id = rs.getInt("id"),
    displayName = rs.getString("display_name"),
    avatarKey = rs.getString("avatar_key"),
    accessCodeHash = rs.getString("access_code_hash"),
    role = rs.getString("role"),
)

@Component
class UserRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun findAll(): List<UserRow> = jdbcTemplate.query("SELECT $USER_COLUMNS FROM user", ::rowMapper)

    fun findById(id: Int): UserRow? = jdbcTemplate.query("SELECT $USER_COLUMNS FROM user WHERE id = ?", ::rowMapper, id).firstOrNull()

    fun insert(
        displayName: String,
        avatarKey: String,
        accessCodeHash: String,
        role: String,
    ): Int {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            { connection ->
                connection
                    .prepareStatement(
                        "INSERT INTO user (display_name, avatar_key, access_code_hash, role) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS,
                    ).apply {
                        setString(1, displayName)
                        setString(2, avatarKey)
                        setString(3, accessCodeHash)
                        setString(4, role)
                    }
            },
            keyHolder,
        )
        return keyHolder.key?.toInt() ?: error("insert into user did not return a generated id")
    }

    fun updateProfile(
        id: Int,
        displayName: String?,
        avatarKey: String?,
    ) {
        when {
            displayName != null && avatarKey != null ->
                jdbcTemplate.update("UPDATE user SET display_name = ?, avatar_key = ? WHERE id = ?", displayName, avatarKey, id)
            displayName != null ->
                jdbcTemplate.update("UPDATE user SET display_name = ? WHERE id = ?", displayName, id)
            avatarKey != null ->
                jdbcTemplate.update("UPDATE user SET avatar_key = ? WHERE id = ?", avatarKey, id)
        }
    }

    fun updateAccessCodeHash(
        id: Int,
        hash: String,
    ) {
        jdbcTemplate.update("UPDATE user SET access_code_hash = ? WHERE id = ?", hash, id)
    }

    fun deleteById(id: Int) {
        jdbcTemplate.update("DELETE FROM user WHERE id = ?", id)
    }
}

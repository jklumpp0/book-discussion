package com.octoberdiscussion.message

import com.octoberdiscussion.auth.CURRENT_USER_ATTRIBUTE
import com.octoberdiscussion.auth.CurrentUser
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.server.WebFilter

/**
 * Test-only stand-in for Group A's real session WebFilter, which does not exist in this
 * worktree yet. Tests pick the current user per-request via the `X-Test-User-Id` header
 * (looked up against the real `user` table), rather than driving a real login flow.
 */
@TestConfiguration
class MessageTestAuthConfig {
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun messageTestCurrentUserFilter(jdbcTemplate: JdbcTemplate): WebFilter =
        WebFilter { exchange, chain ->
            val userId =
                exchange.request.headers
                    .getFirst("X-Test-User-Id")
                    ?.toIntOrNull()
            if (userId != null) {
                val user =
                    jdbcTemplate
                        .query(
                            "SELECT id, display_name, avatar_key, role FROM user WHERE id = ?",
                            { rs, _ ->
                                CurrentUser(
                                    id = rs.getInt("id"),
                                    displayName = rs.getString("display_name"),
                                    avatarKey = rs.getString("avatar_key"),
                                    role = rs.getString("role"),
                                )
                            },
                            userId,
                        ).firstOrNull()
                if (user != null) {
                    exchange.attributes[CURRENT_USER_ATTRIBUTE] = user
                }
            }
            chain.filter(exchange)
        }
}

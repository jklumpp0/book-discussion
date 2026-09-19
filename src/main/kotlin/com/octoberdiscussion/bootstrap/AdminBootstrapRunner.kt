package com.octoberdiscussion.bootstrap

import at.favre.lib.crypto.bcrypt.BCrypt
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.security.SecureRandom

private val logger = KotlinLogging.logger {}

private const val ACCESS_CODE_LENGTH = 12
private const val ACCESS_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
private const val BCRYPT_COST = 12

/**
 * Seeds a single admin user with a random access code on the very first boot
 * (when the `user` table is empty). Never reseeds afterward.
 */
@Component
class AdminBootstrapRunner(
    private val jdbcTemplate: JdbcTemplate,
) : ApplicationRunner {
    private val random = SecureRandom()

    override fun run(args: ApplicationArguments) {
        val userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user", Int::class.java) ?: 0
        if (userCount > 0) return

        val accessCode = generateAccessCode()
        val accessCodeHash = BCrypt.withDefaults().hashToString(BCRYPT_COST, accessCode.toCharArray())

        jdbcTemplate.update(
            "INSERT INTO user (display_name, avatar_key, access_code_hash, role) VALUES (?, ?, ?, ?)",
            "Admin",
            "default",
            accessCodeHash,
            "admin",
        )

        logger.warn { "=== First-run admin access code: $accessCode — save this now, it will not be shown again ===" }
    }

    private fun generateAccessCode(): String =
        (1..ACCESS_CODE_LENGTH)
            .map { ACCESS_CODE_ALPHABET[random.nextInt(ACCESS_CODE_ALPHABET.length)] }
            .joinToString("")
}

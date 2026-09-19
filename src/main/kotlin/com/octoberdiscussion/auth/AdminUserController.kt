package com.octoberdiscussion.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import java.security.SecureRandom

private val logger = KotlinLogging.logger {}
private const val ACCESS_CODE_LENGTH = 12
private const val BCRYPT_COST = 10
private val ACCESS_CODE_ALPHABET = (('A'..'Z') + ('a'..'z') + ('0'..'9')).toCharArray()
private val secureRandom = SecureRandom()
private val VALID_ROLES = setOf("member", "admin")

private fun generateAccessCode(): String =
    CharArray(ACCESS_CODE_LENGTH) { ACCESS_CODE_ALPHABET[secureRandom.nextInt(ACCESS_CODE_ALPHABET.size)] }.concatToString()

private fun hashAccessCode(accessCode: String): String = BCrypt.withDefaults().hashToString(BCRYPT_COST, accessCode.toCharArray())

@RestController
class AdminUserController(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
) {
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/admin/users")
    suspend fun createUser(
        @RequestBody request: CreateUserRequest,
        exchange: ServerWebExchange,
    ): CreateUserResponse {
        exchange.requireCurrentUser().requireAdmin()
        if (request.avatarKey !in AVATAR_KEYS) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid avatarKey")
        if (request.role !in VALID_ROLES) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid role")

        val accessCode = generateAccessCode()
        val userId =
            withContext(Dispatchers.IO) {
                userRepository.insert(
                    displayName = request.displayName,
                    avatarKey = request.avatarKey,
                    accessCodeHash = hashAccessCode(accessCode),
                    role = request.role,
                )
            }
        logger.info { "Admin created user id=$userId" }
        return CreateUserResponse(
            id = userId,
            displayName = request.displayName,
            avatarKey = request.avatarKey,
            role = request.role,
            accessCode = accessCode,
        )
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/api/admin/users/{id}")
    suspend fun deleteUser(
        @PathVariable id: Int,
        exchange: ServerWebExchange,
    ) {
        exchange.requireCurrentUser().requireAdmin()
        withContext(Dispatchers.IO) {
            userRepository.findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
            sessionRepository.deleteAllForUser(id)
            userRepository.deleteById(id)
        }
        logger.info { "Admin deleted user id=$id" }
    }

    @PostMapping("/api/admin/users/{id}/reset-code")
    suspend fun resetCode(
        @PathVariable id: Int,
        exchange: ServerWebExchange,
    ): ResetCodeResponse {
        exchange.requireCurrentUser().requireAdmin()
        val accessCode = generateAccessCode()
        withContext(Dispatchers.IO) {
            userRepository.findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
            userRepository.updateAccessCodeHash(id, hashAccessCode(accessCode))
            sessionRepository.deleteAllForUser(id)
        }
        logger.info { "Admin reset access code for user id=$id" }
        return ResetCodeResponse(id = id, accessCode = accessCode)
    }
}

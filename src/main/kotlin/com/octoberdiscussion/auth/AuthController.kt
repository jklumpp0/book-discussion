package com.octoberdiscussion.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import java.time.Duration

@RestController
class AuthController(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
) {
    @PostMapping("/api/session")
    suspend fun login(
        @RequestBody request: LoginRequest,
        exchange: ServerWebExchange,
    ): UserResponse {
        val user =
            withContext(Dispatchers.IO) {
                userRepository.findAll().firstOrNull { row ->
                    BCrypt.verifyer().verify(request.accessCode.toCharArray(), row.accessCodeHash).verified
                }
            } ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)

        val sessionId = withContext(Dispatchers.IO) { sessionRepository.create(user.id) }
        exchange.response.addCookie(sessionCookie(sessionId, maxAge = SESSION_TTL))
        return user.toResponse()
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/api/session")
    suspend fun logout(exchange: ServerWebExchange) {
        exchange.request.cookies.getFirst(SESSION_COOKIE_NAME)?.value?.let { sessionId ->
            withContext(Dispatchers.IO) { sessionRepository.deleteById(sessionId) }
        }
        exchange.response.addCookie(sessionCookie("", maxAge = Duration.ZERO))
    }

    @GetMapping("/api/me")
    suspend fun me(exchange: ServerWebExchange): UserResponse = exchange.requireCurrentUser().toResponse()

    @PatchMapping("/api/me")
    suspend fun updateMe(
        @RequestBody request: UpdateMeRequest,
        exchange: ServerWebExchange,
    ): UserResponse {
        val currentUser = exchange.requireCurrentUser()
        request.avatarKey?.let {
            if (it !in AVATAR_KEYS) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid avatarKey")
        }
        return withContext(Dispatchers.IO) {
            userRepository.updateProfile(currentUser.id, request.displayName, request.avatarKey)
            userRepository.findById(currentUser.id)
        }?.toResponse() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }
}

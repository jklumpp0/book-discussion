package com.octoberdiscussion.auth

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange

/**
 * Populated as an exchange attribute by the session-auth WebFilter (implemented
 * alongside login/logout in this same package) on every request carrying a
 * valid session cookie. Other feature packages read the current user via the
 * extension functions below rather than depending on the filter directly.
 */
data class CurrentUser(
    val id: Int,
    val displayName: String,
    val avatarKey: String,
    val role: String,
)

const val CURRENT_USER_ATTRIBUTE = "currentUser"

fun ServerWebExchange.currentUserOrNull(): CurrentUser? = attributes[CURRENT_USER_ATTRIBUTE] as? CurrentUser

fun ServerWebExchange.requireCurrentUser(): CurrentUser = currentUserOrNull() ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)

fun CurrentUser.requireAdmin(): CurrentUser {
    if (role != "admin") throw ResponseStatusException(HttpStatus.FORBIDDEN)
    return this
}

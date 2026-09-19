package com.octoberdiscussion.auth

import org.springframework.http.ResponseCookie
import java.time.Duration

const val SESSION_COOKIE_NAME = "SESSIONID"
val SESSION_TTL: Duration = Duration.ofDays(30)

fun sessionCookie(
    value: String,
    maxAge: Duration,
): ResponseCookie =
    ResponseCookie
        .from(SESSION_COOKIE_NAME, value)
        .httpOnly(true)
        .sameSite("Lax")
        .path("/")
        .maxAge(maxAge)
        .build()

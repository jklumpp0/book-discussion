package com.octoberdiscussion.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

// Never rejects — a missing/invalid session simply leaves no CurrentUser attribute set.
// Routes that need auth call requireCurrentUser()/requireAdmin() themselves, since login
// and static assets must stay reachable without a session.
@Component
class SessionAuthWebFilter(
    private val sessionRepository: SessionRepository,
) : WebFilter {
    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> =
        mono {
            val sessionId =
                exchange.request.cookies
                    .getFirst(SESSION_COOKIE_NAME)
                    ?.value ?: return@mono
            val user = withContext(Dispatchers.IO) { sessionRepository.findValidByCookie(sessionId) }
            user?.let { exchange.attributes[CURRENT_USER_ATTRIBUTE] = it }
        }.then(chain.filter(exchange))
}

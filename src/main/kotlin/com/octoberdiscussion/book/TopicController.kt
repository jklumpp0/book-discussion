package com.octoberdiscussion.book

import com.octoberdiscussion.auth.requireAdmin
import com.octoberdiscussion.auth.requireCurrentUser
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange

@RestController
class TopicController(
    private val bookRepository: BookRepository,
) {
    @PostMapping("/api/admin/topics")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createTopic(
        exchange: ServerWebExchange,
        @RequestBody request: CreateTopicRequest,
    ): Topic {
        exchange.requireCurrentUser().requireAdmin()
        return bookRepository.createTopic(bookId = request.bookId, title = request.title, position = request.position)
    }

    @PatchMapping("/api/admin/topics/{id}")
    suspend fun updateTopic(
        exchange: ServerWebExchange,
        @PathVariable id: Int,
        @RequestBody request: UpdateTopicRequest,
    ): Topic {
        exchange.requireCurrentUser().requireAdmin()
        return bookRepository.updateTopic(
            id = id,
            title = request.title,
            position = request.position,
            isClosed = request.isClosed,
        ) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }
}

package com.octoberdiscussion.book

import com.octoberdiscussion.auth.requireAdmin
import com.octoberdiscussion.auth.requireCurrentUser
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange

@RestController
class BookController(
    private val bookRepository: BookRepository,
) {
    @GetMapping("/api/book/current")
    suspend fun getCurrentBook(exchange: ServerWebExchange): CurrentBookResponse {
        exchange.requireCurrentUser()
        return bookRepository.findCurrentBookWithTopics() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }

    @GetMapping("/api/admin/books")
    suspend fun listBooks(exchange: ServerWebExchange): List<Book> {
        exchange.requireCurrentUser().requireAdmin()
        return bookRepository.findAllBooks()
    }

    @PostMapping("/api/admin/books")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createBook(
        exchange: ServerWebExchange,
        @RequestBody request: CreateBookRequest,
    ): Book {
        exchange.requireCurrentUser().requireAdmin()
        return bookRepository.createBook(title = request.title, author = request.author)
    }

    @PostMapping("/api/admin/books/{id}/activate")
    suspend fun activateBook(
        exchange: ServerWebExchange,
        @PathVariable id: Int,
    ): Book {
        exchange.requireCurrentUser().requireAdmin()
        return bookRepository.activateBook(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }
}

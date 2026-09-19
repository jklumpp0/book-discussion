package com.octoberdiscussion.book

import com.fasterxml.jackson.annotation.JsonProperty

data class TopicSummary(
    val id: Int,
    val title: String,
    val position: Int,
    @get:JsonProperty("isClosed")
    val isClosed: Boolean,
)

data class CurrentBookResponse(
    val id: Int,
    val title: String,
    val author: String?,
    val topics: List<TopicSummary>,
)

data class Book(
    val id: Int,
    val title: String,
    val author: String?,
    @get:JsonProperty("isCurrent")
    val isCurrent: Boolean,
    val createdAt: String,
)

data class Topic(
    val id: Int,
    val bookId: Int,
    val title: String,
    val position: Int,
    @get:JsonProperty("isClosed")
    val isClosed: Boolean,
    val createdAt: String,
)

data class CreateBookRequest(
    val title: String,
    val author: String? = null,
)

data class CreateTopicRequest(
    val bookId: Int,
    val title: String,
    val position: Int,
)

data class UpdateTopicRequest(
    val title: String? = null,
    val position: Int? = null,
    @get:JsonProperty("isClosed")
    val isClosed: Boolean? = null,
)

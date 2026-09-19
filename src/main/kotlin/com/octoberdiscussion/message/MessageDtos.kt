package com.octoberdiscussion.message

data class MessageResponse(
    val id: Int,
    val topicId: Int,
    val authorId: Int,
    val authorName: String,
    val authorAvatar: String,
    val parentId: Int?,
    val body: String?,
    val bodyHtml: String?,
    val createdAt: String,
    val editedAt: String?,
    val deletedAt: String?,
)

data class CreateMessageRequest(
    val body: String,
    val parentId: Int? = null,
)

data class UpdateMessageRequest(
    val body: String,
)

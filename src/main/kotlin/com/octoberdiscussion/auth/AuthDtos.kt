package com.octoberdiscussion.auth

data class LoginRequest(
    val accessCode: String,
)

data class UpdateMeRequest(
    val displayName: String? = null,
    val avatarKey: String? = null,
)

data class UserResponse(
    val id: Int,
    val displayName: String,
    val avatarKey: String,
    val role: String,
)

data class CreateUserRequest(
    val displayName: String,
    val avatarKey: String,
    val role: String = "member",
    val accessCode: String? = null,
)

data class CreateUserResponse(
    val id: Int,
    val displayName: String,
    val avatarKey: String,
    val role: String,
    val accessCode: String,
)

data class ResetCodeRequest(
    val accessCode: String? = null,
)

data class ResetCodeResponse(
    val id: Int,
    val accessCode: String,
)

fun CurrentUser.toResponse() = UserResponse(id = id, displayName = displayName, avatarKey = avatarKey, role = role)

fun UserRow.toResponse() = UserResponse(id = id, displayName = displayName, avatarKey = avatarKey, role = role)

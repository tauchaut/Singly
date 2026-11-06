package com.tauchaut.singly.models

internal data class AccountProfile(
    val id: String,
    val name: String,
    val avatarEmoji: String,
    val avatarColor: Int,
    val createdAt: Long,
    val avatarPhotoPath: String? = null
)

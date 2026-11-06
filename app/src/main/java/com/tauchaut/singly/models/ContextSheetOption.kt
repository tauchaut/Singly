package com.tauchaut.singly.models

data class ContextSheetOption(
    val label: String,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val trailingCheck: Boolean = false,
    val trailingSwitch: Boolean? = null,
    val onSwitch: ((Boolean) -> Unit)? = null,
    val action: (() -> Unit)? = null
)

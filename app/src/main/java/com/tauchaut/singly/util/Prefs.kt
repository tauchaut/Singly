package com.tauchaut.singly.util

internal fun prefsNameFor(id: String): String =
    if (id == "default") "Singly" else "Singly_acct_$id"
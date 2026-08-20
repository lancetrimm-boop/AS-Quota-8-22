package com.example.ui.models

import androidx.compose.runtime.Immutable

@Immutable
data class LibraryItemUi(
    val id: String,
    val title: String,
    val mediaType: String,
    val imageUrl: String,
    val uriPath: String,
    val duration: String,
    val selectionReason: String? = null
)

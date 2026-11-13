package com.nuvio.tv.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class TmdbReview(
    val id: String,
    val authorDisplayName: String,
    val authorUsername: String?,
    val comment: String,
    val rating: String?,
    val createdAt: String?,
    val updatedAt: String?
)

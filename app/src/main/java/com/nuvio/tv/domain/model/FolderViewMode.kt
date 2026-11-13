package com.nuvio.tv.domain.model

enum class FolderViewMode {
    TABBED_GRID,
    ROWS,
    FOLLOW_LAYOUT,
    PREVIEW_GRID,
    SHOWCASE;

    val usesTabs: Boolean get() = this == TABBED_GRID || this == PREVIEW_GRID

    companion object {
        fun fromString(value: String?): FolderViewMode = when (value?.lowercase()) {
            "rows" -> ROWS
            "follow_layout" -> FOLLOW_LAYOUT
            "preview_grid" -> PREVIEW_GRID
            "showcase" -> SHOWCASE
            else -> TABBED_GRID
        }
    }
}

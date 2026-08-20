package com.example.data

object CompareResultsHelper {
    /**
     * Ranks participating media items by their current Elo score.
     * Takes the top 10 items.
     */
    fun getRankedResults(
        participatingIds: Set<String>,
        mediaItemsMap: Map<String, MediaItem>
    ): List<MediaItem> {
        return participatingIds
            .mapNotNull { mediaItemsMap[it] }
            .filter { !it.isDeleted }
            .sortedWith(
                compareByDescending<MediaItem> { it.eloRating }
                    .thenBy { it.id } // Deterministic tie-break
            )
            .take(10)
    }
}

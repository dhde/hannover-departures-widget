package de.dhde.hannover.departures.widget.data

/** Time-to-live, innerhalb derer ein laufender Refresh als „aktiv" gilt. */
internal const val REFRESH_TTL_MS = 15_000L

/** Ein Refresh gilt als aktiv, wenn er läuft UND jünger als die TTL ist. */
internal fun isRefreshFresh(isRefreshing: Boolean, refreshTs: Long, nowMs: Long): Boolean =
    isRefreshing && (nowMs - refreshTs < REFRESH_TTL_MS)

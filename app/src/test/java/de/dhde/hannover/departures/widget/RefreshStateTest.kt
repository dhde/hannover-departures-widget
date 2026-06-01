package de.dhde.hannover.departures.widget

import de.dhde.hannover.departures.widget.data.REFRESH_TTL_MS
import de.dhde.hannover.departures.widget.data.isRefreshFresh
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshStateTest {

    @Test
    fun notRefreshing_isNeverFresh() {
        assertFalse(isRefreshFresh(isRefreshing = false, refreshTs = 1_000L, nowMs = 1_000L))
    }

    @Test
    fun refreshing_andWithinTtl_isFresh() {
        assertTrue(isRefreshFresh(isRefreshing = true, refreshTs = 1_000L, nowMs = 1_000L + REFRESH_TTL_MS - 1))
    }

    @Test
    fun refreshing_atTtlBoundary_isNotFresh() {
        assertFalse(isRefreshFresh(isRefreshing = true, refreshTs = 1_000L, nowMs = 1_000L + REFRESH_TTL_MS))
    }

    @Test
    fun refreshing_butExpired_isNotFresh() {
        assertFalse(isRefreshFresh(isRefreshing = true, refreshTs = 1_000L, nowMs = 1_000L + REFRESH_TTL_MS + 5_000L))
    }
}

package de.dhde.hannover.departures.widget.debug

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Prozess-lokaler Ringpuffer für Debug-Ausgaben (aktivierbar über 7-Tap in der Hilfe).
 * Schreibt gleichzeitig nach Logcat (Tag "UestraDebug") und in einen StateFlow, der die
 * Debug-View versorgt. Nicht persistent — bei App-Kill sind Logs weg.
 */
object DebugLog {
    private const val TAG = "UestraDebug"
    private const val CAPACITY = 500

    data class Entry(val timestampMs: Long, val message: String, val stack: String?)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    @Synchronized
    fun log(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(TAG, message, throwable) else Log.i(TAG, message)
        val entry = Entry(System.currentTimeMillis(), message, throwable?.stackTraceToString())
        val next = (_entries.value + entry).let {
            if (it.size > CAPACITY) it.drop(it.size - CAPACITY) else it
        }
        _entries.value = next
    }

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
    }
}

package de.dhde.hannover.departures.widget.data

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * App-Sprache unabhängig von der Systemsprache.
 *
 * - Android 13+ (API 33): offizielle Pro-App-Sprache über [LocaleManager]. Identisch mit
 *   Einstellungen → Apps → Sprache; das System wendet sie auf App UND Widget an.
 * - Android 8–12: Auswahl in SharedPreferences (synchron lesbar, nötig in attachBaseContext);
 *   Activity und Widget holen sich per [wrap] einen Context mit der gewählten Sprache.
 *
 * Tag null/leer = Systemsprache.
 */
object AppLocale {
    /** Unterstützte Sprachen (muss zu den values-xx-Ordnern passen). Reihenfolge = Anzeige in den Optionen. */
    val SUPPORTED = listOf("de", "en", "tr", "pl", "uk", "ru")

    private const val PREFS = "app_locale"
    private const val KEY_TAG = "tag"

    /** Aktuell gewählte Sprache als Tag ("en", …) oder null für Systemsprache. */
    fun getTag(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales?.takeIf { !it.isEmpty }?.get(0)?.language
        } else {
            prefs(context).getString(KEY_TAG, null)?.takeIf { it.isNotBlank() }
        }

    /**
     * Setzt die App-Sprache (null = Systemsprache). Ab API 33 erzeugt das System selbst eine
     * Konfigurationsänderung; darunter muss der Aufrufer die Activity neu erzeugen und das Widget neu zeichnen.
     */
    fun setTag(context: Context, tag: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag.isNullOrBlank()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            prefs(context).edit().putString(KEY_TAG, tag.orEmpty()).apply()
        }
    }

    /** Context mit der gewählten Sprache (nur < API 33 nötig; ab 33 unverändert zurück). */
    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
        val tag = prefs(context).getString(KEY_TAG, null)?.takeIf { it.isNotBlank() } ?: return context
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(config)
    }

    /** Eigenname einer Sprache in ihrer eigenen Sprache („Türkçe“, „Polski“ …), erster Buchstabe groß. */
    fun nativeName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }

    /** Flaggen-Emoji vor dem Sprachnamen in den Optionen; Systemsprache = Globus. */
    fun flag(tag: String?): String = when (tag) {
        "de" -> "🇩🇪"
        "en" -> "🇬🇧"
        "tr" -> "🇹🇷"
        "pl" -> "🇵🇱"
        "uk" -> "🇺🇦"
        "ru" -> "🇷🇺"
        else -> "🌐"
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

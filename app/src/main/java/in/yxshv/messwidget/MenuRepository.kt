package `in`.yxshv.messwidget

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

/**
 * Loads menu.json: the latest copy from GitHub when online, otherwise the last
 * downloaded copy, otherwise the one bundled in the APK.
 */
class MenuRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("menu", Context.MODE_PRIVATE)

    fun cached(): Menu {
        val text = prefs.getString(KEY_JSON, null)
            ?: context.assets.open("menu.json").bufferedReader().use { it.readText() }
        return Menu.parse(text)
    }

    /** Downloads a fresh copy if the cache is stale (or [force]). Blocking; call off the main thread. */
    fun refresh(force: Boolean): Boolean {
        val age = System.currentTimeMillis() - prefs.getLong(KEY_FETCHED_AT, 0)
        if (!force && age < MAX_AGE_MS) return false
        return try {
            val conn = URL(MENU_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Cache-Control", "no-cache")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            Menu.parse(text) // validate before caching
            prefs.edit()
                .putString(KEY_JSON, text)
                .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        const val MENU_URL = "https://raw.githubusercontent.com/yxshv/mess-widget/HEAD/data/menu.json"
        private const val MAX_AGE_MS = 2 * 60 * 60 * 1000L
        private const val KEY_JSON = "json"
        private const val KEY_FETCHED_AT = "fetched_at"
    }
}

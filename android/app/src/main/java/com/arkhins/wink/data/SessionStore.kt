package com.arkhins.wink.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wink_session")

/**
 * The bearer token that says who this device is signed in as. Kept in
 * DataStore across launches and mirrored in memory so the HTTP layer can
 * read it without suspending.
 */
class SessionStore(private val context: Context) {
    @Volatile
    var token: String? = null
        private set

    /** The FCM token this device last registered, so sign-out can forget it. */
    @Volatile
    var pushToken: String? = null
        private set

    /** The screen that was open when the app was last in the foreground, and when. */
    @Volatile
    var lastRoute: String? = null
        private set
    @Volatile
    var lastRouteAt: Long = 0L
        private set

    private val tokenKey = stringPreferencesKey("token")
    private val pushKey = stringPreferencesKey("push")
    private val routeKey = stringPreferencesKey("route")
    private val batteryKey = stringPreferencesKey("batteryAsked")
    private val routeAtKey = longPreferencesKey("routeAt")

    /** Called once at start-up; the first DataStore read is quick. */
    fun load() = runBlocking {
        val prefs = context.dataStore.data.first()
        token = prefs[tokenKey]
        pushToken = prefs[pushKey]
        lastRoute = prefs[routeKey]
        lastRouteAt = prefs[routeAtKey] ?: 0L
        batteryAsked = prefs[batteryKey] == "1"
    }

    /** Whether the battery dialog has been shown on this phone. */
    @Volatile
    var batteryAsked: Boolean = false
        private set

    suspend fun markBatteryAsked() {
        batteryAsked = true
        context.dataStore.edit { it[batteryKey] = "1" }
    }

    /** Remembered so a restart (the system killing the app in the background) comes back to the same screen. */
    suspend fun saveRoute(route: String) {
        lastRoute = route
        lastRouteAt = System.currentTimeMillis()
        context.dataStore.edit {
            it[routeKey] = route
            it[routeAtKey] = lastRouteAt
        }
    }

    suspend fun save(newToken: String) {
        token = newToken
        context.dataStore.edit { it[tokenKey] = newToken }
    }

    suspend fun savePushToken(value: String) {
        pushToken = value
        context.dataStore.edit { it[pushKey] = value }
    }

    suspend fun clear() {
        token = null
        lastRoute = null
        context.dataStore.edit {
            it.remove(tokenKey)
            it.remove(routeKey)
            it.remove(routeAtKey)
        }
    }

    val signedIn: Boolean get() = !token.isNullOrBlank()
}

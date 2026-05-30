package xyz.lisfox.big.notenoughfakeqqbot.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AppPreferences(private val context: Context) {
    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_TOKEN = stringPreferencesKey("token")
        private val KEY_CURRENT_PLATFORM = stringPreferencesKey("current_platform")
        private val KEY_CURRENT_SELF_ID = stringPreferencesKey("current_self_id")
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_IMAGE_CACHE_SIZE_MB = intPreferencesKey("image_cache_size_mb")
        private val KEY_IS_CONFIGURED = booleanPreferencesKey("is_configured")
        private val KEY_QUICK_PHRASES = stringSetPreferencesKey("quick_phrases")
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { it[KEY_SERVER_URL] ?: "" }
    val token: Flow<String> = context.dataStore.data.map { it[KEY_TOKEN] ?: "" }
    val currentPlatform: Flow<String> = context.dataStore.data.map { it[KEY_CURRENT_PLATFORM] ?: "" }
    val currentSelfId: Flow<String> = context.dataStore.data.map { it[KEY_CURRENT_SELF_ID] ?: "" }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIFICATIONS_ENABLED] ?: true }
    val imageCacheSizeMb: Flow<Int> = context.dataStore.data.map { it[KEY_IMAGE_CACHE_SIZE_MB] ?: 200 }
    val isConfigured: Flow<Boolean> = context.dataStore.data.map { it[KEY_IS_CONFIGURED] ?: false }
    val quickPhrases: Flow<Set<String>> = context.dataStore.data.map { it[KEY_QUICK_PHRASES] ?: emptySet() }

    suspend fun setServerConfig(url: String, token: String) {
        context.dataStore.edit {
            it[KEY_SERVER_URL] = url
            it[KEY_TOKEN] = token
            it[KEY_IS_CONFIGURED] = true
        }
    }

    suspend fun setCurrentBot(platform: String, selfId: String) {
        context.dataStore.edit {
            it[KEY_CURRENT_PLATFORM] = platform
            it[KEY_CURRENT_SELF_ID] = selfId
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setImageCacheSizeMb(size: Int) {
        context.dataStore.edit { it[KEY_IMAGE_CACHE_SIZE_MB] = size }
    }

    suspend fun addQuickPhrase(phrase: String) {
        context.dataStore.edit {
            val current = it[KEY_QUICK_PHRASES] ?: emptySet()
            it[KEY_QUICK_PHRASES] = current + phrase
        }
    }

    suspend fun removeQuickPhrase(phrase: String) {
        context.dataStore.edit {
            val current = it[KEY_QUICK_PHRASES] ?: emptySet()
            it[KEY_QUICK_PHRASES] = current - phrase
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}

package dev.clagon.xact

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

/** 端末内に保存するのは接続先 URL と Main Chat の Session ID だけ（ADR 0004）。 */
class Settings(
    context: Context,
) {
    private val store = context.dataStore

    val baseUrl: Flow<String?> = store.data.map { it[BASE_URL] }

    suspend fun setBaseUrl(url: String) {
        store.edit {
            it[BASE_URL] = url
            it.remove(MAIN_SESSION_ID)
        }
    }

    suspend fun mainSessionId(): String? = store.data.first()[MAIN_SESSION_ID]

    suspend fun setMainSessionId(id: String) {
        store.edit { it[MAIN_SESSION_ID] = id }
    }

    private companion object {
        val BASE_URL = stringPreferencesKey("base_url")
        val MAIN_SESSION_ID = stringPreferencesKey("main_session_id")
    }
}

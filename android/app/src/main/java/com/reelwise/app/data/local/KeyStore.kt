package com.reelwise.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.reelwise.app.engine.KeyProvider
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The person's own TMDB, Groq and OMDb keys. Kept in their own DataStore file ("secrets"), which the
 * backup rules leave out, so the keys never leave the phone in a cloud backup or device transfer.
 */
@Singleton
class KeyStore @Inject constructor(@Named("secrets") private val store: DataStore<Preferences>) : KeyProvider {
    val hasTmdbKey: Flow<Boolean> = store.data.map { !it[TMDB].isNullOrBlank() }

    override suspend fun tmdbKey(): String? = store.data.first()[TMDB]
    override suspend fun groqKey(): String? = store.data.first()[GROQ]
    override suspend fun omdbKey(): String? = store.data.first()[OMDB]

    suspend fun save(tmdb: String, groq: String?, omdb: String?) {
        store.edit {
            it[TMDB] = tmdb.trim()
            if (groq.isNullOrBlank()) it.remove(GROQ) else it[GROQ] = groq.trim()
            if (omdb.isNullOrBlank()) it.remove(OMDB) else it[OMDB] = omdb.trim()
        }
    }

    private companion object {
        val TMDB = stringPreferencesKey("tmdb_key")
        val GROQ = stringPreferencesKey("groq_key")
        val OMDB = stringPreferencesKey("omdb_key")
    }
}

package com.reelwise.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.reelwise.app.BuildConfig
import com.reelwise.app.data.Clock
import com.reelwise.app.data.DefaultRepository
import com.reelwise.app.data.Repository
import com.reelwise.app.data.local.KeyStore
import com.reelwise.app.data.local.ReelwiseDatabase
import com.reelwise.app.data.remote.LocalReelwiseApi
import com.reelwise.app.data.remote.ReelwiseApi
import com.reelwise.app.engine.KeyProvider
import com.reelwise.app.engine.QueryParser
import com.reelwise.app.engine.Recommender
import com.reelwise.app.engine.TmdbClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                // BASIC logs URLs; a v3 TMDB key rides in the query, so never log in release builds.
                addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
        }
        .build()

    @Provides
    @Singleton
    fun tmdb(client: OkHttpClient, keys: KeyProvider, json: Json): TmdbClient = TmdbClient(client, keys, json)

    @Provides
    @Singleton
    fun api(client: OkHttpClient, keys: KeyProvider, json: Json, tmdb: TmdbClient): ReelwiseApi =
        LocalReelwiseApi(tmdb, Recommender(tmdb), QueryParser(client, keys, json))

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ReelwiseDatabase =
        Room.databaseBuilder(context, ReelwiseDatabase::class.java, "reelwise.db").build()

    @Provides
    @Singleton
    @Named("settings")
    fun settingsStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    /** Separate file so backup rules can leave the keys out. */
    @Provides
    @Singleton
    @Named("secrets")
    fun secretsStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("secrets") }

    @Provides
    fun clock(): Clock = Clock { System.currentTimeMillis() }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun repository(impl: DefaultRepository): Repository

    @Binds
    abstract fun keyProvider(impl: KeyStore): KeyProvider
}

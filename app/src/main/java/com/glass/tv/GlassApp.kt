package com.glass.tv

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.glass.tv.data.Favorites
import com.glass.tv.data.Progress
import com.glass.tv.data.Repo
import com.glass.tv.data.SecureStore
import com.glass.tv.data.Settings
import com.glass.tv.data.Tmdb
import com.glass.tv.player.PlaybackQueue
import com.glass.tv.record.RecordingStore
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class GlassApp : Application(), ImageLoaderFactory {

    lateinit var http: OkHttpClient
        private set
    lateinit var vault: SecureStore
        private set
    lateinit var settings: Settings
        private set
    lateinit var repo: Repo
        private set
    lateinit var favorites: Favorites
        private set
    lateinit var progress: Progress
        private set
    lateinit var recordings: RecordingStore
        private set
    lateinit var tmdb: Tmdb
        private set
    lateinit var updater: com.glass.tv.data.Updater
        private set
    lateinit var recents: com.glass.tv.data.RecentChannels
        private set
    lateinit var folders: com.glass.tv.data.Folders
        private set
    lateinit var epg: com.glass.tv.data.EpgStore
        private set
    /** Sección Descubrir: addons compatibles con Stremio y cuentas debrid. */
    lateinit var discover: com.glass.tv.addons.Discover
        private set
    /** La app abre una pantalla suya (reproductor): no cuenta como salir para volver a bloquear carpetas. */
    @Volatile var internalNav = false
    val queue = PlaybackQueue()

    override fun onCreate() {
        super.onCreate()
        instance = this
        http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                // Identificación genérica y honesta del cliente; nunca se registran URLs.
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
            .build()
        vault = SecureStore(this)
        settings = Settings(this)
        repo = Repo(this, http, vault)
        favorites = Favorites(this)
        progress = Progress(this)
        recordings = RecordingStore(this)
        tmdb = Tmdb(http)
        updater = com.glass.tv.data.Updater(this, http)
        recents = com.glass.tv.data.RecentChannels(this)
        folders = com.glass.tv.data.Folders(this)
        epg = com.glass.tv.data.EpgStore(this, http)
        discover = com.glass.tv.addons.Discover(this, http, vault, settings)
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_REC, "Grabaciones", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(http)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("img"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()

    companion object {
        const val CHANNEL_REC = "rec"
        const val USER_AGENT = "Glass/0.1 (Android TV)"
        lateinit var instance: GlassApp
            private set
    }
}

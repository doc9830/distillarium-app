package ru.distillarium

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import ru.distillarium.batches.BatchRepository
import ru.distillarium.content.ContentRepository
import ru.distillarium.content.HttpContentClient
import ru.distillarium.core.content.ContentInstaller
import ru.distillarium.core.content.ContentUpdater
import ru.distillarium.core.content.FileEtagStore
import ru.distillarium.knowledge.WikiRepository

/** Собирает зависимости приложения. Без DI-фреймворков: их пока нечем оправдать. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val httpContentClient = HttpContentClient(
        client = httpClient,
        userAgent = "Distillarium/${BuildConfig.VERSION_NAME} (Android)",
    )
    private val installer = ContentInstaller(
        rootDir = appContext.filesDir,
        appVersion = BuildConfig.VERSION_NAME,
    )
    private val manifestUrl = BuildConfig.CONTENT_MANIFEST_URL
    private val packUrl = if (manifestUrl.isBlank()) {
        ""
    } else {
        manifestUrl.substringBefore('?').substringBeforeLast('/') + "/content-pack.zip"
    }

    val contentRepository: ContentRepository = ContentRepository(
        context = appContext,
        installer = installer,
        updater = ContentUpdater(
            installer = installer,
            manifestFetcher = httpContentClient,
            packDownloader = httpContentClient,
            etagStore = FileEtagStore(File(appContext.filesDir, "content/etag.txt")),
        ),
        manifestUrl = manifestUrl,
        packUrl = packUrl,
    )

    val wikiRepository: WikiRepository = WikiRepository(contentRepository)
    val batchRepository: BatchRepository = BatchRepository(appContext)
}

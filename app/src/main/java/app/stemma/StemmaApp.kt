package app.stemma

import android.app.Application
import android.content.Context
import android.os.Build
import app.stemma.data.EntryRepository
import app.stemma.data.ExploreRepository
import app.stemma.data.Library
import app.stemma.data.RecentEntries
import app.stemma.data.SettingsStore
import app.stemma.data.WiktionaryApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

open class StemmaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
    }

    /** Tests override this to point the app at a local fake server. */
    protected open fun createContainer(): AppContainer = AppContainer(this)
}

/** Creates the app's long-lived objects once. */
class AppContainer(context: Context, wiktionaryUrl: HttpUrl = WIKTIONARY_URL.toHttpUrl()) {
    val appScope = CoroutineScope(SupervisorJob())

    /**
     * Wikimedia asks API clients to identify themselves with a User-Agent that includes
     * contact information: https://foundation.wikimedia.org/wiki/Policy:User-Agent_policy
     */
    val userAgent: String =
        "Stemma/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; $PROJECT_URL)"

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http"), 30L * 1024 * 1024))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
        }
        .build()

    val api = WiktionaryApi(httpClient, wiktionaryUrl)
    val settings = SettingsStore(context)
    val library = Library(
        file = File(context.filesDir, "library.json"),
        offlineDir = File(context.filesDir, "offline"),
        scope = appScope,
    )
    val entries = EntryRepository(
        api = api,
        library = library,
        scope = appScope,
        recent = RecentEntries(File(context.cacheDir, "entries")),
        preferredLanguage = { settings.settings.first().preferredLanguage },
    )
    val explore = ExploreRepository(api, File(context.filesDir, "explore.json"))

    companion object {
        const val WIKTIONARY_URL = "https://en.wiktionary.org/"

        /** Where Wikimedia (or anyone) can find out about the app and contact its developer. */
        const val PROJECT_URL = "https://github.com/ML-Chen/stemma"
    }
}

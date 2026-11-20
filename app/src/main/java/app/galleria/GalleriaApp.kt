package app.galleria

import android.app.Application
import app.galleria.data.MediaDatabase
import app.galleria.data.MediaRepository
import app.galleria.data.Settings
import app.galleria.media.ThumbnailFetcher
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** Hand-wired dependencies (D-032: no Hilt). */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob())
    val database = MediaDatabase.create(app)
    val settings = Settings(app)
    val media = MediaRepository(app, database, scope)
}

class GalleriaApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(ThumbnailFetcher.Factory(contentResolver))
                add(ThumbnailFetcher.Key())
            }
            .build()
}

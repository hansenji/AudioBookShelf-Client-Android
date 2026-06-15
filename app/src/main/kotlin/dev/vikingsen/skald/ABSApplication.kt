package dev.vikingsen.skald

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import androidx.appfunctions.service.AppFunctionConfiguration
import dev.vikingsen.skald.appfunctions.SkaldAppFunctions
import dev.vikingsen.skald.di.appModule
import io.ktor.client.HttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import kotlinx.coroutines.launch

class ABSApplication : Application(), SingletonImageLoader.Factory, AppFunctionConfiguration.Provider {
    override val appFunctionConfiguration: AppFunctionConfiguration
        get() = AppFunctionConfiguration.Builder()
            .addEnclosingClassFactory(SkaldAppFunctions::class.java) {
                GlobalContext.get().get<SkaldAppFunctions>()
            }
            .build()
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@ABSApplication)
            modules(appModule)
        }

        val repository = org.koin.core.context.GlobalContext.get().get<dev.vikingsen.skald.domain.repository.AudiobookshelfRepository>()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            repository.scanAndRelinkDownloads()
        }
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        val httpClient = GlobalContext.get().get<HttpClient>()
        return ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory(httpClient))
            }
            .build()
    }
}

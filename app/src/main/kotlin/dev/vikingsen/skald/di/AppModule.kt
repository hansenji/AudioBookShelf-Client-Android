package dev.vikingsen.skald.di

import dev.vikingsen.skald.core.database.coreDatabaseModule
import dev.vikingsen.skald.core.network.coreNetworkModule
import dev.vikingsen.skald.core.player.corePlayerModule
import dev.vikingsen.skald.core.preferences.corePreferencesModule
import dev.vikingsen.skald.data.dataModule
import dev.vikingsen.skald.domain.domainModule
import dev.vikingsen.skald.feature.library.featureLibraryModule
import dev.vikingsen.skald.feature.login.featureLoginModule
import dev.vikingsen.skald.feature.player.featurePlayerModule
import dev.vikingsen.skald.feature.androidauto.featureAndroidAutoModule
import dev.vikingsen.skald.feature.miniplayer.featureMiniPlayerModule
import dev.vikingsen.skald.feature.home.featureHomeModule
import dev.vikingsen.skald.feature.settings.featureSettingsModule
import dev.vikingsen.skald.MainViewModel
import dev.vikingsen.skald.appfunctions.SkaldAppFunctions
import org.koin.dsl.module
import org.koin.plugin.module.dsl.viewModel

val appModule = module {
    includes(
        corePreferencesModule,
        coreDatabaseModule,
        coreNetworkModule,
        corePlayerModule,
        domainModule,
        dataModule,
        featureLoginModule,
        featureLibraryModule,
        featurePlayerModule,
        featureHomeModule,
        featureSettingsModule,
        featureAndroidAutoModule,
        featureMiniPlayerModule
    )

    viewModel<MainViewModel>()

    single {
        SkaldAppFunctions(
            playerManager = get(),
            repository = get(),
            settingsRepository = get(),
            fetchBookDetailsUseCase = get(),
            downloadAudioFileUseCase = get(),
            getPlaylistsUseCase = get()
        )
    }
}


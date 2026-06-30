package dev.vikingsen.skald.core.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import org.koin.android.ext.android.inject

class AudiobookPlayerService : MediaLibraryService() {
    private val player: Player by inject()
    private val sessionCallback: MediaLibrarySession.Callback by inject()
    private var mediaLibrarySession: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            action = "dev.vikingsen.skald.ACTION_PLAYER"
        }
        val pendingIntent = intent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = MediaLibrarySession.Builder(
            this,
            player,
            sessionCallback
        )
        if (pendingIntent != null) {
            builder.setSessionActivity(pendingIntent)
        }
        mediaLibrarySession = builder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        mediaLibrarySession?.run {
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }
}

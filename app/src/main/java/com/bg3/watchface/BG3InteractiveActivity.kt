package com.bg3.watchface

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import com.bg3.watchface.view.BG3InteractiveView

/**
 * Fullscreen Interactive Activity for Baldur's Gate 3 Wear OS.
 * Can be launched directly from the watch app drawer or by tapping the D20 on the watch face.
 * Runs at 60 FPS with full 3D tumbling D20 physics, particle bursts, and touch interactions.
 */
class BG3InteractiveActivity : Activity() {

    companion object {
        const val ACTION_APPLY_WATCH_FACE = "com.bg3.watchface.action.APPLY_WATCH_FACE"
    }

    private lateinit var interactiveView: BG3InteractiveView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == ACTION_APPLY_WATCH_FACE) {
            try {
                val wallpaperIntent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@BG3InteractiveActivity, BG3WatchFaceService::class.java)
                    )
                }
                startActivity(wallpaperIntent)
                finish()
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        interactiveView = BG3InteractiveView(this)
        setContentView(interactiveView)
    }

    override fun onResume() {
        super.onResume()
        if (::interactiveView.isInitialized) {
            interactiveView.invalidate()
        }
    }
}

package com.bg3.watchface

import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyleSchema
import com.bg3.watchface.renderer.BG3CanvasRenderer

/**
 * Main Service entry point for the Baldur's Gate 3 Wear OS Watch Face.
 * Implements Jetpack WatchFaceService lifecycle and hooks up the custom CanvasRenderer.
 */
class BG3WatchFaceService : WatchFaceService() {

    override fun createUserStyleSchema(): UserStyleSchema {
        // Can be extended with UserStyleSetting for customizable color themes (e.g. Illithid purple vs Arcane Gold)
        return UserStyleSchema(emptyList())
    }

    override fun createComplicationSlotsManager(
        currentUserStyleRepository: CurrentUserStyleRepository
    ): ComplicationSlotsManager {
        // Returns the ComplicationSlotsManager (Complications can also be attached here)
        return ComplicationSlotsManager(emptyList(), currentUserStyleRepository)
    }

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace {
        val renderer = BG3CanvasRenderer(
            context = applicationContext,
            surfaceHolder = surfaceHolder,
            currentUserStyleRepository = currentUserStyleRepository,
            watchState = watchState,
            canvasType = CanvasType.HARDWARE
        )

        return WatchFace(
            watchFaceType = WatchFaceType.DIGITAL,
            renderer = renderer
        ).setTapListener(renderer)
    }
}

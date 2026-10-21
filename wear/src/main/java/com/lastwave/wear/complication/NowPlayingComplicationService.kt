package com.lastwave.wear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.lastwave.wear.R
import com.lastwave.wear.sync.WearDataLayerListenerService
import com.lastwave.wear.ui.MainActivity

class NowPlayingComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("Music").build(),
            contentDescription = PlainComplicationText.Builder("LastWave Now Playing").build()
        ).build()
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) {
            return null
        }

        val state = WearDataLayerListenerService.phonePlaybackState.value
        val label = if (state.isPlaying && state.title.isNotBlank()) {
            state.title.take(8)
        } else {
            "LastWave"
        }

        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(label).build(),
            contentDescription = PlainComplicationText.Builder("LastWave Music").build()
        )
            .setMonochromaticImage(
                MonochromaticImage.Builder(
                    Icon.createWithResource(this, R.drawable.ic_tile_music)
                ).build()
            )
            .setTapAction(tapIntent)
            .build()
    }
}

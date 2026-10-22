package com.nube.physiohelper.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.nube.physiohelper.data.Exercise

sealed class Media {
    data class Video(val uri: Uri) : Media()
    data class Image(val uri: Uri) : Media()
}

object MediaLoader {
    @Volatile private var loader: ImageLoader? = null
    fun get(ctx: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: ImageLoader.Builder(ctx.applicationContext)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .build().also { loader = it }
    }

    /**
     * Media for an exercise: a clip the user attached in the app wins; otherwise a bundled
     * file in res/raw named after the exercise id (e.g. res/raw/prone_y.mp4 or prone_y.gif).
     */
    fun resolve(ctx: Context, ex: Exercise): Media? {
        if (ex.mediaUri.isNotBlank()) {
            val uri = Uri.parse(ex.mediaUri)
            val type = runCatching { ctx.contentResolver.getType(uri) }.getOrNull() ?: ""
            return if (type.startsWith("video")) Media.Video(uri) else Media.Image(uri)
        }
        @Suppress("DiscouragedApi")
        val id = ctx.resources.getIdentifier(ex.id, "raw", ctx.packageName)
        if (id == 0) return null
        val uri = Uri.parse("android.resource://${ctx.packageName}/$id")
        val h = runCatching {
            ctx.resources.openRawResource(id).use { s -> ByteArray(12).also { s.read(it) } }
        }.getOrNull() ?: return null
        fun at(i: Int, c: Char) = h[i] == c.code.toByte()
        val isGif = at(0, 'G') && at(1, 'I') && at(2, 'F')
        val isPng = at(1, 'P') && at(2, 'N') && at(3, 'G')
        val isWebp = at(0, 'R') && at(1, 'I') && at(8, 'W') && at(9, 'E')
        val isJpg = h[0] == 0xFF.toByte() && h[1] == 0xD8.toByte()
        return if (isGif || isPng || isWebp || isJpg) Media.Image(uri) else Media.Video(uri)
    }
}

@Composable
fun ExerciseMedia(ex: Exercise, height: Dp = 220.dp) {
    val ctx = LocalContext.current
    val media = remember(ex.id, ex.mediaUri) { MediaLoader.resolve(ctx, ex) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when (media) {
            null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp),
            ) {
                Text("No animation yet", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (ex.videoUrl.isNotBlank()) {
                    TextButton(onClick = { openUrl(ctx, ex.videoUrl) }) { Text("Open reference video") }
                }
            }
            is Media.Video -> key(media.uri) {
                AndroidView(
                    factory = { c ->
                        VideoView(c).apply {
                            setVideoURI(media.uri)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                mp.setVolume(0f, 0f)
                                start()
                            }
                            setOnErrorListener { _, _, _ -> true }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is Media.Image -> AsyncImage(
                model = ImageRequest.Builder(ctx).data(media.uri).build(),
                contentDescription = ex.name,
                imageLoader = MediaLoader.get(ctx),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

package app.galleria.media

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.Dimension
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A thumbnail of a photo or a video, by MediaStore id.
 * [aspect] is long side / short side (≥ 1), see [thumbnailAspect].
 */
data class Thumbnail(val id: Long, val volume: String, val isVideo: Boolean, val aspect: Float = 1f)

/** Long side / short side from MediaStore's size, capped for panoramas; 1 if unknown. */
fun thumbnailAspect(width: Int?, height: Int?): Float {
    if (width == null || height == null || width <= 0 || height <= 0) return 1f
    return (maxOf(width, height).toFloat() / minOf(width, height)).coerceAtMost(MAX_ASPECT)
}

private const val MAX_ASPECT = 4f

/**
 * Whether the system thumbnail is sharp enough: tiles are cropped squares, so its short side
 * must cover the tile (a little tolerance avoids decoding the original for a few pixels).
 */
fun isSharpEnough(width: Int, height: Int, tilePx: Int): Boolean = min(width, height) >= tilePx * 0.9f

/**
 * Loads thumbnails, never from the network:
 * 1. Android's own thumbnail (`ContentResolver.loadThumbnail`): fast and cached by the system.
 * 2. If that is too small for the tile (camera photos often get the tiny 108×192 preview stored
 *    inside the JPEG) or fails, the original is decoded at the right size: photos with
 *    `ImageDecoder` (which applies the EXIF rotation), videos with a frame from the file.
 */
class ThumbnailFetcher(
    private val data: Thumbnail,
    private val options: Options,
    private val resolver: ContentResolver,
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val collection = if (data.isVideo) {
            MediaStore.Video.Media.getContentUri(data.volume)
        } else {
            MediaStore.Images.Media.getContentUri(data.volume)
        }
        val uri = ContentUris.withAppendedId(collection, data.id)
        val px = (options.size.width as? Dimension.Pixels)?.px ?: DEFAULT_PX

        // loadThumbnail fits the image *inside* the box: ask for a box as large as the long side
        // will be. The aspect is used on both sides because MediaStore ignores the photo rotation.
        val box = (px * data.aspect).toInt()
        val system = runCatching { resolver.loadThumbnail(uri, Size(box, box), null) }.getOrNull()

        val bitmap = when {
            system != null && isSharpEnough(system.width, system.height, px) -> system
            data.isVideo -> runCatching { videoFrame(uri, px) }.getOrNull() ?: system
            else -> runCatching { decodeImage(uri, px) }.getOrNull() ?: system
        } ?: error("No thumbnail for $uri")

        ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    /** Decodes the photo so that its short side is [px]; the original file is only read. */
    private fun decodeImage(uri: Uri, px: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = px.toFloat() / min(w, h)
            if (scale < 1f) {
                decoder.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
            }
        }

    /** A frame from the video, short side about [px]. */
    private fun videoFrame(uri: Uri, px: Int): Bitmap? =
        resolver.openFileDescriptor(uri, "r")?.use { fd ->
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(fd.fileDescriptor)
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                if (w == null || h == null || w <= 0 || h <= 0) {
                    retriever.frameAtTime
                } else {
                    val scale = (px.toFloat() / min(w, h)).coerceAtMost(1f)
                    retriever.getScaledFrameAtTime(
                        -1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        (w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        }

    class Factory(private val resolver: ContentResolver) : Fetcher.Factory<Thumbnail> {
        override fun create(data: Thumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbnailFetcher(data, options, resolver)
    }

    class Key : Keyer<Thumbnail> {
        override fun key(data: Thumbnail, options: Options): String =
            "thumb:${data.volume}:${data.id}:${options.size}"
    }

    private companion object {
        const val DEFAULT_PX = 256
    }
}

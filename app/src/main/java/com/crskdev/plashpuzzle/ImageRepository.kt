package com.crskdev.plashpuzzle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.*
import java.net.HttpURLConnection
import java.net.HttpURLConnection.*
import java.net.URL
import java.security.MessageDigest


/**
 * Created by Cristian Pela on 15.07.2019.
 */
interface ImageRepository {

    fun fetch(url: String): Flow<Bitmap>

    fun save(url: String): Flow<String>

    fun cancelFetch(): Flow<Unit>
}


@Suppress("NON_APPLICABLE_CALL_FOR_BUILDER_INFERENCE")
@ExperimentalCoroutinesApi
class ImageRepositoryImpl(private val context: Context) : ImageRepository {

    @Volatile
    private var fetchJob: Job? = null

    override fun fetch(url: String): Flow<Bitmap> = channelFlow {
        val baseUrl = URL(url)
        val conn = (baseUrl.openConnection() as HttpURLConnection)
        try {
            val cache = File(context.cacheDir, "plashPuzzleCache")
            if (!cache.exists()) {
                assert(cache.mkdir()) { "Cache folder was not created" }
            }
            val cachFile = File(cache, fileNameHash(url))
            if (cachFile.exists()) {
                val bitmap: Bitmap? =
                    BitmapFactory.decodeFile(cachFile.absolutePath, BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    })
                bitmap?.let {
                    offer(it)
                    close()
                }
                    ?: close(PromptableException("Could not load bitmap from file ${cachFile.absolutePath}"))
            } else {
                fetchJob = synchronized(this@ImageRepositoryImpl) { Job(coroutineContext[Job]) }
                val bitmap = withContext(fetchJob!!) {
                    var bitmap: Bitmap?
                    var tries = 0
                    var backOff = 0L
                    do {
                        bitmap = openConnection(url).autoConnect {
                            BufferedInputStream(inputStream, 8192).use { stream ->
                                BitmapFactory.decodeStream(stream)
                            }
                        }
                        if (bitmap != null)
                            break
                        backOff += 500L // linear backoff
                        delay(backOff)
                    } while (++tries < 5)
                    bitmap
                }
                //clear cache: it should be one file only
                if (bitmap == null) {
                    close(PromptableException("Image not fetched"))
                } else {
                    cache.listFiles { f -> f.delete() }
                    cachFile.outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
                    }
                    offer(bitmap!!)
                    close()
                }
            }
        } catch (ex: Exception) {
            close(PromptableException(ex))
        } finally {
            conn.disconnect()
        }
    }

    private fun fileNameHash(url: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .apply { update(url.toByteArray()) }
            .digest()
            .let {
                buildString {
                    for (element in it) {
                        append(Integer.toHexString(0xFF and element.toInt()))
                    }
                }
            }

    @Throws(IOException::class)
    private inline fun openConnection(url: String, settings: HttpURLConnection.() -> Unit = {
        connectTimeout = 150000
        readTimeout = 15000
        // allowUserInteraction = true
        instanceFollowRedirects = false
    }): CloseableHttpConnection {
        var redirectedUrl = url
        var connection: HttpURLConnection
        var redirected: Boolean
        do {
            connection = (URL(redirectedUrl).openConnection() as HttpURLConnection).apply(settings)
            val code = connection.responseCode
            redirected =
                code == HTTP_MOVED_PERM || code == HTTP_MOVED_TEMP || code == HTTP_SEE_OTHER
            if (redirected) {
                val connUrl = connection.url
                val location = connection.getHeaderField("Location").substring(1)
                redirectedUrl = Uri.Builder()
                    .scheme(connUrl.protocol)
                    .authority(connUrl.authority)
                    .appendEncodedPath(location).build().toString()
                connection.disconnect()
            }
        } while (redirected)
        return CloseableHttpConnection(connection)
    }

    override fun save(url: String): Flow<String> = channelFlow {
        val cache = File(context.cacheDir, "plashPuzzleCache")
        if (!cache.exists()) {
            channel.close(PromptableException("Could not save file"))
        }
        val cachedFile = File(cache, fileNameHash(url))
        if (!cachedFile.exists()) {
            channel.close(PromptableException("Could not save file"))
        } else {
            val resource = BitmapFactory.decodeFile(cachedFile.absolutePath)
            val gallery = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            ).toString()
            val appFolder = File(gallery, "PlashPuzzle")
            if (!appFolder.exists()) {
                appFolder.mkdir()
            }
            val galleryFile = File(appFolder, "plashPuzzle_${System.currentTimeMillis()}.jpg")
            FileOutputStream(galleryFile).use {
                try {
                    resource.compress(Bitmap.CompressFormat.JPEG, 90, it)
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(galleryFile.absolutePath),
                        arrayOf("image/jpeg")
                    ) { _, uri ->
                        uri?.also {
                            channel.offer(uri.toString())
                            channel.close()
                        } ?: channel.close(NullPointerException("Uri saved to gallery is Null"))
                    }
                } catch (e: Exception) {
                    channel.close(PromptableException(e))
                }
            }
        }
        awaitClose()
    }

    override fun cancelFetch(): Flow<Unit> = flow {
        fetchJob?.cancel()
        emit(Unit)
    }
}


//private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
//    // Raw height and width of image
//    val (height: Int, width: Int) = options.run { outHeight to outWidth }
//    var inSampleSize = 1
//    if (height > reqHeight || width > reqWidth) {
//        val halfHeight: Int = height / 2
//        val halfWidth: Int = width / 2
//        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
//        // height and width larger than the requested height and width.
//        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
//            inSampleSize *= 2
//        }
//    }
//    return inSampleSize
//}
//
//private fun decodeBitmap(
//    assets: AssetManager,
//    location: String,
//    reqWidth: Int,
//    reqHeight: Int
//): Bitmap? {
//    // First decode with inJustDecodeBounds=true to check dimensions
//    return BitmapFactory.Options().run {
//        inJustDecodeBounds = true
//        assets
//            .open(location).use {
//                BitmapFactory.decodeStream(it, null, this)
//            }
//
//        // Calculate inSampleSize
//        inSampleSize = calculateInSampleSize(this, reqWidth, reqHeight)
//
//        // Decode pieceInfo with inSampleSize set
//        inJustDecodeBounds = false
//
//        assets
//            .open(location).use {
//                BitmapFactory.decodeStream(it, null, this)
//            }
//    }
//}


class CloseableHttpConnection(val connection: HttpURLConnection) : Closeable {
    override fun close() {
        connection.disconnect()
    }
}

inline fun <T> CloseableHttpConnection.autoConnect(block: HttpURLConnection.() -> T): T =
    use { block(it.connection) }




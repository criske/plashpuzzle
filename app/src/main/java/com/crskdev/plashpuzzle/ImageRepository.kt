package com.crskdev.plashpuzzle

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Environment
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import java.io.FileOutputStream


/**
 * Created by Cristian Pela on 15.07.2019.
 */

interface ImageRepository {

    fun fetch(url: String): Flow<Bitmap>

    fun save(url: String): Flow<String>
}


@Suppress("NON_APPLICABLE_CALL_FOR_BUILDER_INFERENCE")
@ExperimentalCoroutinesApi
class ImageRepositoryImpl(private val context: Context) : ImageRepository {

    private val requestManager  = GlideApp.with(context)

    private val requestBuilder by lazy{
        requestManager
            .asBitmap()
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .skipMemoryCache(true)
    }


    override fun fetch(url: String): Flow<Bitmap> = callbackFlow {

        val target = object : SimpleTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                offer(resource)
                channel.close()
            }
        }
        requestBuilder
            .load(url)
            .addListener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(e: GlideException?,
                                          model: Any?,
                                          target: Target<Bitmap>?,
                                          isFirstResource: Boolean): Boolean {
                    channel.close(PromptableException(e))
                    return true
                }

                override fun onResourceReady(resource: Bitmap?,
                                             model: Any?,
                                             target: Target<Bitmap>?,
                                             dataSource: DataSource?,
                                             isFirstResource: Boolean): Boolean = false

            })
            .into(target)
        awaitClose {
            requestManager.clear(target)
        }
    }


    override fun save(url: String): Flow<String> = callbackFlow {

        val target = object : SimpleTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {

                val gallery = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES
                ).toString()
                val appFolder = File(gallery, "PlashPuzzle")
                if (!appFolder.exists()) {
                    appFolder.mkdir()
                }
                val file = File(appFolder, "plashPuzzle_${System.currentTimeMillis()}.jpg")
                val outputStream = FileOutputStream(file)
                try {
                    resource.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.toString()),
                        arrayOf("image/jpeg")
                    ) { _, uri ->
                        channel.offer(uri.toString())
                        channel.close()
                    }
                } catch (e: Exception) {
                    channel.close(e)
                } finally {
                    outputStream.close()
                }
            }
        }

        requestBuilder
            .load(url)
            .addListener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(e: GlideException?,
                                          model: Any?,
                                          target: Target<Bitmap>?,
                                          isFirstResource: Boolean): Boolean {
                    channel.close(PromptableException(e))
                    return true
                }
                override fun onResourceReady(resource: Bitmap?,
                                             model: Any?,
                                             target: Target<Bitmap>?,
                                             dataSource: DataSource?,
                                             isFirstResource: Boolean): Boolean = false

            })
            .into(target)
        awaitClose { requestManager }
    }

}


private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    // Raw height and width of image
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2
        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
        // height and width larger than the requested height and width.
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

private fun decodeBitmap(
    assets: AssetManager,
    location: String,
    reqWidth: Int,
    reqHeight: Int
): Bitmap? {
    // First decode with inJustDecodeBounds=true to check dimensions
    return BitmapFactory.Options().run {
        inJustDecodeBounds = true
        assets
            .open(location).use {
                BitmapFactory.decodeStream(it, null, this)
            }

        // Calculate inSampleSize
        inSampleSize = calculateInSampleSize(this, reqWidth, reqHeight)

        // Decode pieceInfo with inSampleSize set
        inJustDecodeBounds = false

        assets
            .open(location).use {
                BitmapFactory.decodeStream(it, null, this)
            }
    }
}


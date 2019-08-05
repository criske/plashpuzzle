package com.crskdev.plashpuzzle

import android.content.Context
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.module.AppGlideModule

@GlideModule
class PlashPuzzleGlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        val diskCacheSizeBytes = 1024 * 1024 * 1// 1 MB
        builder.setDiskCache(
            InternalCacheDiskCacheFactory(
                context,
                diskCacheSizeBytes.toLong()
            )
        )
    }
}
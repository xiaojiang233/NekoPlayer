package top.xiaojiang233.nekoplayer

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import top.xiaojiang233.nekoplayer.utils.AudioCoverFetcher

class NekoPlayerApplication : Application(), ImageLoaderFactory {

    init {
        instance = this
    }

    companion object {
        private var instance: NekoPlayerApplication? = null

        fun getAppContext(): Context = instance!!.applicationContext
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(AudioCoverFetcher.Factory())
            }
            .build()
    }
}

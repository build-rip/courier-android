package rip.build.courier

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CourierApplication : Application(), ImageLoaderFactory {
    @Inject lateinit var imageLoader: ImageLoader
    override fun newImageLoader() = imageLoader
}

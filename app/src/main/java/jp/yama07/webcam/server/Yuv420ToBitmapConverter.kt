package jp.yama07.webcam.server

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import jp.yama07.webcam.util.ImageUtil

class Yuv420ToBitmapConverter(val handler: Handler?) {

  fun enqueue(image: Yuv420Image): LiveData<Bitmap> {
    val liveData = MutableLiveData<Bitmap>()
    (handler ?: Handler(Looper.myLooper())).post {
      val bitmap = execute(image)
      liveData.postValue(bitmap)
    }

    return liveData
  }

  fun execute(image: Yuv420Image): Bitmap {
    val rgbBytes = IntArray(image.size.width * image.size.height)
    ImageUtil.convertYuv420ToArgb8888(
      image.yuvBytes,
      image.size,
      image.yRowStride,
      image.uvRowStride,
      image.uvPixelStride,
      rgbBytes
    )
    return Bitmap
      .createBitmap(image.size.width, image.size.height, Bitmap.Config.ARGB_8888)
      .also { bmp ->
        bmp.setPixels(
          rgbBytes, 0, image.size.width, 0, 0,
          image.size.width, image.size.height
        )
      }
  }
}
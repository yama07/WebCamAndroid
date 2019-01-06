package jp.yama07.webcam.server

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.os.Handler
import android.os.Looper
import android.renderscript.*
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class Yuv420ToBitmapConverter(val handler: Handler?, val context: Context) {

  private val rs = RenderScript.create(context)
  private val yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))

  fun enqueue(image: Image): LiveData<Bitmap> {
    val liveData = MutableLiveData<Bitmap>()
    (handler ?: Handler(Looper.myLooper())).post {
      val bitmap = execute(image)
      liveData.postValue(bitmap)
    }

    return liveData
  }

  fun execute(image: Image): Bitmap {
    val yuvBytes = toYuvBytes(image)

    val yuvType = Type.Builder(rs, Element.U8(rs)).setX(yuvBytes.size).create()
    val allocationIn = Allocation.createTyped(rs, yuvType, Allocation.USAGE_SCRIPT)
    allocationIn.copyFrom(yuvBytes)
    yuvToRgbIntrinsic.setInput(allocationIn)

    val cropRect = image.cropRect
    val bmp = Bitmap.createBitmap(cropRect.width(), cropRect.height(), Bitmap.Config.ARGB_8888)
    val allocationOut = Allocation.createFromBitmap(rs, bmp)

    yuvToRgbIntrinsic.forEach(allocationOut)
    allocationOut.copyTo(bmp)

    return bmp
  }

  private fun toYuvBytes(image: Image): ByteArray {
    val cropRect = image.cropRect
    val width = cropRect.width()
    val height = cropRect.height()

    val yuvBytes = ByteArray(width * height * ImageFormat.getBitsPerPixel(image.format) / 8)

    image.planes[0].also { y ->
      val rowStride = y.rowStride
      var offset = 0
      var position = rowStride * cropRect.top + cropRect.left
      for (row in 0 until height) {
        y.buffer.also {
          it.position(position)
          it.get(yuvBytes, offset, width)
          offset += width
          position = it.position() + rowStride - width
        }
      }
    }

    Pair(image.planes[1], image.planes[2]).also { uvPlanes ->
      val (u, v) = uvPlanes
      val rowStride = u.rowStride
      val pixelStride = u.pixelStride
      val uvWidth = width shr 1
      val uvHeight = height shr 1

      val uBytes = ByteArray(rowStride)
      val vBytes = ByteArray(rowStride)
      var offset = width * height
      var position = rowStride * (cropRect.top shr 1) + pixelStride * (cropRect.left shr 1)
      for (row in 0 until uvHeight) {
        val length = (uvWidth - 1) * pixelStride + 1
        u.buffer.also {
          it.position(position)
          it.get(uBytes, 0, length)
        }
        v.buffer.also {
          it.position(position)
          it.get(vBytes, 0, length)
        }
        for (col in 0 until uvWidth) {
          val index = col * pixelStride
          yuvBytes[offset++] = vBytes[index]
          yuvBytes[offset++] = uBytes[index]
        }
        position = u.buffer.position() + rowStride - length
      }
    }

    return yuvBytes
  }

}
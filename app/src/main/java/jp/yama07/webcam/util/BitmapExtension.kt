package jp.yama07.webcam.util

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

fun Bitmap.toJpegByteArray(quality: Int = 100): ByteArray = ByteArrayOutputStream().also { bos ->
  this.compress(Bitmap.CompressFormat.JPEG, quality, bos)
}.toByteArray()

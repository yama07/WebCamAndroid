package jp.yama07.webcam.ui

object ImageUtil {
  fun convertYuv420ToArgb8888(
    yData: ByteArray,
    uData: ByteArray,
    vData: ByteArray,
    width: Int,
    height: Int,
    yRowStride: Int,
    uvRowStride: Int,
    uvPixelStride: Int,
    out: IntArray
  ) {
    var yp = 0
    for (j in 0 until height) {
      val pY = yRowStride * j
      val pUV = uvRowStride * (j shr 1)
      for (i in 0 until width) {
        val uvOffset = pUV + (i shr 1) * uvPixelStride

        out[yp++] = yuv2Rgb(
          0xff and yData[pY + i].toInt(),
          0xff and uData[uvOffset].toInt(),
          0xff and vData[uvOffset].toInt()
        )
      }
    }
  }

  private const val MAX_CHANNEL_VALUE = 262143

  private fun yuv2Rgb(y_: Int, u_: Int, v_: Int): Int {
    val y = if (y_ - 16 < 0) 0 else y_ - 16
    val u = u_ - 128
    val v = v_ - 128

    val y1192 = 1192 * y
    var r = y1192 + 1634 * v
    var g = y1192 - 833 * v - 400 * u
    var b = y1192 + 2066 * u

    r = if (r > MAX_CHANNEL_VALUE) MAX_CHANNEL_VALUE else if (r < 0) 0 else r
    g = if (g > MAX_CHANNEL_VALUE) MAX_CHANNEL_VALUE else if (g < 0) 0 else g
    b = if (b > MAX_CHANNEL_VALUE) MAX_CHANNEL_VALUE else if (b < 0) 0 else b

    return -0x1000000 or (r shl 6 and 0xff0000) or (g shr 2 and 0xff00) or (b shr 10 and 0xff)
  }
}
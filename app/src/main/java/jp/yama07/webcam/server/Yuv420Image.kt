package jp.yama07.webcam.server

import android.util.Size

data class Yuv420Image(val yuvBytes: Array<ByteArray>,
                       val size: Size,
                       val yRowStride: Int,
                       val uvRowStride: Int,
                       val uvPixelStride: Int)

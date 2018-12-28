package jp.yama07.webcam.server

import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.os.Handler
import fi.iki.elonen.NanoHTTPD
import jp.yama07.webcam.util.observeAtOnce
import jp.yama07.webcam.util.toJpegByteArray
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

class MJpegHTTPD(
  hostname: String?, port: Int,
  private val owner: LifecycleOwner,
  private val src: LiveData<Yuv420Image>,
  private val handler: Handler?
) : NanoHTTPD(hostname, port) {

  companion object {
    private val CRLF = byteArrayOf(0x0d, 0x0a)
  }

  override fun serve(session: IHTTPSession?): Response {
    Timber.d("Request URI: ${session?.uri}")
    return when (session?.uri) {
      "/current" -> {
        val output = PipedOutputStream()
        val input = PipedInputStream(output)
        val bufferedOutput = BufferedOutputStream(output, 512 * 1000)

        src.observeAtOnce(owner, Observer { yuvImage ->
          yuvImage ?: return@Observer
          Yuv420ToBitmapConverter(handler)
            .enqueue(yuvImage).observeAtOnce(owner, Observer { bmpImage ->
              bmpImage ?: return@Observer
              val body = bmpImage.toJpegByteArray()
              bufferedOutput.let {
                it.write(body)
                it.close()
              }
            })
        })
        newChunkedResponse(Response.Status.OK, "image/jpeg", input)
      }

      "/mjpeg" -> {
        val output = PipedOutputStream()
        val input = PipedInputStream(output)
        val bufferedOutput = BufferedOutputStream(output, 512 * 1000)

        val boundary = "--frame"
        src.observe(owner, Observer { yuvImage ->
          yuvImage ?: return@Observer
          Yuv420ToBitmapConverter(handler)
            .enqueue(yuvImage).observeAtOnce(owner, Observer { bmpImage ->
              bmpImage ?: return@Observer
              val jpgByteArray = bmpImage.toJpegByteArray()
              bufferedOutput.let {
                it.write(boundary.toByteArray() + CRLF)
                it.write("Content-Type: image/jpeg".toByteArray() + CRLF)
                it.write("Content-Length: ${jpgByteArray.size}".toByteArray() + CRLF + CRLF)
                it.write(jpgByteArray + CRLF)
                it.flush()
              }
            })
        })
        newChunkedResponse(
          Response.Status.OK, "multipart/x-mixed-replace; boundary=$boundary", input
        )
      }

      else -> {
        newFixedLengthResponse("Welcome to MJpeg HTTPD!")
      }
    }
  }
}

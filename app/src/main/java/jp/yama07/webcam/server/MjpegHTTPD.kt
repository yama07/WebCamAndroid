package jp.yama07.webcam.server

import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.LiveData
import android.os.Handler
import com.shopify.livedataktx.observe
import fi.iki.elonen.NanoHTTPD
import timber.log.Timber
import java.io.*

class MjpegHTTPD(
  hostname: String?, port: Int,
  private val owner: LifecycleOwner,
  private val bmpLiveData: LiveData<ByteArray>,
  private val observeHandler: Handler?
) : NanoHTTPD(hostname, port) {

  var body: ByteArray? = null

  override fun serve(session: IHTTPSession?): Response {
    Timber.d("Request URI: ${session?.uri}")
    return when (session?.uri) {
      "/current" -> {
        val input = ByteArrayInputStream(body)
        newChunkedResponse(Response.Status.OK, "image/jpeg", input)
      }
      "/mjpeg" -> {
        val boundary = "--frame"

        val output = PipedOutputStream()
        val input = PipedInputStream(output)

        val bufferedOutput = BufferedOutputStream(output, 512 * 1000)

        observeHandler?.post {
          Timber.d("mjpegOutputStream: in")
          bmpLiveData.observe(owner) {
            it ?: return@observe
            Timber.d("mjpegOutputStream: Start.")
            try {
              bufferedOutput.write(boundary.toByteArray())
              bufferedOutput.write("\r\n".toByteArray())
              bufferedOutput.write("Content-Type: image/jpeg".toByteArray())
              bufferedOutput.write("\r\n".toByteArray())
              bufferedOutput.write("Content-Length: ${it.size}".toByteArray())
              bufferedOutput.write("\r\n\r\n".toByteArray())
              bufferedOutput.write(it)
              bufferedOutput.write("\r\n".toByteArray())
              bufferedOutput.flush()
            } catch (e: IOException) {
              Timber.e(e, "mjpegOutputStream: Exception")
              bmpLiveData.removeObservers(owner)
            }
            Timber.d("mjpegOutputStream: Finish.")
          }
        }
        newChunkedResponse(
          Response.Status.OK,
          "multipart/x-mixed-replace; boundary=$boundary",
          input
        )
      }
      else -> {
        newFixedLengthResponse("Hello world!")
      }
    }
  }
}

package jp.yama07.webcam.ui

import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.MutableLiveData
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.v7.app.AppCompatActivity
import android.util.Size
import android.view.Surface
import android.view.TextureView
import com.shopify.livedataktx.observe
import fi.iki.elonen.NanoHTTPD
import jp.yama07.webcam.R
import jp.yama07.webcam.camera.CameraCaptureSessionData
import jp.yama07.webcam.camera.CameraCaptureSessionData.CameraCaptureSessionStateEvents
import jp.yama07.webcam.camera.CameraComponent
import jp.yama07.webcam.camera.CameraDeviceData.DeviceStateEvents
import jp.yama07.webcam.util.addSourceNonNullObserve
import jp.yama07.webcam.util.toJpegByteArray
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

class MainActivity : AppCompatActivity() {
  private lateinit var server: WebCamServer
  private val bmpLiveData = MutableLiveData<ByteArray>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    server = WebCamServer("0.0.0.0", 8080, this, bmpLiveData, backgroundHandler).also { it.start() }

    cameraComponent = CameraComponent(
      cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager,
      cameraId = "0", handler = backgroundHandler
    )
    setupCaptureManager()

    texture_view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
      override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {}
      override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {}
      override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
        captureManager.removeObservers(this@MainActivity)
        lifecycle.removeObserver(cameraComponent)
        return true
      }

      override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
        Timber.d("onSurfaceTextureAvailable: $width x $height")
        imageReader = ImageReader
          .newInstance(previewSize.width, previewSize.height, ImageFormat.YUV_420_888, 2)
          .apply {
            setOnImageAvailableListener({ reader ->
              val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
              if (isProcessing) {
                image.close()
                return@setOnImageAvailableListener
              }
              isProcessing = true

              val imageSize = Size(image.width, image.height)

              val yuvBytes = Array(3) { i ->
                val buf = image.planes[i].buffer
                ByteArray(buf.remaining()).also { buf.get(it) }
              }

              val yRowStride = image.planes[0].rowStride
              val uvRowStride = image.planes[1].rowStride
              val uvPixelStride = image.planes[1].pixelStride
              image.close()

              Runnable {
                Timber.d("ImageConvert: Start.")

                val rgbBytes = IntArray(imageSize.width * imageSize.height)
                ImageUtil.convertYuv420ToArgb8888(
                  yuvBytes[0],
                  yuvBytes[1],
                  yuvBytes[2],
                  imageSize.width,
                  imageSize.height,
                  yRowStride,
                  uvRowStride,
                  uvPixelStride,
                  rgbBytes
                )
                val rgbFrameBitmap = Bitmap
                  .createBitmap(imageSize.width, imageSize.height, Bitmap.Config.ARGB_8888)
                  .also { bmp ->
                    bmp.setPixels(
                      rgbBytes, 0, imageSize.width, 0, 0,
                      imageSize.width, imageSize.height
                    )
                  }
                Timber.d("ImageConvert: Finish.")
                server.body = rgbFrameBitmap.toJpegByteArray()
                bmpLiveData.postValue(rgbFrameBitmap.toJpegByteArray())

                isProcessing = false
              }.run()
            }, backgroundHandler)
          }
        lifecycle.addObserver(cameraComponent)
        captureManager.observe(this@MainActivity) {}
      }
    }

    bmpLiveData.observe(this) {
      Timber.d("observe: ${it?.size}")
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    server.stop()
  }

  private lateinit var cameraComponent: CameraComponent
  private val captureManager = MediatorLiveData<Unit>()
  private lateinit var imageReader: ImageReader
  private var previewSize = Size(640, 480)
  private var isProcessing = false

  private fun setupCaptureManager() {
    captureManager.addSourceNonNullObserve(cameraComponent.cameraDeviceLiveData) { cameraDeviceData ->
      var captureSession: CameraCaptureSession? = null
      var captureSessionLiveData: LiveData<CameraCaptureSessionData>? = null

      if (cameraDeviceData.deviceStateEvents == DeviceStateEvents.ON_OPENED) {
        val targetSurfaces = listOf(Surface(texture_view.surfaceTexture), imageReader.surface)
        val previewCaptureRequest = cameraDeviceData.createPreviewCaptureRequest(targetSurfaces)
          ?: return@addSourceNonNullObserve
        captureSessionLiveData =
            cameraDeviceData.createCaptureSession(targetSurfaces, backgroundHandler)
        captureManager.addSourceNonNullObserve(captureSessionLiveData) {
          if (it.cameraCaptureSessionStateEvents == CameraCaptureSessionStateEvents.ON_READY) {
            captureSession = it.cameraCaptureSession
            it.setRepeatingRequest(previewCaptureRequest, backgroundHandler)
          }
        }
      } else if (cameraDeviceData.deviceStateEvents == DeviceStateEvents.ON_CLOSED) {
        captureSession?.close()
        captureSessionLiveData?.also { captureManager.removeSource(it) }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    startBackgroundThread()
  }

  override fun onPause() {
    super.onPause()
    stopBackgroundThread()
  }

  private var backgroundThread: HandlerThread? = null
  private var backgroundHandler: Handler? = null
  private fun startBackgroundThread() {
    backgroundThread = HandlerThread("ImageListener")
    backgroundThread?.start()
    backgroundHandler = Handler(backgroundThread?.looper)
  }

  private fun stopBackgroundThread() {
    backgroundThread?.quitSafely()
    try {
      backgroundThread?.join()
      backgroundThread = null
      backgroundHandler = null
    } catch (e: InterruptedException) {
      Timber.e(e, "Exception!")
    }
  }

  class WebCamServer(
    host: String,
    port: Int,
    private val owner: LifecycleOwner,
    private val bmpLiveData: LiveData<ByteArray>,
    private val observeHandler: Handler?
  ) : NanoHTTPD(host, port) {
    var body: ByteArray? = null

    override fun serve(session: IHTTPSession?): Response {
      Timber.d("Request URI: ${session?.uri}")
      return when (session?.uri) {
        "/current" -> {
          val input = ByteArrayInputStream(body)
          newChunkedResponse(Response.Status.OK, "image/jpeg", input)
        }
        "/mjpeg" -> {
          val boundary = "--webcamserver"

          val output = PipedOutputStream()
          val input = PipedInputStream(output)

          val bufferedOutput = BufferedOutputStream(output, 512 * 1000)

          observeHandler?.post {
            bmpLiveData.observe(owner) {
              it ?: return@observe
              bufferedOutput.write(boundary.toByteArray())
              bufferedOutput.write("\r\n".toByteArray())
              bufferedOutput.write("Content-Type: image/jpeg".toByteArray())
              bufferedOutput.write("\r\n".toByteArray())
              bufferedOutput.write("Content-Length: ${it.size}".toByteArray())
              bufferedOutput.write("\r\n\r\n".toByteArray())
              bufferedOutput.write(it)
              bufferedOutput.write("\r\n".toByteArray())
              bufferedOutput.flush()
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

}

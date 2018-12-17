package jp.yama07.webcam.ui

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
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
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {
  private lateinit var server: WebCamServer

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    server = WebCamServer("0.0.0.0", 8080).also { it.start() }

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
              val image = reader.acquireLatestImage()

              val yBuf = image.planes[0].buffer
              val yBufSize = yBuf.remaining()

              val uBuf = image.planes[1].buffer
              val uBufSize = uBuf.remaining()

              val vBuf = image.planes[2].buffer
              val vBufSize = vBuf.remaining()

              val yuvBytes = Array<ByteArray?>(3, { null })
              yuvBytes[0] = ByteArray(yBufSize).also { yBuf.get(it) }
              yuvBytes[1] = ByteArray(uBufSize).also { uBuf.get(it) }
              yuvBytes[2] = ByteArray(vBufSize).also { vBuf.get(it) }
              val yRowStride = image.planes[0].rowStride
              val uvRowStride = image.planes[1].rowStride
              val uvPixelStride = image.planes[1].pixelStride
              image.close()

              Runnable {
                Timber.d("ImageConvert: Start.")

                val rgbBytes = IntArray(previewSize.width * previewSize.height, { 0 })
                ImageUtil.convertYuv420ToArgb8888(
                  yuvBytes[0]!!,
                  yuvBytes[1]!!,
                  yuvBytes[2]!!,
                  previewSize.width,
                  previewSize.height,
                  yRowStride,
                  uvRowStride,
                  uvPixelStride,
                  rgbBytes
                )
                val rgbFrameBitmap = Bitmap
                  .createBitmap(width, previewSize.height, Bitmap.Config.ARGB_8888)
                  .also { bmp ->
                    bmp.setPixels(
                      rgbBytes, 0, previewSize.width, 0, 0,
                      previewSize.width, previewSize.height
                    )
                  }
                Timber.d("ImageConvert: Finish.")
                server.body = rgbFrameBitmap.toJpegByteArray()
              }.run()
            }, backgroundHandler)
          }
        lifecycle.addObserver(cameraComponent)
        captureManager.observe(this@MainActivity) {}
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    server.stop()
  }

  private lateinit var cameraComponent: CameraComponent
  private val captureManager = MediatorLiveData<Unit>()
  private lateinit var imageReader: ImageReader
  private val previewSize = Size(640, 480)

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

  class WebCamServer(host: String, port: Int) : NanoHTTPD(host, port) {
    var body: ByteArray? = null
    override fun serve(session: IHTTPSession?): Response {
      Timber.d("Request URI: ${session?.uri}")
      return when (session?.uri) {
        "/current" -> {
          val input = ByteArrayInputStream(body)
          newChunkedResponse(Response.Status.OK, "image/jpeg", input)
        }
        else -> {
          newFixedLengthResponse("Hello world!")
        }
      }
    }
  }

}

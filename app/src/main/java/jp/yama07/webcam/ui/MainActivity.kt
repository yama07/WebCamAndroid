package jp.yama07.webcam.ui

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.systemService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import jp.yama07.webcam.R
import jp.yama07.webcam.camera.CameraCaptureSessionData
import jp.yama07.webcam.camera.CameraCaptureSessionData.CameraCaptureSessionStateEvents
import jp.yama07.webcam.camera.CameraComponent
import jp.yama07.webcam.camera.CameraDeviceData.DeviceStateEvents
import jp.yama07.webcam.server.MJpegHTTPD
import jp.yama07.webcam.server.Yuv420ToBitmapConverter
import jp.yama07.webcam.util.NonNullObserver
import jp.yama07.webcam.util.addSourceNonNullObserve
import jp.yama07.webcam.util.observeElementAt
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber

class MainActivity : AppCompatActivity() {
  private lateinit var server: MJpegHTTPD
  private val cameraImage = MutableLiveData<Bitmap>()

  private lateinit var cameraComponent: CameraComponent
  private val captureManager = MediatorLiveData<Unit>()
  private lateinit var imageReader: ImageReader
  private var imageSize: Size = Size(1440, 1080)
  private lateinit var converter: Yuv420ToBitmapConverter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    startBackgroundThread()

    cameraComponent = CameraComponent(
      cameraManager = systemService<CameraManager>(),
      cameraId = "0",
      handler = backgroundHandler
    )
    setupCaptureManager()
    setupSurfaceTexture()
    setupImageReader(imageSize.width, imageSize.height)
    converter = Yuv420ToBitmapConverter(backgroundHandler, this)

    lifecycle.addObserver(cameraComponent)
    server =
        MJpegHTTPD("0.0.0.0", 8080, this, cameraImage, 20, backgroundHandler).also { it.start() }
  }

  override fun onDestroy() {
    super.onDestroy()
    converter.destroy()
    server.stop()
    lifecycle.removeObserver(cameraComponent)
  }

  override fun onResume() {
    super.onResume()
    startBackgroundThread()
  }

  override fun onPause() {
    super.onPause()
    stopBackgroundThread()
  }

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

  private fun setupSurfaceTexture() {
    texture_view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
      override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {}
      override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {}
      override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
        Timber.d("onSurfaceTextureAvailable: $width x $height")
        imageSize = Size(width, height)
        captureManager.observe(this@MainActivity, Observer {})
      }

      override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
        captureManager.removeObservers(this@MainActivity)
        return true
      }
    }
  }

  private var isProcessingFrame = false
  private fun setupImageReader(width: Int, height: Int) {
    imageReader = ImageReader
      .newInstance(width, height, ImageFormat.YUV_420_888, 2)
      .apply {
        setOnImageAvailableListener({ reader ->
          val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
          if (cameraImage.hasActiveObservers() && !isProcessingFrame) {
            isProcessingFrame = true
            converter.enqueue(image)
              .observeElementAt(this@MainActivity, 0, NonNullObserver {
                cameraImage.postValue(it)
                image.close()
                isProcessingFrame = false
              })
          } else {
            image.close()
          }
        }, backgroundHandler)
      }
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
}

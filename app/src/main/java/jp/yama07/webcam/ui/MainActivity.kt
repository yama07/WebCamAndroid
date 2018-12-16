package jp.yama07.webcam.ui

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.v7.app.AppCompatActivity
import android.view.Surface
import android.view.TextureView
import com.shopify.livedataktx.observe
import jp.yama07.webcam.R
import jp.yama07.webcam.camera.CameraCaptureSessionData
import jp.yama07.webcam.camera.CameraCaptureSessionData.CameraCaptureSessionStateEvents
import jp.yama07.webcam.camera.CameraComponent
import jp.yama07.webcam.camera.CameraDeviceData.DeviceStateEvents
import jp.yama07.webcam.util.addSourceNonNullObserve
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
                lifecycle.addObserver(cameraComponent)
                captureManager.observe(this@MainActivity) {}
            }
        }
    }

    private lateinit var cameraComponent: CameraComponent
    private val captureManager = MediatorLiveData<Unit>()

    private fun setupCaptureManager() {
        captureManager.addSourceNonNullObserve(cameraComponent.cameraDeviceLiveData) { cameraDeviceData ->
            var captureSession: CameraCaptureSession? = null
            var captureSessionLiveData: LiveData<CameraCaptureSessionData>? = null

            if (cameraDeviceData.deviceStateEvents == DeviceStateEvents.ON_OPENED) {
                val targetSurfaces = listOf(Surface(texture_view.surfaceTexture))
                val previewCaptureRequest = cameraDeviceData.createPreviewCaptureRequest(targetSurfaces)
                    ?: return@addSourceNonNullObserve
                captureSessionLiveData = cameraDeviceData.createCaptureSession(targetSurfaces, backgroundHandler)
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

}

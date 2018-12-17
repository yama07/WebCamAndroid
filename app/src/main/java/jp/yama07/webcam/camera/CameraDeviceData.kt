package jp.yama07.webcam.camera

import android.arch.lifecycle.MutableLiveData
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.view.Surface
import timber.log.Timber

data class CameraDeviceData(
  val deviceStateEvents: DeviceStateEvents,
  val cameraDevice: CameraDevice?
) {
  enum class DeviceStateEvents {
    ON_OPENED,
    ON_CLOSED,
    ON_DISCONNECTED,
    ON_ERROR
  }

  fun createCaptureSession(surfaceList: List<Surface>, handler: Handler?)
      : MutableLiveData<CameraCaptureSessionData> {
    val cameraCaptureSessionLiveData = MutableLiveData<CameraCaptureSessionData>()
    cameraDevice?.createCaptureSession(surfaceList, object : CameraCaptureSession.StateCallback() {
      override fun onConfigured(session: CameraCaptureSession) {
        cameraCaptureSessionLiveData.postValue(
          CameraCaptureSessionData(
            CameraCaptureSessionData.CameraCaptureSessionStateEvents.ON_CONFIGURED,
            session
          )
        )
      }

      override fun onConfigureFailed(session: CameraCaptureSession) {
        cameraCaptureSessionLiveData.postValue(
          CameraCaptureSessionData(
            CameraCaptureSessionData.CameraCaptureSessionStateEvents.ON_CONFIGURE_FAILED,
            session
          )
        )

        Timber.e(CreateCaptureSessionException(session), "Exception!")
      }

      override fun onReady(session: CameraCaptureSession) {
        super.onReady(session)
        cameraCaptureSessionLiveData.postValue(
          CameraCaptureSessionData(
            CameraCaptureSessionData.CameraCaptureSessionStateEvents.ON_READY,
            session
          )
        )
      }

      override fun onActive(session: CameraCaptureSession) {
        super.onActive(session)
        cameraCaptureSessionLiveData.postValue(
          CameraCaptureSessionData(
            CameraCaptureSessionData.CameraCaptureSessionStateEvents.ON_ACTIVE,
            session
          )
        )
      }

      override fun onClosed(session: CameraCaptureSession) {
        super.onClosed(session)
        cameraCaptureSessionLiveData.postValue(
          CameraCaptureSessionData(
            CameraCaptureSessionData.CameraCaptureSessionStateEvents.ON_CLOSE,
            session
          )
        )
      }

      override fun onSurfacePrepared(
        session: CameraCaptureSession,
        surface: Surface
      ) {
        super.onSurfacePrepared(session, surface)
        cameraCaptureSessionLiveData.postValue(
          CameraCaptureSessionData(
            CameraCaptureSessionData.CameraCaptureSessionStateEvents.ON_SURFACE_PREPARED,
            session
          )
        )
      }
    }, handler)
    return cameraCaptureSessionLiveData
  }

  fun createPreviewCaptureRequest(surfaceList: List<Surface>): CaptureRequest? = cameraDevice
    ?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
    ?.let {
      surfaceList.forEach { surface -> it.addTarget(surface) }
      it.build()
    }

}
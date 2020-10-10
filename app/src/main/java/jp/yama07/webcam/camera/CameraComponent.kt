package jp.yama07.webcam.camera

import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.OnLifecycleEvent
import timber.log.Timber

class CameraComponent(
  private val cameraManager: CameraManager,
  private val cameraId: String,
  private val handler: Handler?
) : LifecycleObserver {
  val cameraDeviceLiveData: MutableLiveData<CameraDeviceData> = MutableLiveData()

  @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
  fun initializeCamera() {
    if (cameraDeviceLiveData.value?.deviceStateEvents == CameraDeviceData.DeviceStateEvents.ON_OPENED) {
      return
    }

    try {
      cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
          cameraDeviceLiveData.postValue(
            CameraDeviceData(
              CameraDeviceData.DeviceStateEvents.ON_OPENED,
              camera
            )
          )
        }

        override fun onClosed(camera: CameraDevice) {
          super.onClosed(camera)
          cameraDeviceLiveData.postValue(
            CameraDeviceData(
              CameraDeviceData.DeviceStateEvents.ON_CLOSED,
              camera
            )
          )
        }

        override fun onDisconnected(camera: CameraDevice) {
          cameraDeviceLiveData.postValue(
            CameraDeviceData(
              CameraDeviceData.DeviceStateEvents.ON_DISCONNECTED,
              camera
            )
          )
          camera.close()
        }

        override fun onError(
          camera: CameraDevice,
          error: Int
        ) {
          cameraDeviceLiveData.postValue(
            CameraDeviceData(
              CameraDeviceData.DeviceStateEvents.ON_ERROR,
              camera
            )
          )
          camera.close()
          Timber.e(OpenCameraException(error), "exception!")
        }
      }, handler)
    } catch (ex: SecurityException) {
      cameraDeviceLiveData.postValue(
        CameraDeviceData(
          CameraDeviceData.DeviceStateEvents.ON_ERROR,
          null
        )
      )
      Timber.e(ex, "exception!")
    }
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
  fun releaseCamera() {
    val device = cameraDeviceLiveData.value?.cameraDevice
    device?.close()
  }
}
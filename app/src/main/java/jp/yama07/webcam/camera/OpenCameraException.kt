package jp.yama07.webcam.camera

import android.hardware.camera2.CameraDevice

class OpenCameraException(val reason: Reason) : Exception() {

    constructor(cameraErrorCode: Int) : this(
        Reason.values().first { it.reason == cameraErrorCode }
    )

    enum class Reason(val reason: Int) {
        ERROR_CAMERA_IN_USE(CameraDevice.StateCallback.ERROR_CAMERA_IN_USE),
        ERROR_MAX_CAMERAS_IN_USE(CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE),
        ERROR_CAMERA_DISABLED(CameraDevice.StateCallback.ERROR_CAMERA_DISABLED),
        ERROR_CAMERA_DEVICE(CameraDevice.StateCallback.ERROR_CAMERA_DEVICE),
        ERROR_CAMERA_SERVICE(CameraDevice.StateCallback.ERROR_CAMERA_SERVICE);
    }
}
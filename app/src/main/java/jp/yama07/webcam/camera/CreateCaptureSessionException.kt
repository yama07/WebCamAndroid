package jp.yama07.webcam.camera

import android.hardware.camera2.CameraCaptureSession

class CreateCaptureSessionException private constructor(message: String?) : Exception(message) {
  constructor(session: CameraCaptureSession) : this("Camera Capture Session Failed.") {
    session.close()
  }
}
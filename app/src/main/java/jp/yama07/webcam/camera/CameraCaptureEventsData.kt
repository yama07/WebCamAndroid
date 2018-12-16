package jp.yama07.webcam.camera

import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.view.Surface

data class CameraCaptureEventsData(
    val cameraCaptureEvents: CameraCaptureEvents,
    val result: TotalCaptureResult? = null,
    val partialResult: CaptureResult? = null,
    val timestamp: Long? = null,
    val frameNumber: Long? = null,
    val sequenceId: Int? = null,
    val target: Surface? = null,
    val failure: CaptureFailure? = null
) {
    enum class CameraCaptureEvents {
        ON_STARTED,
        ON_PROGRESSED,
        ON_COMPLETED,
        ON_SEQUENCE_COMPLETED,
        ON_SEQUENCE_ABORTED,
        ON_BUFFER_LOST,
        ON_FAILED
    }
}
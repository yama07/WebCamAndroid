package jp.yama07.webcam.camera

import android.arch.lifecycle.MutableLiveData
import android.hardware.camera2.*
import android.os.Handler
import android.view.Surface

data class CameraCaptureSessionData(
  val cameraCaptureSessionStateEvents: CameraCaptureSessionStateEvents,
  val cameraCaptureSession: CameraCaptureSession?
) {
  enum class CameraCaptureSessionStateEvents {
    ON_CONFIGURED,
    ON_READY,
    ON_ACTIVE,
    ON_CLOSE,
    ON_SURFACE_PREPARED,
    ON_CONFIGURE_FAILED
  }

  private val cameraCaptureLiveData = MutableLiveData<CameraCaptureEventsData>()

  fun setRepeatingRequest(
    captureRequest: CaptureRequest,
    handler: Handler?
  ) {
    cameraCaptureSession?.setRepeatingRequest(
      captureRequest, object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureStarted(
          session: CameraCaptureSession?,
          request: CaptureRequest?,
          timestamp: Long,
          frameNumber: Long
        ) {
          super.onCaptureStarted(session, request, timestamp, frameNumber)
          cameraCaptureLiveData.postValue(
            CameraCaptureEventsData(
              CameraCaptureEventsData.CameraCaptureEvents.ON_STARTED,
              timestamp = timestamp, frameNumber = frameNumber
            )
          )
        }

        override fun onCaptureProgressed(
          session: CameraCaptureSession?,
          request: CaptureRequest?,
          partialResult: CaptureResult?
        ) {
          super.onCaptureProgressed(session, request, partialResult)
          cameraCaptureLiveData.postValue(
            CameraCaptureEventsData(
              CameraCaptureEventsData.CameraCaptureEvents.ON_PROGRESSED,
              partialResult = partialResult
            )
          )
        }

        override fun onCaptureCompleted(
          session: CameraCaptureSession?,
          request: CaptureRequest?,
          result: TotalCaptureResult?
        ) {
          super.onCaptureCompleted(session, request, result)
          cameraCaptureLiveData.postValue(
            CameraCaptureEventsData(
              CameraCaptureEventsData.CameraCaptureEvents.ON_COMPLETED,
              result = result
            )
          )
        }

        override fun onCaptureSequenceCompleted(
          session: CameraCaptureSession?,
          sequenceId: Int,
          frameNumber: Long
        ) {
          super.onCaptureSequenceCompleted(session, sequenceId, frameNumber)
          cameraCaptureLiveData.postValue(
            CameraCaptureEventsData(
              CameraCaptureEventsData.CameraCaptureEvents.ON_SEQUENCE_COMPLETED,
              frameNumber = frameNumber
            )
          )
        }

        override fun onCaptureSequenceAborted(
          session: CameraCaptureSession?,
          sequenceId: Int
        ) {
          super.onCaptureSequenceAborted(session, sequenceId)
          cameraCaptureLiveData.postValue(
            CameraCaptureEventsData(
              CameraCaptureEventsData.CameraCaptureEvents.ON_SEQUENCE_ABORTED,
              sequenceId = sequenceId
            )
          )
        }

        override fun onCaptureBufferLost(
          session: CameraCaptureSession?,
          request: CaptureRequest?,
          target: Surface?,
          frameNumber: Long
        ) {
          super.onCaptureBufferLost(session, request, target, frameNumber)
          cameraCaptureLiveData.postValue(
            CameraCaptureEventsData(
              CameraCaptureEventsData.CameraCaptureEvents.ON_BUFFER_LOST,
              target = target, frameNumber = frameNumber
            )
          )
        }

        override fun onCaptureFailed(
          session: CameraCaptureSession?,
          request: CaptureRequest?,
          failure: CaptureFailure?
        ) {
          super.onCaptureFailed(session, request, failure)
          cameraCaptureLiveData.postValue(
            CameraCaptureEventsData(
              CameraCaptureEventsData.CameraCaptureEvents.ON_FAILED,
              failure = failure
            )
          )
        }
      }, handler
    )
  }
}
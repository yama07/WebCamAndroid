package jp.yama07.webcam.util

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import timber.log.Timber

fun <T> LiveData<T>.observeAtOnce(lifecycleOwner: LifecycleOwner, observer: Observer<T>) {
  Handler(Looper.getMainLooper()).post {
    observe(lifecycleOwner, object : Observer<T> {
      override fun onChanged(t: T?) {
        observer.onChanged(t)
        removeObserver(this)
      }
    })
  }
}

fun <T> LiveData<T>.observeByStatus(
  lifecycleOwner: LifecycleOwner, status: LiveDataStatus, observer: Observer<T>
) {
  Handler(Looper.getMainLooper()).post {
    observe(lifecycleOwner, object : Observer<T> {
      override fun onChanged(t: T?) {
        Timber.d("State: ${status.state}")
        when (status.state) {
          LiveDataStatus.State.EMITTING -> observer.onChanged(t)
          LiveDataStatus.State.SUSPENDED -> removeObserver(this)
          else -> {
            /* Do nothing */
          }
        }
      }
    })
  }
}

data class LiveDataStatus(var state: LiveDataStatus.State = LiveDataStatus.State.EMITTING) {
  enum class State {
    EMITTING, SLEEPING, SUSPENDED
  }
}

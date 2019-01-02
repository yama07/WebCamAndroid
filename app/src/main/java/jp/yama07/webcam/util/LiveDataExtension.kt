package jp.yama07.webcam.util

import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.os.Handler
import android.os.Looper

fun <T> LiveData<T>.observeAtOnce(lifecycleOwner: LifecycleOwner, observer: Observer<T>) {
  observe(lifecycleOwner, object : Observer<T> {
    override fun onChanged(t: T?) {
      observer.onChanged(t)
      Handler(Looper.getMainLooper()).post {
        removeObserver(this)
      }
    }
  })
}

fun <T> LiveData<T>.observeByStatus(
  lifecycleOwner: LifecycleOwner, status: LiveDataStatus, observer: Observer<T>
) {
  observe(lifecycleOwner, object : Observer<T> {
    override fun onChanged(t: T?) {
      when (status.state) {
        LiveDataStatus.State.EMITTING -> observer.onChanged(t)
        LiveDataStatus.State.SUSPENDED ->
          Handler(Looper.getMainLooper()).post {
            removeObserver(this)
          }
        else -> {
          /* Do nothing */
        }
      }
    }
  })
}

data class LiveDataStatus(var state: LiveDataStatus.State = LiveDataStatus.State.EMITTING) {
  enum class State {
    EMITTING, SLEEPING, SUSPENDED
  }
}

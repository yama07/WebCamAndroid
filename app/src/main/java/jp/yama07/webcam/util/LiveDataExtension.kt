package jp.yama07.webcam.util

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

fun <T> LiveData<T>.observeElementAt(
  lifecycleOwner: LifecycleOwner,
  index: Int,
  observer: Observer<T>
) {
  Handler(Looper.getMainLooper()).post {
    var count = 0
    observe(lifecycleOwner, object : Observer<T> {
      override fun onChanged(t: T) {
        if (index <= count++) {
          observer.onChanged(t)
          removeObserver(this)
        }
      }
    })
  }
}

fun <T> LiveData<T>.observeByStatus(
  lifecycleOwner: LifecycleOwner, status: ObserverStatus, observer: Observer<T>
) {
  Handler(Looper.getMainLooper()).post {
    observe(lifecycleOwner, object : Observer<T> {
      override fun onChanged(t: T?) {
        when (status.state) {
          ObserverStatus.State.ACTIVE -> observer.onChanged(t)
          ObserverStatus.State.REMOVED -> removeObserver(this)
          else -> {
            /* Do nothing */
          }
        }
      }
    })
  }
}

data class ObserverStatus(var state: ObserverStatus.State) {
  enum class State {
    ACTIVE, INACTIVE, REMOVED
  }
}

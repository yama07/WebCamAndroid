package jp.yama07.webcam.util

import android.arch.lifecycle.Observer

interface NonNullObserver<T> : Observer<T> {

  fun onNonNullChanged(t: T)

  override fun onChanged(t: T?) {
    t?.let { onNonNullChanged(it) }
  }
}

fun <T> NonNullObserver(func: (T) -> Unit) = object : NonNullObserver<T> {
  override fun onNonNullChanged(t: T) = func(t)
}

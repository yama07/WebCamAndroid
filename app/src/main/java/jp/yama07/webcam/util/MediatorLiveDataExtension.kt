package jp.yama07.webcam.util

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData

fun <T, S> MediatorLiveData<T>.addSourceNonNullObserve(
  source: LiveData<S>,
  onChange: (t: S) -> Unit
) {
  addSource(source) {
    it?.let {
      onChange(it)
    }
  }
}
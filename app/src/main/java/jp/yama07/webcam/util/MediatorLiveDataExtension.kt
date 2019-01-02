package jp.yama07.webcam.util

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData

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
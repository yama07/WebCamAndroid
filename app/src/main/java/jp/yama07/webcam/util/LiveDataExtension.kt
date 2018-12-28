package jp.yama07.webcam.util

import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.arch.lifecycle.Transformations
import android.os.Handler
import android.os.Looper

fun <X, Y> LiveData<X>.map(func: (X) -> Y) = Transformations.map(this, func)
fun <X, Y> LiveData<X>.switchMap(func: (X) -> LiveData<Y>) = Transformations.switchMap(this, func)

fun <T> LiveData<T>.observe(owner: LifecycleOwner, observer: (T) -> Unit) = observe(owner, Observer {
  if (it != null) observer.invoke(it)
})

fun <T> LiveData<T>.observeNotNull(owner: LifecycleOwner, observe: (value: T) -> Unit) = apply {
  observe(owner, Observer { value ->
    value ?: return@Observer
    observe(value)
  })
}

fun <T> LiveData<T>.observeAtOnce(lifecycleOwner: LifecycleOwner, observer: Observer<T>) {
  observeForever(object : Observer<T> {
    override fun onChanged(t: T?) {
      observer.onChanged(t)
      Handler(Looper.getMainLooper()).post {
        removeObserver(this)
      }
    }
  })
}

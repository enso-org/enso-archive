package org.enso.languageserver.io

import java.io.OutputStream

import org.enso.languageserver.io.ObservableOutputStream.OutputObserver

class ObservableOutputStream extends OutputStream {

  private var observers = Set.empty[OutputObserver]

  override def write(byte: Int): Unit = this.synchronized {
    notify(Array[Byte](byte.toByte))
  }

  override def write(bytes: Array[Byte]): Unit = this.synchronized {
    if (bytes.length > 0) {
      notify(bytes)
    }
  }

  override def write(bytes: Array[Byte], off: Int, len: Int): Unit =
    this.synchronized {
      if (len > 0) {
        val buf = new Array[Byte](len)
        Array.copy(bytes, off, buf, 0, len)
        notify(buf)
      }
    }

  def attach(observer: OutputObserver): Unit = this.synchronized {
    observers += observer
  }

  def detach(observer: OutputObserver): Unit = this.synchronized {
    observers -= observer
  }

  protected def notify(output: Array[Byte]): Unit = {
    observers foreach { _.update(output) }
  }

}

object ObservableOutputStream {

  trait OutputObserver {

    def update(output: Array[Byte]): Unit

  }

}

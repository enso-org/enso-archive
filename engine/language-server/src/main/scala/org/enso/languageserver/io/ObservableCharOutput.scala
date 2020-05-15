package org.enso.languageserver.io

import java.io.OutputStream

import org.enso.languageserver.io.ObservableCharOutput.Subscriber

class ObservableCharOutput extends OutputStream {

  private var subscribers = Set.empty[Subscriber]

  override def write(b: Int): Unit = this.synchronized {
    val output = new String(Array[Byte](b.toByte))
    notify(output)
  }

  override def write(b: Array[Byte]): Unit = this.synchronized {
    if (b.length > 0) {
      val output = new String(b)
      notify(output)
    }
  }

  override def write(b: Array[Byte], off: Int, len: Int): Unit =
    this.synchronized {
      if (len > 0) {
        val output = new String(b, off, len)
        notify(output)
      }
    }

  def subscribe(subscriber: Subscriber): Unit = this.synchronized {
    subscribers += subscriber
  }

  def unsubscribe(subscriber: Subscriber): Unit = this.synchronized {
    subscribers -= subscriber
  }

  protected def notify(output: String): Unit = {
    subscribers foreach { _.update(output) }
  }

}

object ObservableCharOutput {

  trait Subscriber {

    def update(output: String): Unit

  }

}

package org.enso.languageserver.io

import java.io.{ByteArrayOutputStream, OutputStream}

import org.enso.languageserver.io.ObservableCharOutput.Subscriber

class ObservableCharOutput extends OutputStream {

  private val buffer = new ByteArrayOutputStream()

  private var subscribers = Set.empty[Subscriber]

  override def write(b: Int): Unit = this.synchronized { buffer.write(b) }

  override def write(b: Array[Byte]): Unit = this.synchronized {
    buffer.write(b)
  }

  override def write(b: Array[Byte], off: Int, len: Int): Unit =
    this.synchronized { buffer.write(b, off, len) }

  def subscribe(subscriber: Subscriber): Unit = this.synchronized {
    subscribers += subscriber
  }

  def unsubscribe(subscriber: Subscriber): Unit = this.synchronized {
    subscribers -= subscriber
  }

  override def flush(): Unit = this.synchronized {
    try {
      val charSequence = buffer.toString
      subscribers foreach { _.update(charSequence) }
    } finally {
      buffer.reset()
    }
  }

}

object ObservableCharOutput {

  trait Subscriber {

    def update(output: String): Unit

  }

}

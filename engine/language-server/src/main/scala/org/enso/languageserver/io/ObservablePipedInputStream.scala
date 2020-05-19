package org.enso.languageserver.io

import java.io.InputStream

import akka.util.ByteString
import org.enso.languageserver.io.ObservableOutputStream.OutputObserver
import org.enso.languageserver.io.ObservablePipedInputStream.{
  InputObserver,
  InputStreamEvent,
  ReadBlocked
}

class ObservablePipedInputStream(sink: ObservableOutputStream)
    extends InputStream
    with OutputObserver {

  sink.attach(this)

  private val lock = new AnyRef

  private var observers = Set.empty[InputObserver]

  private var buffer: ByteString = ByteString.empty

  override def read(): Int = lock.synchronized {
    waitForBuffer()
    val byte = buffer.head
    buffer = buffer.tail
    lock.notifyAll()
    byte.toInt
  }

  override def read(array: Array[Byte]): Int = read(array, 0, array.length)

  override def read(array: Array[Byte], off: Int, len: Int): Int =
    lock.synchronized {
      waitForBuffer()
      val sliceLength = if (buffer.length >= len) len else buffer.length
      val slice       = buffer.slice(0, sliceLength)
      buffer = buffer.drop(sliceLength)
      Array.copy(slice.toArray, 0, array, off, sliceLength)
      lock.notifyAll()
      sliceLength
    }

  private def waitForBuffer(): Unit =
    while (buffer.isEmpty) {
      notifyObservers(ReadBlocked)
      lock.wait()
    }

  override def available(): Int = lock.synchronized {
    buffer.length
  }

  override def update(output: Array[Byte]): Unit = lock.synchronized {
    buffer = ByteString.createBuilder
      .append(buffer)
      .append(ByteString.fromArray(output))
      .result()
    lock.notifyAll()
  }

  def attach(observer: InputObserver): Unit = lock.synchronized {
    observers += observer
  }

  def detach(observer: InputObserver): Unit = lock.synchronized {
    observers -= observer
  }

  protected def notifyObservers(event: InputStreamEvent): Unit =
    lock.synchronized {
      observers foreach (_.update(event))
    }

}

object ObservablePipedInputStream {

  sealed trait InputStreamEvent
  case object ReadBlocked extends InputStreamEvent

  trait InputObserver {

    def update(event: InputStreamEvent): Unit

  }

}

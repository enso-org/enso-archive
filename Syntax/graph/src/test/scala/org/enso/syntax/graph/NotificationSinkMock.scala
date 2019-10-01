package org.enso.syntax.graph

import scala.collection.mutable

/** Simply accumulates all received notifications. */
final case class NotificationSinkMock() extends NotificationSink {
  val received: mutable.ArrayBuffer[API.Notification] =
    mutable.ArrayBuffer()

  override def notify(notification: API.Notification): Unit = {
    println(s"Got notification: $notification")
    received += notification
  }
}

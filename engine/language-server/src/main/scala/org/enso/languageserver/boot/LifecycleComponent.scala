package org.enso.languageserver.boot

import org.enso.languageserver.boot.LifecycleComponent.{
  ComponentRestarted,
  ComponentStarted,
  ComponentStopped
}

import scala.concurrent.Future

trait LifecycleComponent {

  def start(): Future[ComponentStarted.type]

  def stop(): Future[ComponentStopped.type]

  def restart(): Future[ComponentRestarted.type]

}

object LifecycleComponent {

  case object ComponentStarted

  case object ComponentStopped

  case object ComponentRestarted

}

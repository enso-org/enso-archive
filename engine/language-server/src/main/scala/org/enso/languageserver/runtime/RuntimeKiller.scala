package org.enso.languageserver.runtime

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import org.enso.languageserver.runtime.RuntimeKiller._
import org.enso.languageserver.util.UnhandledLogging
import org.enso.polyglot.runtime.Runtime.Api
import org.graalvm.polyglot.Context

import scala.annotation.unused
import scala.concurrent.duration._
import scala.util.control.NonFatal

class RuntimeKiller(runtimeConnector: ActorRef, truffleContext: Context)
    extends Actor
    with ActorLogging
    with UnhandledLogging {

  import context.dispatcher

  override def receive: Receive = idle()

  private def idle(): Receive = {
    case KillRuntime =>
      log.info("Shutting down the runtime server")
      runtimeConnector ! Api.Request(
        UUID.randomUUID(),
        Api.ShutDownRuntimeServer()
      )
      val cancellable =
        context.system.scheduler
          .scheduleOnce(5.seconds, self, ResourceDisposalTimeout)
      context.become(shuttingDownRuntimeServer(sender(), cancellable))
  }

  private def shuttingDownRuntimeServer(
    replyTo: ActorRef,
    cancellable: Cancellable
  ): Receive = {
    case ResourceDisposalTimeout =>
      log.error("Disposal of runtime resources timed out")
      shutDownTruffle(replyTo)

    case Api.Response(_, Api.RuntimeServerShutDown()) =>
      cancellable.cancel()
      shutDownTruffle(replyTo)
  }

  private def shuttingDownTruffle(
    replyTo: ActorRef,
    @unused retryCount: Int
  ): Receive = {
    case TryToStopTruffle =>
      shutDownTruffle(replyTo, retryCount)
  }

  private def shutDownTruffle(replyTo: ActorRef, retryCount: Int = 0): Unit = {
    try {
      log.info("Shutting down the Truffle context")
      truffleContext.close()
      replyTo ! RuntimeGracefullyStopped
      context.stop(self)
    } catch {
      case NonFatal(ex) =>
        log.error(ex, "An error occurred during stopping Truffle context")
        if (retryCount < MaxRetries) {
          context.system.scheduler
            .scheduleOnce((retryCount + 1).seconds, self, TryToStopTruffle)
          context.become(shuttingDownTruffle(replyTo, retryCount + 1))
        } else {
          replyTo ! RuntimeNotStopped
          context.stop(self)
        }
    }
  }

}

object RuntimeKiller {

  val MaxRetries = 3

  case object KillRuntime

  sealed trait RuntimeShutdownResult

  case object RuntimeGracefullyStopped extends RuntimeShutdownResult

  case object RuntimeNotStopped extends RuntimeShutdownResult

  private case object ResourceDisposalTimeout

  private case object TryToStopTruffle

  def props(runtimeConnector: ActorRef, truffleContext: Context): Props =
    Props(new RuntimeKiller(runtimeConnector, truffleContext))

}

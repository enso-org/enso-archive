package org.enso

import java.nio.file.Path
import java.util.UUID

import akka.actor.Scheduler
import akka.actor.typed
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import io.methvin.watcher.DirectoryWatcher
import org.enso.fileManager.API
import org.enso.fileManager.API._

import scala.collection.mutable
import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success
import scala.util.Try

case class FileManager(projectRoot: Path, context: ActorContext[InputMessage])
    extends AbstractBehavior[API.InputMessage] {

  val watchers: mutable.Map[UUID, DirectoryWatcher] = mutable.Map()

  def onMessageTyped[response <: Response.Success: ClassTag](
    message: Request[response]
  ): Unit = {
    val response = try {
      message.contents.validate(projectRoot)
      val result = message.contents.handle(this)
      Success(result)
    } catch { case ex: Throwable => Failure(ex) }
    context.log.debug(s"Responding with $response")
    message.replyTo ! response
  }

  override def onMessage(message: InputMessage): this.type = {
    context.log.debug(s"Received $message")
    message.handle(this)
    this
  }
}

object FileManager {
  val API: org.enso.fileManager.API.type = org.enso.fileManager.API

  def apply(projectRoot: Path): Behavior[InputMessage] =
    Behaviors.setup(context => FileManager(projectRoot, context))

  def ask[response <: Response.Success: ClassTag](
    actor: ActorRef[API.InputMessage],
    payload: Request.Payload[response]
  )(implicit timeout: Timeout,
    scheduler: Scheduler
  ): Future[Try[response]] = {
    actor.ask { replyTo: ActorRef[Try[response]] =>
      Request(replyTo, payload)
    }
  }
}

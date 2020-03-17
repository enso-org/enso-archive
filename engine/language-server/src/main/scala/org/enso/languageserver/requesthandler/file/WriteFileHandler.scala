package org.enso.languageserver.requesthandler.file

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.enso.languageserver.data.Client
import org.enso.languageserver.filemanager.FileManagerApi.WriteFile
import org.enso.languageserver.filemanager.{
  FileManagerProtocol,
  FileSystemFailureMapper
}
import org.enso.languageserver.jsonrpc.Errors.ServiceError
import org.enso.languageserver.jsonrpc._
import org.enso.languageserver.requesthandler.RequestTimeout

import scala.concurrent.duration.FiniteDuration

class WriteFileHandler(fsActor: ActorRef, timeout: FiniteDuration)
    extends Actor
    with ActorLogging {

  import context.dispatcher

  override def receive: Receive = requestStage

  private def requestStage: Receive = {
    case Request(WriteFile, id, params: WriteFile.Params) =>
      fsActor ! FileManagerProtocol.WriteFile(params.path, params.contents)
      context.system.scheduler.scheduleOnce(timeout, self, RequestTimeout)
      context.become(responseStage(id, sender()))
  }

  private def responseStage(id: Id, replyTo: ActorRef): Receive = {
    case RequestTimeout =>
      log.error(s"Writing file for request#$id timed out")
      replyTo ! ResponseError(Some(id), ServiceError)
      context.stop(self)

    case FileManagerProtocol.WriteFileResult(Left(failure)) =>
      replyTo ! ResponseError(
        Some(id),
        FileSystemFailureMapper.mapFailure(failure)
      )

    case FileManagerProtocol.WriteFileResult(Right(())) =>
      replyTo ! ResponseResult(WriteFile, id, Unused)
  }

  override def unhandled(message: Any): Unit =
    log.warning("Received unknown message: {}", message)

}

object WriteFileHandler {

  def props(
    fsActor: ActorRef,
    requestTimeout: FiniteDuration
  ): Props = Props(new WriteFileHandler(fsActor, requestTimeout))

}

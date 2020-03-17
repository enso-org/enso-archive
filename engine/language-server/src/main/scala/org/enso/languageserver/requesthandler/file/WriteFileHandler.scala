package org.enso.languageserver.requesthandler.file

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Status}
import org.enso.languageserver.filemanager.FileManagerApi.WriteFile
import org.enso.languageserver.filemanager.{
  FileManagerProtocol,
  FileSystemFailureMapper
}
import org.enso.languageserver.jsonrpc.Errors.ServiceError
import org.enso.languageserver.jsonrpc._
import org.enso.languageserver.requesthandler.RequestTimeout

import scala.concurrent.duration.FiniteDuration

class WriteFileHandler(timeout: FiniteDuration, fsManager: ActorRef)
    extends Actor
    with ActorLogging {

  import context.dispatcher

  override def receive: Receive = requestStage

  private def requestStage: Receive = {
    case Request(WriteFile, id, params: WriteFile.Params) =>
      fsManager ! FileManagerProtocol.WriteFile(params.path, params.contents)
      context.system.scheduler.scheduleOnce(timeout, self, RequestTimeout)

      context.become(responseStage(id, sender()))
  }

  private def responseStage(id: Id, replyTo: ActorRef): Receive = {
    case Status.Failure(ex) =>
      log.error(s"Failure during write operation:", ex)
      replyTo ! ResponseError(Some(id), ServiceError)
      context.stop(self)

    case RequestTimeout =>
      log.error(s"Request#$id timed out")
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
}

object WriteFileHandler {

  def props(timeout: FiniteDuration, fsManager: ActorRef): Props =
    Props(new WriteFileHandler(timeout, fsManager))

}

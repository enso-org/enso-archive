package org.enso.filemanager

import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AbstractBehavior
import org.apache.commons.io.FileUtils
import sun.reflect.generics.reflectiveObjects.NotImplementedException
import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success
import scala.util.Try

object API {
  type InputMessage  = API.Request[API.SuccessResponse]
  type OutputMessage = Try[SuccessResponse]

  final case class PathOutsideProjectException(
    projectRoot: Path,
    accessedPath: Path)
      extends Exception(
        s"Cannot access path $accessedPath because it does not belong to the project under root directory $projectRoot"
      )

  sealed case class Request[SpecificResponse <: SuccessResponse: ClassTag](
    replyTo: ActorRef[Try[SpecificResponse]],
    contents: RequestPayload[SpecificResponse])

  sealed abstract class AnyResponse
  sealed abstract class SuccessResponse          extends AnyResponse
  case class ErrorResponse(exception: Throwable) extends AnyResponse

  abstract class RequestPayload[+ResponseType <: SuccessResponse: ClassTag] {
    def touchedPaths: Seq[Path]
    def handle(fileManager: FileManagerBehavior): ResponseType

    def validate(projectRoot: Path): Unit =
      touchedPaths.foreach(Detail.validatePath(_, projectRoot))
  }

  case class ExistsRequest(p: Path) extends RequestPayload[ExistsResponse] {
    override def touchedPaths: Seq[Path] = Seq(p)
    override def handle(fileManager: FileManagerBehavior) =
      ExistsResponse(Files.exists(p))
  }
  case class ExistsResponse(exists: Boolean) extends SuccessResponse

  case class TouchFileRequest(p: Path)
      extends RequestPayload[TouchFileResponse] {
    override def touchedPaths: Seq[Path] = Seq(p)
    override def handle(fileManager: FileManagerBehavior): TouchFileResponse = {
      FileUtils.touch(p.toFile)
      TouchFileResponse()
    }
  }
  case class TouchFileResponse() extends SuccessResponse

  case class WatchPathRequest(p: Path, observer: ActorRef[FileSystemEvent])
      extends RequestPayload[WatchPathResponse] {
    override def touchedPaths: Seq[Path] = Seq(p)
    override def handle(fileManager: FileManagerBehavior): WatchPathResponse = {
      throw new NotImplementedException()
      WatchPathResponse()
    }
  }
  case class WatchPathResponse() extends SuccessResponse

  case class FileSystemEvent()
}

object Detail {
  import API._

  def validatePath(validatedPath: Path, projectRoot: Path): Unit = {
    val normalized = validatedPath.toAbsolutePath.normalize()
    if (!normalized.startsWith(projectRoot))
      throw PathOutsideProjectException(projectRoot, validatedPath)
  }
}

class FileManagerBehavior(
  projectRoot: Path,
  context: ActorContext[API.InputMessage])
    extends AbstractBehavior[API.InputMessage] {
  import API._

  override def onMessage(message: InputMessage): Behavior[InputMessage] = {
    context.log.info(s"Received $message")
    val response = try {
      message.contents.validate(projectRoot)
      // Note: `handle` signature guarantees that for derived request we get derived response type
      val result = message.contents.handle(this)
      Success(result)
    } catch {
      case ex: Throwable =>
        Failure(ex)
    }
    message.replyTo ! response
    this
  }
}

object FileManager {
  def fileManager(projectRoot: Path): Behavior[API.InputMessage] =
    Behaviors.setup(context => new FileManagerBehavior(projectRoot, context))
}

package org.enso.filemanager

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import org.apache.commons.io.FileUtils
import org.enso.filemanager.API.SuccessResponse

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.reflect.classTag

object API {
  type InputMessage  = API.Request[API.SuccessResponse]
  type OutputMessage = Either[ErrorResponse, SuccessResponse]

  final case class PathOutsideProjectException(
    projectRoot: Path,
    accessedPath: Path)
      extends Exception(
        s"Cannot access path $accessedPath because it does not belong to the project under root directory $projectRoot"
      )

  sealed case class Request[SpecificResponse <: SuccessResponse: ClassTag](
    replyTo: ActorRef[Either[ErrorResponse, SpecificResponse]],
    contents: RequestPayload[SpecificResponse])

  sealed abstract class AnyResponse
  sealed abstract class SuccessResponse          extends AnyResponse
  case class ErrorResponse(exception: Throwable) extends AnyResponse

  abstract class RequestPayload[+ResponseType <: SuccessResponse: ClassTag] {
    def touchedPaths: Seq[Path]
    def handleStateless: ResponseType

    def handle: (ResponseType, Behavior[InputMessage]) =
      (handleStateless, Behaviors.same)

    def validate(projectRoot: Path): Unit =
      touchedPaths.foreach(Detail.validatePath(_, projectRoot))
  }

  case class ExistsRequest(p: Path) extends RequestPayload[ExistsResponse] {
    override def touchedPaths: Seq[Path] = Seq(p)
    override def handleStateless = ExistsResponse(Files.exists(p))
  }
  case class ExistsResponse(exists: Boolean) extends SuccessResponse

  case class TouchFileRequest(p: Path)
      extends RequestPayload[TouchFileResponse] {
    override def touchedPaths: Seq[Path] = Seq(p)
    override def handleStateless: TouchFileResponse = {
      FileUtils.touch(p.toFile)
      TouchFileResponse()
    }
  }
  case class TouchFileResponse() extends SuccessResponse
}

object Detail {
  import API._

  def validatePath(validatedPath: Path, projectRoot: Path): Unit = {
    val normalized = validatedPath.toAbsolutePath.normalize()
    if (!normalized.startsWith(projectRoot))
      throw PathOutsideProjectException(projectRoot, validatedPath)
  }
}

object FileManager {
  import API._

  case class State(projectRoot: Path) {}

  def fileManager(projectRoot: Path): Behavior[InputMessage] =
    Behaviors.receive { (context, request) =>
      context.log.info(s"Received $request")
      val response = try {
        request.contents.validate(projectRoot)
        Right(request.contents.handleStateless)
      } catch {
        case ex: Throwable =>
          Left(ErrorResponse(ex))
      }
      request.replyTo ! response
      Behaviors.same
    }
}

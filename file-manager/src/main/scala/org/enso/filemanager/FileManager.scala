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
import scala.reflect.{ClassTag, classTag}

object API {
  type Response[specific >: AnyResponse] = Either[ErrorResponse, specific]

  final case class PathOutsideProjectException(
    projectRoot: Path,
    accessedPath: Path)
      extends Exception(
        s"Cannot access path $accessedPath because it does not belong to the project under root directory $projectRoot"
      )

  sealed case class Request[SpecificResponse](
    replyTo: ActorRef[Either[ErrorResponse, SpecificResponse]],
    contents: RequestPayload)

  sealed abstract class AnyResponse
  sealed abstract class SuccessResponse          extends AnyResponse
  case class ErrorResponse(exception: Throwable) extends AnyResponse

  trait RequestPayload {
    implicit val responseClassTag: ClassTag[ResponseType] = classTag[ResponseType]
    type ResponseType <: SuccessResponse
  }

  case class ExistsRequest(p: Path) extends RequestPayload {
    override type ResponseType = ExistsResponse
  }
  case class ExistsResponse(exists: Boolean) extends SuccessResponse

  case class TouchFileRequest(p: Path) extends RequestPayload {
    override type ResponseType = TouchFileResponse
  }
  case class TouchFileResponse() extends SuccessResponse
}

object Detail {
  import API._

  def extractPaths(request: RequestPayload): Array[Path] = request match {
    case ExistsRequest(p)    => Array(p)
    case TouchFileRequest(p) => Array(p)
  }

  def validatePath(validatedPath: Path, projectRoot: Path): Unit = {
    val normalized = validatedPath.toAbsolutePath.normalize()
    if (!normalized.startsWith(projectRoot))
      throw PathOutsideProjectException(projectRoot, validatedPath)
  }

  def validateRequest(request: RequestPayload, projectRoot: Path): Unit = {
    extractPaths(request).foreach(validatePath(_, projectRoot))
  }

  def handleSpecific(
    context: ActorContext[Request[SuccessResponse]],
    request: RequestPayload,
    projectRoot: Path
  ): AnyResponse = {
    try {
      Detail.validateRequest(request, projectRoot)
      request match {

        case msg: ExistsRequest =>
          ExistsResponse(Files.exists(msg.p))
        case msg: TouchFileRequest =>
          FileUtils.touch(msg.p.toFile)
          TouchFileResponse()
      }
    } catch {
      case ex: Throwable =>
        context.log.warning(s"Failed to handle request $request: $ex")
        ErrorResponse(ex)
    }
  }
}

object FileManager {
  import API._

  def fileManager(
    projectRoot: Path
  ): Behavior[API.Request[API.SuccessResponse]] =
    Behaviors.receive { (context, request) =>
      context.log.info(s"Received $request")
      val response =
        Detail.handleSpecific(context, request.contents, projectRoot)
      context.log.info(s"Replying: $response")
      val responsePacked = response match {
        case msg: ErrorResponse   => Left(msg)
        case msg: SuccessResponse => Right(msg)
      }
      request.replyTo ! responsePacked
      Behaviors.same
    }
}

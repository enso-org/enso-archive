package org.enso.filemanager

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import org.apache.commons.io.FileUtils

import scala.concurrent.Future

object API {
  final case class PathOutsideProject(projectRoot: Path, accessedPath: Path)
      extends Exception(
        s"Cannot access path $accessedPath because it does not belong to the project under root directory $projectRoot"
      )

  sealed case class Request(
    replyTo: ActorRef[Response],
    contents: RequestPayload)

  sealed abstract class Response

  case class ErrorResponse(exception: Throwable) extends Response

  abstract class RequestPayload

  case class ExistsRequest(path: Path) extends RequestPayload

  case class ExistsResponse(exists: Boolean) extends Response

  case class TouchFileRequest(path: Path) extends RequestPayload

  case class TouchFileResponse() extends Response
}

object API2 {
  final case class PathOutsideProject(projectRoot: Path, accessedPath: Path)
      extends Exception(
        s"Cannot access path $accessedPath because it does not belong to the project under root directory $projectRoot"
      )

  sealed case class Request[SpecificResponse](
    replyTo: ActorRef[Option[SpecificResponse]],
    contents: RequestPayload)

  sealed abstract class Response

  case class ErrorResponse(exception: Throwable) extends Response

  trait RequestPayload {
    type ResponseType
  }

  case class ExistsRequest(p: Path) extends RequestPayload {
    override type ResponseType = ExistsResponse
  }
  case class ExistsResponse(exists: Boolean) extends Response

  case class TouchFileRequest(p: Path) extends RequestPayload {
    override type ResponseType = TouchFileResponse
  }
  case class TouchFileResponse() extends Response

//  def ask[T, R](actorRef: ActorRef[T], reqMaker: ActorRef[R] => T): Future[T] = ???
//  ask(ListRequest(???)): Option[ListResponse]
}

object Detail {
  import API2._

  def extractPaths(request: RequestPayload): Array[Path] = request match {
    case ExistsRequest(p)    => Array(p)
    case TouchFileRequest(p) => Array(p)
  }

  def validatePath(validatedPath: Path, projectRoot: Path): Unit = {
    val normalized = validatedPath.toAbsolutePath.normalize()
    if (!normalized.startsWith(projectRoot))
      throw PathOutsideProject(projectRoot, validatedPath)
  }

  def validateRequest(request: RequestPayload, projectRoot: Path): Unit = {
    extractPaths(request).foreach(validatePath(_, projectRoot))
  }

  def handleSpecific(
    context: ActorContext[Request[Response]],
    request: RequestPayload,
    projectRoot: Path
  ): Response = {
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

object FFF {
  trait Foo {}
  case class Der1() extends Foo
  case class Der2() extends Foo

  def handle[T](t: T) {}

  def dispatch(f: Foo) = {
    f match {
      case d: Der1 => handle(d)
      case d: Der2 => handle(d)
    }
  }
}

object FileManager {

  def fileManager(projectRoot: Path): Behavior[API2.Request[API2.Response]] =
    Behaviors.receive { (context, request) =>
      context.log.info(s"Received $request")
      val response =
        Detail.handleSpecific(context, request.contents, projectRoot)
      context.log.info(s"Replying: $response")
      request.replyTo ! Some(response)
      Behaviors.same
    }
}

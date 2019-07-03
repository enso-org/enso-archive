package org.enso.filemanager

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior

object API {
  final case class PathOutsideProject(projectRoot: Path, accessedPath: Path)
      extends Exception(
        s"Cannot access path $accessedPath because it does not belong to the project under root directory $projectRoot"
      ) {}

  sealed case class Request(
    replyTo: ActorRef[Response],
    contents: RequestPayload) {}

  sealed abstract class Response {}

  case class ErrorResponse(exception: Throwable) extends Response

  abstract class RequestPayload {}

  case class WriteRequest(path: Path, contents: Array[Byte])
      extends RequestPayload {}

  case class WriteResponse() extends Response {}

  case class ReadRequest(path: Path) extends RequestPayload {}

  case class ReadResponse(contents: Array[Byte]) extends Response {}

  case class ExistsRequest(path: Path) extends RequestPayload {}

  case class ExistsResponse(exists: Boolean) extends Response {}

  case class ListRequest(path: Path) extends RequestPayload {}

  case class ListResponse(entries: Array[Path]) extends Response {
    override def equals(obj: Any): Boolean = obj match {
      case rhs: ListResponse => this.entries.deep == rhs.entries.deep
      case _                 => false
    }
  }

  case class StatRequest(path: Path) extends RequestPayload {}

  case class StatResponse(
    path: Path,
    lastModified: Instant,
    size: Long,
    isDirectory: Boolean)
      extends Response {}
}

object FileManager {
  import API._

  def extractPaths(request: RequestPayload): Array[Path] = request match {
    case WriteRequest(p, _) => Array(p)
    case ReadRequest(p)     => Array(p)
    case ExistsRequest(p)   => Array(p)
    case ListRequest(p)     => Array(p)
    case StatRequest(p)     => Array(p)
  }

  def validatePath(validatedPath: Path, projectRoot: Path): Unit = {
    val normalized = validatedPath.toAbsolutePath.normalize()
    if (!normalized.startsWith(projectRoot))
      throw PathOutsideProject(projectRoot, validatedPath)
  }

  def handleSpecific(
    context: ActorContext[Request],
    request: RequestPayload,
    projectRoot: Path
  ): Response = {
    try {
      extractPaths(request).foreach(validatePath(_, projectRoot))
      request match {
        case msg: ListRequest =>
          val str = Files.list(msg.path)
          try {
            ListResponse(str.toArray.map(_.asInstanceOf[Path]))
          } finally str.close()
        case msg: ExistsRequest =>
          ExistsResponse(Files.exists(msg.path))
        case msg: WriteRequest =>
          Files.write(msg.path, msg.contents)
          WriteResponse()
        case msg: StatRequest =>
          val path         = msg.path.toRealPath()
          val lastModified = Files.getLastModifiedTime(path).toInstant
          val size         = Files.size(path)
          val isDirectory  = Files.isDirectory(path)
          StatResponse(path, lastModified, size, isDirectory)
        case msg: ReadRequest =>
          val path     = msg.path.toRealPath()
          val contents = Files.readAllBytes(path)
          ReadResponse(contents)
      }
    } catch {
      case ex: Throwable =>
        context.log.warning(
          "Encountered an exception when handling request: " + ex.toString
        )
        ErrorResponse(ex)
    }
  }

  def fileManager(projectRoot: Path): Behavior[Request] =
    Behaviors.receive { (context, request) =>
      context.log.info("Received {}", request.toString)
      val response = handleSpecific(context, request.contents, projectRoot)
      request.replyTo ! response
      Behaviors.same
    }
}

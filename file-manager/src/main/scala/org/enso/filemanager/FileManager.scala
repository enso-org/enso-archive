package org.enso.filemanager

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import org.apache.commons.io.FileUtils

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

  case class ListResponse(entries: Array[Path]) extends Response {}

  case class StatRequest(path: Path) extends RequestPayload {}

  case class StatResponse(
    path: Path,
    lastModified: Instant,
    size: Long,
    isDirectory: Boolean)
      extends Response {}

  case class TouchFileRequest(path: Path) extends RequestPayload {}

  case class TouchFileResponse() extends Response {}

  case class CopyFileRequest(from: Path, to: Path) extends RequestPayload {}

  case class CopyFileResponse() extends Response {}

  case class MoveFileRequest(from: Path, to: Path) extends RequestPayload {}

  case class MoveFileResponse() extends Response {}

  case class DeleteFileRequest(path: Path) extends RequestPayload {}

  case class DeleteFileResponse() extends Response {}

  case class CopyDirectoryRequest(from: Path, to: Path) extends RequestPayload {}

  case class CopyDirectoryResponse() extends Response {}

  case class MoveDirectoryRequest(from: Path, to: Path) extends RequestPayload {}

  case class MoveDirectoryResponse() extends Response {}

  case class DeleteDirectoryRequest(path: Path) extends RequestPayload {}

  case class DeleteDirectoryResponse() extends Response {}
}

object Detail {
  import API._

  def extractPaths(request: RequestPayload): Array[Path] = request match {
    case WriteRequest(p, _)             => Array(p)
    case ReadRequest(p)                 => Array(p)
    case ExistsRequest(p)               => Array(p)
    case ListRequest(p)                 => Array(p)
    case StatRequest(p)                 => Array(p)
    case TouchFileRequest(p)            => Array(p)
    case CopyFileRequest(from, to)      => Array(from, to)
    case MoveFileRequest(from, to)      => Array(from, to)
    case DeleteFileRequest(p)           => Array(p)
    case CopyDirectoryRequest(from, to) => Array(from, to)
    case MoveDirectoryRequest(from, to) => Array(from, to)
    case DeleteDirectoryRequest(p)      => Array(p)
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
    context: ActorContext[Request],
    request: RequestPayload,
    projectRoot: Path
  ): Response = {
    try {
      Detail.validateRequest(request, projectRoot)
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
        case msg: TouchFileRequest =>
          FileUtils.touch(msg.path.toFile)
          TouchFileResponse()
        case msg: ReadRequest =>
          val path     = msg.path.toRealPath()
          val contents = Files.readAllBytes(path)
          ReadResponse(contents)
        case msg: CopyFileRequest =>
          Files.copy(msg.from, msg.to)
          CopyFileResponse()
        case msg: MoveFileRequest =>
          Files.move(msg.from, msg.to)
          MoveFileResponse()
        case msg: DeleteFileRequest =>
          Files.delete(msg.path)
          DeleteFileResponse()
        case msg: CopyDirectoryRequest =>
          FileUtils.copyDirectory(msg.from.toFile, msg.to.toFile)
          CopyDirectoryResponse()
        case msg: MoveDirectoryRequest =>
          FileUtils.moveDirectory(msg.from.toFile, msg.to.toFile)
          MoveDirectoryResponse()
        case msg: DeleteDirectoryRequest =>
          FileUtils.deleteDirectory(msg.path.toFile)
          DeleteDirectoryResponse()
      }
    } catch {
      case ex: Throwable =>
        context.log.warning(s"Failed to handle request $request: $ex")
        ErrorResponse(ex)
    }
  }
}

object FileManager {
  def fileManager(projectRoot: Path): Behavior[API.Request] =
    Behaviors.receive { (context, request) =>
      context.log.info(s"Received $request")
      val response =
        Detail.handleSpecific(context, request.contents, projectRoot)
      context.log.info(s"Replying: $response")
      request.replyTo ! response
      Behaviors.same
    }
}

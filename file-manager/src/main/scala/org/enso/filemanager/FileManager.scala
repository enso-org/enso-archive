package org.enso.filemanager

import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.UserPrincipal
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

  abstract class RequestPayload[+ResponseType <: SuccessResponse: ClassTag] {
    def touchedPaths: Seq[Path]
    def handle(fileManager: FileManagerBehavior): ResponseType

    def validate(projectRoot: Path): Unit =
      touchedPaths.foreach(Detail.validatePath(_, projectRoot))
  }

  sealed abstract class AnyResponse
  sealed abstract class SuccessResponse          extends AnyResponse
  case class ErrorResponse(exception: Throwable) extends AnyResponse

  case class CopyDirectoryRequest(from: Path, to: Path) extends RequestPayload[CopyDirectoryResponse] {
    override def touchedPaths: Seq[Path] = Seq(from, to)

    override def handle(fileManager: FileManagerBehavior): CopyDirectoryResponse = {
      FileUtils.copyDirectory(from.toFile, to.toFile)
      CopyDirectoryResponse()
    }
  }
  case class CopyDirectoryResponse() extends SuccessResponse {}

  case class CopyFileRequest(from: Path, to: Path) extends RequestPayload[CopyFileResponse] {
    override def touchedPaths: Seq[Path] = Seq(from, to)

    override def handle(fileManager: FileManagerBehavior): CopyFileResponse = {
      Files.copy(from, to)
      CopyFileResponse()
    }
  }
  case class CopyFileResponse() extends SuccessResponse {}



  case class DeleteDirectoryRequest(path: Path) extends RequestPayload[SuccessResponse] {
    override def touchedPaths: Seq[Path] = Seq(path)

    override def handle(fileManager: FileManagerBehavior): SuccessResponse = {
      FileUtils.deleteDirectory(path.toFile)
      DeleteDirectoryResponse()
    }
  }
  case class DeleteDirectoryResponse() extends SuccessResponse

  case class DeleteFileRequest(path: Path) extends RequestPayload[DeleteFileResponse] {
    override def touchedPaths: Seq[Path] = Seq(path)

    override def handle(fileManager: FileManagerBehavior): DeleteFileResponse = {
      Files.delete(path)
      DeleteFileResponse()
    }
  }
  case class DeleteFileResponse() extends SuccessResponse

  case class ExistsRequest(path: Path) extends RequestPayload[ExistsResponse] {
    override def touchedPaths: Seq[Path] = Seq(path)
    override def handle(fileManager: FileManagerBehavior) =
      ExistsResponse(Files.exists(path))
  }
  case class ExistsResponse(exists: Boolean) extends SuccessResponse

  case class ListRequest(path: Path) extends RequestPayload[ListResponse] {
    override def touchedPaths: Seq[Path] = Seq(path)

    override def handle(fileManager: FileManagerBehavior): ListResponse = {
      val str = Files.list(path)
      try {
        ListResponse(str.toArray.map(_.asInstanceOf[Path]))
      } finally str.close()
    }
  }

  case class ListResponse(entries: Array[Path]) extends SuccessResponse {}

  case class MoveDirectoryRequest(from: Path, to: Path) extends RequestPayload[MoveDirectoryResponse] {
    override def touchedPaths: Seq[Path] = Seq(from, to)

    override def handle(fileManager: FileManagerBehavior): MoveDirectoryResponse = {
      FileUtils.moveDirectory(from.toFile, to.toFile)
      MoveDirectoryResponse()
    }
  }
  case class MoveDirectoryResponse() extends SuccessResponse

  case class MoveFileRequest(from: Path, to: Path) extends RequestPayload[MoveFileResponse] {
    override def touchedPaths: Seq[Path] = Seq(from, to)

    override def handle(fileManager: FileManagerBehavior): MoveFileResponse = {
      Files.move(from, to)
      MoveFileResponse()
    }
  }

  case class MoveFileResponse() extends SuccessResponse

  case class ReadRequest(path: Path) extends RequestPayload[ReadResponse] {
    override def touchedPaths: Seq[Path] = Seq(path)
    override def handle(fileManager: FileManagerBehavior): ReadResponse = {
      val contents = Files.readAllBytes(path)
      ReadResponse(contents)
    }
  }

  case class ReadResponse(contents: Array[Byte]) extends SuccessResponse

  case class StatRequest(path: Path) extends RequestPayload[StatResponse] {
    override def touchedPaths: Seq[Path] = Seq(path)

    override def handle(fileManager: FileManagerBehavior): StatResponse = {
      // warning: might be race'y
      val realPath     = path.toRealPath()
      val lastModified = Files.getLastModifiedTime(path).toInstant
      val owner        = Files.getOwner(path)
      val size         = Files.size(path)
      val isDirectory  = Files.isDirectory(path)
      StatResponse(realPath, lastModified, owner, size, isDirectory)
    }
  }

  case class StatResponse(
    path: Path,
    lastModified: Instant,
    owner: UserPrincipal,
    size: Long,
    isDirectory: Boolean)
      extends SuccessResponse

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

  case class WriteRequest(path: Path, contents: Array[Byte])
      extends RequestPayload[WriteResponse] {
    override def touchedPaths: Seq[Path] = Seq(path)
    override def handle(fileManager: FileManagerBehavior): WriteResponse = {
      Files.write(path, contents)
      WriteResponse()
    }
  }
  case class WriteResponse() extends SuccessResponse

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

package org.enso.filemanager

import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.util.Timeout

import io.methvin.watcher.DirectoryChangeEvent
import io.methvin.watcher.DirectoryWatcher

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.UserPrincipal
import java.time.Instant
import java.util.UUID

import org.apache.commons.io.FileUtils

import scala.collection.mutable
import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success
import scala.util.Try

object API {
  abstract class InputMessage {
    def handle(fileManager: FileManagerBehavior): Unit
    def validate(projectRoot: Path): Unit
  }

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
      extends InputMessage {
    override def handle(fileManager: FileManagerBehavior): Unit = {
      fileManager.onMessageTyped(this)
    }
    override def validate(projectRoot: Path): Unit =
      contents.validate(projectRoot)
  }

  abstract class RequestPayload[+ResponseType <: SuccessResponse: ClassTag] {
    def touchedPaths: Seq[Path]
    def handle(fileManager: FileManagerBehavior): ResponseType

    def validate(projectRoot: Path): Unit =
      touchedPaths.foreach(Detail.validatePath(_, projectRoot))
  }

  sealed abstract class SuccessResponse

  case class CopyDirectoryRequest(from: Path, to: Path)
      extends RequestPayload[CopyDirectoryResponse] {
    override def touchedPaths: Seq[Path] = Seq(from, to)

    override def handle(
      fileManager: FileManagerBehavior
    ): CopyDirectoryResponse = {
      FileUtils.copyDirectory(from.toFile, to.toFile)
      CopyDirectoryResponse()
    }
  }
  case class CopyDirectoryResponse() extends SuccessResponse {}

  case class CopyFileRequest(from: Path, to: Path)
      extends RequestPayload[CopyFileResponse] {
    override def touchedPaths: Seq[Path] = Seq(from, to)

    override def handle(fileManager: FileManagerBehavior): CopyFileResponse = {
      Files.copy(from, to)
      CopyFileResponse()
    }
  }
  case class CopyFileResponse() extends SuccessResponse {}

  case class DeleteDirectoryRequest(path: Path)
      extends RequestPayload[SuccessResponse] {
    override def touchedPaths: Seq[Path] = Seq(path)

    override def handle(fileManager: FileManagerBehavior): SuccessResponse = {
      // despite what commons-io documentation says, the exception is not thrown
      // when directory is missing, so we do it by hand.
      if (!Files.exists(path))
        throw new NoSuchFileException(path.toString)

      FileUtils.deleteDirectory(path.toFile)
      DeleteDirectoryResponse()
    }
  }
  case class DeleteDirectoryResponse() extends SuccessResponse

  case class DeleteFileRequest(path: Path)
      extends RequestPayload[DeleteFileResponse] {
    override def touchedPaths: Seq[Path] = Seq(path)

    override def handle(
      fileManager: FileManagerBehavior
    ): DeleteFileResponse = {
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

  case class MoveDirectoryRequest(from: Path, to: Path)
      extends RequestPayload[MoveDirectoryResponse] {
    override def touchedPaths: Seq[Path] = Seq(from, to)

    override def handle(
      fileManager: FileManagerBehavior
    ): MoveDirectoryResponse = {
      FileUtils.moveDirectory(from.toFile, to.toFile)
      MoveDirectoryResponse()
    }
  }
  case class MoveDirectoryResponse() extends SuccessResponse

  case class MoveFileRequest(from: Path, to: Path)
      extends RequestPayload[MoveFileResponse] {
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
      val lastModified = Files.getLastModifiedTime(path).toInstant
      val owner        = Files.getOwner(path)
      val size         = Files.size(path)
      val isDirectory  = Files.isDirectory(path)
      StatResponse(lastModified, owner, size, isDirectory)
    }
  }

  case class StatResponse(
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

  case class CreateWatcherRequest(
    path: Path,
    observer: ActorRef[FileSystemEvent])
      extends RequestPayload[CreateWatcherResponse] {
    override def touchedPaths: Seq[Path] = Seq(path)
    override def handle(
      fileManager: FileManagerBehavior
    ): CreateWatcherResponse = {
      val id = UUID.randomUUID()
      val watcher = DirectoryWatcher
        .builder()
        .path(path)
        .listener(event => {
          fileManager.context.log.info(s"Notifying $observer with $event")
          observer ! FileSystemEvent(event)
        })
        .build()
      watcher.watchAsync()
      fileManager.watchers += (id -> watcher)
      CreateWatcherResponse(id)
    }
  }
  case class CreateWatcherResponse(id: UUID) extends SuccessResponse

  case class WatcherRemoveRequest(id: UUID)
      extends RequestPayload[WatcherRemoveResponse] {
    override def touchedPaths: Seq[Path] = Seq()
    override def handle(
      fileManager: FileManagerBehavior
    ): WatcherRemoveResponse = {
      val watcher = fileManager.watchers(id)
      watcher.close()
      fileManager.watchers -= id
      WatcherRemoveResponse()
    }
  }

  case class WatcherRemoveResponse() extends SuccessResponse

  case class WriteRequest(path: Path, contents: Array[Byte])
      extends RequestPayload[WriteResponse] {
    override def touchedPaths: Seq[Path] = Seq(path)
    override def handle(fileManager: FileManagerBehavior): WriteResponse = {
      Files.write(path, contents)
      WriteResponse()
    }
  }
  case class WriteResponse() extends SuccessResponse

  case class FileSystemEvent(event: DirectoryChangeEvent)
}

object Detail {
  import API._

  def validatePath(validatedPath: Path, projectRoot: Path): Unit = {
    val normalized = validatedPath.toAbsolutePath.normalize()
    if (!normalized.startsWith(projectRoot))
      throw PathOutsideProjectException(projectRoot, validatedPath)
  }
}

case class FileManagerBehavior(
  projectRoot: Path,
  context: ActorContext[API.InputMessage])
    extends AbstractBehavior[API.InputMessage] {
  import API._

  val watchers: mutable.Map[UUID, DirectoryWatcher] = mutable.Map()

  def onMessageTyped[response <: SuccessResponse: ClassTag](
    message: Request[response]
  ): Unit = {
    val response = try {
      message.contents.validate(projectRoot)
      val result = message.contents.handle(this)
      Success(result)
    } catch {
      case ex: Throwable =>
        Failure(ex)
    }
    context.log.info(s"Responding with $response")
    message.replyTo ! response
  }

  override def onMessage(message: InputMessage): Behavior[InputMessage] = {
    context.log.info(s"Received $message")
    message.handle(this)
    this
  }
}

object FileManager {
  import API._

  def fileManager(projectRoot: Path): Behavior[API.InputMessage] =
    Behaviors.setup(context => FileManagerBehavior(projectRoot, context))

  def ask[response <: SuccessResponse: ClassTag](
    actor: ActorRef[API.InputMessage],
    requestPayload: RequestPayload[response]
  )(implicit timeout: Timeout,
    scheduler: Scheduler
  ): Future[Try[response]] = {
    actor.ask(
      (replyTo: ActorRef[Try[response]]) => {
        Request(replyTo, requestPayload)
      }
    )
  }
}

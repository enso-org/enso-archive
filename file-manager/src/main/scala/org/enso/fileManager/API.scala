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
import java.nio.file.NotDirectoryException
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

  type OutputMessage = Try[Success]

  final case class PathOutsideProjectException(
    projectRoot: Path,
    accessedPath: Path)
      extends Exception(
        s"Cannot access path $accessedPath because it does not belong to the project under root directory $projectRoot"
      )

  ////////////////////////
  //// RPC Definition ////
  ////////////////////////
  
  sealed case class Request[SpecificResponse <: Success: ClassTag](
    replyTo: ActorRef[Try[SpecificResponse]],
    contents: Request.Payload[SpecificResponse])
      extends InputMessage {

    // FIXME: remove curly braces
    override def handle(fileManager: FileManagerBehavior): Unit = {
      fileManager.onMessageTyped(this)
    }
    override def validate(projectRoot: Path): Unit =
      contents.validate(projectRoot)
  }
  
  object Request {
    abstract class Payload[+ResponseType <: Success: ClassTag] {
      def touchedPaths: Seq[Path]
      def handle(fileManager: FileManagerBehavior): ResponseType

      def validate(projectRoot: Path): Unit =
        touchedPaths.foreach(Detail.validatePath(_, projectRoot))
    }
  }

  object Response {
    sealed abstract class Success
  }

  import Request.Payload
  import Response.Success

  //////////////////////////////
  //// Requests / Responses ////
  //////////////////////////////

  case class CopyDirectoryRequest(from: Path, to: Path)
      extends Request.Payload[CopyDirectory.Response] {
    override def touchedPaths: Seq[Path] = Seq(from, to)

    override def handle(
      fileManager: FileManager.Behavior
    ): CopyDirectory.Response = {
      FileUtils.copyDirectory(from.toFile, to.toFile)
      CopyDirectory.Response()
    }
  }
  case class CopyDirectory.Response() extends Success {}


  object CopyFile {
    case object Response extends Success
    case class Request(from: Path, to: Path) extends Payload[Response] {
      override def touchedPaths: Seq[Path] = 
        Seq(from, to)
      override def handle(fileManager: FileManager.Behavior): Response = {
        Files.copy(from, to)
        Response
      }
    }
  }

  case class DeleteDirectoryRequest(path: Path)
      extends Request.Payload[Success] {
    override def touchedPaths: Seq[Path] = Seq(path)

    override def handle(fileManager: FileManagerBehavior): Success = {
      // Despite what commons-io documentation says, the exception is not thrown
      // when directory is missing, so we do it by hand.
      if (Files.notExists(path))
        throw new NoSuchFileException(path.toString)

      FileUtils.deleteDirectory(path.toFile)
      DeleteDirectoryResponse
    }
  }
  case object DeleteDirectoryResponse extends Success




  case class DeleteFileRequest(path: Path)
      extends Request.Payload[DeleteFileResponse] {
    override def touchedPaths: Seq[Path] = Seq(path)

    override def handle(
      fileManager: FileManagerBehavior
    ): DeleteFileResponse = {
      Files.delete(path)
      DeleteFileResponse()
    }
  }
  case class DeleteFileResponse() extends Success

  case class ExistsRequest(path: Path) extends Request.Payload[ExistsResponse] {
    override def touchedPaths: Seq[Path] = Seq(path)
    override def handle(fileManager: FileManagerBehavior) =
      ExistsResponse(Files.exists(path))
  }
  case class ExistsResponse(exists: Boolean) extends Success

  case class ListRequest(path: Path) extends Request.Payload[ListResponse] {
    override def touchedPaths: Seq[Path] = Seq(path)

    override def handle(fileManager: FileManagerBehavior): ListResponse = {
      val str = Files.list(path)
      try {
        ListResponse(str.to[Seq].map(_.asInstanceOf[Path]))
      } finally str.close()
    }
  }

  case class ListResponse(entries: Seq[Path]) extends Success {}

  case class MoveDirectoryRequest(from: Path, to: Path)
      extends Request.Payload[MoveDirectoryResponse] {
    override def touchedPaths: Seq[Path] = Seq(from, to)

    override def handle(
      fileManager: FileManagerBehavior
    ): MoveDirectoryResponse = {
      FileUtils.moveDirectory(from.toFile, to.toFile)
      MoveDirectoryResponse()
    }
  }
  case class MoveDirectoryResponse() extends Success

  case class MoveFileRequest(from: Path, to: Path)
      extends Request.Payload[MoveFileResponse] {
    override def touchedPaths: Seq[Path] = Seq(from, to)

    override def handle(fileManager: FileManagerBehavior): MoveFileResponse = {
      Files.move(from, to)
      MoveFileResponse()
    }
  }

  case class MoveFileResponse() extends Success

  case class ReadRequest(path: Path) extends Request.Payload[ReadResponse] {
    override def touchedPaths: Seq[Path] = Seq(path)
    override def handle(fileManager: FileManagerBehavior): ReadResponse = {
      val contents = Files.readAllBytes(path)
      ReadResponse(contents)
    }
  }

  case class ReadResponse(contents: Array[Byte]) extends Success

  case class StatRequest(path: Path) extends Request.Payload[StatResponse] {
    override def touchedPaths: Seq[Path] = Seq(path)

    override def handle(fileManager: FileManagerBehavior): StatResponse = {
      // FIXME: Comments starting with upper case char pls! :)
      // FIXME: If thats not in guidelines, pls add it there :)

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
      extends Success

  // FIXME: lets write better var names than "p" pls :)
  case class TouchFileRequest(p: Path)
      extends Request.Payload[TouchFileResponse] {
    override def touchedPaths: Seq[Path] = Seq(p)
    override def handle(fileManager: FileManagerBehavior): TouchFileResponse = {
      FileUtils.touch(p.toFile)
      TouchFileResponse()
    }
  }
  case class TouchFileResponse() extends Success

  // NOTE
  // The watched path must designate a directory (i.e. not a regular file and
  // not a symlink). The parent path components may include symlink. Watch is
  // recursive. On removal, it is not guaranteed that delete notifications will
  // be emitted for all elements, it might happen that only parent elements'
  // deletion will be observed.
  // Also, if the parent is removed quickly after an item, the event for the
  // item might not get emitted as well.
  case class CreateWatcherRequest(
    path: Path,
    observer: ActorRef[FileSystemEvent])
      extends Request.Payload[CreateWatcherResponse] {
    override def touchedPaths: Seq[Path] = Seq(path)
    override def handle(
      fileManager: FileManagerBehavior
    ): CreateWatcherResponse = {
      // Watching a symlink target works only on Windows, presumably thanks to
      // recursive watch being natively supported. We block it until we know it
      // works on all supported platforms.
      if (Files.isSymbolicLink(path))
        throw new NotDirectoryException(path.toString)

      // Watching ordinary file throws an exception on Windows. Similarly, to
      // unify observed behavior we check for this here.
      
      // FIXME: isNotDirectory?
      
      if (!Files.isDirectory(path))
        throw new NotDirectoryException(path.toString)

      // macOS generates events containing resolved path, i.e. with symlinks
      // resolved. We don't really want this, as we want to be completely
      // indifferent to symlink presence and still be able to easily compare
      // paths. Therefore if we are under symlink and generated event uses
      // real path, we replace it with path prefix that was observation target
      val realPath = path.toRealPath()


      // val fixPath = (p: Path) => {
      //   if (realPath != path && p.startsWith(realPath)) {
      //     val relative = realPath.relativize(p)
      //     path.resolve(relative)
      //   } else p
      // }

      // FIXME: The ^^^ code to vvv
      val fixPath = (path: Path) => {
        val isCorrect = realPath != path && p.startsWith(realPath)
        isCorrect match {
          True  => path.resolve(realPath.relativize(path))
          False => path
        }
      }

      val id = UUID.randomUUID()
      val watcher = DirectoryWatcher
        .builder
        .path(path)
        .listener { event => 
          val message = FileSystemEvent(event.eventType, fixPath(event.path)) // FIXME: things like getters: event.eventType() => event.eventType
          if (message.path != path) {
            fileManager.context.log.debug(s"Notifying $observer with $message")
            observer ! message
          }
        }
        .build()
      watcher.watchAsync()
      fileManager.watchers += (id -> watcher)
      CreateWatcherResponse(id)
    }
  }
  case class CreateWatcherResponse(id: UUID) extends Success

  case class WatcherRemoveRequest(id: UUID)
      extends Request.Payload[WatcherRemoveResponse] {
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

  case class WatcherRemoveResponse() extends Success

  case class WriteRequest(path: Path, contents: Array[Byte])
      extends Request.Payload[WriteResponse] {
    override def touchedPaths: Seq[Path] = Seq(path)
    override def handle(fileManager: FileManagerBehavior): WriteResponse = {
      Files.write(path, contents)
      WriteResponse()
    }
  }
  case class WriteResponse() extends Success

  case class FileSystemEvent(
    eventType: DirectoryChangeEvent.EventType,
    path: Path)
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

  override def finalize(): Unit = super.finalize()

  def onMessageTyped[response <: Success: ClassTag](
    message: Request[response]
  ): Unit = {
    val response = try {
      message.contents.validate(projectRoot)
      val result = message.contents.handle(this)
      Success(result)
    } catch { case ex: Throwable => Failure(ex) }
    context.log.debug(s"Responding with $response")
    message.replyTo ! response
  }

  override def onMessage(message: InputMessage): this.type = { // FIXME: check if works this. type instead of Behavior[InputMessage]
    context.log.debug(s"Received $message")
    message.handle(this)
    this
  }
}



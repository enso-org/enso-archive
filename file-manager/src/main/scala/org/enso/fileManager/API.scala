package org.enso.fileManager

import akka.actor.typed.ActorRef
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
import org.enso.FileManager

import scala.reflect.ClassTag
import scala.util.Try

object API {
  import Request.Payload
  import Response.Success

  abstract class InputMessage {
    def handle(fileManager: FileManager): Unit
    def validate(projectRoot: Path): Unit
  }

  type OutputMessage = Try[Success]

  final case class PathOutsideProjectException(
    projectRoot: Path,
    accessedPath: Path)
      extends Exception(
        s"""Cannot access path $accessedPath because it does not belong to 
           |the project under root directory $projectRoot""".stripMargin
          .replaceAll("\n", " ")
      )

  ////////////////////////
  //// RPC Definition ////
  ////////////////////////

  sealed case class Request[SpecificResponse <: Success: ClassTag](
    replyTo: ActorRef[Try[SpecificResponse]],
    contents: Payload[SpecificResponse])
      extends InputMessage {

    override def handle(fileManager: FileManager): Unit =
      fileManager.onMessageTyped(this)
    override def validate(projectRoot: Path): Unit =
      contents.validate(projectRoot)
  }

  object Request {
    abstract class Payload[+ResponseType <: Success: ClassTag] {
      def touchedPaths: Seq[Path]
      def handle(fileManager: FileManager): ResponseType

      def validate(projectRoot: Path): Unit =
        touchedPaths.foreach(Detail.validatePath(_, projectRoot))
    }
  }

  object Response {
    sealed abstract class Success
  }

  //////////////////////////////
  //// Requests / Responses ////
  //////////////////////////////

  object CopyDirectory {
    case class Response() extends Success
    case class Request(from: Path, to: Path) extends Payload[Response] {
      override def touchedPaths: Seq[Path] = Seq(from, to)
      override def handle(fileManager: FileManager): Response = {
        FileUtils.copyDirectory(from.toFile, to.toFile)
        Response()
      }
    }
  }

  object CopyFile {
    case class Response() extends Success
    case class Request(from: Path, to: Path) extends Payload[Response] {
      override def touchedPaths: Seq[Path] =
        Seq(from, to)
      override def handle(fileManager: FileManager): Response = {
        Files.copy(from, to)
        Response()
      }
    }
  }

  object DeleteDirectory {
    case class Response() extends Success
    case class Request(path: Path) extends Payload[Response] {
      override def touchedPaths: Seq[Path] = Seq(path)
      override def handle(fileManager: FileManager): Response = {
        // Despite what commons-io documentation says, the exception is not thrown
        // when directory is missing, so we do it by hand.
        if (Files.notExists(path))
          throw new NoSuchFileException(path.toString)

        FileUtils.deleteDirectory(path.toFile)
        Response()
      }
    }
  }

  object DeleteFile {
    case class Response() extends Success
    case class Request(path: Path) extends Payload[Response] {
      override def touchedPaths: Seq[Path] = Seq(path)
      override def handle(fileManager: FileManager): Response = {
        Files.delete(path)
        Response()
      }
    }
  }

  object Exists {
    case class Response(exists: Boolean) extends Success
    case class Request(path: Path) extends Payload[Response] {
      override def touchedPaths: Seq[Path] = Seq(path)
      override def handle(fileManager: FileManager) =
        Response(Files.exists(path))
    }
  }

  object List {
    case class Response(entries: Seq[Path]) extends Success
    case class Request(path: Path) extends Payload[Response] {
      override def touchedPaths: Seq[Path] = Seq(path)
      override def handle(fileManager: FileManager): Response = {
        val str = Files.list(path)
        try {
          Response(str.toArray.to[Vector].map(_.asInstanceOf[Path]))
        } finally str.close()
      }
    }
  }

  object MoveDirectory {
    case class Response() extends Success
    case class Request(from: Path, to: Path) extends Payload[Response] {
      override def touchedPaths: Seq[Path] = Seq(from, to)
      override def handle(fileManager: FileManager): Response = {
        FileUtils.moveDirectory(from.toFile, to.toFile)
        Response()
      }
    }
  }

  object MoveFile {
    case class Response() extends Success
    case class Request(from: Path, to: Path) extends Payload[Response] {
      override def touchedPaths: Seq[Path] = Seq(from, to)
      override def handle(fileManager: FileManager): Response = {
        Files.move(from, to)
        Response()
      }
    }
  }

  object Read {
    case class Response(contents: Array[Byte]) extends Success
    case class Request(path: Path) extends Payload[Response] {
      override def touchedPaths: Seq[Path] = Seq(path)
      override def handle(fileManager: FileManager): Response = {
        val contents = Files.readAllBytes(path)
        Response(contents)
      }
    }
  }

  object Stat {
    case class Response(
      lastModified: Instant,
      owner: UserPrincipal,
      size: Long,
      isDirectory: Boolean)
        extends Success
    case class Request(path: Path) extends Payload[Response] {
      override def touchedPaths: Seq[Path] = Seq(path)
      override def handle(fileManager: FileManager): Response = {
        // FIXME: Comments starting with upper case char pls! :)
        // FIXME: If thats not in guidelines, pls add it there :)

        // warning: might be race'y
        val lastModified = Files.getLastModifiedTime(path).toInstant
        val owner        = Files.getOwner(path)
        val size         = Files.size(path)
        val isDirectory  = Files.isDirectory(path)
        Response(lastModified, owner, size, isDirectory)
      }
    }
  }

  object Touch {
    case class Response() extends Success
    case class Request(path: Path) extends Payload[Response] {
      override def touchedPaths: Seq[Path] = Seq(path)
      override def handle(fileManager: FileManager): Response = {
        FileUtils.touch(path.toFile)
        Response()
      }
    }
  }

  object Watch {

    // NOTE
    // The watched path must designate a directory (i.e. not a regular file and
    // not a symlink). The parent path components may include symlink. Watch is
    // recursive. On removal, it is not guaranteed that delete notifications will
    // be emitted for all elements, it might happen that only parent elements'
    // deletion will be observed.
    // Also, if the parent is removed quickly after an item, the event for the
    // item might not get emitted as well.
    object Create {
      case class Response(id: UUID) extends Success
      case class Request(
        observedPath: Path,
        observer: ActorRef[FileSystemEvent])
          extends Payload[Response] {
        override def touchedPaths: Seq[Path] = Seq(observedPath)

        override def handle(fileManager: FileManager): Response = {
          // Watching a symlink target works only on Windows, presumably thanks to
          // recursive watch being natively supported. We block it until we know it
          // works on all supported platforms.
          if (Files.isSymbolicLink(observedPath))
            throw new NotDirectoryException(observedPath.toString)

          // Watching ordinary file throws an exception on Windows. Similarly, to
          // unify observed behavior we check for this here.
          if (!Files.isDirectory(observedPath))
            throw new NotDirectoryException(observedPath.toString)

          // macOS generates events containing resolved path, i.e. with symlinks
          // resolved. We don't really want this, as we want to be completely
          // indifferent to symlink presence and still be able to easily compare
          // paths. Therefore if we are under symlink and generated event uses
          // real path, we replace it with path prefix that was observation target
          val realPath       = observedPath.toRealPath()
          val unresolvedPath = realPath != observedPath

          val fixPath = (path: Path) => {
            val needsFixing = unresolvedPath && path.startsWith(realPath)
            needsFixing match {
              case true  => path.resolve(realPath.relativize(path))
              case false => path
            }
          }

          val id = UUID.randomUUID()
          val watcher = DirectoryWatcher.builder
            .path(observedPath)
            .listener { event =>
              val message = FileSystemEvent(
                event.eventType,
                fixPath(event.path)
              )
              if (message.path != observedPath) {
                val logText = s"Notifying $observer with $message"
                fileManager.context.log.debug(logText)
                observer ! message
              }
            }
            .build()
          watcher.watchAsync()
          fileManager.watchers += (id -> watcher)
          Response(id)
        }
      }
    }

    object Remove {
      case class Response() extends Success
      case class Request(id: UUID) extends Payload[Response] {
        override def touchedPaths: Seq[Path] = Seq()
        override def handle(fileManager: FileManager): Response = {
          val watcher = fileManager.watchers(id)
          watcher.close()
          fileManager.watchers -= id
          Response()
        }
      }
    }
  }

  object Write {
    case class Response() extends Success
    case class Request(path: Path, contents: Array[Byte])
        extends Payload[Response] {
      override def touchedPaths: Seq[Path] = Seq(path)
      override def handle(fileManager: FileManager): Response = {
        Files.write(path, contents)
        Response()
      }
    }
  }

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

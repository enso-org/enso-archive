package org.enso.filemanager

import java.io.{File, FileWriter}
import java.nio.file.{Files, Path, Paths}
import java.time.Instant

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}


object FileManager {
  sealed case class Request(replyTo: ActorRef[FileManager.Response], contents: RequestContents) {}
  sealed abstract class Response {}
  case class ErrorResponse(exception: Throwable) extends Response

  abstract class RequestContents {}

  case class WriteRequest(path: File, contents: String) extends RequestContents {}
  case class WriteResponse() extends FileManager.Response {}

  case class ExistsRequest(path: Path) extends RequestContents {}
  case class ExistsResponse(exists: Boolean) extends FileManager.Response {}

  case class ListRequest(path: Path) extends RequestContents {}
  case class ListResponse(entries: Array[Path]) extends FileManager.Response {
    override def equals(obj: Any): Boolean = obj match {
      case rhs: ListResponse => this.entries.deep == rhs.entries.deep
      case _                 => false
    }
  }

  case class StatRequest(path: Path) extends RequestContents {}
  case class StatResponse(path: Path, lastModified: Instant, size: Long, isDirectory: Boolean) extends FileManager.Response {}

  def handleSpecific(request: FileManager.RequestContents): FileManager.Response = {
    try {
      request match {
        case msg: ListRequest =>
          val str = Files.list(msg.path)
          try {
            ListResponse(str.toArray.map(_.asInstanceOf[Path]))
          }
          finally str.close()
        case msg: ExistsRequest =>
          ExistsResponse(Files.exists(msg.path))
        case msg: WriteRequest =>
          val writer = new FileWriter(msg.path)
          try {
            writer.write(msg.contents)
            WriteResponse()
          }
          finally
            writer.close()
        case msg: StatRequest =>
          val path = msg.path.toRealPath()
          val lastModified = Files.getLastModifiedTime(path).toInstant
          val size = Files.size(path)
          val isDirectory = Files.isDirectory(path)
          StatResponse(path, lastModified, size, isDirectory)
      }
    }
    catch {
      case ex: Throwable =>
        println("Encountered an exception when handling request: " + ex.toString)
        ErrorResponse(ex)
    }
  }

  val fileManager: Behavior[FileManager.Request] = Behaviors.receive { (context, request) =>
    context.log.info("Received {}", request.toString)
    val response = handleSpecific(request.contents)
    request.replyTo ! response
    Behaviors.same
  }
}
package org.enso.filemanager

import java.io.{File, FileWriter}
import java.time.Instant

import akka.actor.Props
import org.enso.filemanager.FileManager._
import akka.actor.{Actor, ActorRef}

class FileManager extends Actor {

  def handleSpecific(request: FileManager.RequestContents): FileManager.Response = request match {
    case msg: ListRequest =>
      ListResponse(msg.path.listFiles())
    case msg: ExistsRequest =>
      ExistsResponse(msg.path.exists())
    case msg: WriteRequest =>
      val writer = new FileWriter(msg.path)
      try {
        writer.write(msg.contents)
        WriteResponse()
      }
      finally
        writer.close()
    case msg: StatRequest =>
      val path = msg.path.getCanonicalFile
      val lastModified = Instant.ofEpochMilli(path.lastModified())
      val size = path.length()
      val isDirectory = path.isDirectory
      StatResponse(path, lastModified, size, isDirectory)
  }

  override def receive: Receive = {
    case msg: FileManager.Request =>
      val response = try {
        handleSpecific(msg.contents)
      }
      catch {
        case ex: Throwable =>
          println("Encountered an exception when handling request: " + ex.toString)
          FileManager.ErrorResponse(ex.toString)
      }
      msg.replyTo ! response
    case msg =>
      println("FileManager: Unrecognized message: " + msg.toString)
  }
}

object FileManager {
  def props: Props = Props[FileManager]

  sealed case class Request(replyTo: ActorRef, contents: RequestContents) {}
  sealed abstract class Response {}
  case class ErrorResponse(errorMessage: String)

  abstract class RequestContents {}

  case class WriteRequest(path: File, contents: String) extends RequestContents {}
  case class WriteResponse() extends FileManager.Response {}

  case class ExistsRequest(path: File) extends RequestContents {}
  case class ExistsResponse(exists: Boolean) extends FileManager.Response {}

  case class ListRequest(path: File) extends RequestContents {}
  case class ListResponse(entries: Array[File]) extends FileManager.Response {
    override def equals(obj: Any): Boolean = obj match {
      case rhs: ListResponse => this.entries.deep == rhs.entries.deep
      case _                 => false
    }
  }

  case class StatRequest(path: File) extends RequestContents {}
  case class StatResponse(path: File, lastModified: Instant, size: Long, isDirectory: Boolean) extends FileManager.Response {}
}
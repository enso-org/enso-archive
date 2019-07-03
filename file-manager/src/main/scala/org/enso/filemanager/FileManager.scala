package org.enso.filemanager

import java.io.{File, FileWriter}
import java.nio.file.{Files, Path, Paths}
import java.time.Instant

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import jdk.nashorn.internal.runtime.regexp.joni.constants.Arguments


object API {

  abstract class ResponseBase {
    val isError: Boolean
  }

  //  trait Tag[validResponse] {
  //  }
  //  sealed case class ErrorResponse(ex: Throwable) extends ResponseBase with Tag[Any] {
  //    override val isError: Boolean = true
  //  }
  //  sealed case class SuccessResponse[T]() extends ResponseBase with Tag[T] {
  //    override val isError: Boolean = false
  //  }
  //
  //  abstract class RequestPayload {
  //    type response <: ResponseBase
  //  }
  //
  //  trait Method {
  //    type Response <: RequestBase
  //    type Request <: RequestBase
  //  }

  trait Method {
    type Response
    type Request
  }


  object CopyFile {

    case class Response() {}

    case class Request() {}

  }

  def main(args: Array[String]): Unit = {
  }


  //  object List extends Method {
  //    type method = this.type
  //  }
  //
  //  def main(args: Array[String]): Unit = {
  //    val m: Method = List
  //    val rq: m.Request = List.Request()
  //    val rs: m.Response= null
  //  }
}


object FileManager {

  sealed case class Request(replyTo: ActorRef[Response], contents: RequestPayload) {}

  sealed abstract class Response {}

  case class ErrorResponse(exception: Throwable) extends Response

  abstract class RequestPayload {}

  case class WriteRequest(path: Path, contents: Array[Byte]) extends RequestPayload {}

  case class WriteResponse() extends Response {}

  case class ReadRequest(path: Path) extends RequestPayload {}

  case class ReadResponse(contents: Array[Byte]) extends Response {}

  case class ExistsRequest(path: Path) extends RequestPayload {}

  case class ExistsResponse(exists: Boolean) extends Response {}

  case class ListRequest(path: Path) extends RequestPayload {}

  case class ListResponse(entries: Array[Path]) extends Response {
    override def equals(obj: Any): Boolean = obj match {
      case rhs: ListResponse => this.entries.deep == rhs.entries.deep
      case _ => false
    }
  }

  case class StatRequest(path: Path) extends RequestPayload {}

  case class StatResponse
  (path: Path, lastModified: Instant, size: Long, isDirectory: Boolean)
    extends Response {}

  def extractPaths(request: RequestPayload): Array[Path] = request match {
    case WriteRequest(p, _) => Array(p)
    case ExistsRequest(p) => Array(p)
    case ListRequest(p) => Array(p)
  }

  def handleSpecific(context: ActorContext[FileManager.Request], request: RequestPayload): Response = {
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
          Files.write(msg.path, msg.contents)
          WriteResponse()
        case msg: StatRequest =>
          val path = msg.path.toRealPath()
          val lastModified = Files.getLastModifiedTime(path).toInstant
          val size = Files.size(path)
          val isDirectory = Files.isDirectory(path)
          StatResponse(path, lastModified, size, isDirectory)
        case msg: ReadRequest =>
          val path = msg.path.toRealPath()
          val contents = Files.readAllBytes(path)
          ReadResponse(contents)
      }
    }
    catch {
      case ex: Throwable =>
        context.log.warning("Encountered an exception when handling request: " + ex.toString)
        ErrorResponse(ex)
    }
  }

  val fileManager: Behavior[FileManager.Request] = Behaviors.receive { (context, request) =>
    context.log.info("Received {}", request.toString)
    val response = handleSpecific(context, request.contents)
    request.replyTo ! response
    Behaviors.same
  }
}
package org.enso.parserservice

import io.circe.Json
import io.circe.parser._
import io.circe.generic.auto._
import io.circe.syntax._
import org.enso.syntax.text.AST

object Protocol {
  sealed trait Request
  final case class ParseRequest(program: String) extends Request

  sealed trait Response
  final case class Success(ast: String)   extends Response
  final case class Error(message: String) extends Response
}

/** Exchanging JSON-encoded messages: a single Response for a Request. */
trait Protocol {
  import Protocol._

  def handleRequest(request: Request): Response

  def handleMessage(input: String): String = {
    try {
      decode[Request](input) match {
        case Left(err)      => throw err
        case Right(request) => handleRequest(request).asJson.toString()
      }
    } catch {
      case e: Throwable => (Error(e.toString): Response).asJson.noSpaces
    }
  }
}

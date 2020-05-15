package org.enso.languageserver.io

import java.io.PrintStream

import akka.actor.{Actor, Props}
import org.enso.languageserver.io.InputOutputProtocol.FeedStandardInput

class InputRedirectionController(pipeEnd: PrintStream) extends Actor {

  override def receive: Receive = {
    case FeedStandardInput(input, isLineTerminated) =>
      if (isLineTerminated) {
        pipeEnd.println(input)
      } else {
        pipeEnd.print(input)
      }
  }

}

object InputRedirectionController {

  def props(pipeEnd: PrintStream): Props =
    Props(new InputRedirectionController(pipeEnd))

}

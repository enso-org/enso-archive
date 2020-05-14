package org.enso.languageserver.io
import org.enso.jsonrpc.{HasParams, HasResult, Method, Unused}

object InputOutputApi {

  case object RedirectStdOut extends Method("io/redirectStdOut") {

    implicit val hasParams = new HasParams[this.type] {
      type Params = Unused.type
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case object SuppressStdOut extends Method("io/suppressStdOut") {

    implicit val hasParams = new HasParams[this.type] {
      type Params = Unused.type
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case object StdOutAppended extends Method("io/stdOutAppended") {

    case class Params(charSequence: String)

    implicit val hasParams = new HasParams[this.type] {
      type Params = StdOutAppended.Params
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

}

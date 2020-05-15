package org.enso.languageserver.io
import org.enso.jsonrpc.{HasParams, HasResult, Method, Unused}

object InputOutputApi {

  case object RedirectStandardOutput
      extends Method("io/redirectStandardOutput") {

    implicit val hasParams = new HasParams[this.type] {
      type Params = Unused.type
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case object SuppressStandardOutput
      extends Method("io/suppressStandardOutput") {

    implicit val hasParams = new HasParams[this.type] {
      type Params = Unused.type
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case object StandardOutputAppended
      extends Method("io/standardOutputAppended") {

    case class Params(output: String)

    implicit val hasParams = new HasParams[this.type] {
      type Params = StandardOutputAppended.Params
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case object FeedStandardInput extends Method("io/feedStandardInput") {

    case class Params(input: String, isLineTerminated: Boolean)

    implicit val hasParams = new HasParams[this.type] {
      type Params = FeedStandardInput.Params
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

}

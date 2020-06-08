package org.enso.syntax.text

import org.enso.flexer.Parser.Result

/** This preprocessor step is responsible for hoisting occurrences of `in` as a
  * variable identifier to occurrences of an _operator_ identifier, letting it
  * behave properly in the syntax.
  */
case object InHoisting {

  /** Executes the hoisting procedure on the provided token stream.
    *
    * @param tokenStream the input token stream
    * @return `tokenStream` with all occurrences of `Var("in")` replaced with
    *         `Opr("in")`
    */
  def run(tokenStream: Result[AST.Module]): Result[AST.Module] = {
    tokenStream.value match {
      case Result.Failure(result) => ???
      case Result.Partial(result) => ???
      case Result.Success(result) => ???
    }
  }
}

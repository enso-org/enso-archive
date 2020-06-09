package org.enso.polyglot.debugger

import org.enso.polyglot.debugger.protocol.{
  ExceptionRepresentation,
  ObjectRepresentation
}

trait ReplExecutor {

  /**
    * Evaluates an arbitrary expression in the current execution context.
    *
    * @param expression the expression to evaluate
    * @return the result of evaluating the expression or error
    */
  def evaluate(
    expression: String
  ): Either[ExceptionRepresentation, ObjectRepresentation]

  /**
    * Lists all the bindings available in the current execution scope.
    *
    * @return a map, where keys are variable names and values are current
    *         values of variables.
    */
  def listBindings(): Map[String, ObjectRepresentation]

  /**
    * Terminates this REPL session.
    *
    * The last result of {@link #evaluate(String)} (or
    * {@link Builtins#unit()} if {@link #evaluate(String)} was not called
    * before) will be returned from the instrumented node.
    *
    * This function must always be called at the end of REPL session, as
    * otherwise the program will never resume. It's forbidden to use this object
    * after exit has been called.
    *
    * As it brings control back to the interpreter, it never returns.
    */
  def exit(): Nothing
}

trait SessionManager {
  def startSession(executor: ReplExecutor): Nothing
}

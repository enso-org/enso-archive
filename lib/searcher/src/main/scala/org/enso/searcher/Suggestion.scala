package org.enso.searcher

/** A search suggestion. */
sealed trait Suggestion
object Suggestion {

  /** An argument of an atom or a function.
    *
    * @param name the argument name
    * @param reprType the type of the argument
    * @param isSuspended is the argument lazy
    * @param hasDefault does the argument have a default
    * @param defaultValue optional default value
    */
  case class Argument(
    name: String,
    reprType: String,
    isSuspended: Boolean,
    hasDefault: Boolean,
    defaultValue: Option[String]
  )

  /** A value constructor.
    *
    * @param name the atom name
    * @param arguments the list of arguments
    * @param returnType the type of an atom
    * @param documentation the documentation string
    */
  case class Atom(
    name: String,
    arguments: Seq[Argument],
    returnType: String,
    documentation: Option[String]
  ) extends Suggestion

  /** A function defined on a type or a module.
    *
    * @param name the method name
    * @param arguments the function arguments
    * @param selfType the self type of a method
    * @param returnType the return type of a method
    * @param documentation the documentation string
    */
  case class Method(
    name: String,
    arguments: Seq[Argument],
    selfType: String,
    returnType: String,
    documentation: Option[String]
  ) extends Suggestion

  /** A local value.
    *
    * @param name the name of a value
    * @param returnType the type of a local value
    */
  case class Local(name: String, returnType: String) extends Suggestion
}

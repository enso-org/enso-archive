package org.enso.polyglot

import org.graalvm.polyglot.Value

/**
  * Represents an Enso Module.
  *
  * @param value the polyglot value of this scope
  */
class Module(private val value: Value) {
  import MethodNames.Module._

  /**
    * @return the associated type of this module
    */
  def getAssociatedConstructor: Value =
    value.invokeMember(GET_ASSOCIATED_CONSTRUCTOR)

  /**
    * Gets a method by the type it's defined on and name.
    *
    * @param constructor the constructor the method is defined on
    * @param name the name of the method
    * @return the runtime representation of the method
    */
  def getMethod(constructor: Value, name: String): Function =
    new Function(value.invokeMember(GET_METHOD, constructor, name))

  /**
    * Parses additional source code in the context of this module.
    *
    * Updates the module with any new methods and imports from the new
    * source.
    *
    * @param additionalSource the new source to parse
    */
  def patch(additionalSource: String): Unit =
    value.invokeMember(PATCH, additionalSource)
}
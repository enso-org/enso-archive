package org.enso.polyglot

import org.graalvm.polyglot.Value

/**
  * Represents an Enso Module.
  *
  * @param value the polyglot value of this scope
  */
class Module(private val value: Value) {

  /**
    * @return the associated type of this module
    */
  def getAssociatedConstructor: Value =
    value.invokeMember("get_associated_constructor")

  /**
    * Gets a method by the type it's defined on and name.
    *
    * @param constructor the constructor the method is defined on
    * @param name the name of the method
    * @return the runtime representation of the method
    */
  def getMethod(constructor: Value, name: String): Function =
    new Function(value.invokeMember("get_method", constructor, name))

  /**
    * Parses additional source code in the context of this module.
    *
    * Updates the module with any new methods and imports from the new
    * source.
    *
    * @param additionalSource the new source to parse
    */
  def patch(additionalSource: String): Unit =
    value.invokeMember("patch", additionalSource)
}
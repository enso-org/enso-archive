package org.enso.polyglot

import org.graalvm.polyglot.{Context, Value}

/**
  * Represents the top scope of Enso execution context
  *
  * @param value the polyglot value of this scope
  */
class TopScope(private val value: Value) {

  /** Gets a module by name.
    *
    * @param name the module name to get
    * @return the representation of the module
    */
  def getModule(name: String): Module =
    new Module(value.invokeMember("get_module", name))

  /**
    * Creates a new, unregistered module.
    *
    * The returned module is not accessible through [[getModule()]].
    *
    * @param name the name for the new module
    * @return a new module
    */
  def createModule(name: String): Module =
    new Module(value.invokeMember("create_module", name))
}

object TopScope {

  /**
    * @param context the current language context
    * @return the top scope of Enso execution context
    */
  def get(context: Context): TopScope =
    new TopScope(context.getBindings(LanguageInfo.ID))
}

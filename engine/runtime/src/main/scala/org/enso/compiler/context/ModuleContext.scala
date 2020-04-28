package org.enso.compiler.context

import org.enso.compiler.pass.FreshNameSupply

/** A type containing the information about the execution context for a module.
  *
  * @param freshNameSupply the compiler's supply of fresh names
  */
case class ModuleContext(freshNameSupply: Option[FreshNameSupply] = None)
object ModuleContext {

  /** Implements a null-safe conversion from nullable objects to scala's option
    * internally.
    *
    * @param freshNameSupply the compiler's supply of fresh names
    * @return the [[ModuleContext]] instance corresponding to the arguments
    */
  def fromJava(freshNameSupply: FreshNameSupply): ModuleContext = {
    ModuleContext(Option(freshNameSupply))
  }
}

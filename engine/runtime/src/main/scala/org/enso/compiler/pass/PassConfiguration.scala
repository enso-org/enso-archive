package org.enso.compiler.pass

import shapeless.{HList, LUBConstraint}

import scala.annotation.unused

/** Stores configuration for the various compiler passes. */
class PassConfiguration(
  configs: Map[IRPass, Any] = Map()
) {
  // TODO [AA] Ensure that the constructor is type safe (use hlists of
  //  dependent pairs)
  private var configuration: Map[IRPass, Any] = configs

  /** Adds a new configuration entity to the pass configuration, or updates it
    * if it already exists for a given pass.
    *
    * @param pass the pass to add the configuration for
    * @param config the configuration to add for `pass`
    * @tparam K the concrete type of `pass`
    */
  def update[K <: IRPass](pass: K)(config: pass.Config): Unit = {
    configuration = configuration + (pass -> config)
  }

  /** Removes the configuration for the specified pass from the list.
    *
    * @param pass the pass to remove configuration for
    * @tparam K the concrete type of `pass`
    * @return the removed configuration for that pass, if it exists
    */
  def remove[K <: IRPass](pass: K): Option[pass.Config] = {
    if (configuration.contains(pass)) {
      val res = configuration.get(pass).map(_.asInstanceOf[pass.Config])
      configuration = configuration.filter(t => t._1 != pass)
      res
    } else {
      None
    }
  }

  /** Gets the configuration for the specified pass.
    *
    * @param pass the pass to get the configuration for
    * @tparam K the concrete type of `pass`
    * @return the configuration for `pass`, if it exists
    */
  def get[K <: IRPass](pass: K): Option[pass.Config] = {
    configuration.get(pass).map(_.asInstanceOf[pass.Config])
  }

  /** Compares to pass configuration stores for equality.
    *
    * @param obj the object to compare against
    * @return `true` if `this == obj`, otherwise `false`
    */
  override def equals(obj: Any): Boolean = obj match {
    case that: PassConfiguration => this.configuration == that.configuration
    case _                       => false
  }
}
// TODO [AA] Organisation for better imports
object PassConfiguration {

  def apply[XS <: HList](
    @unused pairs: XS
  )(
    ): PassConfiguration = {

    new PassConfiguration
  }

  sealed trait ConfigPair[P <: IRPass] {
    val pass: P
    val config: pass.Config

    override def toString: String = s"ConfigPair(pass: $pass, config: $config)"
  }
  object ConfigPair {
    def apply[P <: IRPass](newPass: P)(
      configuration: newPass.Config
    ): ConfigPair[newPass.type] = {
      new ConfigPair[newPass.type] {
        val pass   = newPass
        val config = configuration
      }
    }

    object syntax {
      implicit final class ToPair[P <: IRPass](val pass: P) {
        def -->>(config: pass.Config): ConfigPair[pass.type] = {
          ConfigPair(pass)(config)
        }
      }
    }
  }
}

package org.enso.compiler.pass

import org.enso.compiler.pass.PassConfiguration.ConfigPair

/** Stores configuration for the various compiler passes. */
class PassConfiguration(
  configs: Seq[ConfigPair[_]] = Seq()
) {
  private val pairs: Seq[(IRPass, Any)] =
    configs.map(_.asPair.asInstanceOf[(IRPass, Any)])
  private var configuration: Map[IRPass, Any] = Map(pairs: _*)

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
object PassConfiguration extends PassConfigurationSyntax {

  /** Creates a new pass configuration safely.
    *
    * @param pairs the pairs of (pass, configuration)
    * @return a new [[PassConfiguration]]
    */
  def apply(pairs: ConfigPair[_]*): PassConfiguration = {
    new PassConfiguration(pairs)
  }

  /** A dependent pair type for storing a pass and its configuration type.
    *
    * @tparam P the concrete pass type
    */
  sealed trait ConfigPair[P <: IRPass] {

    /** The pass itself. */
    val pass: P

    /** The configuration instance for [[pass]]. */
    val config: pass.Config

    /** Creates a string representation of the dependent pair.
      *
      * @return a string representation of `this`
      */
    override def toString: String = s"ConfigPair(pass: $pass, config: $config)"

    /** Determines whether two config pairs are equal.
      *
      * @param obj the object to check for equality against `this`
      * @return `true` if `this == obj`, otherwise `false`
      */
    override def equals(obj: Any): Boolean = obj match {
      case that: ConfigPair[_] =>
        (this.pass == that.pass) && (this.config == that.config)
      case _ => false
    }

    /** Converts the dependent pair into a standard pair ([[Tuple2]]).
      *
      * @return `this` as a pair
      */
    def asPair: (pass.type, pass.Config) = (pass, config)
  }
  object ConfigPair {

    /** Constructs a new config pair from a pass and configuration for that
      * pass.
      *
      * @param newPass the pass
      * @param configuration the configuration for `pass`
      * @tparam P the concrete type of `newPass`
      * @return a config pair containing `newPass` and `configuration`
      */
    def apply[P <: IRPass](newPass: P)(
      configuration: newPass.Config
    ): ConfigPair[newPass.type] = {
      new ConfigPair[newPass.type] {
        val pass   = newPass
        val config = configuration
      }
    }
  }
}

/** Useful syntax for working with pass configuration. */
trait PassConfigurationSyntax {

  /** Adds an extension method on passes for concatenating them into pairs with
    * configuration for the pass.
    *
    * @param pass the pass to create a pair with
    * @tparam P the concrete type of `pass`
    */
  implicit final class ToPair[P <: IRPass](val pass: P) {

    /** Concatenates [[pass]] with a configuration object for that pass.
      *
      * @param config the configuration to turn into a pair
      * @return the pair of ([[pass]], configuration)
      */
    def -->>(config: pass.Config): ConfigPair[pass.type] = {
      ConfigPair(pass)(config)
    }
  }
}

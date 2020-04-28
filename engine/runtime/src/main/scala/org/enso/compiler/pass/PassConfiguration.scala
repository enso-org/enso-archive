package org.enso.compiler.pass

import shapeless.=:!=

import scala.annotation.unused
import scala.reflect.ClassTag

/** Stores configuration for the various compiler passes.
 *
 * @param config the initial pass configurations
 */
class PassConfiguration(config: List[IRPass.Configuration] = List()){
  private var configuration: List[IRPass.Configuration] = config

  /** Adds a new configuration entity to the pass configuration.
    *
    * The configuration is added such that later-added configuration will be
    * found first.
    *
    * @param config the configuration to add
    */
  def add(config: IRPass.Configuration): Unit = {
    configuration = config :: configuration
  }

  /** Removes the first entry of type `T` from the list.
    *
    * @param ev ensures that `T` is not inferred
    * @tparam T the type of configuration to remove
    * @return the removed configuration, if it exists
    */
  def remove[T <: IRPass.Configuration: ClassTag](
    implicit @unused ev: T =:!= IRPass.Configuration
  ): Option[T] = {
    configuration.collectFirst { case c: T => c } match {
      case r @ Some(config) =>
        configuration = configuration.filterNot(_ == config)
        r
      case None => None
    }
  }

  /** Replaces the configuration specified by the type `T` with `ev`.
    *
    * @param config the updated configuration to replace `T` with
    * @param ev ensures that `T` is not inferred
    * @tparam T the type of configuration to replace
    */
  def replace[T <: IRPass.Configuration: ClassTag](
    config: IRPass.Configuration
  )(
    implicit @unused ev: T =:!= IRPass.Configuration
  ): Unit = {
    remove[T]: Unit
    add(config)
  }

  /** Gets the most recently added configuration of the specified type.
    *
    * @param ev ensures that the configuration type cannot be inferred
    * @tparam T the type of configuration to get
    * @return the configuration of type `T`, if it exists
    */
  def get[T <: IRPass.Configuration: ClassTag](
    implicit @unused ev: T =:!= IRPass.Configuration
  ): Option[T] = {
    configuration.collectFirst { case c: T => c }
  }

  /** Gets all the configuration of the specified type.
    *
    * @param ev ensures that the configuration type cannot be inferred
    * @tparam T the type of configuration to get
    * @return all configuration of type `T`
    */
  def getAll[T <: IRPass.Configuration: ClassTag](
    implicit @unused ev: T =:!= IRPass.Configuration
  ): List[T] = {
    configuration.collect { case c: T => c }
  }
}

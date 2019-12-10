package org.enso.compiler.core

/** [[Core]] is the sophisticated internal representation supported by the
  * compiler.
  *
  * It is a structure designed to be amenable to program analysis and
  * transformation and features:
  * - High performance on a mutable graph structure.
  * - High levels of type-safety to reduce the incidence of bugs.
  * - Mutable links to represent program structure.
  */
object Core {

  /** This contains the primitive components of [[Core]].
    *
    * The primitive components of [[Core]] are those which have no simpler
    * representation and are hence fundamental building blocks of the Enso
    * language. The idea is that most of the analysis performed on [[Core]]
    * takes place on this [[Primitive]] representation, thereby greatly
    * simplifying the number of constructs with which said analyses will need to
    * contend.
    */
  object Primitive {}

  /** This contains all the components of [[Core]] that can be expressed in
    * terms of [[Core.Primitive]].
    *
    * While some analyses may need to contend with the constructs included
    * herein, most notably alias analysis, most analyses should not. To this
    * end, the desugaring passes should lower constructs from [[Sugar]] to
    * constructs from [[Primitive]] as soon as possible.
    */
  object Sugar {}
}

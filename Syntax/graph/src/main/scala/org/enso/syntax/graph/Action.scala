package org.enso.syntax.graph

import org.enso.data.ADT
import org.enso.syntax.graph.SpanTree.Node
import org.enso.syntax.graph.SpanTree.Pathed

//////////////////
//// Actions ////
/////////////////

/** Base type for all Actions that can be performed on [[SpanTree]] node in
  * the context of node expression.
  */
sealed trait Action
object Action {
  object Insert extends Action
  object Erase  extends Action
  object Set    extends Action
}
object Actions {
  val All: Set[Action]      = ADT.constructors[Action]
  val None: Set[Action]     = Set()
  val Function: Set[Action] = Set(Action.Set)
  val Root: Set[Action]     = Set(Action.Set)
}

/////////////////////
//// WithActions ////
/////////////////////

case class WithActions[+T](elem: T, actions: Set[Action]) {
  def supports(action: Action): Boolean = actions.contains(action)
  def settable: Boolean                 = supports(Action.Set)
  def erasable: Boolean                 = supports(Action.Erase)
  def insertable: Boolean               = supports(Action.Insert)
}
object WithActions {
  implicit def unwrap[T](t: WithActions[T]): T                 = t.elem
  implicit def unwrapWithPath[T](t: WithActions[Pathed[T]]): T = t.elem

  def apply[T](elem: T, action: Action): WithActions[T] =
    WithActions(elem, Set(action))

  def withAll[T](elem: T): WithActions[T] =
    WithActions(elem, Actions.All)

  def withNone[T](elem: T): WithActions[T] =
    WithActions(elem, Actions.None)

  /** Create empty node that supports only [[Set]]. */
  def insertionPoint(position: TextPosition): WithActions[SpanTree] =
    WithActions(Node.Empty(position), Action.Insert)
}

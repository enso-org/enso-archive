package org.enso.languageserver.data.buffer
import java.util

import cats.kernel.Monoid

sealed trait NodeVal[C, M] {
  def foreach(f: C => Unit): Unit
}

trait TreeShape {
  def maxChildren: Int
  def minChildren: Int
}

case class Empty[C, M]() extends NodeVal[C, M] {
  override def foreach(f: C => Unit): Unit = ()
}

case class Internal[C, M](
  children: Array[Node[C, M]]
) extends NodeVal[C, M] {
  override def foreach(f: C => Unit): Unit =
    children.foreach(_.value.foreach(f))

  override def toString: String =
    s"Internal(${util.Arrays.toString(children.asInstanceOf[Array[Object]])})"
}

case class Leaf[C, M](
  elements: C
) extends NodeVal[C, M] {
  override def foreach(f: C => Unit): Unit = f(elements)
}

case class Node[C, M](
  height: Int,
  measure: M,
  value: NodeVal[C, M]
) {
  private def unsafeChildren: Array[Node[C, M]] = value match {
    case Internal(children) => children
    case Leaf(_) =>
      throw new Exception("Node.unsafeChildren called on a Leaf.")
    case Empty() =>
      throw new Exception("Node.unsafeChildren called on an Empty node.")
  }

  private def isEmpty: Boolean = value match {
    case Empty() => true
    case _       => false
  }

  private def canBecomeChild(implicit treeShape: TreeShape): Boolean =
    value match {
      case Leaf(_)            => true
      case Internal(children) => children.length >= treeShape.minChildren
      case Empty()            => false
    }

  def ++(
    that: Node[C, M]
  )(implicit treeShape: TreeShape, measureMonoid: Monoid[M]): Node[C, M] = {

    // get rid of empty arguments as soon as possible
    if (isEmpty) return that
    if (that.isEmpty) return this

    if (height < that.height) {
      // `that` has depth of at least 1 here, so we're guaranteed a non-empty
      // children list.
      val thatChildren = that.unsafeChildren
      if (height == that.height - 1 && canBecomeChild) {
        // `this` would be the same height as any of `thatChildren`, so they
        // form a valid children list together.
        Node.mergeChildren(Array(this), thatChildren)
      } else {
        // Either `this` is inserted lower into the tree, preserving the height
        // of the first child or a new tree was formed, increasing the height
        // by one.
        val newNode           = this ++ thatChildren(0)
        val remainingChildren = thatChildren.drop(1)
        if (newNode.height == that.height - 1) {
          // `newNode` has the same height as any of `thatChildren`, so they
          // can be made children of a new node together.
          Node.mergeChildren(Array(newNode), remainingChildren)
        } else {
          // all children of `newNode` are the same height as children of
          // `that`. That also means `newNode` definitely has children.
          Node.mergeChildren(newNode.unsafeChildren, remainingChildren)
        }
      }
    } else if (height == that.height) {
      if (this.canBecomeChild && that.canBecomeChild) {
        // Both nodes have the same height and both are OK as children.
        Node.unsafeFromChildren(Array(this, that))
      } else {
        // Both nodes are Internal, because otherwise they could become
        // children, so they definitely have children.
        Node.mergeChildren(this.unsafeChildren, that.unsafeChildren)
      }
    } else {
      // This case is exactly symmetric to the first one.
      val thisChildren = unsafeChildren
      if (that.height == height - 1 && that.canBecomeChild) {
        Node.mergeChildren(thisChildren, Array(that))
      } else {
        val lastChildIdx      = thisChildren.length - 1
        val newNode           = thisChildren(lastChildIdx) ++ that
        val remainingChildren = thisChildren.take(lastChildIdx)
        if (newNode.height == this.height - 1) {
          Node.mergeChildren(remainingChildren, Array(newNode))
        } else {
          Node.mergeChildren(remainingChildren, newNode.unsafeChildren)
        }
      }
    }
  }

  def take[I](
    offset: I,
    measureOps: MeasureOps[I, C, M]
  )(
    implicit measureMonoid: Monoid[M],
    measurable: Measurable[C, M],
    treeShape: TreeShape
  ): Node[C, M] = {
    value match {
      case Empty() => this
      case Leaf(c) => Node(measureOps.take(c, offset))

      case Internal(children) =>
//        println("==== take ====")
//        println(this)
//        println("== idx ==")
//        println(offset)
        val (left, mid, _) = findIndexChild(offset, measureOps, children)
//        println("== left ==")
//        println(left)
//        println("== mid ==")
//        println(mid)

        mid match {
          case Some((newOffset, mid)) =>
            val tail = mid.take(newOffset, measureOps)
            Node.mergeTrees(left :+ tail)
          case None => Node.mergeTrees(left)
        }
    }
  }

  def drop[I](
    offset: I,
    measureOps: MeasureOps[I, C, M]
  )(
    implicit measureMonoid: Monoid[M],
    measurable: Measurable[C, M],
    treeShape: TreeShape
  ): Node[C, M] = {
    value match {
      case Empty() => this
      case Leaf(c) => Node(measureOps.drop(c, offset))
      case Internal(children) =>
        val (_, mid, right) = findIndexChild(offset, measureOps, children)
        mid match {
          case Some((newOffset, mid)) =>
            val head = mid.drop(newOffset, measureOps)
            Node.mergeTrees(head :: right)
          case None => Node.mergeTrees(right)
        }
    }
  }

  private def findIndexChild[I](
    ix: I,
    measureOps: MeasureOps[I, C, M],
    childrenArray: Array[Node[C, M]]
  ): (List[Node[C, M]], Option[(I, Node[C, M])], List[Node[C, M]]) = {
    val children  = childrenArray.toList
    var currentIx = ix
    val leftChildren = children.takeWhile { node =>
      // Left children are all the children wholly contained inside offset
      if (!measureOps.isOffsetBeforeEnd(currentIx, node.measure)) {
        currentIx = measureOps.moveAfter(currentIx, node.measure)
        true
      } else false
    }
    val leftoverChildren = children.drop(leftChildren.length)
    leftoverChildren match {
      case Nil => (leftChildren, None, Nil)
      case midChild :: rightChildren =>
        // midChild is only relevant if the offset actually cuts it
        if (measureOps.isOffsetAfterBegin(currentIx, midChild.measure))
          (leftChildren, Some((currentIx, midChild)), rightChildren)
        else (leftChildren, None, leftoverChildren)
    }
  }

  def splitAt[I](
    ix: I,
    measureOps: MeasureOps[I, C, M]
  )(
    implicit measureMonoid: Monoid[M],
    measurable: Measurable[C, M],
    treeShape: TreeShape
  ): (Node[C, M], Node[C, M]) = {
    value match {
      case Empty() => (this, this)
      case Leaf(elements) =>
        val (leftC, rightC) = measureOps.splitAt(elements, ix)
        (Node(leftC), Node(rightC))
      case Internal(children) =>
        val (leftChildren, splitChild, rightChildren) =
          findIndexChild(ix, measureOps, children)
        splitChild match {
          case Some((newIx, child)) =>
            val (leftSplit, rightSplit) = child.splitAt(newIx, measureOps)

            val left  = Node.mergeTrees(leftChildren :+ leftSplit)
            val right = Node.mergeTrees(rightSplit :: rightChildren)
            (left, right)
          case None =>
            (Node.mergeTrees(leftChildren), Node.mergeTrees(rightChildren))
        }
    }
  }
  def get[I](index: I, measureOps: MeasureOps[I, C, M]): measureOps.Elem = {
    value match {
      case Empty()        => throw new ArrayIndexOutOfBoundsException
      case Leaf(elements) => measureOps.get(elements, index)
      case Internal(children) =>
        var currentIdx = index
        children
          .find { node =>
            if (measureOps.contains(currentIdx, node.measure)) true
            else {
              currentIdx = measureOps.moveAfter(currentIdx, node.measure)
              false
            }
          }
          .getOrElse(throw new ArrayIndexOutOfBoundsException)
          .get(currentIdx, measureOps)

    }
  }
}

object Node {
  def apply[C, M](elems: C)(implicit measurable: Measurable[C, M]): Node[C, M] =
    Node(0, measurable.measure(elems), Leaf[C, M](elems))

  def unsafeFromChildren[C, M](
    children: Array[Node[C, M]]
  )(implicit measureMonoid: Monoid[M]): Node[C, M] = {
    val height = children(0).height + 1
    val size = children.foldLeft(measureMonoid.empty)(
      (acc, n) => measureMonoid.combine(acc, n.measure)
    )
    Node(height, size, Internal(children))
  }

  def mergeChildren[C, M](
    leftChildren: Array[Node[C, M]],
    rightChildren: Array[Node[C, M]]
  )(implicit treeShape: TreeShape, measureMonoid: Monoid[M]): Node[C, M] = {
    val allChildren = leftChildren ++ rightChildren
    val nChildren   = allChildren.length
    if (nChildren <= treeShape.maxChildren) {
      unsafeFromChildren(allChildren)
    } else {
      val splitPoint =
        math.min(treeShape.maxChildren, nChildren - treeShape.minChildren)
      val parentNodes = Array(
        Node.unsafeFromChildren(allChildren.take(splitPoint)),
        Node.unsafeFromChildren(allChildren.drop(splitPoint))
      )
      Node.unsafeFromChildren(parentNodes)
    }
  }

  def empty[C, M](implicit measureMonoid: Monoid[M]): Node[C, M] =
    Node(0, measureMonoid.empty, Empty())

  def mergeTrees[C, M](
    nodes: List[Node[C, M]]
  )(implicit treeShape: TreeShape, measureMonoid: Monoid[M]): Node[C, M] =
    nodes.foldLeft(empty[C, M])((tree, node) => tree ++ node)
}

trait MeasureOps[I, C, M] {
  type Elem

  def get(container: C, index: I): Elem

  def splitAt(container: C, index: I): (C, C)

  def isOffsetBeforeEnd(index: I, measure: M): Boolean

  def isOffsetAfterBegin(index: I, measure: M): Boolean

  def contains(index: I, measure: M): Boolean

  def moveAfter(index: I, measure: M): I

  def take(container: C, len: I): C

  def drop(container: C, len: I): C
}

trait Measurable[C, M] {
  def measure(container: C): M
}

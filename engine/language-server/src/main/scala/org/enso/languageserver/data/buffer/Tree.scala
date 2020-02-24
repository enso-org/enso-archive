package org.enso.languageserver.data.buffer

sealed trait NodeVal[Elem] {
  def children: Array[Node[Elem]]
}

trait TreeShape {
  def maxChildren: Int
  def minChildren: Int
}

case class Internal[Elem](children: Array[Node[Elem]]) extends NodeVal[Elem]

case class Leaf[Elem](elem: Elem) extends NodeVal[Elem] {
  override def children: Array[Node[Elem]] =
    throw new Exception("NodeVal.children called on a Leaf.")
}

case class Node[Elem](height: Int, size: Int, value: NodeVal[Elem]) {
  def unsafeChildren: Array[Node[Elem]] = value match {
    case Leaf(_)            => throw new Exception("NodeVal.children called on a Leaf.")
    case Internal(children) => children
  }

  def canBecomeChild(implicit treeShape: TreeShape): Boolean = value match {
    case Leaf(_)            => true
    case Internal(children) => children.length >= treeShape.minChildren
  }

  def ++(that: Node[Elem])(implicit treeShape: TreeShape): Node[Elem] = {
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

}

object Node {
  private[buffer] def unsafeFromChildren[Elem](
    children: Array[Node[Elem]]
  ): Node[Elem] = {
    val height = children(0).height + 1
    val size   = children.foldLeft(0)((acc, n) => acc + n.size)
    Node(height, size, Internal(children))
  }

  def mergeChildren[Elem](
    leftChildren: Array[Node[Elem]],
    rightChildren: Array[Node[Elem]]
  )(implicit treeShape: TreeShape): Node[Elem] = {
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
}

class Tree {}

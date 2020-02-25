package org.enso.languageserver.data.buffer
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

  def splitAt[I](
    ix: I,
    measureOps: MeasureOps[I, C, M]
  )(
    implicit measureMonoid: Monoid[M],
    measurable: Measurable[C, M],
    treeShape: TreeShape
  ): (Node[C, M], Node[C, M]) = {
    value match {
      case Leaf(elements) =>
        val (leftC, rightC) = measureOps.splitAt(elements, ix)
        (Node(leftC), Node(rightC))
      case Internal(childrenArray) =>
        val children  = childrenArray.toList
        var currentIx = ix
        val leftChildren = children.takeWhile { node =>
          if (!measureOps.isInside(currentIx, node.measure)) {
            currentIx = measureOps.moveLeft(currentIx, node.measure)
            true
          } else false
        }
        val leftOverChildren = children.drop(leftChildren.length)
        leftOverChildren match {
          case Nil => (Node.mergeTrees(leftChildren), Node.empty)
          case splitNode :: rightChildren =>
            val (leftSplit, rightSplit) =
              splitNode.splitAt(currentIx, measureOps)
            val left  = Node.mergeTrees(leftChildren :+ leftSplit)
            val right = Node.mergeTrees(rightSplit :: rightChildren)
            (left, right)
        }

    }
  }
}

trait MeasureOps[I, C, M] {
  def splitAt(container: C, index: I): (C, C)
  def isInside(index: I, measure: M):  Boolean
  def moveLeft(index: I, measure: M):  I
}

trait Measurable[C, M] {
  def measure(container: C): M
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

case class StringMeasure(utf16Size: Int, utf32Size: Int)

case object StringMeasure {
  implicit val monoid: Monoid[StringMeasure] = new Monoid[StringMeasure] {
    override def empty: StringMeasure = StringMeasure(0, 0)
    override def combine(x: StringMeasure, y: StringMeasure): StringMeasure =
      StringMeasure(x.utf16Size + y.utf16Size, x.utf32Size + y.utf32Size)
  }
}

case class StringRope(root: Node[String, StringMeasure]) {
  import StringRope._

  def ++(that: StringRope): StringRope = StringRope(this.root ++ that.root)

  override def toString: String = {
    val sb = new StringBuilder(root.measure.utf16Size)
    root.value.foreach(sb.append(_: String))
    sb.toString()
  }

  private def splitWith(
    ix: Int,
    ops: MeasureOps[Int, String, StringMeasure]
  ): (StringRope, StringRope) = {
    val (lNode, rNode) = root.splitAt(ix, ops)
    (StringRope(lNode), StringRope(rNode))
  }

  def splitAtCodePoint(ix: Int): (StringRope, StringRope) =
    splitWith(ix, codePointOps)

//  def splitAt(ix: Int): (StringRope, StringRope ) = splitWith(ix, charOps)
}

object StringHelpers {}

object StringRope {
  implicit val treeShape: TreeShape = new TreeShape {
    override def maxChildren: Int = 8
    override def minChildren: Int = 4
  }

  implicit val measurable: Measurable[String, StringMeasure] =
    (str: String) =>
      StringMeasure(str.length, str.codePointCount(0, str.length))

  val codePointOps: MeasureOps[Int, String, StringMeasure] =
    new MeasureOps[Int, String, StringMeasure] {
      override def splitAt(
        container: String,
        index: Int
      ): (String, String) = {
        val splitPoint = container.offsetByCodePoints(0, index)
        (
          container.substring(0, splitPoint),
          container.substring(splitPoint, container.length)
        )
      }
      override def isInside(
        index: Int,
        measure: StringMeasure
      ): Boolean = index < measure.utf32Size
      override def moveLeft(
        index: Int,
        measure: StringMeasure
      ): Int = index - measure.utf32Size
    }
//  lazy val charOps: MeasureOps[Int, String, StringMeasure]      = ???

  def apply(str: String): StringRope = StringRope(Node(str))
}

//case class Rope(root: Node[Elem])(implicit treeShape: TreeShape) {
//  def ++(that: Rope[Elem]): Rope[Elem] = Rope(this.root ++ that.root)
//
//  def apply(idx: Int): Option[Elem] = root(idx)
//
//  def toArray(implicit ev: ClassTag[Elem]): Array[Elem] = {
//    val builder = new mutable.ArrayBuffer[Elem](root.size)
//    root.value.dumpElements(builder)
//    builder.toArray
//  }
////  override def iterator: Iterator[Elem] = ???
//}
//
//object Rope {
//  implicit val defaultTreeShape: TreeShape = new TreeShape {
//    override def maxChildren: Int = 8
//    override def minChildren: Int = 4
//  }
//
//  def apply[Elem](items: Array[Elem]): Rope[Elem] = Rope(Node(items))
//
//  def apply(items: String): Rope[Char] = Rope(items.toCharArray)
//}

package org.enso.languageserver.data.buffer
import cats.kernel.Monoid

case class StringMeasure(utf16Size: Int, utf32Size: Int)

case object StringMeasure {
  implicit val monoid: Monoid[StringMeasure] = new Monoid[StringMeasure] {
    override def empty: StringMeasure = StringMeasure(0, 0)
    override def combine(x: StringMeasure, y: StringMeasure): StringMeasure =
      StringMeasure(x.utf16Size + y.utf16Size, x.utf32Size + y.utf32Size)
  }
}

object CodePointRopeOps extends MeasureOps[Int, String, StringMeasure] {
  type Elem = Int

  override def contains(
    index: Int,
    measure: StringMeasure
  ): Boolean = index < measure.utf32Size

  override def get(
    container: String,
    index: Int
  ): Int = container.codePointAt(container.offsetByCodePoints(0, index))

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
  override def offsetInside(
    index: Int,
    measure: StringMeasure
  ): Boolean = index > 0 && index < measure.utf32Size
  override def moveAfter(
    index: Int,
    measure: StringMeasure
  ): Int = index - measure.utf32Size
  override def take(
    container: String,
    len: Int
  ): String = {
    val splitPoint = container.offsetByCodePoints(0, len)
    container.substring(0, splitPoint)
  }
  override def drop(
    container: String,
    len: Int
  ): String = {
    val splitPoint = container.offsetByCodePoints(0, len)
    container.substring(splitPoint, container.length)
  }

}

case class CodePointRope(rope: Rope) {
  def splitAt(ix: Int): (Rope, Rope) =
    rope.splitWith(ix, CodePointRopeOps)

  def take(len: Int): Rope = rope.takeWith(len, CodePointRopeOps)

  def drop(len: Int): Rope = rope.dropWith(len, CodePointRopeOps)

  def substring(startIndex: Int, endIndex: Int): Rope =
    take(endIndex).codePoints.drop(startIndex)

  def get(index: Int): Int = rope.root.get(index, CodePointRopeOps)
}

object CharRopeOps extends MeasureOps[Int, String, StringMeasure] {
  type Elem = Char

  override def get(
    container: String,
    index: Int
  ): Char = container(index)

  override def contains(
    index: Int,
    measure: StringMeasure
  ): Boolean = index < measure.utf16Size

  override def splitAt(container: String, index: Int): (String, String) =
    container.splitAt(index)

  override def offsetInside(index: Int, measure: StringMeasure): Boolean =
    index > 0 && index < measure.utf16Size

  override def moveAfter(index: Int, measure: StringMeasure): Int =
    index - measure.utf16Size

  override def take(
    container: String,
    len: Int
  ): String = container.substring(0, len)

  override def drop(
    container: String,
    len: Int
  ): String = container.substring(len, container.length)

}

case class CharRope(rope: Rope) extends CharSequence {
  def splitAt(ix: Int): (Rope, Rope) =
    rope.splitWith(ix, CharRopeOps)

  def take(len: Int): Rope = rope.takeWith(len, CharRopeOps)

  def drop(len: Int): Rope = rope.dropWith(len, CharRopeOps)

  def substring(startIndex: Int, endIndex: Int): Rope =
    take(endIndex).characters.drop(startIndex)

  override def length: Int = rope.root.measure.utf16Size

  override def charAt(index: Int): Char = rope.getWith(index, CharRopeOps)

  override def subSequence(
    start: Int,
    end: Int
  ): CharSequence = CharRope(substring(start, end))
}

case class Rope(root: Node[String, StringMeasure]) {
  import Rope._

  def ++(that: Rope): Rope = Rope(this.root ++ that.root)

  override def toString: String = {
    val sb = new StringBuilder(root.measure.utf16Size)
    root.value.foreach { str => val _ = sb.append(str) }
    sb.toString()
  }

  def splitWith(
    ix: Int,
    ops: MeasureOps[Int, String, StringMeasure]
  ): (Rope, Rope) = {
    val (lNode, rNode) = root.splitAt(ix, ops)
    (Rope(lNode), Rope(rNode))
  }

  def takeWith(
    len: Int,
    ops: MeasureOps[Int, String, StringMeasure]
  ): Rope = Rope(root.take(len, ops))

  def dropWith(
    len: Int,
    ops: MeasureOps[Int, String, StringMeasure]
  ): Rope = Rope(root.drop(len, ops))

  def getWith(
    index: Int,
    ops: MeasureOps[Int, String, StringMeasure]
  ): ops.Elem = root.get(index, ops)

  def codePoints: CodePointRope = CodePointRope(this)

  def characters: CharRope = CharRope(this)
}

object Rope {
  implicit val treeShape: TreeShape = new TreeShape {
    override def maxChildren: Int = 8
    override def minChildren: Int = 4
  }

  implicit val measurable: Measurable[String, StringMeasure] =
    (str: String) =>
      StringMeasure(str.length, str.codePointCount(0, str.length))

  def apply(str: String): Rope = Rope(Node(str))
}

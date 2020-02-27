package org.enso.languageserver.data.buffer
import cats.kernel.Monoid

case class StringMeasure(
  utf16Size: Int,
  utf32Size: Int,
  fullLines: Int,
  endsInNewLine: Boolean
)

case object StringMeasure {
  implicit val monoid: Monoid[StringMeasure] = new Monoid[StringMeasure] {
    override def empty: StringMeasure = StringMeasure(0, 0, 0, false)
    override def combine(x: StringMeasure, y: StringMeasure): StringMeasure =
      StringMeasure(
        x.utf16Size + y.utf16Size,
        x.utf32Size + y.utf32Size,
        x.fullLines + y.fullLines,
        y.endsInNewLine
      )
  }
}

object CodePointRopeOps
    extends RangeOps[Int, String, StringMeasure]
    with ElemOps[Int, String, StringMeasure] {
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
  override def isOffsetBeforeEnd(
    index: Int,
    measure: StringMeasure
  ): Boolean = index < measure.utf32Size

  override def isOffsetAfterBegin(
    index: Int,
    measure: StringMeasure
  ): Boolean = index > 0

  override def shiftLeft(
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

  def at(index: Int): Int = rope.root.get(index, CodePointRopeOps)
}

object CharRopeOps
    extends RangeOps[Int, String, StringMeasure]
    with ElemOps[Int, String, StringMeasure] {
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

  override def isOffsetBeforeEnd(index: Int, measure: StringMeasure): Boolean =
    index < measure.utf16Size

  override def isOffsetAfterBegin(
    index: Int,
    measure: StringMeasure
  ): Boolean = index > 0

  override def shiftLeft(index: Int, measure: StringMeasure): Int =
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

case class LineRope(rope: Rope) {
  def take(len: Int): Rope = rope.takeWith(len, LineRopeOps)

  def drop(len: Int): Rope = rope.dropWith(len, LineRopeOps)

  def splitAt(offset: Int): (Rope, Rope) = rope.splitWith(offset, LineRopeOps)
}

object LineRopeOps extends RangeOps[Int, String, StringMeasure] {
  override def splitAt(
    container: String,
    index: Int
  ): (String, String) = if (index == 0) ("", container) else (container, "")

  override def isOffsetBeforeEnd(
    index: Int,
    measure: StringMeasure
  ): Boolean =
    (index < measure.fullLines || (index == measure.fullLines && !measure.endsInNewLine))

  override def isOffsetAfterBegin(
    index: Int,
    measure: StringMeasure
  ): Boolean = index > 0

  override def shiftLeft(
    index: Int,
    measure: StringMeasure
  ): Int = index - measure.fullLines

  override def take(
    container: String,
    len: Int
  ): String = if (len == 0) "" else container

  override def drop(
    container: String,
    len: Int
  ): String = if (len == 0) container else ""
}

case class Rope(root: Node[String, StringMeasure]) {
  import Rope._

  def ++(that: Rope): Rope = Rope(this.root ++ that.root)

  override def toString: String = {
    val sb = new StringBuilder(root.measure.utf16Size)
    root.value.foreach { str =>
      val _ = sb.append(str)
    }
    sb.toString()
  }

  def splitWith(
    ix: Int,
    ops: RangeOps[Int, String, StringMeasure]
  ): (Rope, Rope) = {
    val (lNode, rNode) = root.splitAt(ix, ops)
    (Rope(lNode), Rope(rNode))
  }

  def takeWith(
    len: Int,
    ops: RangeOps[Int, String, StringMeasure]
  ): Rope = Rope(root.take(len, ops))

  def dropWith(
    len: Int,
    ops: RangeOps[Int, String, StringMeasure]
  ): Rope = Rope(root.drop(len, ops))

  def getWith(
    index: Int,
    elemOps: ElemOps[Int, String, StringMeasure]
  ): elemOps.Elem = root.get(index, elemOps)

  def codePoints: CodePointRope = CodePointRope(this)

  def characters: CharRope = CharRope(this)

  def lines: LineRope = LineRope(this)
}

object Rope {
  implicit val treeShape: TreeShape = new TreeShape {
    override def maxChildren: Int = 8
    override def minChildren: Int = 4
  }

  implicit val measurable: Measurable[String, StringMeasure] =
    (str: String) =>
      StringMeasure(str.length, str.codePointCount(0, str.length), 0, false)

  def apply(str: String): Rope = {
    val (fullLines, mayLastLine) = StringUtils.getLines(str)
    val fullNodes = fullLines.map(
      line =>
        Node(
          0,
          StringMeasure(
            line.length,
            line.codePointCount(0, line.length),
            1,
            true
          ),
          Leaf[String, StringMeasure](line)
        )
    )
    val maybeLastNode = mayLastLine.map(
      line =>
        Node(
          0,
          StringMeasure(
            line.length,
            line.codePointCount(0, line.length),
            0,
            false
          ),
          Leaf[String, StringMeasure](line)
        )
    )
    Rope(Node.mergeTrees(fullNodes ++ maybeLastNode))
  }

  def empty: Rope = Rope(Node.empty[String, StringMeasure])
}

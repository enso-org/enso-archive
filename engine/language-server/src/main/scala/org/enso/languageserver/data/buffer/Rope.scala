package org.enso.languageserver.data.buffer
import cats.kernel.Monoid

/**
  * The measure used for storing strings in the b-tree.
  * @param utf16Size number of characters.
  * @param utf32Size number of code points.
  * @param fullLines number of lines terminated with a new line character.
  * @param endsInNewLine whether this range ends with a new line character.
  */
case class StringMeasure(
  utf16Size: Int,
  utf32Size: Int,
  fullLines: Int,
  endsInNewLine: Boolean
) {

  /**
    * Number of lines measured, including the possibly non-terminated last line.
    * @return the number of lines.
    */
  def linesCount: Int = fullLines + (if (endsInNewLine) 0 else 1)
}

object StringMeasure {
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

object CodePointView {
  object Ops
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
}

/**
  * Exposes a code points based view over rope indexing operations.
  * @param rope the underlying rope.
  */
case class CodePointView(rope: Rope) {

  /**
    * Splits the rope into two parts at a given offset.
    *
    * @param offset the number of code points in the first part.
    * @return a sub-rope containing `offset` code points and a sub-rope
    *         containing all the remaining code points.
    */
  def splitAt(offset: Int): (Rope, Rope) =
    rope.splitWith(offset, CodePointView.Ops)

  /**
    * Takes a prefix of the rope of the given length.
    *
    * @param len the length of the prefix to take.
    * @return a prefix of `this` containing `len` code points.
    */
  def take(len: Int): Rope = rope.takeWith(len, CodePointView.Ops)

  /**
    * Takes a suffix of the rope by removing the first `len` code points.
    *
    * @param len the number of code points to remove.
    * @return the suffix consisting of the remaining code points.
    */
  def drop(len: Int): Rope = rope.dropWith(len, CodePointView.Ops)

  /**
    * Takes a substring of the rope, with the first code point at `startOffset`
    * and the last at `endOffset` - 1.
    *
    * @param startOffset the offset of the first code point.
    * @param endOffset the offset after the last code point.
    * @return a substring of this rope.
    */
  def substring(startOffset: Int, endOffset: Int): Rope =
    take(endOffset).codePoints.drop(startOffset)

  /**
    * Gets the code point at a specified position.
    *
    * @param index the index of the code point to get.
    * @return the code point at `index`
    */
  def at(index: Int): Int = rope.root.get(index, CodePointView.Ops)

  /**
    * Gets the number of code points in this rope.
    *
    * @return the length of this rope.
    */
  def length: Int = rope.measure.utf32Size
}

object CharView {
  object Ops
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

    override def isOffsetBeforeEnd(
      index: Int,
      measure: StringMeasure
    ): Boolean =
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
}

/**
  * Exposes a character-based API for rope operations.
  * Also exposes a [[CharSequence]] interface, for no-copy use with Java APIs.
  *
  * @param rope the underlying rope.
  */
case class CharView(rope: Rope) extends CharSequence {

  /**
    * Splits the rope into two parts at a given offset.
    *
    * @param offset the number of characters in the first part.
    * @return a sub-rope containing `offset` characters and a sub-rope
    *         containing all the remaining characters.
    */
  def splitAt(offset: Int): (Rope, Rope) =
    rope.splitWith(offset, CharView.Ops)

  /**
    * Takes a prefix of the rope of the given length.
    *
    * @param len the length of the prefix to take.
    * @return a prefix of `this` containing `len` characters.
    */
  def take(len: Int): Rope = rope.takeWith(len, CharView.Ops)

  /**
    * Takes a suffix of the rope by removing the first `len` characters.
    *
    * @param len the number of characters to remove.
    * @return the suffix consisting of the remaining characters.
    */
  def drop(len: Int): Rope = rope.dropWith(len, CharView.Ops)

  /**
    * Takes a substring of the rope, with the first character at `startOffset`
    * and the last at `endOffset` - 1.
    *
    * @param startOffset the offset of the first character.
    * @param endOffset the offset after the last character.
    * @return a substring of this rope.
    */
  def substring(startOffset: Int, endOffset: Int): Rope =
    take(startOffset).characters.drop(endOffset)

  /**
    * Gets the number of characters in this rope.
    *
    * @return the length of this rope.
    */
  override def length: Int = rope.root.measure.utf16Size

  /**
    * Gets the character at a specified position.
    *
    * @param index the index of the character to get.
    * @return the character at `index`
    */
  override def charAt(index: Int): Char = rope.getWith(index, CharView.Ops)

  /**
    * Takes a substring of the rope, with the first character at `start`
    * and the last at `end` - 1.
    *
    * @param start the offset of the first character.
    * @param end the offset after the last character.
    * @return a substring of this rope.
    */
  override def subSequence(
    start: Int,
    end: Int
  ): CharSequence = CharView(substring(start, end))
}

/**
  * Exposes a line-based API for the rope.
  * @param rope the underlying rope.
  */
case class LineRope(rope: Rope) {

  /**
    * Takes a prefix of the rope of the given length.
    *
    * @param len the length of the prefix to take.
    * @return a prefix of `this` containing `len` lines.
    */
  def take(len: Int): Rope = rope.takeWith(len, LineView.Ops)

  /**
    * Takes a suffix of the rope by removing the first `len` lines.
    *
    * @param len the number of lines to remove.
    * @return the suffix consisting of the remaining lines.
    */
  def drop(len: Int): Rope = rope.dropWith(len, LineView.Ops)

  /**
    * Splits the rope into two parts at a given offset.
    *
    * @param offset the number of lines in the first part.
    * @return a sub-rope containing `offset` lines and a sub-rope
    *         containing all the remaining lines.
    */
  def splitAt(offset: Int): (Rope, Rope) = rope.splitWith(offset, LineView.Ops)

  /**
    * Gets the number of lines in this rope.
    *
    * @return the length of this rope.
    */
  def length: Int = rope.measure.linesCount
}

object LineView {
  object Ops extends RangeOps[Int, String, StringMeasure] {
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
}

/**
  * Represents a string using a tree.
  *
  * Provides fast indexing operations by lines, characters and code points,
  * as well as fast concatenation.
  *
  * The elements are stored in a [[Node]] hierarchy, with leaves storing
  * Strings, with an additional invariant that new line characters are only
  * allowed at the end of leaves.
  *
  * @param root the underlying tree's root.
  */
case class Rope(root: Node[String, StringMeasure]) {
  import Rope._

  /**
    * Concates another rope at the end of this one.
    *
    * @param that the rope to add at the end.
    * @return the result of concatenating the two ropes.
    */
  def ++(that: Rope): Rope = Rope(this.root ++ that.root)

  /**
    * Converts this rope to a String.
    *
    * @return a String with this rope's contents
    */
  override def toString: String = {
    val sb = new StringBuilder(root.measure.utf16Size)
    root.value.foreach { str =>
      val _ = sb.append(str)
    }
    sb.toString()
  }

  private[buffer] def splitWith(
    ix: Int,
    ops: RangeOps[Int, String, StringMeasure]
  ): (Rope, Rope) = {
    val (lNode, rNode) = root.splitAt(ix, ops)
    (Rope(lNode), Rope(rNode))
  }

  private[buffer] def takeWith(
    len: Int,
    ops: RangeOps[Int, String, StringMeasure]
  ): Rope = Rope(root.take(len, ops))

  private[buffer] def dropWith(
    len: Int,
    ops: RangeOps[Int, String, StringMeasure]
  ): Rope = Rope(root.drop(len, ops))

  private[buffer] def getWith(
    index: Int,
    elemOps: ElemOps[Int, String, StringMeasure]
  ): elemOps.Elem = root.get(index, elemOps)

  /**
    * Returns a code points based view of this rope.
    *
    * @return a code points view for this rope.
    */
  def codePoints: CodePointView = CodePointView(this)

  /**
    *  Returns a characters view of this rope.
    *
    * @return a characters view for this rope.
    */
  def characters: CharView = CharView(this)

  /**
    * Returns a lines view of this rope.
    *
    * @return a lines view of this rope.
    */
  def lines: LineRope = LineRope(this)

  private[buffer] def measure: StringMeasure = root.measure
}

object Rope {
  implicit val treeShape: TreeShape = new TreeShape {
    override def maxChildren: Int = 8
    override def minChildren: Int = 4
  }

  implicit val measurable: Measurable[String, StringMeasure] =
    (str: String) => {
      val endsWithNewLine = StringUtils.endsInNewline(str)
      StringMeasure(
        str.length,
        str.codePointCount(0, str.length),
        if (endsWithNewLine) 1 else 0,
        endsWithNewLine
      )
    }

  /**
    * Creates a new Rope from a given string.
    *
    * @param str a string to convert into a rope.
    * @return a rope with the same contents as `str`
    */
  def apply(str: String): Rope = {
    val lines = StringUtils.getLines(str)
    val nodes = lines.map(Node(_))
    Rope(Node.mergeTrees(nodes))
  }

  /**
    * Creates an empty rope.
    *
    * @return an empty rope.
    */
  def empty: Rope = Rope(Node.empty[String, StringMeasure])
}

package org.enso.syntax.graph

import org.enso.data.Shifted
import org.enso.syntax.text.AST
import org.enso.syntax.text.ast.meta.Pattern

import scala.annotation.tailrec

////////////////////
//// TextLength ////
////////////////////

case class TextLength(value: Int) extends Ordered[TextLength] {
  def +(that: TextLength): TextLength = TextLength(value + that.value)
  def -(that: TextLength): TextLength = TextLength(value - that.value)
  def compare(rhs: TextLength): Int   = value compare rhs.value
}
object TextLength {
  val Empty                                 = TextLength(0)
  def apply(pat: Pattern.Match): TextLength = TextLength(pat.toStream)
  def apply(ast: AST): TextLength           = TextLength(ast.span)
  def apply(text: String): TextLength       = TextLength(text.length)
  def apply(sast: Shifted[AST]): TextLength =
    TextLength(sast.off) + TextLength(sast.el) // FIXME: SAST.span
  def apply[A](elems: Seq[Shifted[AST]]): TextLength = {
    var ret = TextLength(0)
    elems.foreach(ret += TextLength(_))
    ret
  }
}

//////////////////////
//// TextPosition ////
//////////////////////

/** Strongly typed index position in a text. */
case class TextPosition(index: Int) extends Ordered[TextPosition] {
  def +(offset: Int): TextPosition          = TextPosition(index + offset)
  def +(offset: TextLength): TextPosition   = TextPosition(index + offset.value)
  def -(offset: TextLength): TextPosition   = TextPosition(index - offset.value)
  def -(offset: TextPosition): TextPosition = TextPosition(index - offset.index)

  /** Span between two text positions. Operands order is irrelevant. */
  def <->(that: TextPosition): TextSpan = TextSpan(this, that)

  def compare(rhs: TextPosition): Int = index compare rhs.index
}
object TextPosition {
  val Start = TextPosition(0)
}

//////////////////
//// TextSpan ////
//////////////////

case class TextSpan(begin: TextPosition, length: TextLength)
    extends Ordered[TextSpan] {

  /** Index of the first character past the span */
  def end: TextPosition = begin + length

  override def compare(that: TextSpan): Int =
    (begin -> length).compare(that.begin -> that.length)

  /** Erases span which is ( before | overlapping | behind ) the current span */
  def -(t: TextSpan): TextSpan = () match {
    case () if end < t.begin    => this
    case () if begin > t.end    => TextSpan(begin - t.length, length)
    case () if begin >= t.begin => TextSpan(t.begin, end - t.end)
    case () if end >= t.end     => TextSpan(begin, length - t.length)
    case ()                     => TextSpan(begin, t.begin + length - end)
  }
  /** Inserts span ( before | into | after ) the current text span */
  def +(t: TextSpan): TextSpan = () match {
    case () if end < t.begin   => this
    case () if begin > t.begin => TextSpan(begin + t.length, length)
    case ()                    => TextSpan(begin, length + t.length)
  }
}
object TextSpan {

  @tailrec
  def apply(pos1: TextPosition, pos2: TextPosition): TextSpan =
    if (pos1 > pos2) TextSpan(pos2, pos1)
    else TextSpan(pos1, TextLength(pos2.index - pos1.index))

  def apply(pos: TextPosition, ast: AST): TextSpan =
    TextSpan(pos, TextLength(ast.span))

  def apply(offset: Int, length: Int): TextSpan =
    TextSpan(TextPosition(offset), TextLength(length))

  def apply(text: String): TextSpan =
    TextSpan(TextPosition.Start, TextLength(text))

  def Empty(pos: TextPosition): TextSpan = TextSpan(pos, TextLength.Empty)

  implicit class StringOps(text: String) {
    def substring(span: TextSpan): String =
      text.substring(span.begin.index, span.end.index)
  }
}

////////////////////
//// Positioned ////
////////////////////
case class Positioned[+T](elem: T, position: TextPosition)
object Positioned {
  implicit def unwrap[T](t: Positioned[T]): T = t.elem
}

////////////////////
//// Spanned ////
////////////////////
case class Spanned[+T](elem: T, span: TextSpan)
object Spanned {
  implicit def unwrap[T](t: Positioned[T]): T = t.elem
}

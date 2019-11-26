package org.enso.syntax.text2

import java.util.UUID

import cats.Foldable
import cats.Functor
import cats.derived._
import cats.implicits._
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.AutoDerivation
import io.circe.generic.auto._
import org.enso.data.List1._
import org.enso.data.List1
import org.enso.data.Pool
import org.enso.data2.Index
import org.enso.data.Shifted
import org.enso.data2.Size
import org.enso.data2.Span
import org.enso.data.Tree
import org.enso.lint.Unused
//import org.enso.syntax.text.AST
//import org.enso.syntax.text.AST.OffsetZip
import org.enso.syntax.text2.ast.Repr.R
import org.enso.syntax.text2.ast.Repr._
//import org.enso.syntax.text.ast.Doc
import org.enso.syntax.text2.ast.Repr
import org.enso.syntax.text.ast.opr
import org.enso.syntax.text2.AST.Ident.Cons.pool

import scala.annotation.tailrec
import scala.reflect.ClassTag

//////////////////////////////////////////////////////////////////////////////
//// HasSpan /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

trait HasSpan[T] {
  def span(t: T): Int
}
object HasSpan {
  def apply[T: HasSpan] = implicitly[HasSpan[T]]

  object implicits {
    implicit class ToHasSpanOps[T: HasSpan](t: T) {
      def span(): Int = {
        implicitly[HasSpan[T]].span(t)
      }
    }
  }

  implicit def fromShifted[T: HasSpan]: HasSpan[Shifted[T]] = { shifted =>
    val ev = implicitly[HasSpan[T]]
    shifted.off + ev.span(shifted.el)
  }

  // FIXME consider if this is any good, if `span` collides with List method
  implicit def fromStream[T: HasSpan]: HasSpan[AST.StreamOf[T]] = { stream =>
    val ev: HasSpan[Shifted[T]] = fromShifted
    stream.foldLeft(0)((acc, sast) => acc + ev.span(sast))
  }
}

//////////////////////////////////////////////////////////////////////////////
//// OffsetZip ///////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

/** Zips every child [[A]] with offset from the left side of the parent
  * node. The offset is a number of UTF8 characters (code points).
  */
trait OffsetZip[F[A], A] {
  def zipWithOffset(t: F[A]): F[(Index, A)]
}
object OffsetZip {
  def apply[F[A], A](implicit ev: OffsetZip[F, A]): OffsetZip[F, A] = ev
  def apply[F[A], A](t: F[A])(implicit ev: OffsetZip[F, A]): F[(Index, A)] =
    OffsetZip[F, A].zipWithOffset(t)

  //// Default Instances ////
  implicit def fromStream[T: HasSpan]: OffsetZip[AST.StreamOf, T] = { stream =>
    val ev  = implicitly[HasSpan[T]]
    var off = Index.Start
    stream.map { t =>
      off += Size(t.off)
      val out = t.map((off, _))
      off += Size(ev.span(t.el))
      out
    }
  }
}

//////////////////////////////////////////////////////////////////////////////
//// Phantom /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

/** Phantom type. Use with care, as Scala cannot prove its proper usage. When
  * a type is phantom, then its last type argument is not used and we can
  * safely coerce it to something else.
  */
sealed trait Phantom
object Phantom {
  implicit class PhantomOps[T[_] <: Phantom](ident: T[_]) {
    def coerce[S]: T[S] = ident.asInstanceOf[T[S]]
  }
}

//////////////////////////////////////////////////////////////////////////////
//// Shape ///////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

sealed trait Shape[T]

/** Implicits for [[Shape]], will overwrite inherited ones from
  * [[ShapeImplicit2]]
  */
sealed trait ShapeImplicit {
  import Shape._

  implicit def ftor: Functor[Shape]    = semi.functor
  implicit def fold: Foldable[Shape]   = semi.foldable
  implicit def repr[T]: Repr[Shape[T]] = ??? // TODO do big match
  implicit def ozip[T: HasSpan]: OffsetZip[Shape, T] = {
    case s: Unrecognized[T] => OffsetZip[Unrecognized, T].zipWithOffset(s)
    case s: Unexpected[T]   => OffsetZip[Unexpected, T].zipWithOffset(s)
    case s: Blank[T]        => OffsetZip[Blank, T].zipWithOffset(s)
    case s: Var[T]          => OffsetZip[Var, T].zipWithOffset(s)
    case s: Cons[T]         => OffsetZip[Cons, T].zipWithOffset(s)
    case s: Opr[T]          => OffsetZip[Opr, T].zipWithOffset(s)
    case s: Mod[T]          => OffsetZip[Mod, T].zipWithOffset(s)
    case s: InvalidSuffix[T] =>
      OffsetZip[InvalidSuffix, T].zipWithOffset(s)
    case s: Number[T]       => OffsetZip[Number, T].zipWithOffset(s)
    case s: DanglingBase[T] => OffsetZip[DanglingBase, T].zipWithOffset(s)
    case s: TextUnclosed[T] => OffsetZip[TextUnclosed, T].zipWithOffset(s)
    case s: InvalidQuote[T] => OffsetZip[InvalidQuote, T].zipWithOffset(s)
    case s: InlineBlock[T]  => OffsetZip[InlineBlock, T].zipWithOffset(s)
    case s: LineRaw[T]      => OffsetZip[LineRaw, T].zipWithOffset(s)
    case s: LineFmt[T]      => OffsetZip[LineFmt, T].zipWithOffset(s)
    case s: BlockRaw[T]     => OffsetZip[BlockRaw, T].zipWithOffset(s) // TODO
    case s: BlockFmt[T]     => OffsetZip[BlockFmt, T].zipWithOffset(s) // TODO
//    case s: SegmentPlain[T]  => OffsetZip[InlineBlock, T].zipWithOffset(s)
//    case s: SegmentExpr[T]   => OffsetZip[LineRaw, T].zipWithOffset(s)
//    case s: SegmentEscape[T] => OffsetZip[LineFmt, T].zipWithOffset(s)
    case s: App[T]          => OffsetZip[App, T].zipWithOffset(s)
    case s: Infix[T]        => OffsetZip[Infix, T].zipWithOffset(s)
    case s: SectionLeft[T]  => OffsetZip[SectionLeft, T].zipWithOffset(s)
    case s: SectionRight[T] => OffsetZip[SectionRight, T].zipWithOffset(s)
    case s: SectionSides[T] => OffsetZip[SectionSides, T].zipWithOffset(s)

  }
  implicit def span[T: HasSpan]: HasSpan[Shape[T]] = ???
}

object Shape extends ShapeImplicit {
  import HasSpan.implicits._
  import AST.StreamOf

  //////////////////
  //// Variants ////
  //////////////////
  sealed trait InvalidOf[T] extends Shape[T]
  final case class Unrecognized[T](str: String)
      extends InvalidOf[T]
      with Phantom
  final case class Unexpected[T](msg: String, stream: StreamOf[T])
      extends InvalidOf[T]

  /// Identifiers ///
  sealed trait IdentOf[T] extends Shape[T] with Phantom {
    val name: String
  }
  final case class Blank[T]()            extends IdentOf[T] { val name = "_" }
  final case class Var[T](name: String)  extends IdentOf[T]
  final case class Cons[T](name: String) extends IdentOf[T]
  final case class Mod[T](name: String)  extends IdentOf[T]
  final case class Opr[T](name: String) extends IdentOf[T] {
    val (prec, assoc) = opr.Info.of(name)
  }
  final case class InvalidSuffix[T](elem: AST.Ident, suffix: String) // TODO: why `elem` doesn't depend on `T`
      extends InvalidOf[T]
      with Phantom

  /// Literals ///
  sealed trait LiteralOf[T] extends Shape[T]

  final case class Number[T](base: Option[String], int: String)
      extends LiteralOf[T]
      with Phantom
  final case class DanglingBase[T](base: String)
      extends InvalidOf[T]
      with Phantom

  sealed trait Text[T] extends Shape[T] with LiteralOf[T] {
    def quote: Repr.Builder
  }
  final case class TextUnclosed[T](line: Line[T])
      extends Text[T]
      with InvalidOf[T] {
    def quote = line.quote
  }
  final case class InvalidQuote[T](quote: Builder)
      extends InvalidOf[T]
      with Phantom

  final case class InlineBlock[T](quote: Builder)
      extends InvalidOf[T]
      with Phantom

  sealed trait Line[T] extends Text[T]
  final case class LineRaw[T](text: List[SegmentRaw[T]])
      extends Line[T]
      with Phantom {
    val quote = '"'
  }
  /* Note [Circe and naming] */
  final case class LineFmt[T](text: List[SegmentFmt[T]]) extends Line[T] {
    val quote = '\''
  }

  sealed trait Block[T] extends Text[T]
  final case class BlockLine[+T](emptyLines: List[Int], text: List[T])

  final case class BlockRaw[T](
    text: List[BlockLine[SegmentRaw[T]]],
    spaces: Int,
    offset: Int
  ) extends Block[T]
      with Phantom {
    val quote = "\"\"\""
  }
  final case class BlockFmt[T](
    text: List[BlockLine[SegmentFmt[T]]],
    spaces: Int,
    offset: Int
  ) extends Block[T] {
    val quote = "'''"
  }

  // TODO shorten
  type Escape = org.enso.syntax.text.ast.text.Escape
  val Escape = org.enso.syntax.text.ast.text.Escape

  sealed trait Segment[T]
  sealed trait SegmentFmt[T]                        extends Segment[T]
  sealed trait SegmentRaw[T]                        extends SegmentFmt[T] with Phantom
  final case class SegmentPlain[T](value: String)   extends SegmentRaw[T]
  final case class SegmentExpr[T](value: Option[T]) extends SegmentFmt[T]
  final case class SegmentEscape[T](code: Escape)
      extends SegmentFmt[T]
      with Phantom

  sealed trait AppOf[T]                            extends Shape[T]
  final case class App[T](fn: T, off: Int, arg: T) extends AppOf[T]
  final case class Infix[T](
    larg: T,
    loff: Int,
    opr: AST.Opr, // FIXME: likely should be a different type, same for other apps
    roff: Int,
    rarg: T
  ) extends AppOf[T]

  sealed trait Section[T] extends AppOf[T]
  final case class SectionLeft[T](arg: T, off: Int, opr: AST.Opr)
      extends Section[T]
  final case class SectionRight[T](opr: AST.Opr, off: Int, arg: T)
      extends Section[T]
  final case class SectionSides[T](opr: AST.Opr) extends Section[T] with Phantom

  ////Companions ///
  // TODO: All companion objects can be generated with macros

  object Unrecognized {
    implicit def ftor: Functor[Unrecognized]         = semi.functor
    implicit def fold: Foldable[Unrecognized]        = semi.foldable
    implicit def repr[T]: Repr[Unrecognized[T]]      = _.str
    implicit def ozip[T]: OffsetZip[Unrecognized, T] = t => t.coerce
    implicit def span[T]: HasSpan[Unrecognized[T]]   = _.str.length
  }

  object Unexpected {
    implicit def ftor: Functor[Unexpected]          = semi.functor
    implicit def fold: Foldable[Unexpected]         = semi.foldable
    implicit def repr[T: Repr]: Repr[Unexpected[T]] = t => Repr(t.stream)
    implicit def ozip[T: HasSpan]: OffsetZip[Unexpected, T] =
      t => t.copy(stream = OffsetZip(t.stream))
    implicit def span[T: HasSpan]: HasSpan[Unexpected[T]] =
      t => HasSpan.fromStream[T].span(t.stream)
  }
  object IdentOf {
    implicit def ftor: Functor[IdentOf]    = semi.functor
    implicit def fold: Foldable[IdentOf]   = semi.foldable
    implicit def repr[T]: Repr[IdentOf[T]] = _.name
    implicit def ozip[T: HasSpan]: OffsetZip[IdentOf, T] = { ident =>
      OffsetZip[Shape, T](ident).asInstanceOf
    }
    implicit def span[T: HasSpan]: HasSpan[IdentOf[T]] =
      t => (t: Shape[T]).span // TODO doesn't this break span caching?
  }
  object Blank {
    implicit def ftor: Functor[Blank]         = semi.functor
    implicit def fold: Foldable[Blank]        = semi.foldable
    implicit def repr[T]: Repr[Blank[T]]      = _.name
    implicit def ozip[T]: OffsetZip[Blank, T] = t => t.coerce
    implicit def span[T]: HasSpan[Blank[T]]   = _ => 1
  }
  object Var {
    implicit def ftor: Functor[Var]         = semi.functor
    implicit def fold: Foldable[Var]        = semi.foldable
    implicit def repr[T]: Repr[Var[T]]      = _.name
    implicit def ozip[T]: OffsetZip[Var, T] = t => t.coerce
    implicit def span[T]: HasSpan[Var[T]]   = t => t.name.length
  }
  object Cons {
    implicit def ftor: Functor[Cons]         = semi.functor
    implicit def fold: Foldable[Cons]        = semi.foldable
    implicit def repr[T]: Repr[Cons[T]]      = _.name
    implicit def ozip[T]: OffsetZip[Cons, T] = t => t.coerce
    implicit def span[T]: HasSpan[Cons[T]]   = t => t.name.length
  }
  object Mod {
    implicit def ftor: Functor[Mod]         = semi.functor
    implicit def fold: Foldable[Mod]        = semi.foldable
    implicit def repr[T]: Repr[Mod[T]]      = R + _.name + "="
    implicit def ozip[T]: OffsetZip[Mod, T] = t => t.coerce
    implicit def span[T]: HasSpan[Mod[T]]   = t => t.name.length
  }
  object Opr {
    implicit def ftor: Functor[Opr]         = semi.functor
    implicit def fold: Foldable[Opr]        = semi.foldable
    implicit def repr[T]: Repr[Opr[T]]      = _.name
    implicit def ozip[T]: OffsetZip[Opr, T] = t => t.coerce
    implicit def span[T]: HasSpan[Opr[T]]   = t => t.name.length
  }
  object InvalidSuffix {
    implicit def ftor: Functor[InvalidSuffix]         = semi.functor
    implicit def fold: Foldable[InvalidSuffix]        = semi.foldable
    implicit def ozip[T]: OffsetZip[InvalidSuffix, T] = t => t.coerce
    implicit def repr[T]: Repr[InvalidSuffix[T]] =
      t => R + t.elem.shape.repr + t.suffix
    implicit def span[T]: HasSpan[InvalidSuffix[T]] =
      t => t.elem.span + t.suffix.length
  }
  object LiteralOf {
    implicit def ftor: Functor[LiteralOf]    = semi.functor
    implicit def fold: Foldable[LiteralOf]   = semi.foldable
    implicit def repr[T]: Repr[LiteralOf[T]] = t => (t: Shape[T]).repr
    implicit def ozip[T: HasSpan]: OffsetZip[LiteralOf, T] = { t =>
      OffsetZip[Shape, T](t).asInstanceOf
    }
    implicit def span[T: HasSpan]: HasSpan[LiteralOf[T]] =
      t => (t: Shape[T]).span
  }
  object Number {
    implicit def fromInt[T](int: Int): AST.Number = AST.Number(int)
    implicit def ftor: Functor[Number]            = semi.functor
    implicit def fold: Foldable[Number]           = semi.foldable
    implicit def ozip[T]: OffsetZip[Number, T]    = t => t.coerce
    implicit def repr[T]: Repr[Number[T]] =
      t => t.base.map(_ + "_").getOrElse("") + t.int
    implicit def span[T]: HasSpan[Number[T]] =
      t => t.base.map(_.length + 1).getOrElse(0) + t.int.length
  }
  object DanglingBase {
    implicit def ftor: Functor[DanglingBase]         = semi.functor
    implicit def fold: Foldable[DanglingBase]        = semi.foldable
    implicit def repr[T]: Repr[DanglingBase[T]]      = R + _.base + '_'
    implicit def ozip[T]: OffsetZip[DanglingBase, T] = t => t.coerce
    implicit def span[T]: HasSpan[DanglingBase[T]] =
      t => t.base.length + 1
  }
  object Text {
    implicit def ftor: Functor[Text]  = semi.functor
    implicit def fold: Foldable[Text] = semi.foldable

    //      implicit def repr[T: Repr]: Repr[TextOf[T]] = {
    //        case t: Line[T]       => Repr(t)
    //        case t: Text.Block[T] => Repr(t)
    //        case t: UnclosedOf[T] => Repr(t)
    //      }
    //      implicit def ozip[T: Repr]: OffsetZip[TextOf, T] = {
    //        case t: Line[T]       => OffsetZip(t)
    //        case t: Text.Block[T] => OffsetZip(t)
    //        case t: UnclosedOf[T] => OffsetZip(t)
    //      }

    implicit def repr[T]: Repr[Text[T]] = t => (t: Shape[T]).repr
    implicit def ozip[T: HasSpan]: OffsetZip[Text, T] = { t =>
      OffsetZip[Shape, T](t).asInstanceOf
    }
    implicit def span[T: HasSpan]: HasSpan[Text[T]] =
      t => (t: Shape[T]).span
  }
  object TextUnclosed {
    implicit def ftor: Functor[TextUnclosed]  = semi.functor
    implicit def fold: Foldable[TextUnclosed] = semi.foldable
    implicit def repr[T: Repr]: Repr[TextUnclosed[T]] = {
      case TextUnclosed(t: LineRaw[T]) => t.repr
      case TextUnclosed(t: LineFmt[T]) => t.repr
    }
    implicit def ozip[T: HasSpan]: OffsetZip[TextUnclosed, T] =
      t => t.copy(line = OffsetZip(t.line))
    implicit def span[T: HasSpan]: HasSpan[TextUnclosed[T]] = _.line.span
  }
  object InvalidQuote {
    implicit def ftor: Functor[InvalidQuote]          = semi.functor
    implicit def fold: Foldable[InvalidQuote]         = semi.foldable
    implicit def repr[T: Repr]: Repr[InvalidQuote[T]] = _.quote
    implicit def ozip[T]: OffsetZip[InvalidQuote, T]  = t => t.coerce
    implicit def span[T]: HasSpan[InvalidQuote[T]]    = _.quote.span
  }
  object InlineBlock {
    implicit def ftor: Functor[InlineBlock]         = semi.functor
    implicit def fold: Foldable[InlineBlock]        = semi.foldable
    implicit def repr[T]: Repr[InlineBlock[T]]      = _.quote
    implicit def ozip[T]: OffsetZip[InlineBlock, T] = t => t.coerce
    implicit def span[T]: HasSpan[InlineBlock[T]]   = _.quote.span
  }
  object Line {
    implicit def ftor: Functor[Line]  = semi.functor
    implicit def fold: Foldable[Line] = semi.foldable
    implicit def repr[T: Repr]: Repr[Line[T]] = {
      case t: LineRaw[T] => t.quote + t.text + t.quote
      case t: LineFmt[T] => t.quote + t.text + t.quote
    }
    implicit def ozip[T: HasSpan]: OffsetZip[Line, T] = {
      case t: LineRaw[T] => OffsetZip(t)
      case t: LineFmt[T] => OffsetZip(t)
    }
    implicit def span[T: HasSpan]: HasSpan[Line[T]] = {
      case t: LineRaw[T] => t.span()
      case t: LineFmt[T] => t.span()
    }
  }

  object LineRaw {
    implicit def ftor: Functor[LineRaw]          = semi.functor
    implicit def fold: Foldable[LineRaw]         = semi.foldable
    implicit def repr[T: Repr]: Repr[LineRaw[T]] = t => t.quote + t.text
    implicit def ozip[T]: OffsetZip[LineRaw, T]  = t => t.coerce
    implicit def span[T]: HasSpan[LineRaw[T]]    = ??? // _.repr.span // FIXME
  }
  object LineFmt {
    implicit def ftor: Functor[LineFmt]          = semi.functor
    implicit def fold: Foldable[LineFmt]         = semi.foldable
    implicit def repr[T: Repr]: Repr[LineFmt[T]] = t => t.quote + t.text
    implicit def ozip[T: HasSpan]: OffsetZip[LineFmt, T] = { t =>
      var offset = Index(t.quote.span)
      val text2 = for (elem <- t.text) yield {
        val offElem = elem.map(offset -> _)
        offset += Size(elem.span)
        offElem
      }
      LineFmt(text2)
    }
    implicit def span[T]: HasSpan[LineFmt[T]] = ??? // _.repr.span // FIXME
  }

  // FIXME trait could delegate to shape
  object Block {
    def line[T: Repr](off: Int, l: BlockLine[SegmentFmt[T]]): Builder =
      R + l.emptyLines.map(AST.newline + _) + AST.newline + off + l.text

    implicit def ftor: Functor[Block]  = semi.functor
    implicit def fold: Foldable[Block] = semi.foldable

    implicit def repr[T: Repr]: Repr[Block[T]] = t => {
      val q = t.quote
      t match {
        case BlockRaw(text, s, off) => q + s + text.map(line(off, _))
        case BlockFmt(text, s, off) => q + s + text.map(line(off, _))
      }
    }
    implicit def ozip[T: HasSpan]: OffsetZip[Block, T] = {
      case body: BlockRaw[T] => body.coerce
      case body: BlockFmt[T] => OffsetZip(body)
    }
    implicit def span[T: HasSpan]: HasSpan[Block[T]] =
      t => (t: Shape[T]).span
  }

  object BlockRaw {
    implicit def ftor: Functor[BlockRaw]  = semi.functor
    implicit def fold: Foldable[BlockRaw] = semi.foldable
    implicit def repr[T: Repr]: Repr[BlockRaw[T]] =
      t => t.quote + t.spaces + t.text.map(Block.line(t.offset, _))
    implicit def ozip[T: HasSpan]: OffsetZip[BlockRaw, T] = t => t.coerce
    implicit def span[T]: HasSpan[BlockRaw[T]]            = ??? //_.str.length
  }

  object BlockFmt {
    implicit def ftor: Functor[BlockFmt]  = semi.functor
    implicit def fold: Foldable[BlockFmt] = semi.foldable
    implicit def repr[T: Repr]: Repr[BlockFmt[T]] =
      t => t.quote + t.spaces + t.text.map(Block.line(t.offset, _))
    implicit def ozip[T: HasSpan]: OffsetZip[BlockFmt, T] = { body =>
      var offset = Index(body.quote.span)
      val text =
        for (line <- body.text) yield {
          offset += Size(line.emptyLines.length + line.emptyLines.sum)
          offset += Size(1 + body.offset)
          val text = for (elem <- line.text) yield {
            val offElem = elem.map(offset -> _)
            offset += Size(elem.span)
            offElem
          }
          line.copy(text = text)
        }
      body.copy(text = text)
    }
    implicit def span[T]: HasSpan[BlockFmt[T]] = ??? //_.str.length
  }

  object Segment {
    implicit def ftor: Functor[Segment]  = semi.functor
    implicit def fold: Foldable[Segment] = semi.foldable
    implicit def repr[T: Repr]: Repr[Segment[T]] = {
      case t: SegmentRaw[T] => Repr(t)
      case t: SegmentFmt[T] => Repr(t)
    }
    implicit def ozip[T]: OffsetZip[Segment, T] = {
      case t: SegmentRaw[T] => OffsetZip(t)
      case t: SegmentFmt[T] => OffsetZip(t)
    }
    implicit def span[T]: HasSpan[Segment[T]] = ??? //_.str.length
  }
  object SegmentFmt {
    implicit def ftor[T]: Functor[SegmentFmt] = semi.functor
    implicit def fold: Foldable[SegmentFmt]   = semi.foldable
    implicit def repr[T: Repr]: Repr[SegmentFmt[T]] = {
      case t: SegmentPlain[T]  => Repr(t)
      case t: SegmentExpr[T]   => Repr(t)
      case t: SegmentEscape[T] => Repr(t)
    }
    implicit def ozip[T]: OffsetZip[SegmentFmt, T] = {
      case t: SegmentPlain[T]  => OffsetZip(t)
      case t: SegmentExpr[T]   => OffsetZip(t)
      case t: SegmentEscape[T] => OffsetZip(t)
    }
    implicit def span[T: HasSpan]: HasSpan[SegmentFmt[T]] = {
      case t: SegmentPlain[T]  => t.span()
      case t: SegmentExpr[T]   => t.span()
      case t: SegmentEscape[T] => t.span()
    }
  }
  object SegmentRaw {
    implicit def ftor[T]: Functor[SegmentRaw] = semi.functor
    implicit def fold: Foldable[SegmentRaw]   = semi.foldable
    implicit def repr[T]: Repr[SegmentRaw[T]] = {
      case t: SegmentPlain[T] => Repr(t)
    }
    implicit def ozip[T]: OffsetZip[SegmentRaw, T] = {
      case t: SegmentPlain[T] => OffsetZip(t)
    }
    implicit def span[T]: HasSpan[SegmentRaw[T]] = {
      case t: SegmentPlain[T] => t.span()
    }
  }
  object SegmentPlain {
    implicit def txtFromString[T](str: String): SegmentPlain[T] =
      SegmentPlain(str)

    implicit def fold: Foldable[SegmentPlain]   = semi.foldable
    implicit def ftor[T]: Functor[SegmentPlain] = semi.functor
    implicit def repr[T]: Repr[SegmentPlain[T]] = _.value
    implicit def ozip[T]: OffsetZip[SegmentPlain, T] =
      t => t.coerce
    implicit def span[T]: HasSpan[SegmentPlain[T]] = _.value.length
  }
  object SegmentExpr {
    implicit def ftor[T]: Functor[SegmentExpr] = semi.functor
    implicit def fold: Foldable[SegmentExpr]   = semi.foldable
    implicit def repr[T: Repr]: Repr[SegmentExpr[T]] =
      R + '`' + _.value + '`'
    implicit def ozip[T]: OffsetZip[SegmentExpr, T] =
      _.map(Index.Start -> _)
    implicit def span[T: HasSpan]: HasSpan[SegmentExpr[T]] =
      2 + _.value.map(_.span()).getOrElse(0)
  }
  object SegmentEscape {
    implicit def ftor: Functor[SegmentEscape]  = semi.functor
    implicit def fold: Foldable[SegmentEscape] = semi.foldable
    implicit def repr[T: Repr]: Repr[SegmentEscape[T]] =
      t => R + ("\\" + t.code.repr)
    implicit def ozip[T]: OffsetZip[SegmentEscape, T] =
      t => t.coerce
    implicit def span[T: HasSpan]: HasSpan[SegmentEscape[T]] =
      1 + _.code.repr.length
  }

  object App {
    implicit def ftor: Functor[App]  = semi.functor
    implicit def fold: Foldable[App] = semi.foldable
    implicit def repr[T: Repr]: Repr[App[T]] =
      t => R + t.fn + t.off + t.arg
    implicit def ozip[T: HasSpan]: OffsetZip[App, T] =
      t =>
        t.copy(
          fn  = (Index.Start, t.fn),
          arg = (Index(t.fn.span + t.off), t.arg)
        )
    implicit def span[T: HasSpan]: HasSpan[App[T]] =
      t => t.fn.span + t.off + t.arg.span

  }

  object Infix {
    implicit def ftor: Functor[Infix]  = semi.functor
    implicit def fold: Foldable[Infix] = semi.foldable
    implicit def repr[T: Repr]: Repr[Infix[T]] =
      t => R + t.larg + t.loff + t.opr + t.roff + t.rarg
    implicit def ozip[T: HasSpan]: OffsetZip[Infix, T] = t => {
      val rargIndex = Index(t.larg.span + t.loff + t.opr.span + t.roff)
      t.copy(larg = (Index.Start, t.larg), rarg = (rargIndex, t.rarg))
    }
    implicit def span[T: HasSpan]: HasSpan[Infix[T]] =
      t => t.larg.span + t.loff + t.opr.span + t.roff + t.rarg.span
  }

  // TODO Section

  object SectionLeft {
    implicit def ftor: Functor[SectionLeft]  = semi.functor
    implicit def fold: Foldable[SectionLeft] = semi.foldable
    implicit def repr[T: Repr]: Repr[SectionLeft[T]] =
      t => R + t.arg + t.off + t.opr
    implicit def ozip[T]: OffsetZip[SectionLeft, T] =
      t => t.copy(arg = (Index.Start, t.arg))
    implicit def span[T: HasSpan]: HasSpan[SectionLeft[T]] =
      t => t.arg.span + t.off + t.opr.span
  }
  object SectionRight {
    implicit def ftor: Functor[SectionRight]  = semi.functor
    implicit def fold: Foldable[SectionRight] = semi.foldable
    implicit def repr[T: Repr]: Repr[SectionRight[T]] =
      t => R + t.opr + t.off + t.arg
    implicit def ozip[T]: OffsetZip[SectionRight, T] =
      t => t.copy(arg = (Index(t.opr.span + t.off), t.arg))
    implicit def span[T: HasSpan]: HasSpan[SectionRight[T]] =
      t => t.opr.span + t.off + t.arg.span
  }
  object SectionSides {
    implicit def ftor: Functor[SectionSides]          = semi.functor
    implicit def fold: Foldable[SectionSides]         = semi.foldable
    implicit def repr[T: Repr]: Repr[SectionSides[T]] = t => R + t.opr
    implicit def ozip[T]: OffsetZip[SectionSides, T]  = t => t.coerce
    implicit def span[T: HasSpan]: HasSpan[SectionSides[T]] =
      t => t.opr.span
  }

  //// Implicits ////

  object implicits {
    import AST.AST

    implicit class ToShapeOps[T[S] <: Shape[S]](t: T[AST])(
      implicit
      functor: Functor[T],
      ozip: OffsetZip[T, AST]
    ) {
      def mapWithOff(f: (Index, AST) => AST): T[AST] =
        Functor[T].map(ozip.zipWithOffset(t))(f.tupled)
    }
  }
}

//////////////////////////////////////////////////////////////////////////////
//// AST /////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

import HasSpan.implicits._

/** =AST=
  *
  * AST is encoded as a simple recursion scheme. See the following links to
  * learn more about the concept:
  * - https://wiki.haskell.org/Catamorphisms
  * - https://www.schoolofhaskell.com/user/edwardk/recursion-schemes/catamorphisms
  * - https://www.schoolofhaskell.com/user/bartosz/understanding-algebras
  * - http://hackage.haskell.org/package/free-5.1.2/docs/Control-Comonad-Cofree.html
  * - https://www.47deg.com/blog/basic-recursion-schemes-in-scala/
  *
  * ==AST Shape==
  *
  * Every AST node like [[Ident.Var]] or [[App.Prefix]] defines a shape of its
  * subtree. Shapes extend [[Shape]], are parametrized with a child type,
  * and follow a simple naming convention - their name is the same as the AST
  * node name with an additional prefix "Of", like [[Ident.VarOf]], or
  * [[App.PrefixOf]]. Shapes contain information about names of children and
  * spacing between them, for example, the [[App.PrefixOf]] shape contains
  * reference to function being its first child ([[App.PrefixOf.fn]]), spacing
  * between the function and its argument ([[App.PrefixOf.off]]), and the
  * argument itself ([[App.PrefixOf.arg]]).
  *
  * ==[[ASTOf]] as Catamorphism==
  *
  * In order to keep the types simple and make the inference predictive, we
  * are not using standard catamorphism implementations. Instead, we have
  * implemented a simple recursion scheme in [[ASTOf]]. Every AST node uses it
  * as the wrapping layer. For example, the most generic AST type, [[AST]] is
  * defined just as an alias to [[(ASTOf[Shape])]]. Every AST node follows
  * the same scheme, including [[Ident.Var]] being an alias to
  * [[(ASTOf[Ident.VarOf])]], or [[App.Prefix]] being an alias to
  * [[(ASTOf[App.PrefixOf])]].
  *
  * ==[[ASTOf]] as Cofree==
  *
  * [[ASTOf]] adds a layer of additional information to each AST node.
  * Currently, the information is just an optional [[ID]], however, it may
  * grow in the future. This design minimizes the necessary boilerplate in
  * storing repeatable information across AST. Moreover, we can easily make
  * [[ASTOf]] polymorphic and allow the Syntax Tree to be tagged with
  * different information in different compilation stages if necessary.
  *
  * ==[[ASTOf]] as Cache Layer==
  *
  * When wrapping an element, [[ASTOf]] requires the element to implement
  * several type classes, including:
  * - [[Functor]]   - Defines mapping over every element in a shape.
  * - [[Repr]]      - Defines shape to code translation.
  * - [[OffsetZip]] - Zips every shape element with offset from the left side
  *                   of the shape.
  *
  * [[ASTOf]] caches the [[Repr]], which contains information about the span.
  * This way querying AST subtree for it span is always O(1).
  *
  * ==[[ASTOf]] as Method Provider==
  *
  * Because [[ASTOf]] has access to all the type class instances of the child
  * element (and they cannot be further exposed because [[ASTOf]] type parameter
  * has to be variant), it is a perfect place for exposing common utils for AST
  * nodes. Please note, that "exposing" means both providing as well as caching.
  * For example, when we eval `myAST.map(a => a)` we are not doing pattern match
  * as one may expect. During the creation of [[ASTOf]], the functor of the
  * shape was obtained and the `map` method references it, so instead of pattern
  * matching, we are acessing the `map` method directly.
  *
  * ==Fields Access==
  *
  * Please note, that [[ASTOf]] is "transparent". There are
  * implicit defs of both wrapping and unwrapping functions, which makes
  * working with AST nodes very convenient. For example, there is no need to
  * write `myVar.shape.name` to first unpack the node from the [[ASTOf]] layer
  * and then access its name. It's possible to just write `myVar.name`, and
  * the unpacking will be performed automatically.
  *
  * ==Pattern Matching==
  *
  * Please note that due to type erasure, it is impossible to pattern match on
  * AST types. Never use `case _: Var => ...` statement, as it will probably
  * crash at runtime. In order to pattern match on AST type, each AST node
  * provides a special "any" matcher, which results in a type narrowed version
  * of the AST node. For example, `case Var.any(v) => ...` will succeed if the
  * match was performed on any [[Ident.Var]] and its result `v` will be of
  * [[Ident.Var]] type. Of course, it is possible to use structural matching
  * without any restrictions.
  **/
object AST {
  import Shape.implicits._

  //////////////////////////////////////////////////////////////////////////////
  //// Reexports ///////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

//  type Assoc = opr.Assoc
//  val Assoc = opr.Assoc
//  val Prec  = opr.Prec

  //////////////////////////////////////////////////////////////////////////////
  //// Definition //////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  //// Structure ////

//  type Shape = Shape[AST]
  type AST = ASTOf[Shape]

  //// Aliases ////

  type SAST         = Shifted[AST]
  type StreamOf[T]  = List[Shifted[T]]
  type StreamOf1[T] = List1[Shifted[T]]
  type Stream       = StreamOf[AST]
  type Stream1      = StreamOf1[AST]
  type ID           = UUID

  //// API ////

//  def tokenize(ast: AST): Shifted.List1[AST] = {
//    @tailrec
//    def go(ast: AST, out: AST.Stream): Shifted.List1[AST] = ast match {
//      case App.Prefix.any(t) => go(t.fn, Shifted(t.off, t.arg) :: out)
//      case _                 => Shifted.List1(ast, out)
//    }
//    go(ast, List())
//  }

//  //// Conversions ////
//
//  object conversions extends conversions
//  sealed trait conversions extends Ident.conversions {
//    implicit def intToAST(int: Int): AST =
//      Literal.Number(int)
//
//    implicit def stringToAST(str: String): AST = {
//      if (str == "") throw new Error("Empty literal")
//      if (str == "_") Blank()
//      else if (str.head.isLower) Var(str)
//      else if (str.head.isUpper) Cons(str)
//      else Opr(str)
//    }
//  }
//
  ////////////////////////////////////
  //// Apply / Unapply Generators ////
  ////////////////////////////////////

  /** [[Unapply]] and [[UnapplyByType]] are unapply generators for AST Shapes.
    * The implementation may seem complex, but this is just a scala way for
    * deconstructing types. When provided with a AST type, like [[Ident.Var]],
    * [[Unapply]] deconstructs it to [[(ASTOf[VarOf])]] and then generates
    * an object providing unapply implementation for the [[Ident.VarOf]] type.
    */
  sealed trait Unapply[T] {
    type In
    def run[Out](f: In => Out)(t: AST): Option[Out]
  }
  object Unapply {
    def apply[T](implicit t: Unapply[T]): Unapply[T] { type In = t.In } = t
    implicit def inst[T[_]](
      implicit ev: ClassTag[T[AST]]
    ): Unapply[ASTOf[T]] { type In = T[AST] } =
      new Unapply[ASTOf[T]] {
        type In = T[AST]
        val ct                              = implicitly[ClassTag[T[AST]]]
        def run[Out](fn: In => Out)(t: AST) = ct.unapply(t.shape).map(fn)
      }
  }

  /** See the documentation for [[Unapply]] */
  sealed trait UnapplyByType[T] {
    def unapply(t: AST): Option[T]
  }
  object UnapplyByType {
    def apply[T](implicit ev: UnapplyByType[T]) = ev
    implicit def instance[T[_]](
      implicit ct: ClassTag[T[_]]
    ): UnapplyByType[ASTOf[T]] =
      new UnapplyByType[ASTOf[T]] {
        def unapply(t: AST) =
          // Note that the `asInstanceOf` usage is safe here.
          // It is used only for performance reasons, otherwise we would need
          // to create a new object which would look exactly the same way
          // as the original one.
          ct.unapply(t.shape).map(_ => t.asInstanceOf[ASTOf[T]])
      }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// ASTOf ///////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  //// Definition ////

  /** The [[ASTOf]] class wraps each AST node. The implementation is similar to
    * standard catamorphic Fix, however, it is parametrized with the head shape
    * type. In combination with covariance of [[T]], it allows us to both keep
    * information about the specific shape of the AST, as well as get natural
    * subtyping behavior. For example, [[AST]] and [[Var]] are aliases to
    * [[(ASTOf[Shape])]] and [[(AST[VarOf])]] respectively, and while
    * [[(VarOf[T] <: Shape[T])]], also [[Var <: AST]].
    *
    * Another important role of [[ASTOf]] is caching of [[Repr.Builder]] and
    * allowing for fast method redirection. When [[ASTOf]] is created, it
    * remembers a bunch of stuff, which can be fast accessed even if we cast the
    * type to generic [[AST]].
    */
  final case class ASTOf[+T[_]](shape: T[AST], span: Int, id: Option[ID] = None)

  object ASTOf extends AstImplicits {
    implicit def repr[T[_]](implicit ev: Repr[T[AST]]): Repr[ASTOf[T]] =
      t => t.shape.repr // FIXME?
    implicit def span[T[_]]: HasSpan[ASTOf[T]] = t => t.span
    implicit def wrap[T[_]](t: T[AST])(implicit ev: HasSpan[T[AST]]): ASTOf[T] =
      ASTOf(t, ev.span(t))
  }

  trait AstImplicits extends AstImplicits2 {
    implicit def encoder_spec(
      implicit ev: Encoder[Shape[AST]]
    ): Encoder[AST] = encoder
  }

  trait AstImplicits2 {
    implicit def encoder[T[_]](
      implicit ev: Encoder[T[AST]]
    ): Encoder[ASTOf[T]] = (ast) => {
      val obj1 = ev(ast.shape).asObject.get
      val obj2 = obj1.mapValues(s => {
        val s2 =
          addField(s, "id", implicitly[Encoder[Option[ID]]].apply(ast.id))
        addField(s2, "span", implicitly[Encoder[Int]].apply(ast.span))
      })
      Json.fromJsonObject(obj2)
    }
  }

  // FIXME: refactor
  def addField(base: Json, name: String, value: Json): Json = {
    val obj  = base.asObject.get
    val obj2 = (name, value) +: obj
    Json.fromJsonObject(obj2)
  }

  //// ASTOps ////

  /** [[ASTOps]] implements handy AST operations. In contrast to [[ASTClass]],
    * implementations in this class do not require any special knowledge of the
    * underlying shape and thus are just a high-level AST addons.
    */
  implicit class AstOps[T[S] <: Shape[S]](t: ASTOf[T])(
    implicit
    functor: Functor[T],
    ozip: OffsetZip[T, AST]
  ) {
    def mapWithOff(f: (Index, AST) => AST): ASTOf[T] =
      t.copy(shape = ToShapeOps(t.shape).mapWithOff(f))

    def traverseWithOff(f: (Index, AST) => AST): ASTOf[T] = {
      def go(i: Index, ast: AST): AST =
        ast.mapWithOff((j, ast) => go(i + j.asSize, f(i + j.asSize, ast)))
      t.mapWithOff((j, ast) => go(j, f(j, ast)))
    }

    def zipWithOffset(): T[(Index, AST)] = {
      OffsetZip(t.shape)
    }

    def idMap(implicit ev: Foldable[Shape]): List[(Span, AST.ID)] = {
      var ids  = List[(Span, AST.ID)]()
      var asts = List[(Index, AST)](Index.Start -> t)
      while (asts.nonEmpty) {
        val (off, ast) = asts.head
        val children = ast.zipWithOffset().toList.map {
          case (o, ast) => (o + off.asSize, ast)
        }
        if (ast.id.nonEmpty)
          ids +:= Span(off, ast) -> ast.id.get
        asts = children ++ asts.tail
      }
      ids.reverse
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Invalid /////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Invalid = ASTOf[Shape.InvalidOf]
  object Invalid {
    type Unrecognized = AST.ASTOf[Shape.Unrecognized]
    type Unexpected   = AST.ASTOf[Shape.Unexpected]

    val any = UnapplyByType[Invalid]

    object Unrecognized {
      val any = UnapplyByType[Unrecognized]
      def unapply(t: AST) =
        Unapply[Unrecognized].run(_.str)(t)
      def apply(str: String): Unrecognized = Shape.Unrecognized[AST](str)
    }
    object Unexpected {
      val any = UnapplyByType[Unexpected]
      def unapply(t: AST) =
        Unapply[Unexpected].run(t => (t.msg, t.stream))(t)
      def apply(msg: String, str: Stream): Unexpected =
        Shape.Unexpected(msg, str)
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Ident ///////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  //// Reexports ////

  type Blank = Ident.Blank
  type Var   = Ident.Var
  type Cons  = Ident.Cons
  type Opr   = Ident.Opr
  type Mod   = Ident.Mod

  val Blank = Ident.Blank
  val Var   = Ident.Var
  val Cons  = Ident.Cons
  val Opr   = Ident.Opr
  val Mod   = Ident.Mod

  //// Definition ////

  type Ident = ASTOf[Shape.IdentOf]

  object Ident {
    type Blank = ASTOf[Shape.Blank]
    type Var   = ASTOf[Shape.Var]
    type Cons  = ASTOf[Shape.Cons]
    type Mod   = ASTOf[Shape.Mod]
    type Opr   = ASTOf[Shape.Opr]

    type InvalidSuffix = ASTOf[Shape.InvalidSuffix]

    //// Conversions ////

    trait Conversions1 {
      implicit def strToVar(str: String): Var   = Var(str)
      implicit def strToCons(str: String): Cons = Cons(str)
      implicit def strToOpr(str: String): Opr   = Opr(str)
      implicit def strToMod(str: String): Mod   = Mod(str)
    }

    trait conversions extends Conversions1 {
      implicit def stringToIdent(str: String): Ident = {
        if (str == "") throw new Error("Empty literal")
        if (str == "_") Blank()
        else if (str.head.isLower) Var(str)
        else if (str.head.isUpper) Cons(str)
        else Opr(str)
      }
    }

    //// Smart Constructors ////

    val any = UnapplyByType[Ident]

    object Blank {
      private val cachedBlank = Shape.Blank[AST]()
      val any                 = UnapplyByType[Blank]
      def unapply(t: AST)     = Unapply[Blank].run(_ => true)(t)
      def apply(): Blank      = cachedBlank
    }
    object Var {
      private val pool             = new Pool[Shape.Var[AST]]()
      val any                      = UnapplyByType[Var]
      def unapply(t: AST)          = Unapply[Var].run(_.name)(t)
      def apply(name: String): Var = pool.get(Shape.Var[AST](name))
    }
    object Cons {
      private val pool              = new Pool[Shape.Cons[AST]]()
      val any                       = UnapplyByType[Cons]
      def unapply(t: AST)           = Unapply[Cons].run(_.name)(t)
      def apply(name: String): Cons = pool.get(Shape.Cons[AST](name))
    }
    object Mod {
      private val pool             = new Pool[Shape.Mod[AST]]()
      val any                      = UnapplyByType[Mod]
      def unapply(t: AST)          = Unapply[Mod].run(_.name)(t)
      def apply(name: String): Mod = pool.get(Shape.Mod[AST](name))
    }
    object Opr {
      private val pool             = new Pool[Shape.Opr[AST]]()
      val app                      = Opr(" ")
      val any                      = UnapplyByType[Opr]
      def unapply(t: AST)          = Unapply[Opr].run(_.name)(t)
      def apply(name: String): Opr = pool.get(Shape.Opr[AST](name))
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Literal /////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  //// Reexports ////

  type Number = Literal.Number
  type Text   = Literal.Text
  val Number = Literal.Number
  val Text   = Literal.Text

  //// Definition ////

  type Literal = ASTOf[Shape.LiteralOf]
  object Literal {

    val any = UnapplyByType[Literal]

    ////////////////
    //// Number ////
    ////////////////

    type Number = ASTOf[Shape.Number]
    object Number {
      type DanglingBase = ASTOf[Shape.DanglingBase]

      //// Smart Constructors ////
      def apply(i: String): Number            = Number(None, i)
      def apply(b: String, i: String): Number = Number(Some(b), i)
      def apply(i: Int): Number               = Number(i.toString)
      def apply(b: Int, i: String): Number    = Number(b.toString, i)
      def apply(b: String, i: Int): Number    = Number(b, i.toString)
      def apply(b: Int, i: Int): Number       = Number(b.toString, i.toString)
      def apply(b: Option[String], i: String): Number =
        Shape.Number[AST](b, i)
      def unapply(t: AST) = Unapply[Number].run(t => (t.base, t.int))(t)
      val any             = UnapplyByType[Number]

      //// DanglingBase ////
      object DanglingBase {
        val any                               = UnapplyByType[DanglingBase]
        def apply(base: String): DanglingBase = Shape.DanglingBase[AST](base)
        def unapply(t: AST) =
          Unapply[DanglingBase].run(_.base)(t)
      }
    }

    //////////////
    //// Text ////
    //////////////

    type Text = ASTOf[Shape.Text]
    object Text {
      val any = UnapplyByType[Text]

      //// Definition ////

      object Line {
        val Raw = Shape.LineRaw
        type Raw[T] = Shape.LineRaw[T]
        val Fmt = Shape.LineFmt
        type Fmt[T] = Shape.LineFmt[T]
      }

      ////// CONSTRUCTORS ///////
      type Unclosed = ASTOf[Shape.TextUnclosed]
      object Unclosed {
        val any = UnapplyByType[Unclosed]
        def unapply(t: AST) =
          Unapply[Unclosed].run(t => t.line)(t)
        def apply(segment: Segment.Fmt*): Unclosed =
          Shape.TextUnclosed(Line.Fmt(segment.to[List]))
        object Raw {
          def apply(segment: Segment.Raw*): Unclosed =
            Shape.TextUnclosed(Line.Raw(segment.to[List]))
        }
      }
      type InvalidQuote = ASTOf[Shape.InvalidQuote]
      object InvalidQuote {
        val any = UnapplyByType[InvalidQuote]
        def unapply(t: AST) =
          Unapply[InvalidQuote].run(t => t.quote)(t)
        def apply(quote: String): InvalidQuote = Shape.InvalidQuote[AST](quote)
      }
      type InlineBlock = ASTOf[Shape.InlineBlock]
      object InlineBlock {
        val any = UnapplyByType[InlineBlock]
        def unapply(t: AST) =
          Unapply[InlineBlock].run(t => t.quote)(t)
        def apply(quote: String): InlineBlock = Shape.InlineBlock[AST](quote)
      }

      def apply(text: Shape.Text[AST]): Text = text
      def apply(segment: Segment.Fmt*): Text = Text(Line.Fmt(segment.to[List]))
      def apply(
        spaces: Int,
        off: Int,
        line: Shape.BlockLine[Segment.Fmt]*
      ): Text =
        Text(Shape.BlockFmt(line.to[List], spaces, off))

      object Raw {
        def apply(segment: Segment.Raw*): Text =
          Text(Line.Raw(segment.to[List]))
        def apply(
          spaces: Int,
          off: Int,
          line: Shape.BlockLine[Segment.Raw]*
        ): Text =
          Text(Shape.BlockRaw(line.to[List], spaces, off))
      }

      /////////////////
      //// Segment ////
      /////////////////
      type Segment[T] = Shape.Segment[T]
      object Segment {

        type Escape = org.enso.syntax.text.ast.text.Escape
        val Escape = org.enso.syntax.text.ast.text.Escape

        //// Definition ////

        type Fmt = Shape.SegmentFmt[AST]
        type Raw = Shape.SegmentRaw[AST]

        object Expr  { def apply(t: Option[AST]): Fmt = Shape.SegmentExpr(t)  }
        object Plain { def apply(s: String): Raw      = Shape.SegmentPlain(s) }
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// App /////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  //// Definition ////

//  type App = ASTOf[Shape.AppOf]
  object App {

//    val any = UnapplyByType[App]

    //// Constructors ////

    type Prefix = ASTOf[Shape.App]
    type Infix  = ASTOf[Shape.Infix]

    //// Smart Constructors ////

    object Prefix {
      val any             = UnapplyByType[Prefix]
      def unapply(t: AST) = Unapply[Prefix].run(t => (t.fn, t.arg))(t)
      def apply(fn: AST, off: Int, arg: AST): Prefix =
        Shape.App(fn, off, arg)
      def apply(fn: AST, arg: AST): Prefix = Prefix(fn, 1, arg)
    }

    object Infix {
      val any             = UnapplyByType[Infix]
      def unapply(t: AST) = Unapply[Infix].run(t => (t.larg, t.opr, t.rarg))(t)
      def apply(larg: AST, loff: Int, opr: Opr, roff: Int, rarg: AST): Infix =
        Shape.Infix(larg, loff, opr, roff, rarg)
      def apply(larg: AST, loff: Int, opr: Opr, rarg: AST): Infix =
        Infix(larg, loff, opr, 1, rarg)
      def apply(larg: AST, opr: Opr, roff: Int, rarg: AST): Infix =
        Infix(larg, 1, opr, roff, rarg)
      def apply(larg: AST, opr: Opr, rarg: AST): Infix =
        Infix(larg, 1, opr, 1, rarg)
    }

    //// Instances ////
    /////////////////
    //// Section ////
    /////////////////

    //// Reexports ////

    type Left  = Section.Left
    type Right = Section.Right
    type Sides = Section.Sides

    val Left  = Section.Left
    val Right = Section.Right
    val Sides = Section.Sides

    //// Definition ////

    type Section = ASTOf[Shape.Section]
    object Section {

      val any = UnapplyByType[Section]

      //// Constructors ////

      type Left  = ASTOf[Shape.SectionLeft]
      type Right = ASTOf[Shape.SectionRight]
      type Sides = ASTOf[Shape.SectionSides]

      //// Smart Constructors ////

      object Left {
        val any             = UnapplyByType[Left]
        def unapply(t: AST) = Unapply[Left].run(t => (t.arg, t.opr))(t)

        def apply(arg: AST, off: Int, opr: Opr): Left =
          Shape.SectionLeft(arg, off, opr)
        def apply(arg: AST, opr: Opr): Left = Left(arg, 1, opr)
      }
      object Right {
        val any             = UnapplyByType[Right]
        def unapply(t: AST) = Unapply[Right].run(t => (t.opr, t.arg))(t)

        def apply(opr: Opr, off: Int, arg: AST): Right =
          Shape.SectionRight(opr, off, arg)
        def apply(opr: Opr, arg: AST): Right = Right(opr, 1, arg)
      }
      object Sides {
        val any                    = UnapplyByType[Sides]
        def unapply(t: AST)        = Unapply[Sides].run(_.opr)(t)
        def apply(opr: Opr): Sides = Shape.SectionSides[AST](opr)
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Block ///////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  val newline = R + '\n'
//
//  type Block = ASTOf[BlockOf]
//  final case class BlockOf[T](
//                               typ: Block.Type,
//                               indent: Int,
//                               emptyLines: List[Int],
//                               firstLine: Block.LineOf[T],
//                               lines: List[Block.LineOf[Option[T]]],
//                               protected val isOrphan: Boolean = false
//                             ) extends Shape[T] {
//    // FIXME: Compatibility mode
//    def replaceType(ntyp: Block.Type): BlockOf[T] = copy(typ = ntyp)
//  }
//
//  object Block {
//    sealed trait Type
//    final case object Continuous    extends Type
//    final case object Discontinuous extends Type
//
//    //// Smart Constructors ////
//
//    // FIXME: Compatibility mode
//    def apply(
//               isOrphan: Boolean,
//               typ: Type,
//               indent: Int,
//               emptyLines: List[Int],
//               firstLine: LineOf[AST],
//               lines: List[LineOf[Option[AST]]]
//             ): Block = {
//      Unused(isOrphan)
//      BlockOf(typ, indent, emptyLines, firstLine, lines, isOrphan)
//    }
//
//    def apply(
//               typ: Type,
//               indent: Int,
//               emptyLines: List[Int],
//               firstLine: LineOf[AST],
//               lines: List[LineOf[Option[AST]]]
//             ): Block = BlockOf(typ, indent, emptyLines, firstLine, lines)
//
//    def apply(
//               indent: Int,
//               firstLine: AST,
//               lines: AST*
//             ): Block = Block(
//      Continuous,
//      indent,
//      List(),
//      Line(firstLine),
//      lines.to[List].map(ast => Line(Some(ast)))
//    )
//
//    val any = UnapplyByType[Block]
//    def unapply(t: AST) =
//      Unapply[Block].run(t => (t.typ, t.indent, t.firstLine, t.lines))(t)
//
//    //// Line ////
//
//    type Line         = LineOf[AST]
//    type OptLineOf[T] = LineOf[Option[T]]
//    type OptLine      = OptLineOf[AST]
//    final case class LineOf[+T](elem: T, off: Int) {
//      // FIXME: Compatibility mode
//      def toOptional: LineOf[Option[T]] = copy(elem = Some(elem))
//    }
//    object LineOf {
//      implicit def ftorLine:          Functor[LineOf]  = semi.functor
//      implicit def fold:              Foldable[LineOf] = semi.foldable
//      implicit def reprLine[T: Repr]: Repr[LineOf[T]]  = t => R + t.elem + t.off
//    }
//    object Line {
//      // FIXME: Compatibility mode
//      type NonEmpty = Line
//      val Required                    = Line
//      def apply[T](elem: T, off: Int) = LineOf(elem, off)
//      def apply[T](elem: T): LineOf[T] = LineOf(elem, 0)
//    }
//    object OptLine {
//      def apply():          OptLine = Line(None, 0)
//      def apply(elem: AST): OptLine = Line(Some(elem))
//      def apply(off: Int):  OptLine = Line(None, off)
//    }
//  }
//  object BlockOf {
//    implicit def ftorBlock: Functor[BlockOf]  = semi.functor
//    implicit def fold:      Foldable[BlockOf] = semi.foldable
//    implicit def reprBlock[T: Repr]: Repr[BlockOf[T]] = t => {
//      val headRepr       = if (t.isOrphan) R else newline
//      val emptyLinesRepr = t.emptyLines.map(R + _ + newline)
//      val firstLineRepr  = R + t.indent + t.firstLine
//      val linesRepr = t.lines.map { line =>
//        newline + line.elem.map(_ => t.indent) + line
//      }
//      headRepr + emptyLinesRepr + firstLineRepr + linesRepr
//    }
//    implicit def ozipBlock[T: Repr]: OffsetZip[BlockOf, T] = t => {
//      val line   = t.firstLine.copy(elem = (Index.Start, t.firstLine.elem))
//      var offset = Index(t.firstLine.span)
//      val lines = for (line <- t.lines) yield {
//        val elem = line.elem.map((offset, _))
//        offset += Size(line.span)
//        line.copy(elem = elem)
//      }
//      t.copy(firstLine = line, lines = lines)
//    }
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Module //////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  type Module = ASTOf[ModuleOf]
//  final case class ModuleOf[T](lines: List1[Block.OptLineOf[T]])
//    extends Shape[T]
//
//  object Module {
//    import Block._
//    type M = Module
//    val any             = UnapplyByType[M]
//    def unapply(t: AST) = Unapply[M].run(_.lines)(t)
//    def apply(ls: List1[OptLine]):            M = ModuleOf(ls)
//    def apply(l: OptLine):                    M = Module(List1(l))
//    def apply(l: OptLine, ls: OptLine*):      M = Module(List1(l, ls.to[List]))
//    def apply(l: OptLine, ls: List[OptLine]): M = Module(List1(l, ls))
//    def traverseWithOff(m: M)(f: (Index, AST) => AST): M = {
//      val lines2 = m.lines.map { line: OptLine =>
//        // FIXME: Why line.map does not work?
//        LineOf.ftorLine.map(line)(_.map(_.traverseWithOff(f)))
//      }
//      m.shape.copy(lines = lines2)
//    }
//  }
//  object ModuleOf {
//    implicit def ftor:    Functor[ModuleOf]      = semi.functor
//    implicit def fold:    Foldable[ModuleOf]     = semi.foldable
//    implicit def ozip[T]: OffsetZip[ModuleOf, T] = _.map(Index.Start -> _)
//    implicit def repr[T: Repr]: Repr[ModuleOf[T]] =
//      t => R + t.lines.head + t.lines.tail.map(newline + _)
//  }
//
//  ////////////////////////////////////////////////////////////////////////////
//  //// Macro ///////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  type Macro = ASTOf[MacroOf]
//  sealed trait MacroOf[T] extends Shape[T]
//  object Macro {
//
//    import org.enso.syntax.text.ast.meta.Pattern
//
//    //// Matched ////
//
//    type Match = ASTOf[MatchOf]
//    final case class MatchOf[T](
//                                 pfx: Option[Pattern.Match],
//                                 segs: Shifted.List1[Match.SegmentOf[T]],
//                                 resolved: AST
//                               ) extends MacroOf[T] {
//      def path: List1[AST] = segs.toList1().map(_.el.head)
//    }
//
//    object MatchOf {
//      implicit def ftor: Functor[MatchOf]  = semi.functor
//      implicit def fold: Foldable[MatchOf] = semi.foldable
//      implicit def ozip[T: Repr]: OffsetZip[MatchOf, T] = t => {
//        var off = 0
//        t.copy(segs = t.segs.map { seg =>
//          OffsetZip(seg).map(_.map(_.map(s => {
//            val loff = off
//            off = Repr(s._2).span
//            (s._1 + Size(loff), s._2)
//          })))
//        })
//      }
//      implicit def repr[T: Repr]: Repr[MatchOf[T]] = t => {
//        val pfxStream = t.pfx.map(_.toStream.reverse).getOrElse(List())
//        val pfxRepr   = pfxStream.map(t => R + t.el + t.off)
//        R + pfxRepr + t.segs
//      }
//    }
//    object Match {
//      val any = UnapplyByType[Match]
//      def apply(
//                 pfx: Option[Pattern.Match],
//                 segs: Shifted.List1[Match.Segment],
//                 resolved: AST
//               ): Match = MatchOf[AST](pfx, segs, resolved)
//
//      type Segment = SegmentOf[AST]
//      final case class SegmentOf[T](
//                                     head: Ident,
//                                     body: Pattern.MatchOf[Shifted[T]]
//                                   ) {
//        def isValid: Boolean = body.isValid
//        def map(
//                 f: Pattern.MatchOf[Shifted[T]] => Pattern.MatchOf[Shifted[T]]
//               ): SegmentOf[T] =
//          copy(body = f(body))
//      }
//      object SegmentOf {
//        def apply[T](head: Ident): SegmentOf[T] =
//          SegmentOf(head, Pattern.Match.Nothing())
//
//        //// Instances ////
//        implicit def repr[T: Repr]: Repr[SegmentOf[T]] =
//          t => R + t.head + t.body
//
//        implicit def ozip[T: Repr]: OffsetZip[SegmentOf, T] = t => {
//          t.copy(body = OffsetZip(t.body).map {
//            case (i, s) => s.map((i + Size(t.head.repr.span), _))
//          })
//        }
//      }
//      implicit class SegmentOps(t: Segment) {
//        def toStream: AST.Stream = Shifted(t.head) :: t.body.toStream
//      }
//
//    }
//
//    //// Ambiguous ////
//
//    type Ambiguous = ASTOf[AmbiguousOf]
//    final case class AmbiguousOf[T](
//                                     segs: Shifted.List1[Ambiguous.Segment],
//                                     paths: Tree[AST, Unit]
//                                   ) extends MacroOf[T]
//    object Ambiguous {
//      def apply(
//                 segs: Shifted.List1[Ambiguous.Segment],
//                 paths: Tree[AST, Unit]
//               ): Ambiguous = ASTOf(AmbiguousOf(segs, paths))
//
//      final case class Segment(head: AST, body: Option[SAST])
//      object Segment {
//        def apply(head: AST): Segment       = Segment(head, None)
//        implicit def repr:    Repr[Segment] = t => R + t.head + t.body
//      }
//    }
//
//    object AmbiguousOf {
//      implicit def ftor:    Functor[AmbiguousOf]      = semi.functor
//      implicit def fold:    Foldable[AmbiguousOf]     = semi.foldable
//      implicit def repr[T]: Repr[AmbiguousOf[T]]      = t => R + t.segs.map(Repr(_))
//      implicit def ozip[T]: OffsetZip[AmbiguousOf, T] = _.map(Index.Start -> _)
//    }
//
//    //// Resolver ////
//
//    type Resolver = Resolver.Context => AST
//    object Resolver {
//      type Context = ContextOf[AST]
//      final case class ContextOf[T](
//                                     prefix: Option[Pattern.Match],
//                                     body: List[Macro.Match.SegmentOf[T]],
//                                     id: ID
//                                   )
//      object Context {
//        def apply(
//                   prefix: Option[Pattern.Match],
//                   body: List[Macro.Match.Segment],
//                   id: ID
//                 ): Context = ContextOf(prefix, body, id)
//      }
//    }
//
//    //// Definition ////
//
//    type Definition = __Definition__
//    final case class __Definition__(
//                                     back: Option[Pattern],
//                                     init: List[Definition.Segment],
//                                     last: Definition.LastSegment,
//                                     resolver: Resolver
//                                   ) {
//      def path: List1[AST] = init.map(_.head) +: List1(last.head)
//      def fwdPats: List1[Pattern] =
//        init.map(_.pattern) +: List1(last.pattern.getOrElse(Pattern.Nothing()))
//    }
//    object Definition {
//      import Pattern._
//
//      final case class Segment(head: AST, pattern: Pattern) {
//        def map(f: Pattern => Pattern): Segment = copy(pattern = f(pattern))
//      }
//      object Segment {
//        type Tup = (AST, Pattern)
//        def apply(t: Tup): Segment = Segment(t._1, t._2)
//      }
//
//      final case class LastSegment(head: AST, pattern: Option[Pattern]) {
//        def map(f: Pattern => Pattern): LastSegment =
//          copy(pattern = pattern.map(f))
//      }
//      object LastSegment {
//        type Tup = (AST, Option[Pattern])
//        def apply(t: Tup): LastSegment = LastSegment(t._1, t._2)
//      }
//
//      def apply(
//                 precSection: Option[Pattern],
//                 t1: Segment.Tup,
//                 ts: List[Segment.Tup]
//               )(
//                 fin: Resolver
//               ): Definition = {
//        val segs    = List1(t1, ts)
//        val init    = segs.init
//        val lastTup = segs.last
//        val last    = (lastTup._1, Some(lastTup._2))
//        Definition(precSection, init, last, fin)
//      }
//
//      def apply(
//                 precSection: Option[Pattern],
//                 t1: Segment.Tup,
//                 ts: Segment.Tup*
//               )(
//                 fin: Resolver
//               ): Definition = Definition(precSection, t1, ts.toList)(fin)
//
//      def apply(t1: Segment.Tup, t2_ : Segment.Tup*)(
//        fin: Resolver
//      ): Definition = Definition(None, t1, t2_.toList)(fin)
//
//      def apply(initTups: List[Segment.Tup], lastHead: AST)(
//        fin: Resolver
//      ): Definition =
//        Definition(None, initTups, (lastHead, None), fin)
//
//      def apply(t1: Segment.Tup, last: AST)(fin: Resolver): Definition =
//        Definition(List(t1), last)(fin)
//
//      def apply(
//                 back: Option[Pattern],
//                 initTups: List[Segment.Tup],
//                 lastTup: LastSegment.Tup,
//                 resolver: Resolver
//               ): Definition = {
//        type PP = Pattern => Pattern
//        val applyValidChecker: PP     = _ | ErrTillEnd("unmatched pattern")
//        val applyFullChecker: PP      = _ :: ErrUnmatched("unmatched tokens")
//        val applyDummyFullChecker: PP = _ :: Nothing()
//
//        val unapplyValidChecker: Pattern.Match => Pattern.Match = {
//          case Pattern.Match.Or(_, Left(tgt)) => tgt
//          case _                              => throw new Error("Internal error")
//        }
//        val unapplyFullChecker: Pattern.Match => Pattern.Match = {
//          case Pattern.Match.Seq(_, (tgt, _)) => tgt
//          case _                              => throw new Error("Internal error")
//        }
//        val applySegInitCheckers: List[Segment] => List[Segment] =
//          _.map(_.map(p => applyFullChecker(applyValidChecker(p))))
//
//        val applySegLastCheckers: LastSegment => LastSegment =
//          _.map(p => applyDummyFullChecker(applyValidChecker(p)))
//
//        val unapplySegCheckers
//        : List[AST.Macro.Match.Segment] => List[AST.Macro.Match.Segment] =
//          _.map(_.map({
//            case m @ Pattern.Match.Nothing(_) => m
//            case m =>
//              unapplyValidChecker(unapplyFullChecker(m))
//          }))
//
//        val initSegs           = initTups.map(Segment(_))
//        val lastSeg            = LastSegment(lastTup)
//        val backPatWithCheck   = back.map(applyValidChecker)
//        val initSegsWithChecks = applySegInitCheckers(initSegs)
//        val lastSegWithChecks  = applySegLastCheckers(lastSeg)
//
//        def unexpected(ctx: Resolver.Context, msg: String): AST = {
//          val pfxStream  = ctx.prefix.map(_.toStream).getOrElse(List())
//          val segsStream = ctx.body.flatMap(_.toStream)
//          val stream     = pfxStream ++ segsStream
//          AST.Invalid.Unexpected(msg, stream)
//        }
//
//        def resolverWithChecks(ctx: Resolver.Context) = {
//          val pfxFail  = !ctx.prefix.forall(_.isValid)
//          val segsFail = !ctx.body.forall(_.isValid)
//          if (pfxFail || segsFail) unexpected(ctx, "invalid statement")
//          else {
//            val ctx2 = ctx.copy(
//              prefix = ctx.prefix.map(unapplyValidChecker),
//              body   = unapplySegCheckers(ctx.body)
//            )
//            try resolver(ctx2)
//            catch {
//              case _: Throwable =>
//                unexpected(ctx, "exception during macro resolution")
//            }
//          }
//        }
//        __Definition__(
//          backPatWithCheck,
//          initSegsWithChecks,
//          lastSegWithChecks,
//          resolverWithChecks
//        )
//      }
//
//    }
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//  //// Space-Unaware AST ///////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  sealed trait SpacelessASTOf[T] extends Shape[T]
//
//  //  implicit def ftor:    Functor[SpacelessASTOf]      = semi.functor implicit def fold:    Foldable[SpacelessASTOf]     = semi.foldable
//  //  implicit def ozip[T]: OffsetZip[SpacelessASTOf, T] = _.map((0, _))
//
//  //////////////////////////////////////////////////////////////////////////////
//  /// Comment //////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  type Comment = ASTOf[CommentOf]
//  final case class CommentOf[T](lines: List[String])
//    extends SpacelessASTOf[T]
//      with Phantom
//  object Comment {
//    val any    = UnapplyByType[Comment]
//    val symbol = "#"
//    def apply(lines: List[String]): Comment = ASTOf(CommentOf(lines))
//    def unapply(t: AST): Option[List[String]] =
//      Unapply[Comment].run(t => t.lines)(t)
//  }
//
//  //// Instances ////
//
//  object CommentOf {
//    import Comment._
//    implicit def ftor: Functor[CommentOf]  = semi.functor
//    implicit def fold: Foldable[CommentOf] = semi.foldable
//    implicit def repr[T]: Repr[CommentOf[T]] =
//      R + symbol + symbol + _.lines.mkString("\n")
//    // FIXME: How to make it automatic for non-spaced AST?
//    implicit def ozip[T]: OffsetZip[CommentOf, T] = _.map(Index.Start -> _)
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Documented //////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  type Documented = ASTOf[DocumentedOf]
//  final case class DocumentedOf[T](doc: Doc, emptyLinesBetween: Int, ast: T)
//    extends Shape[T]
//  object Documented {
//    val any = UnapplyByType[Documented]
//    def apply(doc: Doc, emp: Int, ast: AST): Documented =
//      ASTOf(DocumentedOf(doc, emp, ast))
//    def unapply(t: AST): Option[(Doc, Int, AST)] =
//      Unapply[Documented].run(t => (t.doc, t.emptyLinesBetween, t.ast))(t)
//  }
//
//  //// Instances ////
//
//  object DocumentedOf {
//    import Comment.symbol
//    implicit def functor[T]: Functor[DocumentedOf] = semi.functor
//    implicit def repr[T: Repr]: Repr[DocumentedOf[T]] = t => {
//      val symbolRepr        = R + symbol + symbol
//      val betweenDocAstRepr = R + newline + newline.build * t.emptyLinesBetween
//      R + symbolRepr + t.doc + betweenDocAstRepr + t.ast
//    }
//    implicit def offsetZip[T]: OffsetZip[DocumentedOf, T] =
//      _.map(Index.Start -> _)
//
//    implicit def toJson[T]: Encoder[DocumentedOf[T]] =
//      _ => throw new NotImplementedError()
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Import //////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  type Import = ASTOf[ImportOf]
//  final case class ImportOf[T](path: List1[Cons]) extends SpacelessASTOf[T]
//  object Import {
//    def apply(path: List1[Cons]):            Import = ImportOf[AST](path)
//    def apply(head: Cons):                   Import = Import(head, List())
//    def apply(head: Cons, tail: List[Cons]): Import = Import(List1(head, tail))
//    def apply(head: Cons, tail: Cons*):      Import = Import(head, tail.toList)
//    def unapply(t: AST): Option[List1[Cons]] =
//      Unapply[Import].run(t => t.path)(t)
//    val any = UnapplyByType[Import]
//  }
//  object ImportOf {
//    implicit def ftor: Functor[ImportOf]  = semi.functor
//    implicit def fold: Foldable[ImportOf] = semi.foldable
//    implicit def repr[T]: Repr[ImportOf[T]] =
//      t => R + ("import " + t.path.map(_.repr.build()).toList.mkString("."))
//
//    // FIXME: How to make it automatic for non-spaced AST?
//    implicit def ozip[T]: OffsetZip[ImportOf, T] = _.map(Index.Start -> _)
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Mixfix //////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  type Mixfix = ASTOf[MixfixOf]
//  final case class MixfixOf[T](name: List1[Ident], args: List1[T])
//    extends SpacelessASTOf[T]
//
//  object Mixfix {
//    def apply(name: List1[Ident], args: List1[AST]): Mixfix =
//      MixfixOf(name, args)
//    def unapply(t: AST) = Unapply[Mixfix].run(t => (t.name, t.args))(t)
//    val any             = UnapplyByType[Mixfix]
//  }
//  object MixfixOf {
//    implicit def ftor: Functor[MixfixOf]  = semi.functor
//    implicit def fold: Foldable[MixfixOf] = semi.foldable
//    implicit def repr[T: Repr]: Repr[MixfixOf[T]] = t => {
//      val lastRepr = if (t.name.length == t.args.length) List() else List(R)
//      val argsRepr = t.args.toList.map(R + " " + _) ++ lastRepr
//      val nameRepr = t.name.toList.map(Repr(_))
//      R + (nameRepr, argsRepr).zipped.map(_ + _)
//    }
//    // FIXME: How to make it automatic for non-spaced AST?
//    implicit def ozip[T]: OffsetZip[MixfixOf, T] = _.map(Index.Start -> _)
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Group ///////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  type Group = ASTOf[GroupOf]
//  final case class GroupOf[T](body: Option[T]) extends SpacelessASTOf[T]
//  object Group {
//    val any             = UnapplyByType[Group]
//    def unapply(t: AST) = Unapply[Group].run(_.body)(t)
//    def apply(body: Option[AST]): Group = GroupOf(body)
//    def apply(body: AST):         Group = Group(Some(body))
//    def apply(body: SAST):        Group = Group(body.el)
//    def apply():                  Group = Group(None)
//  }
//  object GroupOf {
//    implicit def ftor: Functor[GroupOf]  = semi.functor
//    implicit def fold: Foldable[GroupOf] = semi.foldable
//    implicit def repr[T: Repr]: Repr[GroupOf[T]] =
//      R + "(" + _.body + ")"
//    // FIXME: How to make it automatic for non-spaced AST?
//    implicit def ozip[T]: OffsetZip[GroupOf, T] = _.map(Index.Start -> _)
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Def /////////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  type Def = ASTOf[DefOf]
//  final case class DefOf[T](name: Cons, args: List[T], body: Option[T])
//    extends SpacelessASTOf[T]
//  object Def {
//    val any    = UnapplyByType[Def]
//    val symbol = "def"
//    def apply(name: Cons):                  Def = Def(name, List())
//    def apply(name: Cons, args: List[AST]): Def = Def(name, args, None)
//    def apply(name: Cons, args: List[AST], body: Option[AST]): Def =
//      DefOf(name, args, body)
//    def unapply(t: AST): Option[(Cons, List[AST], Option[AST])] =
//      Unapply[Def].run(t => (t.name, t.args, t.body))(t)
//  }
//  object DefOf {
//    implicit def ftor: Functor[DefOf]  = semi.functor
//    implicit def fold: Foldable[DefOf] = semi.foldable
//    implicit def repr[T: Repr]: Repr[DefOf[T]] =
//      t => R + Def.symbol + 1 + t.name + t.args.map(R + 1 + _) + t.body
//    // FIXME: How to make it automatic for non-spaced AST?
//    implicit def ozip[T]: OffsetZip[DefOf, T] = _.map(Index.Start -> _)
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Foreign /////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  type Foreign = ASTOf[ForeignOf]
//  final case class ForeignOf[T](indent: Int, lang: String, code: List[String])
//    extends SpacelessASTOf[T]
//  object Foreign {
//    def apply(indent: Int, lang: String, code: List[String]): Foreign =
//      Foreign(indent, lang, code)
//    def unapply(t: AST): Option[(Int, String, List[String])] =
//      Unapply[Foreign].run(t => (t.indent, t.lang, t.code))(t)
//    val any = UnapplyByType[Foreign]
//  }
//  object ForeignOf {
//    implicit def ftor: Functor[ForeignOf]  = semi.functor
//    implicit def fold: Foldable[ForeignOf] = semi.foldable
//    implicit def repr[T: Repr]: Repr[ForeignOf[T]] = t => {
//      val code2 = t.code.map(R + t.indent + _).mkString("\n")
//      R + "foreign " + t.lang + "\n" + code2
//    }
//    // FIXME: How to make it automatic for non-spaced AST?
//    implicit def ozip[T]: OffsetZip[ForeignOf, T] = _.map(Index.Start -> _)
//  }
//
//  //// ASTClass ////
//
//  /** [[ASTClass]] implements set of AST operations based on a precise AST
//    * shape. Because the [[T]] parameter in [[ASTOf]] is covariant, we may lose
//    * information about the shape after we construct the AST, thus this instance
//    * is used to cache all necessary operations during AST construction.
//    */
//  sealed trait ASTClass[T[_]] {
//    def repr(t: T[AST]): Repr.Builder
//    def map(t: T[AST])(f: AST => AST): T[AST]
//    def mapWithOff(t: T[AST])(f: (Index, AST) => AST): T[AST]
//    def zipWithOffset(t: T[AST]): T[(Index, AST)]
//    def encode(t: T[AST]): Json
//  }
//  object ASTClass {
//    def apply[T[_]](implicit cls: ASTClass[T]): ASTClass[T] = cls
//    implicit def instance[T[S] <: Shape[S]](
//                                               implicit
//                                               evRepr: Repr[T[AST]],
//                                               evFtor: Functor[T],
//                                               evOzip: OffsetZip[T, AST]
//                                             ): ASTClass[T] =
//      new ASTClass[T] {
//        def repr(t: T[AST]):               Repr.Builder    = evRepr.repr(t)
//        def map(t: T[AST])(f: AST => AST): T[AST]          = Functor[T].map(t)(f)
//        def zipWithOffset(t: T[AST]):      T[(Index, AST)] = OffsetZip(t)
//        def mapWithOff(t: T[AST])(f: (Index, AST) => AST): T[AST] =
//          Functor[T].map(zipWithOffset(t))(f.tupled)
//        def encode(t: T[AST]): Json = {
//          val shapeEncoder = implicitly[Encoder[Shape[AST]]]
//          shapeEncoder(t)
//        }
//      }
//  }

  /////////////////////////////////////////////////
  /////////////////////////////////////////////////
  /////////////////////////////////////////////////
  /////////////////////////////////////////////////

  def main() {
    import io.circe.syntax._
//    import AST.hhh._

//    import conversions._
//
//    val fff1 = AST.Ident.BlankOf[AST](): Ident.BlankOf[AST]
//    val fff3 = ASTOf(fff1): Blank
//    val fff4 = fff3: AST
//
//    println(fff3)
//    println(fff4)
//
    val v1  = Ident.Var("foo")
    val v1_ = v1: AST

    println(v1_.asJson)
//    val opr1 = Ident.Opr("+")
    val v2  = App.Prefix(Ident.Var("x"), 10, Ident.Var("z"))
    val v2_ = v2: AST
//
////    println(v2.asJson)
    println(v2_.asJson)
    println(OffsetZip(v2.shape))

//    v2_.mapWithOff { (ix, a) =>
//      {
//        println(ix)
//        println(a)
//        a
//      }
//    }

    //
//    println(v1_)
//    println(v1.name)
//    println(opr1.assoc)
//
//    val str1 = "foo": AST
//    println(str1)
//
//    val vx = v2: AST
//    vx match {
//      case Ident.Blank.any(v) => println(s"blank: $v")
//      case Ident.Var.any(v)   => println(s"var: $v")
//      case App.Prefix.any(v)  => println(s"app.prefix: $v")
//    }
//
//    println(vx.repr)
//
//    val voff  = App.Infix(Var("x"), 1, Opr("+"), 2, Var("y"))
//    val voff2 = voff: AST
//    voff.traverseWithOff {
//      case (i, t) =>
//        println(s"> $i = $t")
//        t
//    }
//
//    println(voff2.zipWithOffset())
//
//    val v1_x = vx.as[Var]
//    println(v1_x)
  }
}

object TRTT extends scala.App {
  import Shape._

  val ev  = implicitly[Repr[AST.AST]]
  val ast = LineRaw(List(SegmentPlain[AST.AST]("ffoo")))
  val r   = LineRaw.repr(ev).repr(ast)
  println(r.toString)
  //println(ast.repr.toString)
}

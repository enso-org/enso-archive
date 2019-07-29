package org.enso.syntax.text

import org.enso.data.List1
import org.enso.data.Shifted
import org.enso.data.Tree
import org.enso.syntax.text.ast.Repr.R
import org.enso.syntax.text.ast.Repr
import org.enso.syntax.text.ast.opr
import org.enso.syntax.text.ast.text

sealed trait AST extends AST.Symbol

object AST {

  ///////////////////
  //// Reexports ////
  ///////////////////

  type Assoc = opr.Assoc

  val Assoc = opr.Assoc
  val Prec  = opr.Prec

  ////////////////////
  //// Definition ////
  ////////////////////

  trait Symbol extends Repr.Provider {
    def span:   Int    = repr.span
    def show(): String = repr.show()
  }

  /////////////////////
  //// Conversions ////
  /////////////////////

  implicit def fromString(str: String): AST =
    fromStringRaw(str) match {
      case opr: Opr => App.Sides(opr)
      case any      => any
    }

  def fromStringRaw(str: String): AST = {
    if (str == "") throw new Error("Empty literal")
    if (str == "_") Blank
    else if (str.head.isLower) Var(str)
    else if (str.head.isUpper) Cons(str)
    else Opr(str)
  }

  implicit final private class OptAST(val self: Option[AST]) extends Symbol {
    val repr = self.map(_.repr).getOrElse(Repr())
  }

  /////////////////
  //// Invalid ////
  /////////////////

  trait Invalid extends AST

  final case class Unrecognized(str: String) extends Invalid {
    val repr = str
  }

  /////////////////
  //// Literal ////
  /////////////////

  sealed trait Literal extends AST
  sealed trait Ident extends Literal {
    val name: String
  }

  object Ident {
    final case class InvalidSuffix(elem: Ident, suffix: String)
        extends AST.Invalid {
      val repr = R + elem + suffix
    }
    implicit def fromString(str: String): Ident = {
      if (str == "") throw new Error("Empty literal")
      if (str == "_") Blank
      else if (str.head.isLower) Var(str)
      else if (str.head.isUpper) Cons(str)
      else Opr(str)
    }
  }

  ////////////////////////////
  //// Var / Cons / Blank ////
  ////////////////////////////

  final case object Blank extends Ident {
    val name = "_"
    val repr = name
  }

  final case class Var(name: String) extends Ident {
    val repr = name
  }

  final case class Cons(name: String) extends Ident {
    val repr = name
  }

  //////////////////
  //// Operator ////
  //////////////////

  final case class Opr(name: String) extends Opr.Class {
    val (prec, assoc) = Opr.Info.of(name)
    val repr          = name
  }

  object Opr {
    trait Class extends Ident

    final case class Mod(name: String) extends Opr.Class {
      override val repr = name + '='
    }

    val app: Opr = Opr(" ")

    object Info {
      val map: Map[String, (Int, Assoc)] = Prec.map.map {
        case (name, prec) => name -> (prec, Assoc.of(name))
      }
      def of(op: String) =
        map.getOrElse(op, (Prec.default, Assoc.of(op)))
    }

    implicit def fromString(str: String): Opr = Opr(str)
  }

  /////////////
  //// App ////
  /////////////

  final case class App(func: AST, off: Int, arg: AST) extends AST {
    val repr = R + func + off + arg
  }

  object App {
    def apply(func: AST, arg: AST):         App   = App(func, 1, arg)
    def apply(op: Opr, off: Int, arg: AST): Right = Right(op, off, arg)
    def apply(op: Opr, arg: AST):           Right = Right(op, 1, arg)
    def apply(arg: AST, off: Int, op: Opr): Left  = Left(arg, off, op)
    def apply(arg: AST, op: Opr):           Left  = Left(arg, 1, op)
    def apply(
      leftArg: AST,
      leftOff: Int,
      opr: Opr,
      rightOff: Int,
      rightArg: AST
    ): Infix = Infix(leftArg, leftOff, opr, rightOff, rightArg)

    final case class Left(arg: AST, off: Int, op: Opr) extends AST {
      val repr = R + arg + off + op
    }

    final case class Right(opr: Opr, off: Int, arg: AST) extends AST {
      val repr = R + opr + off + arg
    }

    final case class Sides(opr: Opr) extends AST {
      val repr = R + opr
    }

    final case class Infix(
      leftArg: AST,
      leftOff: Int,
      opr: Opr,
      rightOff: Int,
      rightArg: AST
    ) extends AST {
      val repr = R + leftArg + leftOff + opr + rightOff + rightArg
    }
  }

  ////////////////
  //// Mixfix ////
  ////////////////

  final case class Mixfix(segments: Shifted.List1[Mixfix.Segment.Class])
      extends AST {
    val repr = R + segments.map(_.repr)
  }

  object Mixfix {

    final case class Segment[T: Repr.Of](
      tp: Segment.Type[T],
      head: AST,
      body: T
    ) extends Segment.Class {
      val repr = R + head + body
    }

    object Segment {

      def apply(head: AST): Segment[_] = new Segment(Empty(), head, ())
      def apply(head: AST, body: Option[Shifted[AST]]): Segment[_] =
        new Segment(Expr(), head, body)

      sealed trait Class extends Symbol
      type Any = Segment[_]

      trait Type[+T]
      object Type {
        type Any = Type[_]
      }

      final case class Expr() extends Type[Option[Shifted[AST]]]

      case class Empty() extends Type[Unit]
      case class Expr1() extends Type[Shifted[AST]]

      case class Opt[S](el: Type[S])               extends Type[Option[S]]
      case class Many[S](el: Type[S])              extends Type[List[S]]
      case class Many1[S](el: Type[S])             extends Type[List1[S]]
      case class Seq[L, R](l: Type[L], r: Type[R]) extends Type[(L, R)]

//      object Def {
//        trait Selector
//        final case class Many1[T](elems: T) extends Selector
//        final case class Option[T](elem: T) extends Selector
//        final case class
//      }
//      final case class XList1(elems: List1[Selector]) extends Selector
//      final case class XExpr()                        extends Type[Option[Shifted[AST]]]
//      final case class XExpr1()                       extends Type[Shifted[AST]]

      object Empty {
        final case class NonEmpty(head: AST, body: Shifted[AST])
            extends Class
            with Invalid {
          val repr = R + head + body
        }
      }

      object Expr1 {
        case class Empty(head: AST) extends Class with Invalid {
          val repr = R + head
        }
      }
    }

    case class Unmatched(
      segments: Shifted.List1[Unmatched.Segment],
      possiblePaths: Tree[AST, Unit]
    ) extends AST {
      val repr = R + segments.map(_.repr)
    }
    object Unmatched {
      case class Segment(head: AST, body: Option[Shifted[AST]]) extends Symbol {
        val repr = R + head + body
      }
    }

    case class Definition(segments: List1[Definition.Input])
    object Definition {
      type Input = (AST, Segment.Type[_])
      def apply(t1: Input, ts: Input*): Definition =
        Definition(List1(t1, ts.to[List]))
    }
  }

  ////////////////
  //// Number ////
  ////////////////

  final case class Number(base: Option[String], int: String) extends AST {
    val repr = base.map(_ + "_").getOrElse("") + int
  }

  object Number {
    def apply(i: Int):               Number = Number(i.toString)
    def apply(i: String):            Number = Number(None, i)
    def apply(b: String, i: String): Number = Number(Some(b), i)
    def apply(b: Int, i: String):    Number = Number(b.toString, i)
    def apply(b: String, i: Int):    Number = Number(b, i.toString)
    def apply(b: Int, i: Int):       Number = Number(b.toString, i.toString)

    final case class DanglingBase(base: String) extends AST.Invalid {
      val repr = base + '_'
    }
  }

  //////////////
  //// Text ////
  //////////////

  sealed trait Text extends AST
  object Text {

    //// Abstraction ////

    sealed abstract class Class[This](val quoteChar: Char) extends Text {
      type Segment >: Text.Segment.Raw
      val quote: Quote
      val segments: List[Segment]

      val quoteRepr = R + quoteChar.toString * quote.asInt
      val bodyRepr: Repr

      def _dup(quote: Quote, segments: List[Segment]): This
      def dup(quote: Quote = quote, segments: List[Segment] = segments) =
        _dup(quote, segments)

      def prepend(segment: Segment): This =
        this.dup(segments = segment :: segments)

      def prependMergeReversed(segment: Segment): This =
        (segment, segments) match {
          case (Text.Segment.Plain(n), Text.Segment.Plain(t) :: ss) =>
            this.dup(segments = Text.Segment.Plain(t + n) :: ss)
          case _ => this.dup(segments = segment :: segments)
        }
    }

    //// Smart Constructors ////

    private type I = Interpolated
    private val I = Interpolated
    def apply():                        I = I()
    def apply(q: Quote):                I = I(q)
    def apply(q: Quote, s: I.Segment*): I = I(q, s: _*)
    def apply(s: List[I.Segment]):      I = I(s)
    def apply(s: I.Segment*):           I = I(s: _*)

    //// Definition ////

    final case class Interpolated(
      quote: Text.Quote,
      segments: List[Interpolated.Segment]
    ) extends Class[Interpolated]('\'') {
      type Segment = Interpolated.Segment
      val bodyRepr = R + segments
      val repr     = R + quoteRepr + segments + quoteRepr
      def _dup(quote: Quote, segments: List[Segment]): Interpolated =
        copy(quote, segments)
    }

    final case class Raw(quote: Text.Quote, segments: List[Raw.Segment])
        extends Class[Raw]('"') {
      type Segment = Raw.Segment
      val bodyRepr = R + segments
      val repr     = R + quoteRepr + segments + quoteRepr
      def _dup(quote: Quote, segments: List[Segment]) =
        copy(quote, segments)
    }

    object Raw {
      trait Segment extends Text.Interpolated.Segment
    }

    object Interpolated {
      trait Segment extends Text.Segment

      def apply():                      I = I(Quote.Single, Nil)
      def apply(q: Quote):              I = I(q, Nil)
      def apply(q: Quote, s: Segment*): I = I(q, s.to[List])
      def apply(s: List[Segment]):      I = I(Quote.Single, s)
      def apply(s: Segment*):           I = I(s.to[List])
    }

    //// Quote ////

    sealed trait Quote {
      val asInt: Int
    }
    object Quote {
      final case object Single extends Quote { val asInt = 1 }
      final case object Triple extends Quote { val asInt = 3 }
    }

    //// Segment ////

    trait Segment extends Symbol

    object Segment {
      type Raw          = Text.Raw.Segment
      type Interpolated = Text.Interpolated.Segment

      final case class Plain(value: String) extends Raw {
        val repr = value
      }

      final case class Interpolation(value: Option[AST]) extends Interpolated {
        val repr = R + '`' + value + '`'
      }

      trait Escape extends Interpolated
      val Escape = text.Escape

      implicit def fromString(str: String): Segment.Plain = Segment.Plain(str)
    }

    //// Unclosed ////

    final case class Unclosed(text: Class[_]) extends AST.Invalid {
      val repr = R + text.quoteRepr + text.bodyRepr
    }
  }

  ///////////////
  //// Block ////
  ///////////////

  final case class Block(
    indent: Int,
    emptyLines: List[Int],
    firstLine: Block.Line.Required,
    lines: List[Block.Line]
  ) extends AST {
    val repr = {
      val headRepr       = R + '\n'
      val emptyLinesRepr = emptyLines.map(R + indent + _ + "\n")
      val firstLineRepr  = R + indent + firstLine
      val linesRepr      = lines.map(R + '\n' + indent + _)
      headRepr + emptyLinesRepr + firstLineRepr + linesRepr
    }
  }

  object Block {

    final case class InvalidIndentation(block: Block) extends AST.Invalid {
      val repr = R + block
    }

    final case class Line(elem: Option[AST], offset: Int) extends Symbol {
      val repr = R + elem + offset
      def map(f: AST => AST): Line =
        Line(elem.map(f), offset)
    }

    object Line {
      def apply():            Line = Line(None, 0)
      def apply(offset: Int): Line = Line(None, offset)

      final case class Required(elem: AST, offset: Int) extends Symbol {
        val repr = R + elem + offset
        def toOptional: Line =
          Line(Some(elem), offset)
      }
    }
  }

  ////////////////
  //// Module ////
  ////////////////

  import Block.Line
  final case class Module(firstLine: Line, lines: List[Line]) extends AST {
    val repr = R + firstLine + lines.map(R + '\n' + _)

    def map(f: Line => Line): Module =
      Module(f(firstLine), lines.map(f))
  }

  object Module {
    def apply(l: Line):            Module = Module(l, Nil)
    def apply(l: Line, ls: Line*): Module = Module(l, ls.to[List])
  }
}

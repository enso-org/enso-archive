package org.enso.syntax.text

import monocle.macros.GenLens
import org.enso.data.List1
import org.enso.data.Shifted
import org.enso.data.Tree
import org.enso.syntax.text.ast.Repr.R
import org.enso.syntax.text.ast.Repr
import org.enso.syntax.text.ast.opr
import org.enso.syntax.text.ast.text

import scala.reflect.ClassTag

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

  type Stream  = List[Shifted[AST]]
  type Stream1 = List1[Shifted[AST]]

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

//  implicit final private class OptAST(val self: Option[AST]) extends Symbol {
//    val repr = self.map(_.repr).getOrElse(Repr())
//  }

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
    type Class = Ident
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

  /////////////
  //// Opr ////
  /////////////

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
        case (name, prec) => name -> ((prec, Assoc.of(name)))
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

    final case class MissingArgument() extends Invalid {
      val repr = R
    }
  }

  ////////////////
  //// Mixfix ////
  ////////////////

  final case class Template(segments: Shifted.List1[Template.Segment.Class])
      extends AST {
    val repr = R + segments.map(_.repr)
  }

  object Template {

    final case class Segment(head: AST, body: Segment.Body)
        extends Segment.Class {
      val repr = R + head + body
      def strip(): (Segment, AST.Stream) = (this, List())
    }

    object Segment {

      def apply(head: AST): Segment = new Segment(head, Body.Empty)

      //// Segment Types ////

      sealed trait Class extends Symbol {
        def strip(): (Class, AST.Stream)
      }

      case class Unmatched(pat: Pattern, head: AST, stream: AST.Stream)
          extends Class {
        val repr = R + head + stream
        def strip(): (Unmatched, AST.Stream) =
          (Unmatched(pat, head, List()), stream)
      }

      case class Unsaturated(head: AST, body: Body, stream: AST.Stream1)
          extends Class {
        val repr = R + head + body + stream
        def strip(): (Segment, AST.Stream) =
          (Segment(head, body), stream.toList)
      }

      //// Pattern ////

      sealed trait Pattern
      object Pattern {
        case object End                      extends Pattern
        case object Skip                     extends Pattern
        case object AnyToken                 extends Pattern
        case class Opt(pat: Pattern)         extends Pattern
        case class Many(pat: Pattern)        extends Pattern
        case class Seq(pats: List1[Pattern]) extends Pattern
        case class Alt(pats: List1[Pattern]) extends Pattern
        case class Token[T <: AST]()(implicit val tag: ClassTag[T])
            extends Pattern

        object Seq {
          def apply(pat: Pattern, pats: Pattern*): Seq =
            Seq(List1(pat, pats.toList))
        }
      }

      //// Body ////

      sealed trait Body extends Repr.Provider
      object Body {
        private type B = Body

        case object Empty                extends B { val repr = R }
        case class Expr(t: Shifted[AST]) extends B { val repr = Repr.of(t) }
        case class Many(t: List1[B])     extends B { val repr = Repr.of(t) }

        object Many {
          def apply(head: B, tail: List[B]): Many = Many(List1(head, tail))
          def apply(head: B):                Many = Many(List1(head))
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
      type Scoped       = Template.Scoped[Definition]
      type Restricted   = Template.Restricted[Definition]
      type Unrestricted = Template.Unrestricted[Definition]
      type Input        = (AST, Segment.Pattern)
      def apply(t1: Input, ts: Input*): Definition =
        Definition(List1(t1, ts.to[List]))
    }

    sealed trait Scoped[T] { val el: T }
    case class Restricted[T](el: T)   extends Scoped[T]
    case class Unrestricted[T](el: T) extends Scoped[T]
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

    final case class Line(elem: Option[AST], offset: Int)
        extends Symbol
        with Zipper.Has {
      type Zipper[T] = Line.Zipper.Class[T]
      val repr = R + elem + offset
      def map(f: AST => AST): Line =
        Line(elem.map(f), offset)
    }

    object Line {
      def apply():            Line = Line(None, 0)
      def apply(offset: Int): Line = Line(None, offset)
      def apply(elem: AST):   Line = Line(Some(elem), 0)

      final case class Required(elem: AST, offset: Int) extends Symbol {
        val repr = R + elem + offset
        def toOptional: Line = Line(Some(elem), offset)
      }

      //// Zipper ////

      // TODO: Class below should not define `lens` explicitly, it should be
      //       provided under the hood.

      object Zipper {
        implicit class Class[S](val lens: AST.Zipper.Path[S, Line])
            extends AST.Zipper[S, Line] {
          val offset = zipper(Offset(lens))
        }

        case class Offset[S](lens: AST.Zipper.Path[S, Line])
            extends AST.Zipper.Path[Line, Int] {
          val path = GenLens[Line](_.offset).asOptional
        }

      }

    }
  }

  ////////////////
  /// Comments ///
  ////////////////

  final case class Comment(comment: String) extends AST {
    val repr = R + "#" + comment
  }

  object Comment {

    final case class MultiLine(offset: Int, lines: List[String]) extends AST {
      val repr   = R + offset + "#" + lines.mkString("\n " + " "*offset)
    }

  }

  ////////////////
  //// Module ////
  ////////////////

  def intersperse[T](t: T, lst: List[T]): List[T] = lst match {
    case Nil             => Nil
    case s1 :: s2 :: Nil => s1 :: t :: intersperse(t, s2 :: Nil)
    case s1 :: Nil       => s1 :: Nil
  }

  def intersperse2[T](t: T, lst: List1[T]): List1[T] =
    List1(lst.head, lst.tail.flatMap(s => List(t, s)))

  import Block.Line
  final case class Module(lines: List1[Line]) extends AST {
    val repr = R + intersperse2(R + '\n', lines.map(R + _))

    def map(f: Line => Line): Module =
      Module(lines.map(f))
  }

  object Module {
    def apply(l: Line):                 Module = Module(List1(l))
    def apply(l: Line, ls: Line*):      Module = Module(List1(l, ls.to[List]))
    def apply(l: Line, ls: List[Line]): Module = Module(List1(l, ls))

    object Zipper {
      case class Lines() extends AST.Zipper.Path[Module, List1[Line]] {
        val path = GenLens[Module](_.lines).asOptional
      }
      val lines          = zipper(Lines())
      def line(idx: Int) = lines.index(idx)
    }
  }

  ////////////////
  //// Zipper ////
  ////////////////

  trait Zipper[Begin, End]
  object Zipper {

    trait Path[Begin, End] {
      val path: monocle.Optional[Begin, End]
    }

    trait Has { type Zipper[_] }

    trait Provider[Begin, End] {
      type Zipper
      def focus: Path[Begin, End] => Zipper
    }
    object Provider {
      trait Inferred[Begin, End <: Has] extends Provider[Begin, End] {
        type Zipper = End#Zipper[Begin]

      }
      trait Terminated[Begin, End] extends Provider[Begin, End] {
        type Zipper = Terminator[Begin, End]
      }

      implicit def default[S, T]: Terminated[S, T] =
        new Terminated[S, T] {
          val focus = Terminator(_)
        }
    }
  }

  implicit def inferredZipperProvider[S, T <: Zipper.Has](
    implicit ev: Zipper.Path[S, T] => T#Zipper[S]
  ): Zipper.Provider.Inferred[S, T] = new Zipper.Provider.Inferred[S, T] {
    val focus = ev(_)
  }

  def zipper[S, T](
    lens: Zipper.Path[S, T]
  )(implicit ev: Zipper.Provider[S, T]): ev.Zipper =
    ev.focus(lens)

  case class Terminator[S, T](zipper: Zipper.Path[S, T])
      extends AST.Zipper[S, T]

  implicit def ZipperTarget_List1[S, T]
    : Zipper.Provider[S, List1[T]] { type Zipper = List1Target[S, T] } =
    new Zipper.Provider[S, List1[T]] {
      type Zipper = List1Target[S, T]
      def focus = List1Target(_)
    }

  case class List1Target[S, T](lens: AST.Zipper.Path[S, List1[T]])
      extends AST.Zipper[S, List1[T]] {
    def index(
      idx: Int
    )(implicit ev: Zipper.Provider[List1[T], T]): ev.Zipper =
      zipper(List1Zipper[S, T](lens, idx))
  }

  case class List1Zipper[S, T](
    zipper: AST.Zipper.Path[S, List1[T]],
    idx: Int
  ) extends AST.Zipper.Path[List1[T], T] {
    val getOpt = (t: List1[T]) =>
      idx match {
        case 0 => Some(t.head)
        case i => t.tail.lift(i - 1)
      }
    val setOpt = (s: T) =>
      (t: List1[T]) =>
        idx match {
          case 0 => t.copy(head = s)
          case _ =>
            val i = idx - 1
            if ((i >= t.tail.length) || (i < 0)) t
            else {
              val (front, back) = t.tail.splitAt(i)
              val tail2         = front ++ (s :: back.tail)
              List1(t.head, tail2)
            }
        }
    val path = monocle.Optional[List1[T], T](getOpt)(setOpt)
  }

  val z1 = Module.Zipper.lines.index(5).offset.zipper

//  println("-------------")
//  println(z1)

}

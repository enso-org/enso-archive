package org.enso.syntax.text

import cats.implicits._
import monocle.macros.GenLens
import org.enso.data.Compare._
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
  type SAST = Shifted[AST]

  ///////////////////
  //// Reexports ////
  ///////////////////

  type Assoc = opr.Assoc

  val Assoc = opr.Assoc
  val Prec  = opr.Prec

  ////////////////////
  //// Definition ////
  ////////////////////

  type Stream  = List[SAST]
  type Stream1 = List1[SAST]

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

  // FIXME: This is unsafe and should be defined in AST EDSL module.
  def fromStringRaw(str: String): AST = {
    if (str == "") throw new Error("Empty literal")
    if (str == "_") Blank
    else if (str.head.isLower) Var(str)
    else if (str.head.isUpper) Cons(str)
    else Opr(str)
  }

  /////////////////
  //// Invalid ////
  /////////////////

  trait Invalid extends AST

  final case class Unrecognized(str: String) extends Invalid {
    val repr = str
  }

  final case class Unexpected(stream: Stream) extends Invalid {
    val repr = R + stream
  }

  final case object Missing extends Invalid {
    val repr = R
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

    // FIXME: This is unsafe and should be defined in AST EDSL module.
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

  type App = _App
  final case class _App(func: AST, off: Int = 1, arg: AST) extends AST {
    val repr = R + func + off + arg
  }
  object App {
    def apply(func: AST, off: Int = 1, arg: AST): App = _App(func, off, arg)
    def apply(func: AST, arg: AST): App = App(func, 1, arg)
    def unapply(t: App) = Some((t.func, t.arg))

    def apply(op: Opr, off: Int, arg: AST): Right = Right(op, off, arg)
    def apply(op: Opr, arg: AST):           Right = Right(op, 1, arg)

    def apply(arg: AST, off: Int, op: Opr): Left = Left(arg, off, op)
    def apply(arg: AST, op: Opr):           Left = Left(arg, op)

    def apply(larg: AST, loff: Int, opr: Opr, roff: Int, rarg: AST): Infix =
      Infix(larg, loff, opr, roff, rarg)
    def apply(larg: AST, opr: Opr, roff: Int, rarg: AST): Infix =
      Infix(larg, opr, roff, rarg)
    def apply(larg: AST, loff: Int, opr: Opr, rarg: AST): Infix =
      Infix(larg, loff, opr, rarg)
    def apply(larg: AST, opr: Opr, rarg: AST): Infix =
      Infix(larg, opr, rarg)

    type Left = _Left
    final case class _Left(arg: AST, off: Int = 0, op: Opr) extends AST {
      val repr = R + arg + off + op
    }
    object Left {
      def apply(arg: AST, off: Int, op: Opr): Left = _Left(arg, off, op)
      def apply(arg: AST, op: Opr):           Left = Left(arg, 1, op)
      def unapply(t: Left) = Some((t.arg, t.op))
    }

    type Right = _Right
    final case class _Right(opr: Opr, off: Int = 0, arg: AST) extends AST {
      val repr = R + opr + off + arg
    }
    object Right {
      def apply(opr: Opr, off: Int, arg: AST): Right = _Right(opr, off, arg)
      def apply(opr: Opr, arg: AST):           Right = Right(opr, 1, arg)
      def unapply(t: Right) = Some((t.opr, t.arg))
    }

    final case class Sides(opr: Opr) extends AST {
      val repr = R + opr
    }

    type Infix = _Infix
    final case class _Infix(
      larg: AST,
      loff: Int = 1,
      opr: Opr,
      roff: Int = 1,
      rarg: AST
    ) extends AST {
      val repr = R + larg + loff + opr + roff + rarg
    }
    object Infix {
      def apply(larg: AST, loff: Int, opr: Opr, roff: Int, rarg: AST): Infix =
        _Infix(larg, loff, opr, roff, rarg)
      def apply(larg: AST, opr: Opr, roff: Int, rarg: AST): Infix =
        Infix(larg, 1, opr, roff, rarg)
      def apply(larg: AST, loff: Int, opr: Opr, rarg: AST): Infix =
        Infix(larg, loff, opr, 1, rarg)
      def apply(larg: AST, opr: Opr, rarg: AST): Infix =
        _Infix(larg, 1, opr, 1, rarg)
      def unapply(t: Infix) = Some((t.larg, t.opr, t.rarg))
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

    trait Class[This] extends Text {
      type Segment <: Text.Segment

      val quoteChar: Char
      val quote: Quote
      val segments: List[Segment]

      lazy val quoteRepr = R + (quoteChar.toString * quote.asInt)
      lazy val bodyRepr  = R + segments
      lazy val repr      = R + quoteRepr + segments + quoteRepr

      def _dup(quote: Quote, segments: List[Segment]): This
      def dup(quote: Quote = quote, segments: List[Segment] = segments) =
        _dup(quote, segments)

      def prepend(segment: Segment): This =
        this.dup(segments = segment :: segments)

      def prependMergeReversed(
        segment: Segment
      )(implicit p: Text.Segment.Raw <:< Segment): This =
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

    import Segment._

    final case class Interpolated(
      quote: Text.Quote,
      segments: List[Interpolated.Segment]
    ) extends Class[Interpolated] {
      type Segment = Interpolated.Segment
      val quoteChar = '\''
      def _dup(quote: Quote, segments: List[Segment]): Interpolated =
        copy(quote, segments)

      def raw: Text.Raw =
        Raw(quote, segments.map(s => Segment.Plain(s.repr.show())))
    }

    final case class Raw(quote: Text.Quote, segments: List[Raw.Segment])
        extends Class[Raw] {
      type Segment = Raw.Segment
      val quoteChar = '"'
      def _dup(quote: Quote, segments: List[Segment]) =
        copy(quote, segments)
    }

    final case class MultiLine(
      indent: Int,
      quoteChar: Char,
      quote: Text.Quote,
      segments: List[Segment]
    ) extends Class[MultiLine] {
      type Segment = Text.Segment
      override lazy val bodyRepr = R + segments.flatMap {
          case EOL(true) => List(EOL(), Plain(" " * indent))
          case s         => List(s)
        }

      def _dup(quote: Quote, segments: List[Segment]) =
        copy(indent, quoteChar, quote, segments)
    }

    object MultiLine {

      def stripOffset(
        offset: Int,
        rawSegments: List[Segment]
      ): List[Segment] = {
        if (rawSegments.isEmpty) return rawSegments
        var last = rawSegments.head
        for (s <- rawSegments.tail :+ EOL()) yield (last, s) match {
          case (EOL(_), segment) if offset == 0 =>
            last = segment
            EOL()
          case (EOL(_), Plain(txt))
              if txt.takeWhile(_ == ' ').length >= offset =>
            last = Plain(txt.drop(offset))
            EOL()
          case (EOL(_), segment) =>
            last = segment
            EOL(validIndent = false)
          case (_, segment) => {
            val prev = last
            last = segment
            prev
          }
        }
      }
    }

    object Raw {
      trait Segment extends Text.Interpolated.Segment

      def apply():                      Raw = Raw(Quote.Single, Nil)
      def apply(q: Quote):              Raw = Raw(q, Nil)
      def apply(q: Quote, s: Segment*): Raw = Raw(q, s.to[List])
      def apply(s: List[Segment]):      Raw = Raw(Quote.Single, s)
      def apply(s: Segment*):           Raw = Raw(s.to[List])
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

      final case class EOL(validIndent: Boolean = true) extends Raw {
        val repr = "\n"
      }

      final case class Interpolation(value: Option[AST]) extends Interpolated {
        val repr = R + '`' + value + '`'
      }

      trait Escape extends Interpolated
      val Escape = text.Escape

      implicit def fromString(str: String): Plain = Plain(str)
    }

    //// Unclosed ////

    final case class Unclosed(text: Class[_]) extends AST.Invalid {
      val repr = R + text.quoteRepr + text.bodyRepr
    }
  }

  ///////////////
  //// Block ////
  ///////////////

  type Block = _Block
  final case class _Block(
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
    def apply(
      indent: Int,
      emptyLines: List[Int],
      firstLine: Line.Required,
      lines: List[Line]
    ): Block = _Block(indent, emptyLines, firstLine, lines)

    def apply(indent: Int, firstLine: Line.Required, lines: List[Line]): Block =
      Block(indent, List(), firstLine, lines)

    def apply(indent: Int, firstLine: AST, lines: AST*): Block =
      Block(indent, Line.Required(firstLine), lines.toList.map(Line(_)))

    def unapply(t: Block): Option[(Int, Line.Required, List[Line])] =
      Some((t.indent, t.firstLine, t.lines))

    final case class InvalidIndentation(block: Block) extends AST.Invalid {
      val repr = R + block
    }

    type Line = _Line
    final case class _Line(elem: Option[AST], off: Int)
        extends Symbol
        with Zipper.Has {
      type Zipper[T] = Line.Zipper.Class[T]
      val repr = R + elem + off
      def map(f: AST => AST): Line =
        _Line(elem.map(f), off)
    }
    object Line {
      def apply(elem: Option[AST], off: Int): Line = _Line(elem, off)
      def apply(elem: Option[AST]):           Line = Line(elem, 0)
      def apply(elem: AST):                   Line = Line(Some(elem))
      def apply(off: Int):                    Line = Line(None, off)
      def apply():                            Line = Line(None, 0)

      type Required = _Required
      final case class _Required(elem: AST, off: Int) extends Symbol {
        val repr = R + elem + off
        def toOptional: Line =
          Line(Some(elem), off)
      }
      object Required {
        def apply(elem: AST, off: Int): Required    = _Required(elem, off)
        def apply(elem: AST):           Required    = Required(elem, 0)
        def unapply(t: Required):       Option[AST] = Some(t.elem)
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
          val path = GenLens[Line](_.off).asOptional
        }

      }

    }
  }

  //////////////////
  //// Template ////
  //////////////////

  trait Template extends AST {}
  object Template {

    final case class Valid(segments: Shifted.List1[Segment.Valid])
        extends Template {
      val repr = R + segments.map(_.repr)

      def path(): List1[AST] =
        segments.toList1.map(_.el.head)
    }

    final case class Invalid(segments: Shifted.List1[Segment])
        extends Template {
      val repr = R + segments.map(_.repr)
    }

    case class Partial(
      segments: Shifted.List1[Partial.Segment],
      possiblePaths: Tree[AST, Unit]
    ) extends Template {
      val repr = R + segments.map(_.repr)
    }
    object Partial {
      case class Segment(head: AST, body: Option[SAST]) extends Symbol {
        val repr = R + head + body
      }
    }

    def validate(
      segments: Shifted.List1[Segment]
    ): Option[Shifted.List1[Segment.Valid]] = {
      val segList = segments.toList().map { t =>
        t.el match {
          case s: Segment.Valid => Some(Shifted(t.off, s))
          case _                => None
        }
      }
      segList.sequence.map(s => Shifted.List1.fromListDropHead(s))
    }

    sealed trait Segment extends Symbol {
      def strip(): (Segment, AST.Stream)
    }
    object Segment {

      //// Segment Types ////

      final case class Valid(head: AST, body: Segment.Body) extends Segment {
        val repr = R + head + body
        def strip(): (Segment.Valid, AST.Stream) = (this, List())
      }
      object Valid {
        def apply(head: AST): Valid = new Valid(head, Body.Empty)
      }

      case class Unmatched(pat: Pattern, head: AST, stream: AST.Stream)
          extends Segment {
        val repr = R + head + stream
        def strip(): (Unmatched, AST.Stream) =
          (Unmatched(pat, head, List()), stream)
      }

      case class Unsaturated(head: AST, body: Body, stream: AST.Stream1)
          extends Segment {
        val repr = R + head + body + stream
        def strip(): (Segment.Valid, AST.Stream) =
          (Segment.Valid(head, body), stream.toList)
      }

      //// Pattern ////

      sealed trait Pattern
      object Pattern {
        case object End                      extends Pattern
        case object Skip                     extends Pattern
        case object AnyToken                 extends Pattern
        case object TokenList                extends Pattern
        case class Opt(pat: Pattern)         extends Pattern
        case class Many(pat: Pattern)        extends Pattern
        case class Seq(pats: List1[Pattern]) extends Pattern
        case class Alt(pats: List1[Pattern]) extends Pattern
        case class Token[T <: AST]()(implicit val tag: ClassTag[T])
            extends Pattern
        case class NotToken[T <: AST]()(implicit val tag: ClassTag[T])
            extends Pattern

        object Seq {
          def apply(pat: Pattern, pats: Pattern*): Seq =
            Seq(List1(pat, pats.toList))
        }
      }

      //// Body ////

      sealed trait Body extends Repr.Provider {
        def toStream(): AST.Stream
      }
      object Body {

        case object Empty extends Body {
          val repr       = R
          def toStream() = List()
        }

        case class Expr(t: SAST) extends Body {
          val repr       = Repr.of(t)
          def toStream() = List(t)
        }

        case class Many(t: List[Body]) extends Body {
          val repr       = Repr.of(t)
          def toStream() = t.flatMap(_.toStream)
        }
//
//        object Many {
//          def apply(head: Body, tail: List[Body]): Many =
//            Many(List1(head, tail))
//          def apply(head: Body): Many = Many(List1(head))
//        }
      }
    }

    //// Definition ////

    case class Definition(
      segments: Definition.Input
    )
    object Definition {
      type Input     = List1[Segment]
      type Segment   = (AST, Segment.Pattern)
      type Finalizer = List[Shifted[Template.Segment.Valid]] => AST

      case class Spec[T](scope: Scope, finalizer: Finalizer, el: T) {
        def map[S](fn: T => S): Spec[S] = Spec(scope, finalizer, fn(el))
      }

      def Restricted[T](t1: Segment, ts: Segment*)(fin: Finalizer) =
        Spec(Scope.Restricted, fin, Definition(List1(t1, ts: _*)))

      def Unrestricted[T](t1: Segment, ts: Segment*)(fin: Finalizer) =
        Spec(Scope.Unrestricted, fin, Definition(List1(t1, ts: _*)))

      trait Scope
      object Scope {
        case object Restricted   extends Scope
        case object Unrestricted extends Scope
      }

    }
  }

  ///////////////
  //// Group ////
  ///////////////

  type Group = _Group
  final case class _Group(
    loff: Int         = 0,
    body: Option[AST] = None,
    roff: Int         = 0
  ) extends AST {
    val repr = R + loff + body + roff
  }
  object Group {
    def apply(loff: Int, body: Option[AST], roff: Int) =
      _Group(loff, body, roff)
    def apply(loff: Int, body: AST, roff: Int): Group =
      Group(loff, Some(body), roff)
    def apply(loff: Int, body: Option[AST]): Group = Group(loff, body, 0)
    def apply(loff: Int, body: AST):         Group = Group(loff, Some(body), 0)
    def apply(body: Option[AST], roff: Int): Group = Group(0, body, roff)
    def apply(body: AST, roff: Int):         Group = Group(0, Some(body), roff)
    def apply(body: Option[AST]):            Group = Group(0, body, 0)
    def apply(body: AST):                    Group = Group(0, Some(body), 0)
    def apply(loff: Int):                    Group = Group(loff, None, 0)
    def apply():                             Group = Group(0, None, 0)
    def unapply(t: Group) = Some(t.body)
  }

  /////////////
  //// Def ////
  /////////////

  type Def = _Def
  case class _Def(
    nameOff: Int = 1,
    name: AST,
    argsOff: List[Int] = List(),
    args: List[AST],
    bodyOff: Int = 1,
    body: Option[AST]
  ) extends AST {
    val repr = "type" + nameOff + name + argsOff.zip(args) + bodyOff + body
  }

  object Def {
    def apply(
      nameOff: Int,
      name: AST,
      argsOff: List[Int],
      args: List[AST],
      bodyOff: Int,
      body: Option[AST]
    ): Def = _Def(nameOff, name, argsOff, args, bodyOff, body)
    def apply(name: AST, args: List[AST], body: Option[AST]): Def = {
      val nameOff = 1
      val argsOff = prepOffList(List(), args.length)
      val bodyOff = 0
      Def(nameOff, name, argsOff, args, bodyOff, body)
    }
    def apply(name: SAST, args: AST.Stream, body: Option[SAST]): Def = {
      val bodyOff = body.map(_.off).getOrElse(0)
      val bodyEl  = body.map(_.el)
      Def(name.off, name.el, args.map(_.off), args.map(_.el), bodyOff, bodyEl)
    }
    def unapply(t: Def): Option[(AST, List[AST], Option[AST])] =
      Some((t.name, t.args, t.body))

    def prepOffList(offs: List[Int], argCount: Int): List[Int] =
      compare(offs.length, argCount) match {
        case EQ => offs
        case LT => offs ++ List.fill(argCount - offs.length)(1)
        case GT => offs.take(argCount)
      }
  }

  ////////////////
  /// Comments ///
  ////////////////

  final case class Comment(comment: String) extends AST {
    val repr = R + "#" + comment
  }

  object Comment {

    final case class Block(offset: Int, lines: List[String]) extends AST {
      val repr   = R + offset + "#" + lines.mkString("\n " + " "*offset)
    }

  }


  ////////////////
  //// Module ////
  ////////////////

  import Block.Line

  final case class Module(lines: List1[Line]) extends AST {
    val repr = R + lines.head + lines.tail.map(R + '\n' + _)

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

  /**
    * This is just a stub implementation. It shows how zippers could be
    * implemented for AST.
    */
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
}

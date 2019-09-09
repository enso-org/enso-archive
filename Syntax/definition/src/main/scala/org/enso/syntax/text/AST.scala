package org.enso.syntax.text

//import monocle.macros.GenLens
//import org.enso.data.List1._
//import org.enso.data.List1
//import org.enso.data.Shifted
//import org.enso.data.Tree
//import org.enso.syntax.text.ast.Repr.R
//import org.enso.syntax.text.ast.Repr
//import org.enso.syntax.text.ast.opr
//import org.enso.syntax.text.ast.text
//import java.util.UUID
//
//import scala.annotation.tailrec
//import cats.implicits._
//
//import scala.reflect.ClassTag
//
//sealed trait AST_Type2 extends AST.Symbol {
//  import AST._
//
//  val id: Option[ID]
//  def setID(id: ID): AST
//  def withNewID(): AST = this.setID(UUID.randomUUID())
//  def map(f: AST => AST): AST
//  def mapWithOff(f: (Int, AST) => AST): AST
//
//  def traverseWithOff(f: (Int, AST) => AST): AST = {
//    def go(i: Int, t: AST): AST = {
//      t.mapWithOff { (j, ast) =>
//        val off = i + j
//        go(off, f(off, ast))
//      }
//    }
//    go(0, this)
//  }
//
//  def isValid: Boolean = this match {
//    case _: Invalid => false
//    case _          => true
//  }
//}
//
//object AST {
//
//  type AST      = AST_Type
//  type AST_Type = AST_Type2
//  type ID       = UUID
//
//  object implicits extends Ident.implicits {
//    implicit def stringToAST(str: String): AST = {
//      if (str == "") throw new Error("Empty literal")
//      if (str == "_") Blank()
//      else if (str.head.isLower) Var(str)
//      else if (str.head.isUpper) Cons(str)
//      else Opr(str)
//    }
//  }
//
//  def offMapStream(stream: AST.Stream, f: (Int, AST) => AST): AST.Stream = {
//    var off = 0
//    stream.map { t =>
//      off += t.off
//      val out = t.copy(el = f(off, t.el))
//      off += t.el.span
//      out
//    }
//  }
//
//  sealed trait UnapplyByType[T] {
//    def unapply(t: AST): Option[T]
//  }
//  object UnapplyByType {
//    def apply[T](implicit ev: UnapplyByType[T]) = ev
//    implicit def inst[T](implicit ct: ClassTag[T]): UnapplyByType[T] =
//      new UnapplyByType[T] {
//        def unapply(t: AST) = ct.unapply(t)
//      }
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Reexports ///////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  type Assoc = opr.Assoc
//
//  val Assoc = opr.Assoc
//  val Prec  = opr.Prec
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Definition //////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  type SAST    = Shifted[AST]
//  type Stream  = List[SAST]
//  type Stream1 = List1[SAST]
//
//  sealed trait Symbol extends Repr.Provider {
//    def byteSpan: Int    = repr.byteSpan
//    def span:     Int    = repr.span
//    def show():   String = repr.build()
//  }
//
////  implicit def reprForAST: Repr.Of[AST] = _.repr
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Conversions /////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  def tokenize(ast: AST): Shifted.List1[AST] = {
//    @tailrec
//    def go(ast: AST, out: AST.Stream): Shifted.List1[AST] = ast match {
//      case _App(fn, off, arg, _) => go(fn, Shifted(off, arg) :: out)
//      case anyAst                => Shifted.List1(anyAst, out)
//    }
//    go(ast, List())
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Invalid /////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  trait Invalid extends AST {
//    val id                               = None
//    def setID(newID: ID)                 = this
//    def mapWithOff(f: (Int, AST) => AST) = this
//  }
//  object Invalid {
//    type Unrecognized = AST.Unrecognized
//    val Unrecognized = AST.Unrecognized
//  }
//
//  final case class Unrecognized(str: String) extends Invalid {
//    val repr               = str
//    def map(f: AST => AST) = this
//  }
//
//  final case class Unexpected(
//    msg: String,
//    stream: Stream
//  ) extends Invalid {
//    val repr = R + stream
//    def map(f: AST => AST) =
//      copy(stream = stream.map(t => t.copy(el = f(t.el))))
//    override def mapWithOff(f: (Int, AST) => AST) =
//      copy(stream = offMapStream(stream, f))
//
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Literal /////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  sealed trait Literal extends AST
//
//  sealed trait Ident extends Literal {
//    val name: String
//  }
//  object Ident {
//    final case class InvalidSuffix(
//      elem: Ident,
//      suffix: String
//    ) extends AST.Invalid {
//      val repr               = R + elem + suffix
//      def map(f: AST => AST) = this
//    }
//
//    trait implicits extends Var.implicits with Cons.implicits {
//      implicit def stringToIdent(str: String): Ident = {
//        if (str == "") throw new Error("Empty literal")
//        if (str == "_") Blank()
//        else if (str.head.isLower) Var(str)
//        else if (str.head.isUpper) Cons(str)
//        else Opr(str)
//      }
//    }
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Ident ///////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  final case class Blank(id: Option[ID] = None) extends Ident {
//    val name                             = "_"
//    val repr                             = name
//    def setID(newID: ID)                 = copy(id = Some(newID))
//    def map(f: AST => AST)               = this
//    def mapWithOff(f: (Int, AST) => AST) = this
//  }
//
//  final case class Var(name: String, id: Option[ID] = None) extends Ident {
//    val repr                             = name
//    def setID(newID: ID)                 = copy(id = Some(newID))
//    def map(f: AST => AST)               = this
//    def mapWithOff(f: (Int, AST) => AST) = this
//  }
//  object Var {
//    trait implicits {
//      implicit def stringToVar(str: String): Var = Var(str)
//    }
//  }
//
//  type Cons = _Cons
//  final case class _Cons(name: String, id: Option[ID] = None) extends Ident {
//    val repr                             = name
//    def setID(newID: ID)                 = copy(id = Some(newID))
//    def map(f: AST => AST)               = this
//    def mapWithOff(f: (Int, AST) => AST) = this
//  }
//  object Cons {
//    val any = UnapplyByType[Cons]
//    def apply(name: String): Cons           = _Cons(name)
//    def unapply(t: Cons):    Option[String] = Some(t.name)
//    trait implicits {
//      implicit def stringToCons(str: String): Cons = Cons(str)
//    }
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Opr /////////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  type Opr = _Opr
//  final case class _Opr(name: String, id: Option[ID] = None) extends Opr.Class {
//    val (prec, assoc)                    = Opr.Info.of(name)
//    val repr                             = name
//    def setID(newID: ID)                 = copy(id = Some(newID))
//    def map(f: AST => AST)               = this
//    def mapWithOff(f: (Int, AST) => AST) = this
//  }
//  object Opr {
//    val any = UnapplyByType[Opr]
//    def apply(name: String): Opr            = _Opr(name)
//    def unapply(t: Opr):     Option[String] = Some(t.name)
//
//    sealed trait Class extends Ident
//
//    final case class Mod(name: String, id: Option[ID] = None)
//        extends Opr.Class {
//      override val repr                    = name + '='
//      def setID(newID: ID)                 = copy(id = Some(newID))
//      def map(f: AST => AST)               = this
//      def mapWithOff(f: (Int, AST) => AST) = this
//    }
//
//    val app: Opr = Opr(" ")
//
//    object Info {
//      val map: Map[String, (Int, Assoc)] = Prec.map.map {
//        case (name, prec) => name -> ((prec, Assoc.of(name)))
//      }
//      def of(op: String) =
//        map.getOrElse(op, (Prec.default, Assoc.of(op)))
//    }
//
//    implicit def fromString(str: String): Opr = Opr(str)
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// App /////////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  type App = _App
//  final case class _App(
//    func: AST,
//    off: Int = 1,
//    arg: AST,
//    id: Option[ID] = None
//  ) extends AST {
//    val repr               = R + func + off + arg
//    def setID(newID: ID)   = copy(id = Some(newID))
//    def map(f: AST => AST) = copy(func = f(func), arg = f(arg))
//    def mapWithOff(f: (Int, AST) => AST) =
//      copy(func = f(0, func), arg = f(func.span + off, arg))
//  }
//  object App {
//    val any = UnapplyByType[App]
//
//    def apply(func: AST, off: Int = 1, arg: AST): App = _App(func, off, arg)
//    def apply(func: AST, arg: AST): App = App(func, 1, arg)
//    def unapply(t: App) = Some((t.func, t.arg))
//
//    def apply(op: Opr, off: Int, arg: AST): Right = Right(op, off, arg)
//    def apply(op: Opr, arg: AST):           Right = Right(op, 1, arg)
//
//    def apply(arg: AST, off: Int, op: Opr): Left = Left(arg, off, op)
//    def apply(arg: AST, op: Opr):           Left = Left(arg, op)
//
//    def apply(larg: AST, loff: Int, opr: Opr, roff: Int, rarg: AST): Infix =
//      Infix(larg, loff, opr, roff, rarg)
//    def apply(larg: AST, opr: Opr, roff: Int, rarg: AST): Infix =
//      Infix(larg, opr, roff, rarg)
//    def apply(larg: AST, loff: Int, opr: Opr, rarg: AST): Infix =
//      Infix(larg, loff, opr, rarg)
//    def apply(larg: AST, opr: Opr, rarg: AST): Infix =
//      Infix(larg, opr, rarg)
//
//    object Section {
//      type Left  = App.Left
//      type Right = App.Right
//      type Sides = App.Sides
//
//      val Left  = App.Left
//      val Right = App.Right
//      val Sides = App.Sides
//    }
//
//    val Prefix = App
//
//    type Left = _Left
//    final case class _Left(
//      arg: AST,
//      off: Int = 0,
//      opr: Opr,
//      id: Option[ID] = None
//    ) extends AST {
//      val repr                             = R + arg + off + opr
//      def setID(newID: ID)                 = copy(id = Some(newID))
//      def map(f: AST => AST)               = copy(arg = f(arg))
//      def mapWithOff(f: (Int, AST) => AST) = copy(arg = f(0, arg)) // x
//    }
//
//    object Left {
//      val any = UnapplyByType[Left]
//      def apply(arg: AST, off: Int, op: Opr): Left = _Left(arg, off, op)
//      def apply(arg: AST, op: Opr):           Left = Left(arg, 1, op)
//      def unapply(t: Left) = Some((t.arg, t.opr))
//    }
//
//    type Right = _Right
//    final case class _Right(
//      opr: Opr,
//      off: Int = 0,
//      arg: AST,
//      id: Option[ID] = None
//    ) extends AST {
//      val repr                             = R + opr + off + arg
//      def setID(newID: ID)                 = copy(id = Some(newID))
//      def map(f: AST => AST)               = copy(arg = f(arg))
//      def mapWithOff(f: (Int, AST) => AST) = copy(arg = f(opr.span + off, arg))
//    }
//    object Right {
//      val any = UnapplyByType[Right]
//      def apply(opr: Opr, off: Int, arg: AST): Right = _Right(opr, off, arg)
//      def apply(opr: Opr, arg: AST):           Right = Right(opr, 1, arg)
//      def unapply(t: Right) = Some((t.opr, t.arg))
//    }
//
//    final case class Sides(opr: Opr, id: Option[ID] = None) extends AST {
//      val repr                             = R + opr
//      def setID(newID: ID)                 = copy(id = Some(newID))
//      def map(f: AST => AST)               = this
//      def mapWithOff(f: (Int, AST) => AST) = this
//    }
//    object Sides {
//      val any = UnapplyByType[Sides]
//    }
//
//    type Infix = _Infix
//    final case class _Infix(
//      larg: AST,
//      loff: Int = 1,
//      opr: Opr,
//      roff: Int = 1,
//      rarg: AST,
//      id: Option[ID] = None
//    ) extends AST {
//      val repr               = R + larg + loff + opr + roff + rarg
//      def setID(newID: ID)   = copy(id = Some(newID))
//      def map(f: AST => AST) = copy(larg = f(larg), rarg = f(rarg))
//      def mapWithOff(f: (Int, AST) => AST) = copy(
//        larg = f(0, larg),
//        rarg = f(larg.span + loff + opr.span + roff, rarg)
//      )
//
//    }
//    object Infix {
//      def apply(larg: AST, loff: Int, opr: Opr, roff: Int, rarg: AST): Infix =
//        _Infix(larg, loff, opr, roff, rarg)
//      def apply(larg: AST, opr: Opr, roff: Int, rarg: AST): Infix =
//        Infix(larg, 1, opr, roff, rarg)
//      def apply(larg: AST, loff: Int, opr: Opr, rarg: AST): Infix =
//        Infix(larg, loff, opr, 1, rarg)
//      def apply(larg: AST, opr: Opr, rarg: AST): Infix =
//        _Infix(larg, 1, opr, 1, rarg)
//      def unapply(t: Infix) = Some((t.larg, t.opr, t.rarg))
//    }
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Number //////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  final case class Number(
//    base: Option[String],
//    int: String,
//    id: Option[ID] = None
//  ) extends AST {
//    val repr                             = base.map(_ + "_").getOrElse("") + int
//    def setID(newID: ID)                 = copy(id = Some(newID))
//    def map(f: AST => AST)               = this
//    def mapWithOff(f: (Int, AST) => AST) = this
//  }
//
//  object Literal {
//    type Number = AST.Number
//    val Number = AST.Number
//  }
//
//  object Number {
//    def apply(i: Int):               Number = Number(i.toString)
//    def apply(i: String):            Number = Number(None, i)
//    def apply(b: String, i: String): Number = Number(Some(b), i)
//    def apply(b: Int, i: String):    Number = Number(b.toString, i)
//    def apply(b: String, i: Int):    Number = Number(b, i.toString)
//    def apply(b: Int, i: Int):       Number = Number(b.toString, i.toString)
//
//    final case class DanglingBase(base: String) extends AST.Invalid {
//      val repr               = base + '_'
//      def map(f: AST => AST) = this
//    }
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Text ////////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  sealed trait Text extends AST
//  object Text {
//
//    //// Abstraction ////
//
//    sealed trait Class[This] extends Text {
//      type Segment <: Text.Segment
//
//      val quoteChar: Char
//      val quote: Quote
//      val segments: List[Segment]
//
//      lazy val quoteRepr = R + (quoteChar.toString * quote.asInt)
//      lazy val bodyRepr  = R + segments
//      lazy val repr      = R + quoteRepr + bodyRepr + quoteRepr
//
//      def map(f: AST => AST)               = this
//      def mapWithOff(f: (Int, AST) => AST) = this
//
//      def _dup(quote: Quote, segments: List[Segment]): This
//      def dup(quote: Quote = quote, segments: List[Segment] = segments) =
//        _dup(quote, segments)
//
//      def prepend(segment: Segment): This =
//        this.dup(segments = segment :: segments)
//
//      def prependMergeReversed(
//        segment: Segment
//      )(implicit p: Text.Segment.Raw <:< Segment): This =
//        (segment, segments) match {
//          case (Text.Segment.Plain(n), Text.Segment.Plain(t) :: ss) =>
//            this.dup(segments = Text.Segment.Plain(t + n) :: ss)
//          case _ => this.dup(segments = segment :: segments)
//        }
//
//    }
//
//    //// Smart Constructors ////
//
//    private type I = Interpolated
//    private val I = Interpolated
//    def apply():                        I = I()
//    def apply(q: Quote):                I = I(q)
//    def apply(q: Quote, s: I.Segment*): I = I(q, s: _*)
//    def apply(s: List[I.Segment]):      I = I(s)
//    def apply(s: I.Segment*):           I = I(s: _*)
//
//    //// Definition ////
//
//    import Segment._
//
//    final case class Interpolated(
//      quote: Text.Quote,
//      segments: List[Interpolated.Segment],
//      id: Option[ID] = None
//    ) extends Class[Interpolated] {
//      type Segment = Interpolated.Segment
//      val quoteChar        = '\''
//      def setID(newID: ID) = copy(id = Some(newID))
//      def _dup(quote: Quote, segments: List[Segment]): Interpolated =
//        copy(quote, segments)
//
//      def raw: Text.Raw =
//        Raw(quote, segments.map(s => Segment.Plain(s.repr.build())))
//    }
//
//    final case class Raw(
//      quote: Text.Quote,
//      segments: List[Raw.Segment],
//      id: Option[ID] = None
//    ) extends Class[Raw] {
//      type Segment = Raw.Segment
//      val quoteChar        = '"'
//      def setID(newID: ID) = copy(id = Some(newID))
//      def _dup(quote: Quote, segments: List[Segment]) =
//        copy(quote, segments)
//    }
//
//    // FIXME: Rethink if we should divide text to single line and multiline.
//    //        One of segments is EOL, which makes no sense with this division
//    final case class MultiLine(
//      indent: Int,
//      quoteChar: Char,
//      quote: Text.Quote,
//      segments: List[Segment],
//      id: Option[ID] = None
//    ) extends Class[MultiLine] {
//      type Segment = Text.Segment
//      def setID(newID: ID) = copy(id = Some(newID))
//      override lazy val bodyRepr = R + segments.flatMap {
//          case EOL(true) => List(EOL(), Plain(" " * indent))
//          case s         => List(s)
//        }
//
//      def _dup(quote: Quote, segments: List[Segment]) =
//        copy(indent, quoteChar, quote, segments)
//    }
//
//    object MultiLine {
//      def stripOffset(
//        offset: Int,
//        rawSegments: List[Segment]
//      ): List[Segment] = {
//        if (rawSegments.isEmpty) return rawSegments
//        var last = rawSegments.head
//        for (s <- rawSegments.tail :+ EOL()) yield (last, s) match {
//          case (EOL(_), segment) if offset == 0 =>
//            last = segment
//            EOL()
//          case (EOL(_), Plain(txt))
//              if txt.takeWhile(_ == ' ').length >= offset =>
//            last = Plain(txt.drop(offset))
//            EOL()
//          case (EOL(_), segment) =>
//            last = segment
//            EOL(validIndent = false)
//          case (_, segment) =>
//            val prev = last
//            last = segment
//            prev
//        }
//      }
//    }
//
//    object Raw {
//      sealed trait Segment extends Text.Interpolated.Segment
//      def apply():                      Raw = Raw(Quote.Single, Nil)
//      def apply(q: Quote):              Raw = Raw(q, Nil)
//      def apply(q: Quote, s: Segment*): Raw = Raw(q, s.to[List])
//      def apply(s: List[Segment]):      Raw = Raw(Quote.Single, s)
//      def apply(s: Segment*):           Raw = Raw(s.to[List])
//    }
//
//    object Interpolated {
//      sealed trait Segment extends Text.Segment
//      def apply():                      I = I(Quote.Single, Nil)
//      def apply(q: Quote):              I = I(q, Nil)
//      def apply(q: Quote, s: Segment*): I = I(q, s.to[List])
//      def apply(s: List[Segment]):      I = I(Quote.Single, s)
//      def apply(s: Segment*):           I = I(s.to[List])
//    }
//
//    //// Quote ////
//
//    sealed trait Quote {
//      val asInt: Int
//    }
//    object Quote {
//      final case object Single extends Quote { val asInt = 1 }
//      final case object Triple extends Quote { val asInt = 3 }
//    }
//
//    //// Segment ////
//
//    sealed trait Segment extends Symbol
//    object Segment {
//      type Raw          = Text.Raw.Segment
//      type Interpolated = Text.Interpolated.Segment
//
//      final case class Plain(value: String) extends Raw {
//        val repr = value
//      }
//
//      final case class EOL(validIndent: Boolean = true) extends Raw {
//        val repr = "\n"
//      }
//
//      final case class Interpolation(value: Option[AST]) extends Interpolated {
//        val repr = R + '`' + value + '`'
//      }
//
//      trait Escape extends Interpolated
//      val Escape = text.Escape
//
//      implicit def fromString(str: String): Plain = Plain(str)
//    }
//
//    //// Unclosed ////
//
//    final case class Unclosed(text: Class[_]) extends AST.Invalid {
//      val repr               = R + text.quoteRepr + text.bodyRepr
//      def map(f: AST => AST) = this
//    }
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Block ///////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  val newline = R + '\n'
//
//  abstract class Block(
//    val typ: Block.Type,
//    val indent: Int,
//    val emptyLines: List[Int],
//    val firstLine: Block.Line.NonEmpty,
//    val lines: List[Block.Line]
//  ) extends AST {
//    lazy val headRepr = newline
//    val repr = {
//      val emptyLinesRepr = emptyLines.map(R + _ + newline)
//      val firstLineRepr  = R + indent + firstLine
//      val linesRepr = lines.map { line =>
//        newline + line.elem.map(_ => indent) + line
//      }
//      R + headRepr + emptyLinesRepr + firstLineRepr + linesRepr
//    }
//
//    def replaceType(typ: Block.Type): Block
//    def map(f: AST => AST): Block
//  }
//
//  case class _Block(
//    override val typ: Block.Type,
//    override val indent: Int,
//    override val emptyLines: List[Int],
//    override val firstLine: Block.Line.NonEmpty,
//    override val lines: List[Block.Line],
//    override val id: Option[ID] = None
//  ) extends Block(typ, indent, emptyLines, firstLine, lines) {
//    def replaceType(typ: Block.Type) = copy(typ = typ)
//    def setID(newID: ID)             = copy(id  = Some(newID))
//    def map(f: AST => AST) =
//      copy(firstLine = firstLine.map(f), lines = lines.map(_.map(f)))
//    def mapWithOff(f: (Int, AST) => AST) = {
//      var off        = headRepr.span + indent
//      val firstLine2 = firstLine.map(f(off, _))
//      off += firstLine.span
//      val lines2 = lines.map { line =>
//        off += 1 + indent
//        val line2 = line.map(f(off, _))
//        off += line.span
//        line2
//      }
//      copy(firstLine = firstLine2, lines = lines2)
//    }
//  }
//
//  case class OrphanBlock(
//    override val typ: Block.Type,
//    override val indent: Int,
//    override val emptyLines: List[Int],
//    override val firstLine: Block.Line.NonEmpty,
//    override val lines: List[Block.Line],
//    override val id: Option[ID] = None
//  ) extends Block(typ, indent, emptyLines, firstLine, lines) {
//    def replaceType(typ: Block.Type) = copy(typ = typ)
//    def setID(newID: ID)             = copy(id  = Some(newID))
//    def map(f: AST => AST) =
//      copy(firstLine = firstLine.map(f), lines = lines.map(_.map(f)))
//    def mapWithOff(f: (Int, AST) => AST) = {
//      var off        = headRepr.span + indent
//      val firstLine2 = firstLine.map(f(off, _))
//      off += firstLine.span
//      val lines2 = lines.map { line =>
//        off += 1 + indent
//        val line2 = line.map(f(off, _))
//        off += line.span
//        line2
//      }
//      copy(firstLine = firstLine2, lines = lines2)
//    }
//    override lazy val headRepr = R
//  }
//
//  object Block {
//    sealed trait Type
//    final case object Continuous    extends Type
//    final case object Discontinuous extends Type
//
//    def apply(
//      isOrphan: Boolean,
//      typ: Type,
//      indent: Int,
//      emptyLines: List[Int],
//      firstLine: Line.NonEmpty,
//      lines: List[Line]
//    ): Block =
//      if (isOrphan) OrphanBlock(typ, indent, emptyLines, firstLine, lines)
//      else _Block(typ, indent, emptyLines, firstLine, lines)
//
//    def apply(
//      typ: Type,
//      indent: Int,
//      firstLine: Line.NonEmpty,
//      lines: List[Line]
//    ): Block =
//      Block(isOrphan = false, typ, indent, List(), firstLine, lines)
//
//    def apply(
//      typ: Type,
//      indent: Int,
//      firstLine: AST,
//      lines: Option[AST]*
//    ): Block =
//      Block(typ, indent, Line.Required(firstLine), lines.toList.map(Line(_)))
//
//    def unapply(t: Block): Option[(Int, Line.NonEmpty, List[Line])] =
//      Some((t.indent, t.firstLine, t.lines))
//
//    final case class InvalidIndentation(block: Block) extends AST.Invalid {
//      val repr               = R + block
//      def map(f: AST => AST) = copy(block = block.map(f))
//    }
//
//    //// Line ////
//
//    type OptLine = Line
////    val OptLine = Line
//    object OptLine {
//      def apply[T](t: Int): Line = Line(t)
//    }
//    type Line = _Line
//    final case class _Line(elem: Option[AST], off: Int)
//        extends Symbol
//        with Zipper.Has {
//      type Zipper[T] = Line.Zipper.Class[T]
//      val repr = R + elem + off
//      def map(f: AST => AST): Line = _Line(elem.map(f), off)
//      def toNonEmpty(): Option[Line.NonEmpty] =
//        elem.map(Line.Required(_, off))
//    }
//    object Line {
//      def apply(elem: Option[AST], off: Int): Line = _Line(elem, off)
//      def apply(elem: Option[AST]):           Line = Line(elem, 0)
//      def apply(elem: AST):                   Line = Line(Some(elem))
//      def apply(off: Int):                    Line = Line(None, off)
//      def apply():                            Line = Line(None, 0)
//
//      type NonEmpty = _NonEmpty
//      final case class _NonEmpty(elem: AST, off: Int) extends Symbol {
//        val repr = R + elem + off
//        def toOptional:         Line      = Line(Some(elem), off)
//        def map(f: AST => AST): _NonEmpty = copy(elem = f(elem))
//      }
//      object Required {
//        def apply(elem: AST, off: Int): NonEmpty    = _NonEmpty(elem, off)
//        def apply(elem: AST):           NonEmpty    = Required(elem, 0)
//        def unapply(t: NonEmpty):       Option[AST] = Some(t.elem)
//      }
//
//      //// Zipper ////
//
//      // TODO: Class below should not define `lens` explicitly, it should be
//      //       provided under the hood.
//
//      object Zipper {
//        implicit class Class[S](val lens: AST.Zipper.Path[S, Line])
//            extends AST.Zipper[S, Line] {
//          val offset = zipper(Offset(lens))
//        }
//
//        final case class Offset[S](lens: AST.Zipper.Path[S, Line])
//            extends AST.Zipper.Path[Line, Int] {
//          val path = GenLens[Line](_.off).asOptional
//        }
//
//      }
//
//    }
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Module //////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  import Block.Line
//
//  final case class Module(lines: List1[Line], id: Option[ID] = None)
//      extends AST {
//    val repr             = R + lines.head + lines.tail.map(newline + _)
//    def setID(newID: ID) = copy(id = Some(newID))
//
//    def mapLines(f: Line => Line) = Module(lines.map(f))
//    def map(f: AST => AST)        = copy(lines = lines.map(_.map(f)))
//    def mapWithOff(f: (Int, AST) => AST) = {
//      var off = 0
//      val lines2 = lines.map { line =>
//        val line2 = line.map(f(off, _))
//        off += 1 + line.span
//        line2
//      }
//      copy(lines = lines2)
//    }
//  }
//
//  object Module {
//    def apply(l: Line):                 Module = Module(List1(l))
//    def apply(l: Line, ls: Line*):      Module = Module(List1(l, ls.to[List]))
//    def apply(l: Line, ls: List[Line]): Module = Module(List1(l, ls))
//
//    object Zipper {
//      case class Lines() extends AST.Zipper.Path[Module, List1[Line]] {
//        val path = GenLens[Module](_.lines).asOptional
//      }
//      val lines          = zipper(Lines())
//      def line(idx: Int) = lines.index(idx)
//    }
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Macro ///////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  sealed trait Macro extends AST
//  object Macro {
//
//    import org.enso.syntax.text.ast.meta.Pattern
//
//    //// Matched ////
//
//    final case class Match(
//      pfx: Option[Pattern.Match],
//      segs: Shifted.List1[Match.Segment],
//      resolved: AST,
//      id: Option[ID] = None
//    ) extends Macro {
//      val repr = {
//        val pfxStream = pfx.map(_.toStream.reverse).getOrElse(List())
//        val pfxRepr   = pfxStream.map(t => R + t.el + t.off)
//        val segsRepr  = segs.map(_.repr)
//        R + pfxRepr + segsRepr
//      }
//      def setID(newID: ID) = copy(id = Some(newID))
//      // FIXME ugly code vvv
//      def map(f: AST => AST) = {
//        copy(
//          segs = segs.map(
//            _.map((a: Pattern.Match) => a.map(_.map(f)))
//          )
//        )
//      }
//
//      def mapWithOff(f: (Int, AST) => AST) = {
//        val segs2 = segs.map(
//          _.map(
//            m => {
//              Pattern.MatchOf.mapWithOff(m) {
//                case (i, Shifted(off, t)) =>
//                  Shifted(off, f(i, t))
//              }
//            }
//          )
//        )
//        this.copy(segs = segs2)
//      }
//      def path(): List1[AST] = segs.toList1().map(_.el.head)
//    }
//    object Match {
//      final case class Segment(head: Ident, body: Pattern.Match) {
//        val repr = R + head + body
//        def toStream: AST.Stream = Shifted(head) :: body.toStream
//        def isValid:  Boolean    = body.isValid
//        def map(f: Pattern.Match => Pattern.Match): Segment =
//          copy(body = f(body))
//      }
//      object Segment {
//        def apply(head: Ident): Segment = Segment(head, Pattern.Match.Nothing())
//      }
//    }
//
//    //// Ambiguous ////
//
//    final case class Ambiguous(
//      segs: Shifted.List1[Ambiguous.Segment],
//      paths: Tree[AST, Unit],
//      id: Option[ID] = None
//    ) extends Macro {
//      val repr                             = R + segs.map(_.repr)
//      def setID(newID: ID)                 = copy(id = Some(newID))
//      def map(f: AST => AST)               = this
//      def mapWithOff(f: (Int, AST) => AST) = this
//    }
//    object Ambiguous {
//      final case class Segment(head: AST, body: Option[SAST]) extends Symbol {
//        val repr = R + head + body
//      }
//      object Segment {
//        def apply(head: AST): Segment = Segment(head, None)
//      }
//    }
//
//    //// Resolver ////
//
//    type Resolver = Resolver.Context => AST
//    object Resolver {
//      final case class Context(
//        prefix: Option[Pattern.Match],
//        body: List[Macro.Match.Segment],
//        id: ID
//      )
//    }
//
//    //// Definition ////
//
//    type Definition = __Definition__
//    final case class __Definition__(
//      back: Option[Pattern],
//      init: List[Definition.Segment],
//      last: Definition.LastSegment,
//      resolver: Resolver
//    ) {
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
//      def apply(back: Option[Pattern], t1: Segment.Tup, ts: List[Segment.Tup])(
//        fin: Resolver
//      ): Definition = {
//        val segs    = List1(t1, ts)
//        val init    = segs.init
//        val lastTup = segs.last
//        val last    = (lastTup._1, Some(lastTup._2))
//        Definition(back, init, last, fin)
//      }
//
//      def apply(back: Option[Pattern], t1: Segment.Tup, ts: Segment.Tup*)(
//        fin: Resolver
//      ): Definition = Definition(back, t1, ts.toList)(fin)
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
//        back: Option[Pattern],
//        initTups: List[Segment.Tup],
//        lastTup: LastSegment.Tup,
//        resolver: Resolver
//      ): Definition = {
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
//          : List[AST.Macro.Match.Segment] => List[AST.Macro.Match.Segment] =
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
//          AST.Unexpected(msg, stream)
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
//  //// Space - unaware AST /////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  //////////////////////////////////////////////////////////////////////////////
//  /// Comment //////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  trait Comment extends AST
//  object Comment {
//    val symbol = "#"
//
//    final case class SingleLine(text: String, id: Option[ID] = None)
//        extends Comment {
//      val repr                             = R + symbol + symbol + text
//      def setID(newID: ID)                 = copy(id = Some(newID))
//      def map(f: AST => AST)               = this
//      def mapWithOff(f: (Int, AST) => AST) = this
//    }
//
//    final case class MultiLine(
//      offset: Int,
//      lines: List[String],
//      id: Option[ID] = None
//    ) extends Comment {
//      def map(f: AST => AST)               = this
//      def mapWithOff(f: (Int, AST) => AST) = this
//      val repr = {
//        val commentBlock = lines match {
//          case Nil => Nil
//          case line +: lines =>
//            val indentedLines = lines.map { s =>
//              if (s.forall(_ == ' ')) newline + s
//              else newline + 1 + offset + s
//            }
//            (R + line) +: indentedLines
//        }
//        R + symbol + symbol + commentBlock
//      }
//      def setID(newID: ID) = copy(id = Some(newID))
//    }
//
//    final case class Disable(ast: AST, id: Option[ID] = None) extends AST {
//      val repr                             = R + symbol + " " + ast
//      def setID(newID: ID)                 = copy(id = Some(newID))
//      def map(f: AST => AST)               = copy(ast = f(ast))
//      def mapWithOff(f: (Int, AST) => AST) = copy(ast = f(0, ast)) // x
//    }
//
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Import //////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  final case class Import(path: List1[Cons], id: Option[ID] = None)
//      extends AST {
//    val repr                             = R
//    def setID(newID: ID)                 = copy(id = Some(newID))
//    def map(f: AST => AST)               = this
//    def mapWithOff(f: (Int, AST) => AST) = this
//  }
//  object Import {
//    def apply(head: Cons):                   Import = Import(head, List())
//    def apply(head: Cons, tail: List[Cons]): Import = Import(List1(head, tail))
//    def apply(head: Cons, tail: Cons*):      Import = Import(head, tail.toList)
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Mixfix //////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  final case class Mixfix(
//    name: List1[Ident],
//    args: List1[AST],
//    id: Option[ID] = None
//  ) extends AST {
//    val repr = {
//      val lastRepr = if (name.length - args.length > 0) List(R) else List()
//      val argsRepr = args.toList.map(R + " " + _) ++ lastRepr
//      val nameRepr = name.toList.map(Repr(_))
//      R + (nameRepr, argsRepr).zipped.map(_ + _)
//    }
//    def setID(newID: ID)                 = copy(id = Some(newID))
//    def map(f: AST => AST)               = copy(args = args.map(f))
//    def mapWithOff(f: (Int, AST) => AST) = map(f(0, _))
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Group ///////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  type Group = _Group
//  final case class _Group(body: Option[AST] = None, id: Option[ID] = None)
//      extends AST {
//    val repr                             = R + body
//    def setID(newID: ID)                 = copy(id = Some(newID))
//    def map(f: AST => AST)               = copy(body = body.map(f))
//    def mapWithOff(f: (Int, AST) => AST) = map(f(0, _))
//  }
//  object Group {
//    def apply(body: Option[AST], id: Option[ID] = None): Group =
//      _Group(body, id)
//    def apply(body: AST):  Group = Group(Some(body))
//    def apply(body: SAST): Group = Group(body.el)
//    def apply():           Group = Group(None)
//
//    def unapply(t: Group): Option[Option[AST]] = Some(t.body)
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Def /////////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  final case class Def(
//    name: Cons,
//    args: List[AST],
//    body: Option[AST],
//    id: Option[ID] = None
//  ) extends AST {
//    import Def._
//    val repr               = R + symbol ++ name + args.map(R ++ _) + body
//    def setID(newID: ID)   = copy(id = Some(newID))
//    def map(f: AST => AST) = copy(args = args.map(f), body = body.map(f))
//
//    def mapWithOff(f: (Int, AST) => AST) = map(f(0, _))
//  }
//  object Def {
//    def apply(name: Cons, args: List[AST]): Def = Def(name, args, None)
//    def apply(name: Cons):                  Def = Def(name, List())
//    val symbol = "type"
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Foreign /////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  final case class Foreign(
//    indent: Int,
//    lang: String,
//    code: List[String],
//    id: Option[ID] = None
//  ) extends AST {
//    val repr = {
//      val code2 = code.map(R + indent + _).mkString("\n")
//      R + "foreign " + lang + "\n" + code2
//    }
//    def setID(newID: ID)                 = copy(id = Some(newID))
//    def map(f: AST => AST)               = this
//    def mapWithOff(f: (Int, AST) => AST) = this
//  }
//
//  //////////////////////////////////////////////////////////////////////////////
//  //// Zipper //////////////////////////////////////////////////////////////////
//  //////////////////////////////////////////////////////////////////////////////
//
//  /**
//    * This is just a stub implementation. It shows how zippers could be
//    * implemented for AST.
//    */
//  sealed trait Zipper[Begin, End]
//  object Zipper {
//
//    sealed trait Path[Begin, End] {
//      val path: monocle.Optional[Begin, End]
//    }
//
//    sealed trait Has { type Zipper[_] }
//
//    sealed trait Provider[Begin, End] {
//      type Zipper
//      def focus: Path[Begin, End] => Zipper
//    }
//    object Provider {
//      sealed trait Inferred[Begin, End <: Has] extends Provider[Begin, End] {
//        type Zipper = End#Zipper[Begin]
//
//      }
//      sealed trait Terminated[Begin, End] extends Provider[Begin, End] {
//        type Zipper = Terminator[Begin, End]
//      }
//
//      implicit def default[S, T]: Terminated[S, T] =
//        new Terminated[S, T] {
//          val focus = Terminator(_)
//        }
//    }
//  }
//
//  implicit def inferredZipperProvider[S, T <: Zipper.Has](
//    implicit ev: Zipper.Path[S, T] => T#Zipper[S]
//  ): Zipper.Provider.Inferred[S, T] = new Zipper.Provider.Inferred[S, T] {
//    val focus = ev(_)
//  }
//
//  def zipper[S, T](
//    lens: Zipper.Path[S, T]
//  )(implicit ev: Zipper.Provider[S, T]): ev.Zipper =
//    ev.focus(lens)
//
//  final case class Terminator[S, T](zipper: Zipper.Path[S, T])
//      extends AST.Zipper[S, T]
//
//  implicit def ZipperTarget_List1[S, T]
//    : Zipper.Provider[S, List1[T]] { type Zipper = List1Target[S, T] } =
//    new Zipper.Provider[S, List1[T]] {
//      type Zipper = List1Target[S, T]
//      def focus = List1Target(_)
//    }
//
//  final case class List1Target[S, T](lens: AST.Zipper.Path[S, List1[T]])
//      extends AST.Zipper[S, List1[T]] {
//    def index(
//      idx: Int
//    )(implicit ev: Zipper.Provider[List1[T], T]): ev.Zipper =
//      zipper(List1Zipper[S, T](lens, idx))
//  }
//
//  final case class List1Zipper[S, T](
//    zipper: AST.Zipper.Path[S, List1[T]],
//    idx: Int
//  ) extends AST.Zipper.Path[List1[T], T] {
//    val getOpt = (t: List1[T]) =>
//      idx match {
//        case 0 => Some(t.head)
//        case i => t.tail.lift(i - 1)
//      }
//    val setOpt = (s: T) =>
//      (t: List1[T]) =>
//        idx match {
//          case 0 => t.copy(head = s)
//          case _ =>
//            val i = idx - 1
//            if ((i >= t.tail.length) || (i < 0)) t
//            else {
//              val (front, back) = t.tail.splitAt(i)
//              val tail2         = front ++ (s :: back.tail)
//              List1(t.head, tail2)
//            }
//        }
//    val path = monocle.Optional[List1[T], T](getOpt)(setOpt)
//  }
//
//  val z1 = Module.Zipper.lines.index(5).offset.zipper
//}

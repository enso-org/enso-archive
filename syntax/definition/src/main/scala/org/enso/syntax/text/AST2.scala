package org.enso.syntax.text

import java.util.UUID

import cats.Functor
import cats.derived._
import cats.implicits._
import org.enso.data.List1._
import org.enso.data.List1
import org.enso.data.Shifted
import org.enso.data.Tree
import org.enso.lint.Unused
import org.enso.syntax.text.ast.Repr.R
import org.enso.syntax.text.ast.Repr
import org.enso.syntax.text.ast.opr

import scala.annotation.tailrec
import scala.reflect.ClassTag

object AST {

  //////////////////////////////////////////////////////////////////////////////
  //// Reexports ///////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Assoc = opr.Assoc
  val Assoc = opr.Assoc
  val Prec  = opr.Prec

  //////////////////////////////////////////////////////////////////////////////
  //// Definition //////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  //// Structure ////

  type AST_Type = ASTOf[ShapeOf]
//  type ASTOf[T[_]] = Node[T]
  type Shape = ShapeOf[AST]

  //// Aliases ////

  type SAST        = Shifted[AST]
  type StreamOf[T] = List[Shifted[T]]
  type _Stream1[T] = List1[Shifted[T]]
  type Stream      = StreamOf[AST]
  type Stream1     = _Stream1[AST]
  type ID          = UUID

  //// API ////

  object implicits extends implicits
  trait implicits  extends Conversions.implicits with TopLevel.implicits
  import implicits._

  object TopLevel {
    object implicits extends implicits
    trait implicits {

      implicit def offZipStream[T: Repr]: OffsetZip[StreamOf, T] = { stream =>
        var off = 0
        stream.map { t =>
          off += t.off
          val out = t.map((off, _))
          off += Repr(t.el).span
          out
        }
      }
    }
  }

  object Conversions {
    object implicits extends implicits
    trait implicits extends Ident.Conversions {
      implicit def stringToAST(str: String): AST = {
        if (str == "") throw new Error("Empty literal")
        if (str == "_") Blank()
        else if (str.head.isLower) Var(str)
        else if (str.head.isUpper) Cons(str)
        else Opr(str)
      }
    }
  }

  def tokenize(ast: AST): Shifted.List1[AST] = {
    @tailrec
    def go(ast: AST, out: AST.Stream): Shifted.List1[AST] = ast match {
      case App.Prefix.any(t) => go(t.fn, Shifted(t.off, t.arg) :: out)
      case _                 => Shifted.List1(ast, out)
    }
    go(ast, List())
  }

  ////////////////////////////////////
  //// Apply / Unapply Generators ////
  ////////////////////////////////////

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
        def run[Out](fn: In => Out)(t: AST) = ct.unapply(t.unFix).map(fn)
      }
  }

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
          ct.unapply(t.unFix).map(_ => t.asInstanceOf[ASTOf[T]])
      }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// OffsetZip ///////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  trait OffsetZip[F[A], A] {
    def zipWithOffset(t: F[A]): F[(Int, A)]
  }
  object OffsetZip {
    def apply[F[A], A](implicit ev: OffsetZip[F, A]): OffsetZip[F, A] = ev
    def apply[F[A], A](t: F[A])(implicit ev: OffsetZip[F, A]): F[(Int, A)] =
      OffsetZip[F, A].zipWithOffset(t)
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Catamorphism ////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  //// Wrapper ////

  /** The [[ASTOf]] class wraps each AST node. The implementation is very similar
    * to standard catamorphic Fix, however, it takes the head element into
    * consideration. This way we can keep the info of the shape of the AST. For
    * example, [[Var]] is just an alias to [[ Node[VarOf,ShapeOf] ]], and while
    * [[ VarOf[T] <: ShapeOf[T] ]], also [[Var <: AST]].
    *
    * Another important role of [[ASTOf]] is caching of [[Repr.Builder]] and
    * allowing for fast method redirection. When [[ASTOf]] is created, it
    * remembers a bunch of stuff, which can be fast accessed even if we cast the
    * type to generic [[AST]]
    */
  case class ASTOf[+T[_]](unFix: T[AST], id: Option[ID] = None)(
    implicit cls: ASTClass[T]
  ) {
    override def toString  = s"Node($id,$unFix)"
    val repr: Repr.Builder = cls.repr(unFix)
    def show():             String   = repr.build()
    def setID(newID: ID):   ASTOf[T] = copy(id = Some(newID))
    def withNewID():        ASTOf[T] = copy(id = Some(UUID.randomUUID()))
    def map(f: AST => AST): ASTOf[T] = copy(unFix = cls.map(unFix)(f))
    def mapWithOff(f: (Int, AST) => AST): ASTOf[T] =
      copy(unFix = cls.mapWithOff(unFix)(f))
    def zipWithOffset(): T[(Int, AST)] = cls.zipWithOffset(unFix)
  }
  object ASTOf {
    implicit def repr[H[_]]:                Repr[ASTOf[H]] = _.repr
    implicit def unwrap[T[_]](t: ASTOf[T]): T[AST]         = t.unFix
    implicit def wrap[T[_]](t: T[AST])(
      implicit
      ev: ASTClass[T]
    ): ASTOf[T] = ASTOf(t)
  }

  //// ASTClass ////

  /** [[ASTClass]] implements set of AST operations based on a precise AST
    * shape. Because the `T` parameter in [[ASTOf]] is covariant, we may lose
    * information about the shape after we construct the AST, thus this instance
    * is used to cache all necessary operations during AST construction.
    */
  trait ASTClass[T[_]] {
    def repr(t: T[AST]): Repr.Builder
    def map(t: T[AST])(f: AST => AST): T[AST]
    def mapWithOff(t: T[AST])(f: (Int, AST) => AST): T[AST]
    def zipWithOffset(t: T[AST]): T[(Int, AST)]
  }
  object ASTClass {
    def apply[T[_]](implicit ev: ASTClass[T]): ASTClass[T] = ev
    implicit def instance[T[_]](
      implicit
      evRepr: Repr[T[AST]],
      evFtor: Functor[T],
      evOZip: OffsetZip[T, AST]
    ): ASTClass[T] =
      new ASTClass[T] {
        def repr(t: T[AST]):               Repr.Builder = evRepr.repr(t)
        def map(t: T[AST])(f: AST => AST): T[AST]       = Functor[T].map(t)(f)
        def mapWithOff(t: T[AST])(f: (Int, AST) => AST): T[AST] =
          Functor[T].map(zipWithOffset(t))(f.tupled)
        def zipWithOffset(t: T[AST]): T[(Int, AST)] = OffsetZip(t)
      }
  }

  //// ASTOps ////

  /** [[ASTOps]] implements handy AST operations. In contrast to [[ASTClass]],
    * implementations in this class do not require any special knowledge of the
    * underlying shape and thus are just a high-level AST addons.
    */
  implicit class ASTOps[T[S] <: ShapeOf[S]](t: ASTOf[T]) {
    def as[X: UnapplyByType]: Option[X] = UnapplyByType[X].unapply(t)
    def traverseWithOff(f: (Int, AST) => AST): ASTOf[T] = {
      def go(i: Int, x: AST): AST = {
        x.mapWithOff { (j, ast) =>
          val off = i + j
          go(off, f(off, ast))
        }
      }
      t.mapWithOff((off, ast) => go(off, f(off, ast)))
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Shape ///////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  /** [[Shape]] defines the shape of an AST node. It contains information about
    * the layout of elements and spacing between them.
    *
    * @tparam T The type of elements.
    */
  sealed trait ShapeOf[T]

  //////////////////////////////////////////////////////////////////////////////
  //// Phantom /////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  /** Phantom type. Use with care, as Scala cannot prove its proper usage. When
    * a type is phantom, then its last type argument is not used and we can
    * safely coerce it to something else.
    */
  trait Phantom
  implicit class PhantomOps[T[_] <: Phantom](ident: T[_]) {
    def coerce[S]: T[S] = ident.asInstanceOf[T[S]]
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Invalid /////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Invalid = ASTOf[InvalidOf]
  sealed trait InvalidOf[T] extends ShapeOf[T]
  object InvalidOf {
    implicit def functor: Functor[InvalidOf] = semi.functor
  }
  object Invalid {

    //// Types ////

    type Unrecognized = ASTOf[UnrecognizedOf]
    type Unexpected   = ASTOf[UnexpectedOf]
    case class UnrecognizedOf[T](str: String) extends InvalidOf[T] with Phantom
    case class UnexpectedOf[T](msg: String, stream: StreamOf[T])
        extends InvalidOf[T]

    //// Smart Constructors ////

    object Unrecognized {
      def apply(str: String): Unrecognized = UnrecognizedOf[AST](str)
    }
    object Unexpected {
      def apply(msg: String, stream: Stream): Unexpected =
        UnexpectedOf(msg, stream)
    }

    //// Implicits ////

    object UnrecognizedOf {
      implicit def functor:      Functor[UnrecognizedOf]      = semi.functor
      implicit def repr[T]:      Repr[UnrecognizedOf[T]]      = _.str
      implicit def offsetZip[T]: OffsetZip[UnrecognizedOf, T] = t => t.coerce
    }
    object UnexpectedOf {
      implicit def functor:       Functor[UnexpectedOf] = semi.functor
      implicit def repr[T: Repr]: Repr[UnexpectedOf[T]] = t => Repr(t.stream)
      implicit def offsetZip[T: Repr]: OffsetZip[UnexpectedOf, T] =
        t => t.copy(stream = OffsetZip(t.stream))
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

  type Ident = ASTOf[IdentOf]
  sealed trait IdentOf[T] extends ShapeOf[T] with Phantom { val name: String }
  object IdentOf {
    implicit def functor: Functor[IdentOf] = semi.functor
  }
  object Ident {
    type Blank = ASTOf[BlankOf]
    type Var   = ASTOf[VarOf]
    type Cons  = ASTOf[ConsOf]
    type Opr   = ASTOf[OprOf]
    type Mod   = ASTOf[ModOf]

    case class BlankOf[T]()            extends IdentOf[T] { val name = "_" }
    case class VarOf[T](name: String)  extends IdentOf[T]
    case class ConsOf[T](name: String) extends IdentOf[T]
    case class ModOf[T](name: String)  extends IdentOf[T]
    case class OprOf[T](name: String) extends IdentOf[T] {
      val (prec, assoc) = opr.Info.of(name)
    }

    //// Instances ////

    object BlankOf {
      implicit def functor:      Functor[BlankOf]      = semi.functor
      implicit def repr[T]:      Repr[BlankOf[T]]      = _.name
      implicit def offsetZip[T]: OffsetZip[BlankOf, T] = t => t.coerce
    }
    object VarOf {
      implicit def functor:      Functor[VarOf]      = semi.functor
      implicit def repr[T]:      Repr[VarOf[T]]      = _.name
      implicit def offsetZip[T]: OffsetZip[VarOf, T] = t => t.coerce
    }
    object ConsOf {
      implicit def functor:      Functor[ConsOf]      = semi.functor
      implicit def repr[T]:      Repr[ConsOf[T]]      = _.name
      implicit def offsetZip[T]: OffsetZip[ConsOf, T] = t => t.coerce
    }
    object OprOf {
      implicit def functor:      Functor[OprOf]      = semi.functor
      implicit def repr[T]:      Repr[OprOf[T]]      = _.name
      implicit def offsetZip[T]: OffsetZip[OprOf, T] = t => t.coerce
    }
    object ModOf {
      implicit def functor:      Functor[ModOf]      = semi.functor
      implicit def repr[T]:      Repr[ModOf[T]]      = R + _.name + "="
      implicit def offsetZip[T]: OffsetZip[ModOf, T] = t => t.coerce
    }

    //// Conversions ////

    trait Conversions1 {
      implicit def strToVar(str: String):  Var  = Var(str)
      implicit def strToCons(str: String): Cons = Cons(str)
      implicit def strToOpr(str: String):  Opr  = Opr(str)
      implicit def strToMod(str: String):  Mod  = Mod(str)
    }

    trait Conversions extends Conversions1 {
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
      val any             = UnapplyByType[Blank]
      def unapply(t: AST) = Unapply[Blank].run(_ => true)(t)
      def apply(): Blank = BlankOf[AST]()
    }
    object Var {
      val any             = UnapplyByType[Var]
      def unapply(t: AST) = Unapply[Var].run(_.name)(t)
      def apply(name: String): Var = VarOf[AST](name)
    }
    object Cons {
      val any             = UnapplyByType[Cons]
      def unapply(t: AST) = Unapply[Cons].run(_.name)(t)
      def apply(name: String): Cons = ConsOf[AST](name)
    }
    object Mod {
      val any             = UnapplyByType[Mod]
      def unapply(t: AST) = Unapply[Mod].run(_.name)(t)
      def apply(name: String): Mod = ModOf[AST](name)
    }
    object Opr {
      val app             = Opr(" ")
      val any             = UnapplyByType[Opr]
      def unapply(t: AST) = Unapply[Opr].run(_.name)(t)
      def apply(name: String): Opr = OprOf[AST](name)
    }

    ///////////////////////
    //// InvalidSuffix ////
    ///////////////////////

    type InvalidSuffix = ASTOf[InvalidSuffixOf]
    case class InvalidSuffixOf[T](elem: Ident, suffix: String)
        extends InvalidOf[T]
        with Phantom
    object InvalidSuffixOf {
      implicit def ftor:      Functor[InvalidSuffixOf]      = semi.functor
      implicit def offZip[T]: OffsetZip[InvalidSuffixOf, T] = t => t.coerce
      implicit def repr[T]: Repr[InvalidSuffixOf[T]] =
        t => R + t.elem + t.suffix
    }
    object InvalidSuffix {
      def apply(elem: Ident, suffix: String): InvalidSuffix =
        InvalidSuffixOf[AST](elem, suffix)
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

  type Literal = LiteralOf[AST]
  sealed trait LiteralOf[T] extends ShapeOf[T]
  object LiteralOf {
    implicit def functor: Functor[LiteralOf] = semi.functor
  }
  object Literal {

    ////////////////
    //// Number ////
    ////////////////

    type Number = ASTOf[NumberOf]
    case class NumberOf[T](base: Option[String], int: String)
        extends LiteralOf[T]
        with Phantom

    object Number {

      //// Smart Constructors ////

      def apply(i: String):            Number = Number(None, i)
      def apply(b: String, i: String): Number = Number(Some(b), i)
      def apply(i: Int):               Number = Number(i.toString)
      def apply(b: Int, i: String):    Number = Number(b.toString, i)
      def apply(b: String, i: Int):    Number = Number(b, i.toString)
      def apply(b: Int, i: Int):       Number = Number(b.toString, i.toString)
      def apply(b: Option[String], i: String): Number =
        NumberOf[AST](b, i)

      //// DanglingBase ////

      type DanglingBase = DanglingBaseOf[AST]
      case class DanglingBaseOf[T](base: String)
          extends AST.InvalidOf[T]
          with Phantom
      object DanglingBase {
        def apply(base: String): DanglingBase = DanglingBaseOf(base)
      }
      object DanglingBaseOf {
        implicit def ftorNumDang: Functor[DanglingBaseOf] = semi.functor
        implicit def offZipNumDang[T]: OffsetZip[DanglingBaseOf, T] =
          t => t.coerce
        implicit def reprNumDang[T]: Repr[DanglingBaseOf[T]] = R + _.base + '_'
      }
    }

    //// Instances ////

    object NumberOf {
      implicit def functor:      Functor[NumberOf]      = semi.functor
      implicit def offsetZip[T]: OffsetZip[NumberOf, T] = t => t.coerce
      implicit def repr[T]: Repr[NumberOf[T]] =
        t => t.base.map(_ + "_").getOrElse("") + t.int
      implicit def fromInt[T](int: Int): Number = Number(int)
    }

    //////////////
    //// Text ////
    //////////////

    type Text = ASTOf[TextOf]
    sealed trait TextOf[T] extends ShapeOf[T]
    object Text {

      //// Definition ////

      type Line[T]  = List[T]
      type Block[T] = List1[Line[T]]

      type Raw          = ASTOf[RawOf]
      type Interpolated = ASTOf[InterpolatedOf]
      type Unclosed     = ASTOf[UnclosedOf]

      case class RawOf[T](quote: Quote, lines: Raw.Block[T]) extends TextOf[T] {
        val quoteChar: Char = '"'
      }
      case class InterpolatedOf[T](quote: Quote, lines: Interpolated.Block[T])
          extends TextOf[T] {
        val quoteChar: Char = '\''
      }
      case class UnclosedOf[T](text: TextOf[T]) extends AST.InvalidOf[T]

      object Raw {
        type Segment[T] = Text.Segment._Raw[T]
        type Line[T]    = Text.Line[Segment[T]]
        type Block[T]   = Text.Block[Segment[T]]
      }

      object Interpolated {
        type Segment[T] = Text.Segment._Interpolated[T]
        type Line[T]    = Text.Line[Segment[T]]
        type Block[T]   = Text.Block[Segment[T]]
      }

      //// Instances ////

      object RawOf {
        implicit def functor[T]: Functor[RawOf] = semi.functor
        implicit def repr[T]:    Repr[RawOf[T]] = _ => ???
        implicit def offsetZip[T]: OffsetZip[RawOf, T] = t => {
          t.copy(
            lines = t.lines
              .map(_.map(Segment.implicits.offZipTxtSRaw.zipWithOffset(_)))
          )
        }
      }
      object InterpolatedOf {
        implicit def functor[T]: Functor[InterpolatedOf] = semi.functor
        implicit def repr[T]:    Repr[InterpolatedOf[T]] = _ => ???
        implicit def offsetZip[T]: OffsetZip[InterpolatedOf, T] = t => {
          t.copy(
            lines = t.lines
              .map(_.map(Segment.implicits.offZipTxtSInt.zipWithOffset(_)))
          )
        }
      }
      object UnclosedOf {
        implicit def functor[T]: Functor[UnclosedOf] = semi.functor
        implicit def repr[T]:    Repr[UnclosedOf[T]] = _ => ???
        implicit def offsetZip[T]: OffsetZip[UnclosedOf, T] =
          t => t.copy(text = OffsetZip(t.text))
      }

      ///////////////
      //// Quote ////
      ///////////////

      sealed trait Quote { val asInt: Int }
      object Quote {
        final case object Single extends Quote { val asInt = 1 }
        final case object Triple extends Quote { val asInt = 3 }
      }

      /////////////////
      //// Segment ////
      /////////////////

      sealed trait Segment[T]
      object Segment {

        // FIXME: Compatibility mode
        trait Escape

        // FIXME: Compatibility mode
        case class EOL()

        //// Definition ////

        type Interpolated = _Interpolated[AST]
        type Raw          = _Raw[AST]
        sealed trait _Interpolated[T] extends Segment[T]
        sealed trait _Raw[T]          extends _Interpolated[T]

        type Plain = _Plain[AST]
        type Expr  = _Expr[AST]
        case class _Plain[T](value: String)   extends _Raw[T] with Phantom
        case class _Expr[T](value: Option[T]) extends _Interpolated[T]

        //// Instances ////

        object implicits extends implicits
        trait implicits {
          implicit def reprTxtSPlain[T]: Repr[_Plain[T]] = _.value
          implicit def reprTxtSExpr[T: Repr]: Repr[_Expr[T]] =
            R + '`' + _.value + '`'
          implicit def ftorTxtSPlain[T]:   Functor[_Plain]      = semi.functor
          implicit def ftorTxtSExpr[T]:    Functor[_Expr]       = semi.functor
          implicit def offZipTxtSExpr[T]:  OffsetZip[_Expr, T]  = _.map((0, _))
          implicit def offZipTxtSPlain[T]: OffsetZip[_Plain, T] = t => t.coerce
          implicit def reprTxtSRaw[T]: Repr[_Raw[T]] = {
            case t: _Plain[T] => Repr(t)
          }
          implicit def reprTxtSInt[T: Repr]: Repr[_Interpolated[T]] = {
            case t: _Plain[T] => Repr(t)
            case t: _Expr[T]  => Repr(t)
          }
          implicit def ftorTxtSRaw[T]: Functor[_Raw]          = semi.functor
          implicit def ftorTxtSInt[T]: Functor[_Interpolated] = semi.functor
          implicit def offZipTxtSRaw[T]: OffsetZip[_Raw, T] = {
            case t: _Plain[T] => OffsetZip(t)
          }
          implicit def offZipTxtSInt[T]: OffsetZip[_Interpolated, T] = {
            case t: _Plain[T] => OffsetZip(t)
            case t: _Expr[T]  => OffsetZip(t)
          }
          implicit def txtFromString[T](str: String): _Plain[T] = _Plain(str)
        }
      }
    }
    object TextOf {
      implicit def offZipText[T]: OffsetZip[TextOf, T] = {
        case t: Text.RawOf[T]          => OffsetZip(t)
        case t: Text.InterpolatedOf[T] => OffsetZip(t)
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// App /////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  //// Definition ////

  type App = AppOf[AST]
  sealed trait AppOf[T] extends ShapeOf[T]
  object App {

    //// Constructors ////

    type Prefix = ASTOf[PrefixOf]
    type Infix  = ASTOf[InfixOf]
    case class PrefixOf[T](fn: T, off: Int, arg: T) extends AppOf[T]
    case class InfixOf[T](larg: T, loff: Int, opr: Opr, roff: Int, rarg: T)
        extends AppOf[T]

    //// Smart Constructors ////

    object Prefix {
      val any             = UnapplyByType[Prefix]
      def unapply(t: AST) = Unapply[Prefix].run(t => (t.fn, t.arg))(t)
      def apply(fn: AST, off: Int, arg: AST): Prefix = PrefixOf(fn, off, arg)
      def apply(fn: AST, arg: AST):           Prefix = Prefix(fn, 1, arg)
    }

    object Infix {
      val any             = UnapplyByType[Infix]
      def unapply(t: AST) = Unapply[Infix].run(t => (t.larg, t.opr, t.rarg))(t)
      def apply(larg: AST, loff: Int, opr: Opr, roff: Int, rarg: AST): Infix =
        InfixOf(larg, loff, opr, roff, rarg)
      def apply(larg: AST, loff: Int, opr: Opr, rarg: AST): Infix =
        Infix(larg, loff, opr, 1, rarg)
      def apply(larg: AST, opr: Opr, roff: Int, rarg: AST): Infix =
        Infix(larg, 1, opr, roff, rarg)
      def apply(larg: AST, opr: Opr, rarg: AST): Infix =
        Infix(larg, 1, opr, 1, rarg)
    }

    //// Instances ////

    object PrefixOf {
      implicit def functor: Functor[PrefixOf] = semi.functor
      implicit def repr[T: Repr]: Repr[PrefixOf[T]] =
        t => R + t.fn + t.off + t.arg
      implicit def offsetZip[T: Repr]: OffsetZip[PrefixOf, T] =
        t => t.copy(fn = (0, t.fn), arg = (Repr(t.fn).span + t.off, t.arg))
    }
    object InfixOf {
      implicit def functor: Functor[InfixOf] = semi.functor
      implicit def repr[T: Repr]: Repr[InfixOf[T]] =
        t => R + t.larg + t.loff + t.opr + t.roff + t.rarg
      implicit def offsetZip[T: Repr]: OffsetZip[InfixOf, T] = t => {
        val rargSpan = (R + t.larg + t.loff + t.opr + t.roff).span
        t.copy(larg = (0, t.larg), rarg = (rargSpan, t.rarg))
      }
    }

    /////////////////
    //// Section ////
    /////////////////

    type Section = SectionOf[AST]
    sealed trait SectionOf[T] extends AppOf[T]
    object Section {

      //// Constructors ////

      type Left  = ASTOf[LeftOf]
      type Right = ASTOf[RightOf]
      type Sides = ASTOf[SidesOf]

      case class LeftOf[T](arg: T, off: Int, opr: Opr)  extends SectionOf[T]
      case class RightOf[T](opr: Opr, off: Int, arg: T) extends SectionOf[T]
      case class SidesOf[T](opr: Opr)                   extends SectionOf[T] with Phantom

      //// Smart Constructors ////

      object Left {
        val any             = UnapplyByType[Left]
        def unapply(t: AST) = Unapply[Left].run(t => (t.arg, t.opr))(t)
        def apply(arg: AST, off: Int, opr: Opr): Left = LeftOf(arg, off, opr)
        def apply(arg: AST, opr: Opr):           Left = Left(arg, 1, opr)
      }
      object Right {
        val any             = UnapplyByType[Right]
        def unapply(t: AST) = Unapply[Right].run(t => (t.opr, t.arg))(t)
        def apply(opr: Opr, off: Int, arg: AST): Right = RightOf(opr, off, arg)
        def apply(opr: Opr, arg: AST):           Right = Right(opr, 1, arg)
      }
      object Sides {
        val any             = UnapplyByType[Sides]
        def unapply(t: AST) = Unapply[Sides].run(_.opr)(t)
        def apply(opr: Opr): Sides = SidesOf[AST](opr)
      }

      //// Instances ////

      object LeftOf {
        implicit def functor: Functor[LeftOf] = semi.functor
        implicit def repr[T: Repr]: Repr[LeftOf[T]] =
          t => R + t.arg + t.off + t.opr
        implicit def offsetZip[T]: OffsetZip[LeftOf, T] =
          t => t.copy(arg = (0, t.arg))
      }
      object RightOf {
        implicit def functor: Functor[RightOf] = semi.functor
        implicit def repr[T: Repr]: Repr[RightOf[T]] =
          t => R + t.opr + t.off + t.arg
        implicit def offZipRight[T]: OffsetZip[RightOf, T] =
          t => t.copy(arg = (Repr(t.opr).span + t.off, t.arg))
      }
      object SidesOf {
        implicit def ftorSides: Functor[SidesOf] = semi.functor
        implicit def reprSides[T: Repr]: Repr[SidesOf[T]] =
          t => R + t.opr
        implicit def offsetZip[T]: OffsetZip[SidesOf, T] =
          t => t.coerce
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Block ///////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  val newline = R + '\n'

  type Block = ASTOf[BlockOf]
  case class BlockOf[T](
    typ: Block.Type,
    indent: Int,
    emptyLines: List[Int],
    firstLine: Block.LineOf[T],
    lines: List[Block.LineOf[Option[T]]]
  ) extends ShapeOf[T] {

    // FIXME: Compatibility mode
    def replaceType(ntyp: Block.Type): BlockOf[T] = copy(typ = ntyp)
  }

  object Block {
    sealed trait Type
    final case object Continuous    extends Type
    final case object Discontinuous extends Type

    //// Smart Constructors ////

    // FIXME: Compatibility mode
    def apply(
      isOrphan: Boolean,
      typ: Type,
      indent: Int,
      emptyLines: List[Int],
      firstLine: LineOf[AST],
      lines: List[LineOf[Option[AST]]]
    ): Block = {
      Unused(isOrphan)
      BlockOf(typ, indent, emptyLines, firstLine, lines)
    }

    def apply(
      typ: Type,
      indent: Int,
      emptyLines: List[Int],
      firstLine: LineOf[AST],
      lines: List[LineOf[Option[AST]]]
    ): Block = BlockOf(typ, indent, emptyLines, firstLine, lines)

    def apply(
      typ: Type,
      indent: Int,
      firstLine: LineOf[AST],
      lines: List[LineOf[Option[AST]]]
    ): Block = Block(typ, indent, List(), firstLine, lines)

    val any = UnapplyByType[Block]
    def unapply(t: AST) =
      Unapply[Block].run(t => (t.typ, t.indent, t.firstLine, t.lines))(t)

    //// Line ////

    type Line         = LineOf[AST]
    type OptLineOf[T] = LineOf[Option[T]]
    type OptLine      = OptLineOf[AST]
    case class LineOf[+T](elem: T, off: Int) {
      // FIXME: Compatibility mode
      def toOptional: LineOf[Option[T]] = copy(elem = Some(elem))
    }
    object LineOf {
      implicit def ftorLine:          Functor[LineOf] = semi.functor
      implicit def reprLine[T: Repr]: Repr[LineOf[T]] = t => R + t.elem + t.off
    }
    object Line {
      // FIXME: Compatibility mode
      type NonEmpty = Line
      val Required                    = Line
      def apply[T](elem: T, off: Int) = LineOf(elem, off)
      def apply[T](elem: T): LineOf[T] = LineOf(elem, 0)
    }
    object OptLine {
      def apply():          OptLine = Line(None, 0)
      def apply(elem: AST): OptLine = Line(Some(elem))
      def apply(off: Int):  OptLine = Line(None, off)
    }
  }
  object BlockOf {
    implicit def ftorBlock: Functor[BlockOf] = semi.functor
    implicit def reprBlock[T: Repr]: Repr[BlockOf[T]] = t => {
      val emptyLinesRepr = t.emptyLines.map(R + _ + newline)
      val firstLineRepr  = R + t.indent + t.firstLine
      val linesRepr = t.lines.map { line =>
        newline + line.elem.map(_ => t.indent) + line
      }
      R + newline + emptyLinesRepr + firstLineRepr + linesRepr
    }
    implicit def offZipBlock[T]: OffsetZip[BlockOf, T] = _.map((0, _))
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Module //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Module = ASTOf[ModuleOf]
  case class ModuleOf[T](lines: List1[Block.OptLineOf[T]]) extends ShapeOf[T]

  object Module {
    import Block._
    def apply(ls: List1[OptLine]):            Module = ModuleOf(ls)
    def apply(l: OptLine):                    Module = Module(List1(l))
    def apply(l: OptLine, ls: OptLine*):      Module = Module(List1(l, ls.to[List]))
    def apply(l: OptLine, ls: List[OptLine]): Module = Module(List1(l, ls))
    def traverseWithOff(m: Module)(f: (Int, AST) => AST): Module = {
      val lines2 = m.lines.map { line: OptLine =>
        // FIXME: Why line.map does not work?
        LineOf.ftorLine.map(line)(_.map(_.traverseWithOff(f)))
      }
      m.unFix.copy(lines = lines2)
    }
  }
  object ModuleOf {
    implicit def functor:      Functor[ModuleOf]      = semi.functor
    implicit def offsetZip[T]: OffsetZip[ModuleOf, T] = _.map((0, _))
    implicit def repr[T: Repr]: Repr[ModuleOf[T]] =
      t => R + t.lines.head + t.lines.tail.map(newline + _)
  }

  ////////////////////////////////////////////////////////////////////////////
  //// Macro ///////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Macro = ASTOf[MacroOf]
  sealed trait MacroOf[T] extends ShapeOf[T]
  object Macro {

    import org.enso.syntax.text.ast.meta.Pattern

    //// Matched ////

    type Match = ASTOf[MatchOf]
    final case class MatchOf[T](
      pfx: Option[Pattern.Match],
      segs: Shifted.List1[Match.SegmentOf[T]],
      resolved: AST
    ) extends MacroOf[T] {
      def path(): List1[AST] = segs.toList1().map(_.el.head)
    }

    object MatchOf {
      implicit def functor: Functor[MatchOf] = semi.functor
      implicit def offsetZip[T: Repr]: OffsetZip[MatchOf, T] = t => {
        var off = 0
        t.copy(segs = t.segs.map { seg =>
          OffsetZip(seg).map(_.map(_.map(s => {
            val loff = off
            off = Repr(s._2).span
            (s._1 + loff, s._2)
          })))
        })
      }
      implicit def repr[T: Repr]: Repr[MatchOf[T]] = t => {
        val pfxStream = t.pfx.map(_.toStream.reverse).getOrElse(List())
        val pfxRepr   = pfxStream.map(t => R + t.el + t.off)
        val segsRepr  = t.segs.map(s => R + s.head + s.body) // FIXME: We should be able to use here the repr instance of segment
        R + pfxRepr + segsRepr
      }
    }
    object Match {
      val any = UnapplyByType[Match]
      def apply(
        pfx: Option[Pattern.Match],
        segs: Shifted.List1[Match.Segment],
        resolved: AST
      ): Match = MatchOf[AST](pfx, segs, resolved)

      type Segment = SegmentOf[AST]
      final case class SegmentOf[T](
        head: Ident,
        body: Pattern.MatchOf[Shifted[T]]
      ) {
        def isValid: Boolean = body.isValid
        def map(
          f: Pattern.MatchOf[Shifted[T]] => Pattern.MatchOf[Shifted[T]]
        ): SegmentOf[T] =
          copy(body = f(body))
      }
      object SegmentOf {
        def apply[T](head: Ident): SegmentOf[T] =
          SegmentOf(head, Pattern.Match.Nothing())

        //// Instances ////
        implicit def repr[T: Repr]: Repr[SegmentOf[T]] =
          t => R + t.head + t.body

        implicit def offZip[T: Repr]: OffsetZip[SegmentOf, T] = t => {
          t.copy(body = OffsetZip(t.body).map {
            case (i, s) => s.map((i + t.head.repr.span, _))
          })
        }
      }
      implicit class SegmentOps(t: Segment) {
        def toStream: AST.Stream = Shifted(t.head) :: t.body.toStream
      }

    }

    //// Ambiguous ////

    type Ambiguous = ASTOf[AmbiguousOf]
    final case class AmbiguousOf[T](
      segs: Shifted.List1[Ambiguous.Segment],
      paths: Tree[AST, Unit]
    ) extends MacroOf[T]
    object Ambiguous {
      def apply(
        segs: Shifted.List1[Ambiguous.Segment],
        paths: Tree[AST, Unit]
      ): Ambiguous = ASTOf(AmbiguousOf(segs, paths))

      final case class Segment(head: AST, body: Option[SAST])
      object Segment {
        def apply(head: AST): Segment       = Segment(head, None)
        implicit def repr:    Repr[Segment] = t => R + t.head + t.body
      }
    }

    object AmbiguousOf {
      implicit def functor:   Functor[AmbiguousOf]      = semi.functor
      implicit def repr[T]:   Repr[AmbiguousOf[T]]      = t => R + t.segs.map(Repr(_))
      implicit def offZip[T]: OffsetZip[AmbiguousOf, T] = _.map((0, _))
    }

    //// Resolver ////

    type Resolver = Resolver.Context => AST
    object Resolver {
      type Context = ContextOf[AST]
      final case class ContextOf[T](
        prefix: Option[Pattern.Match],
        body: List[Macro.Match.SegmentOf[T]],
        id: ID
      )
      object Context {
        def apply(
          prefix: Option[Pattern.Match],
          body: List[Macro.Match.Segment],
          id: ID
        ): Context = ContextOf(prefix, body, id)
      }
    }

    //// Definition ////

    type Definition = __Definition__
    final case class __Definition__(
      back: Option[Pattern],
      init: List[Definition.Segment],
      last: Definition.LastSegment,
      resolver: Resolver
    ) {
      def path: List1[AST] = init.map(_.head) +: List1(last.head)
      def fwdPats: List1[Pattern] =
        init.map(_.pattern) +: List1(last.pattern.getOrElse(Pattern.Nothing()))
    }
    object Definition {
      import Pattern._

      final case class Segment(head: AST, pattern: Pattern) {
        def map(f: Pattern => Pattern): Segment = copy(pattern = f(pattern))
      }
      object Segment {
        type Tup = (AST, Pattern)
        def apply(t: Tup): Segment = Segment(t._1, t._2)
      }

      final case class LastSegment(head: AST, pattern: Option[Pattern]) {
        def map(f: Pattern => Pattern): LastSegment =
          copy(pattern = pattern.map(f))
      }
      object LastSegment {
        type Tup = (AST, Option[Pattern])
        def apply(t: Tup): LastSegment = LastSegment(t._1, t._2)
      }

      def apply(back: Option[Pattern], t1: Segment.Tup, ts: List[Segment.Tup])(
        fin: Resolver
      ): Definition = {
        val segs    = List1(t1, ts)
        val init    = segs.init
        val lastTup = segs.last
        val last    = (lastTup._1, Some(lastTup._2))
        Definition(back, init, last, fin)
      }

      def apply(back: Option[Pattern], t1: Segment.Tup, ts: Segment.Tup*)(
        fin: Resolver
      ): Definition = Definition(back, t1, ts.toList)(fin)

      def apply(t1: Segment.Tup, t2_ : Segment.Tup*)(
        fin: Resolver
      ): Definition = Definition(None, t1, t2_.toList)(fin)

      def apply(initTups: List[Segment.Tup], lastHead: AST)(
        fin: Resolver
      ): Definition =
        Definition(None, initTups, (lastHead, None), fin)

      def apply(t1: Segment.Tup, last: AST)(fin: Resolver): Definition =
        Definition(List(t1), last)(fin)

      def apply(
        back: Option[Pattern],
        initTups: List[Segment.Tup],
        lastTup: LastSegment.Tup,
        resolver: Resolver
      ): Definition = {
        type PP = Pattern => Pattern
        val applyValidChecker: PP     = _ | ErrTillEnd("unmatched pattern")
        val applyFullChecker: PP      = _ :: ErrUnmatched("unmatched tokens")
        val applyDummyFullChecker: PP = _ :: Nothing()

        val unapplyValidChecker: Pattern.Match => Pattern.Match = {
          case Pattern.Match.Or(_, Left(tgt)) => tgt
          case _                              => throw new Error("Internal error")
        }
        val unapplyFullChecker: Pattern.Match => Pattern.Match = {
          case Pattern.Match.Seq(_, (tgt, _)) => tgt
          case _                              => throw new Error("Internal error")
        }
        val applySegInitCheckers: List[Segment] => List[Segment] =
          _.map(_.map(p => applyFullChecker(applyValidChecker(p))))

        val applySegLastCheckers: LastSegment => LastSegment =
          _.map(p => applyDummyFullChecker(applyValidChecker(p)))

        val unapplySegCheckers
          : List[AST.Macro.Match.Segment] => List[AST.Macro.Match.Segment] =
          _.map(_.map({
            case m @ Pattern.Match.Nothing(_) => m
            case m =>
              unapplyValidChecker(unapplyFullChecker(m))
          }))

        val initSegs           = initTups.map(Segment(_))
        val lastSeg            = LastSegment(lastTup)
        val backPatWithCheck   = back.map(applyValidChecker)
        val initSegsWithChecks = applySegInitCheckers(initSegs)
        val lastSegWithChecks  = applySegLastCheckers(lastSeg)

        def unexpected(ctx: Resolver.Context, msg: String): AST = {
          val pfxStream  = ctx.prefix.map(_.toStream).getOrElse(List())
          val segsStream = ctx.body.flatMap(_.toStream)
          val stream     = pfxStream ++ segsStream
          AST.Invalid.Unexpected(msg, stream)
        }

        def resolverWithChecks(ctx: Resolver.Context) = {
          val pfxFail  = !ctx.prefix.forall(_.isValid)
          val segsFail = !ctx.body.forall(_.isValid)
          if (pfxFail || segsFail) unexpected(ctx, "invalid statement")
          else {
            val ctx2 = ctx.copy(
              prefix = ctx.prefix.map(unapplyValidChecker),
              body   = unapplySegCheckers(ctx.body)
            )
            try resolver(ctx2)
            catch {
              case _: Throwable =>
                unexpected(ctx, "exception during macro resolution")
            }
          }
        }
        __Definition__(
          backPatWithCheck,
          initSegsWithChecks,
          lastSegWithChecks,
          resolverWithChecks
        )
      }

    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////
  //// Space - unaware AST /////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  sealed trait SpacelessASTOf[T] extends ShapeOf[T]

  implicit def ftorSlessAST[T]:   Functor[SpacelessASTOf]      = semi.functor
  implicit def offZipSlessAST[T]: OffsetZip[SpacelessASTOf, T] = _.map((0, _))

  //////////////////////////////////////////////////////////////////////////////
  /// Comment //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Comment = ASTOf[CommentOf]
  case class CommentOf[T](lines: List[String])
      extends SpacelessASTOf[T]
      with Phantom
  object Comment {
    val symbol = "#"
    def apply(lines: List[String]): Comment = ASTOf(CommentOf(lines))
  }

  //// Instances ////

  object CommentOf {
    import Comment._
    implicit def functor[T]: Functor[CommentOf] = semi.functor
    implicit def repr[T]: Repr[CommentOf[T]] =
      R + symbol + symbol + _.lines.mkString("\n")
    // FIXME: How to make it automatic for non-spaced AST?
    implicit def offsetZip[T]: OffsetZip[CommentOf, T] = _.map((0, _))
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Import //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Import = ASTOf[ImportOf]
  case class ImportOf[T](path: List1[Cons]) extends SpacelessASTOf[T]
  object Import {
    def apply(path: List1[Cons]):            Import = ImportOf[AST](path)
    def apply(head: Cons):                   Import = Import(head, List())
    def apply(head: Cons, tail: List[Cons]): Import = Import(List1(head, tail))
    def apply(head: Cons, tail: Cons*):      Import = Import(head, tail.toList)
  }
  object ImportOf {
    implicit def functor[T]: Functor[ImportOf] = semi.functor
    implicit def repr[T]: Repr[ImportOf[T]] =
      t => R + ("import " + t.path.map(_.repr.build()).toList.mkString("."))

    // FIXME: How to make it automatic for non-spaced AST?
    implicit def offsetZip[T]: OffsetZip[ImportOf, T] = _.map((0, _))
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Mixfix //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Mixfix = MixfixOf[AST]
  case class MixfixOf[T](name: List1[Ident], args: List1[T])
      extends SpacelessASTOf[T]

  object Mixfix {
    def apply(name: List1[Ident], args: List1[AST]): Mixfix =
      MixfixOf(name, args)
  }
  object MixfixOf {
    implicit def functor[T]: Functor[MixfixOf] = semi.functor
    implicit def repr[T: Repr]: Repr[MixfixOf[T]] = t => {
      val lastRepr = if (t.name.length == t.args.length) List() else List(R)
      val argsRepr = t.args.toList.map(R + " " + _) ++ lastRepr
      val nameRepr = t.name.toList.map(Repr(_))
      R + (nameRepr, argsRepr).zipped.map(_ + _)
    }
    // FIXME: How to make it automatic for non-spaced AST?
    implicit def offsetZip[T]: OffsetZip[MixfixOf, T] = _.map((0, _))
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Group ///////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Group = ASTOf[GroupOf]
  case class GroupOf[T](body: Option[T]) extends SpacelessASTOf[T]
  object Group {
    val any             = UnapplyByType[Group]
    def unapply(t: AST) = Unapply[Group].run(_.body)(t)
    def apply(body: Option[AST]): Group = GroupOf(body)
    def apply(body: AST):         Group = Group(Some(body))
    def apply(body: SAST):        Group = Group(body.el)
    def apply():                  Group = Group(None)
  }
  object GroupOf {
    implicit def functpr[T]: Functor[GroupOf] = semi.functor
    implicit def repr[T: Repr]: Repr[GroupOf[T]] =
      R + "(" + _.body + ")"
    // FIXME: How to make it automatic for non-spaced AST?
    implicit def offsetZip[T]: OffsetZip[GroupOf, T] = _.map((0, _))
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Def /////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Def = ASTOf[DefOf]
  case class DefOf[T](name: Cons, args: List[T], body: Option[T])
      extends SpacelessASTOf[T]
  object Def {
    val symbol = "def"
    def apply(name: Cons):                  Def = Def(name, List())
    def apply(name: Cons, args: List[AST]): Def = Def(name, args, None)
    def apply(name: Cons, args: List[AST], body: Option[AST]): Def =
      DefOf(name, args, body)
  }
  object DefOf {
    implicit def functor[T]: Functor[DefOf] = semi.functor
    implicit def repr[T: Repr]: Repr[DefOf[T]] =
      t => R + Def.symbol ++ t.name + t.args.map(R ++ _) + t.body
    // FIXME: How to make it automatic for non-spaced AST?
    implicit def offsetZip[T]: OffsetZip[DefOf, T] = _.map((0, _))
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Foreign /////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Foreign = ForeignOf[AST]
  case class ForeignOf[T](indent: Int, lang: String, code: List[String])
      extends SpacelessASTOf[T]
  object Foreign {
    def apply(indent: Int, lang: String, code: List[String]): Foreign =
      ForeignOf(indent, lang, code)
  }
  object ForeignOf {
    implicit def functor[T]: Functor[ForeignOf] = semi.functor
    implicit def repr[T: Repr]: Repr[ForeignOf[T]] = t => {
      val code2 = t.code.map(R + t.indent + _).mkString("\n")
      R + "foreign " + t.lang + "\n" + code2
    }
    // FIXME: How to make it automatic for non-spaced AST?
    implicit def offsetZip[T]: OffsetZip[ForeignOf, T] = _.map((0, _))
  }

  /////////////////////////////////////////////////
  /////////////////////////////////////////////////
  /////////////////////////////////////////////////
  /////////////////////////////////////////////////

  def main() {

    import implicits._

    val fff1 = AST.Ident.BlankOf[AST](): Ident.BlankOf[AST]
    val fff3 = ASTOf(fff1): Blank
    val fff4 = fff3: AST

    println(fff3)
    println(fff4)

    val v1   = Ident.Var("foo")
    val v1_  = v1: AST
    val opr1 = Ident.Opr("+")
    val v2   = App.Prefix(Var("x"), 10, Var("z"))

    println(v1_)
    println(v1.name)
    println(opr1.assoc)

    val str1 = "foo": AST
    println(str1)

    val vx = v2: AST
    vx match {
      case Ident.Blank.any(v) => println(s"blank: $v")
      case Ident.Var.any(v)   => println(s"var: $v")
      case App.Prefix.any(v)  => println(s"app.prefix: $v")
    }

    println(vx.repr)

    val voff  = App.Infix(Var("x"), 1, Opr("+"), 2, Var("y"))
    val voff2 = voff: AST
    voff.traverseWithOff {
      case (i, t) =>
        println(s"> $i = $t")
        t
    }

    println(voff2.zipWithOffset())

    val v1_x = vx.as[Var]
    println(v1_x)
  }
}

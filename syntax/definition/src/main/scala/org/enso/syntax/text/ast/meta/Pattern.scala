package org.enso.syntax.text.ast.meta

import org.enso.data.Shifted
import org.enso.syntax.text.AST
import org.enso.syntax.text.AST.SAST
import org.enso.syntax.text.AST.Stream
import org.enso.syntax.text.ast.Repr
import org.enso.syntax.text.prec.Operator

import scala.annotation.tailrec
import scala.reflect.ClassTag

/////////////////
//// Pattern ////
/////////////////

sealed trait Pattern {
  import Pattern._
  def ::(that: Pattern): Seq     = Seq(that, this)
  def !(that: Pattern):  Except  = Except(that, this)
  def |(that: Pattern):  Or      = Or(this, that)
  def |?(tag: String):   Tag     = Tag(tag, this)
  def or(that: Pattern): Or      = Or(this, that)
  def or(msg: String):   Or      = this.or(Err(msg))
  def many:              Many    = Many(this)
  def many1:             Pattern = Many1(this)
  def tag(tag: String):  Tag     = Tag(tag, this)
  def opt:               Or      = this | Nothing()
  def build:             Build   = Build(this)

  def match_(stream: Stream, reversed: Boolean = false): MatchResult =
    Pattern.matchUnsafe(this, stream, reversed)

  def matchRev(stream: Stream): MatchResult =
    this.match_(stream, reversed = true)

  def matchOpt(stream: Stream, reversed: Boolean): Option[MatchResult] =
    Pattern.matchOpt(this, stream, reversed)
}

object Pattern {
  sealed trait Of[T] extends Pattern
  type Spaced = Option[Boolean]

  final case class Nothing()                         extends Of[Unit]
  final case class Tok(tok: AST, spaced: Spaced)     extends Of[SAST]
  final case class Many(pat: Pattern)                extends Of[List[Match]]
  final case class Seq(p1: Pattern, p2: Pattern)     extends Of[(Match, Match)]
  final case class Build(pat: Pattern)               extends Of[SAST]
  final case class Except(not: Pattern, ok: Pattern) extends Of[Match]
  final case class Or(p1: Pattern, p2: Pattern)      extends Of[Match]
  final case class TillEnd(pat: Pattern)             extends Of[Match]
  final case class Tag(tag: String, pat: Pattern)    extends Of[Match]
  final case class Err(msg: String, pat: Pattern)    extends Of[SAST]
  final case class Cls[T <: AST](spaced: Spaced)(implicit val tag: ClassTag[T])
      extends Of[Shifted[T]]

  object Tok {
    def apply(tok: AST): Tok = Tok(tok, None)
  }

  object Cls {
    def apply[T <: AST: ClassTag](): Cls[T] = new Cls[T](None)
  }

  object Seq {
    def apply(p1: Pattern, p2: Pattern, ps: Pattern*): Pattern =
      ps.headOption match {
        case None     => Seq(p1, p2)
        case Some(p3) => Seq(Seq(p1, p2), p3, ps.tail: _*)
      }
  }

  object Err {
    def apply(msg: String): Err = Err(msg, Nothing())
  }

  object TillEnd {
    def apply(): TillEnd = TillEnd(Nothing())
  }

  //// Conversions ////

  implicit def fromAST(ast: AST): Pattern = Tok(ast)

  //// Smart Constructors ////

  object Opt {
    def apply(pat: Pattern) = pat | Nothing()
    def unapply(t: Pattern): Option[Pattern] = t match {
      case Or(pat, Nothing()) => Some(pat)
      case _                  => None
    }
  }

  object Any {
    def apply(spaced: Option[Boolean] = None): Pattern = Cls[AST](spaced)
    def unapply(t: Pattern)(implicit astCls: ClassTag[AST]): Boolean =
      t match {
        case t @ Cls(None) => t.tag == astCls
        case _             => false
      }
  }

  object NonSpacedAny {
    def apply(): Pattern = Cls[AST](Some(false))
    def unapply(t: Pattern)(implicit astCls: ClassTag[AST]): Boolean =
      t match {
        case t @ Cls(Some(false)) => t.tag == astCls
        case _                    => false
      }
  }

  object End {
    def apply(): TillEnd = TillEnd(Nothing())
    def unapply(t: Pattern): Boolean =
      t match {
        case TillEnd(Nothing()) => true
        case _                  => false
      }
  }

  object Not {
    def apply(pat: Pattern): Pattern = Except(pat, Nothing())
    def unapply(t: Pattern): Option[Pattern] = t match {
      case Except(pat, Nothing()) => Some(pat)
      case _                      => None
    }
  }

  object AnyBut {
    def apply(pat: Pattern): Pattern = Any() ! pat
    def unapply(t: Pattern): Option[Pattern] = t match {
      case Except(pat, Any()) => Some(pat)
      case _                  => None
    }
  }

  // FIXME: check unapply
  object Many1 {
    def apply(pat: Pattern): Seq = Seq(pat, Many(pat))
    def unapply(p: Seq): Option[Pattern] = p match {
      case Seq(p1, Many(p2)) => if (p == p1) Some(p) else None
    }
  }

  object AnyTill {
    def apply(pat: Pattern): Many = Many(AnyBut(pat))
  }

  object TillEndMarkUnmatched {
    def apply(pat: Pattern, msg: String) =
      TillEnd(pat) | (pat :: ErrTillEnd(msg))
  }

  object Expr {
    def apply() = Build(Many1(Any()))
    def unapply(t: Pattern): Boolean = t match {
      case Build(Many1(Any())) => true
      case _                   => false
    }
  }

  object NonSpacedExpr {
    def apply() = Build(AnyBut(Cls[AST.Block]) :: Many(NonSpacedAny()))
    def unapply(t: Pattern): Boolean = t match {
      case Build(Many1(NonSpacedAny())) => true
      case _                            => false
    }
  }

  object SepList {
    def apply(pat: Pattern, div: Pattern): Seq = pat :: (div :: pat).many
    def apply(pat: Pattern, div: Pattern, err: String): Seq = {
      val seg = pat | Err(err, AnyTill(div))
      SepList(seg, div)
    }
  }

  object AnyTillEnd {
    def apply(): Many = Many(Any())
  }

  object ErrTillEnd {
    def apply(msg: String): Err = Err(msg, AnyTillEnd())
  }

  case class MatchResult(elem: Pattern.Match, stream: Stream) {
    def map(fn: Pattern.Match => Pattern.Match): MatchResult =
      copy(elem = fn(elem))
  }

  def buildASTFrom(stream: Stream): Option[Shifted[AST]] =
    Operator.rebuild(stream)

  ///////////////
  //// Match ////
  ///////////////

  /** Result of AST tokens Macro pattern match. */
  type Match = Match.Of[_]
  object Match {
    def apply[T: Repr.Of](pat: Pattern.Of[T], el: T): Match.Of[T] =
      Match.Of(pat, el)

    final case class Of[T: Repr.Of](pat: Pattern.Of[T], el: T)
        extends Repr.Provider {
      val repr = Repr.of(el)

      override def toString =
        s"${pat.getClass.getSimpleName}(${el.toString})"

      def toStream: Stream = this match {
        case Match.Build(t)    => List(t)
        case Match.Cls(t)      => List(t)
        case Match.TillEnd(t)  => t.toStream
        case Match.Err(t)      => List(t)
        case Match.Many(t)     => t.flatMap(_.toStream)
        case Match.Except(t)   => t.toStream
        case Match.Nothing()   => List()
        case Match.Or(t)       => t.toStream
        case Match.Seq(l, r)   => l.toStream ++ r.toStream
        case Match.Tag(tag, t) => t.toStream
        case Match.Tok(t)      => List(t)
      }

      def isValid: Boolean = this match {
        case Match.Build(_)   => true
        case Match.Cls(_)     => true
        case Match.TillEnd(t) => t.isValid
        case Match.Err(_)     => false
        case Match.Many(t)    => t.forall(_.isValid)
        case Match.Except(t)  => t.isValid
        case Match.Nothing()  => true
        case Match.Or(t)      => t.isValid
        case Match.Seq(l, r)  => l.isValid && r.isValid
        case Match.Tag(_, t)  => t.isValid
        case Match.Tok(_)     => true
      }
    }

    //// Smart Deconstructors ////

    object Build {
      def unapply(t: Match): Option[SAST] = t match {
        case Of(_: Pattern.Build, t) => Some(t)
        case _                       => None
      }
    }

    object Cls {
      def apply(t: SAST): Match = Match(Pattern.Cls[AST](), t)
      def unapply(t: Match): Option[SAST] = t match {
        case Of(_: Pattern.Cls[_], t) => Some(t)
        case _                        => None
      }
    }

    object TillEnd {
      def unapply(t: Match): Option[Match] = t match {
        case Of(_: Pattern.TillEnd, t) => Some(t)
        case _                         => None
      }
    }

    object Err {
      def unapply(t: Match): Option[SAST] = t match {
        case Of(_: Pattern.Err, t) => Some(t)
        case _                     => None
      }
    }

    object Many {
      def unapply(t: Match): Option[List[Match]] = t match {
        case Of(_: Pattern.Many, t) => Some(t)
        case _                      => None
      }
    }

    object Except {
      def unapply(t: Match): Option[Match] = t match {
        case Of(_: Pattern.Except, s) => Some(s)
        case _                        => None
      }
    }

    object Nothing {
      def apply() = Match.Of(Pattern.Nothing(), ())
      def unapply(t: Match): Boolean = t match {
        case Of(_: Pattern.Nothing, _) => true
        case _                         => false
      }
    }

    object Or {
      def unapply(t: Match): Option[Match] = t match {
        case Of(_: Pattern.Or, t) => Some(t)
        case _                    => None
      }
    }

    object Seq {
      def apply(p1: Match, p2: Match): Match =
        Match(Pattern.Seq(p1.pat, p2.pat), (p1, p2))
      def unapply(t: Match): Option[(Match, Match)] = t match {
        case Of(_: Pattern.Seq, t) => Some(t)
        case _                     => None
      }
    }

    object Tag {
      def unapply(t: Match): Option[(String, Match)] = t match {
        case Of(tag: Pattern.Tag, t) => Some((tag.tag, t))
        case _                       => None
      }
    }

    object Tok {
      def unapply(t: Match): Option[SAST] = t match {
        case Of(_: Pattern.Tok, t) => Some(t)
        case _                     => None
      }
    }
  }

  //////////////////////////////////
  //// Pattern Match Resolution ////
  //////////////////////////////////

  def streamShift_(off: Int, revStream: AST.Stream): AST.Stream =
    streamShift(off, revStream)._1

  def streamShift(off: Int, revStream: AST.Stream): (AST.Stream, Int) = {
    @tailrec
    def go(off: Int, str: AST.Stream, out: AST.Stream): (AST.Stream, Int) =
      str match {
        case Nil     => (out, off)
        case t :: ts => go(t.off, ts, Shifted(off, t.el) :: out)
      }
    val (nStream, nOff) = go(off, revStream, List())
    (nStream.reverse, nOff)
  }

  /** Unsafe variant of AST Macro tokens pattern matching. If you want to use
    * patterns that could not match all input tokens, use [[matchOpt]] instead.
    */
  def matchUnsafe(
    pattern: Pattern,
    stream: Stream,
    reversed: Boolean = false
  ): MatchResult = {
    matchOpt(pattern, stream, reversed).getOrElse {
      val msg = "Internal error: template pattern segment was unmatched"
      throw new Error(msg)
    }
  }

  /** This function takes a pattern and applies it to AST input stream. The
    * optional parameter 'reversed' is used for prefix (reverse) matching and is
    * used for prefix macro matching. The function assumes that the pattern does
    * not fail.
    */
  def matchOpt(
    pattern: Pattern,
    stream: Stream,
    reversed: Boolean
  ): Option[MatchResult] = {

    def matchList(p: Pattern, stream: Stream): (List[Match], Stream) = {
      @tailrec
      def go(stream: Stream, revOut: List[Match]): (List[Match], Stream) =
        matchStep(p, stream) match {
          case None    => (revOut.reverse, stream)
          case Some(t) => go(t.stream, t.elem :: revOut)
        }
      go(stream, Nil)
    }

    def matchStep(p: Pattern, stream: Stream): Option[MatchResult] = {

      def ret[S: Repr.Of](pat: Pattern.Of[S], res: S, stream: Stream) =
        Some(MatchResult(Pattern.Match(pat, res), stream))

      p match {

        case Pattern.TillEnd(p1) =>
          matchStep(p1, stream) match {
            case None    => None
            case Some(r) => if (r.stream.isEmpty) Some(r) else None
          }

        case p @ Pattern.Nothing() =>
          ret(p, (), stream)

        case p @ Pattern.Tag(tag, pat2) =>
          matchStep(pat2, stream)

        case p @ Pattern.Build(pat2) =>
          matchStep(pat2, stream).map {
            _.map { patMatch =>
              val stream = patMatch.toStream
              val ast =
                if (!reversed) buildASTFrom(stream).get
                else {
                  // When performing reverse pattern match, tokens use
                  // right-offsets instead of left ones, so we need to push them
                  // back before computing AST.
                  val (shiftedStream, off) = streamShift(0, stream.reverse)
                  val shiftedAst           = buildASTFrom(shiftedStream).get
                  shiftedAst.copy(off = off)
                }
              Pattern.Match(p, ast)
            }
          }

        case p @ Pattern.Seq(pat1, pat2) =>
          matchStep(pat1, stream) match {
            case None => None
            case Some(r1) =>
              matchStep(pat2, r1.stream) match {
                case None => None
                case Some(r2) =>
                  ret(p, (r1.elem, r2.elem), r2.stream)
              }
          }

        case p @ Pattern.Cls(spaced) =>
          stream match {
            case Shifted(off, p.tag(t)) :: ss =>
              val ok = spaced match {
                case None => true
                case Some(s) =>
                  (s == (off > 0)) && (!t.isInstanceOf[AST.Block])
              }
              if (ok) ret(p, Shifted(off, t), ss) else None
            case _ => None
          }

        case p @ Pattern.Many(p2) =>
          val (lst, stream2) = matchList(p2, stream)
          ret(p, lst, stream2)

        case p @ Or(p1, p2) =>
          matchStep(p1, stream) match {
            case Some(t) => Some(t)
            case None    => matchStep(p2, stream)
          }

        case p @ Pattern.Tok(tok, spaced) =>
          stream match {
            case Shifted(off, t) :: ss =>
              val ok = spaced.forall(_ == (off > 0))
              if (tok == t && ok) ret(p, Shifted(off, t), ss) else None
            case _ => None
          }

        case p @ Pattern.Err(msg, p1) =>
          matchStep(p1, stream).map {
            _.map { pmatch =>
              Pattern.Match(p, Shifted(AST.Unexpected(msg, pmatch.toStream)))
            }
          }

        case p @ Except(p1, p2) =>
          matchStep(p1, stream) match {
            case Some(_) => None
            case None    => matchStep(p2, stream)
          }
      }
    }
    matchStep(pattern, stream)
  }

}

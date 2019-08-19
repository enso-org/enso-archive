package org.enso.syntax.text

import org.enso.data.Tree
import org.enso.syntax.text.AST._
import org.enso.data.Shifted
import org.enso.syntax.text.ast.meta.Pattern

object DSL {

  trait MixfixBldr[T] {
    def add(m: Macro.Match, i: Int, t: T): Macro.Match
  }
  trait MixfixBldr1[T] {
    def add1(m: Macro.Match, i: Int, t: T): Macro.Match
  }

//  implicit val string_mixfixBuilder: MixfixBldr[String] =
//    (m: Template.Matched, i: Int, t: String) =>
//      implicitly[MixfixBldr[AST]].add(m, i, fromStringRaw(t))
//
//  implicit val ast_mixfixBuilder: MixfixBldr[AST] =
//    (m: Template.Matched, i: Int, t: AST) => {
//
//      def dd() = {
//        val seg = Template.Matched.Segment(t, Pattern.Match.Nothing())
//        m.copy(segments = m.segments :+ Shifted(i, seg))
//      }
//
//      val tail = m.segments.tail
//      if (tail.nonEmpty) {
//        val sss = tail.last.el
//        tail.last.el match {
//          case seg: Template.Matched.Segment =>
//            seg.body match {
//              case Template.Segment.Body.Empty => {
//                val seg2 =
//                  Template.Matched.Segment(
//                    seg.head,
//                    Template.Segment.Body.Expr(Shifted(i, t))
//                  )
//                val tail2 = tail.init :+ Shifted(tail.last.off, seg2)
//                val segs2 = m.segments.copy(tail = tail2)
//                m.copy(segments = segs2)
//              }
//              case _ => dd()
//            }
//          case _ => dd()
//        }
//      } else dd()
//    }
//
//  implicit val string_mixfixBuilder1: MixfixBldr1[String] =
//    (m: Template.Matched, i: Int, t: String) =>
//      implicitly[MixfixBldr1[AST]].add1(m, i, fromStringRaw(t))
//
//  implicit val ast_mixfixBuilder1: MixfixBldr1[AST] =
//    (m: Template.Matched, i: Int, t: AST) => {
//
//      def dd() = {
//        val seg = Template.Matched.Segment(t, Template.Segment.Body.Empty)
//        m.copy(segments = m.segments :+ Shifted(i, seg))
//      }
//
//      val tail = m.segments.tail
//      if (tail.nonEmpty) {
//        tail.last.el match {
//          case seg: Template.Matched.Segment =>
//            seg.body match {
//              case Template.Segment.Body.Empty => {
//                val seg2 =
//                  Template.Matched.Segment(
//                    seg.head,
//                    Template.Segment.Body.Expr(Shifted(i, t))
//                  )
//                val tail2 = tail.init :+ Shifted(tail.last.off, seg2)
//                val segs2 = m.segments.copy(tail = tail2)
//                m.copy(segments = segs2)
//              }
//              case _ => dd()
//            }
//          case _ => dd()
//        }
//      } else dd()
//    }
//
//  implicit class MixfixBuilder_String(t: String) {
//
//    def empty(i: Int, s: String) = Template.Matched(
//      Shifted.List1(
//        Template.Matched.Segment(fromStringRaw(t), Template.Segment.Body.Empty),
//        List(Shifted(i, Template.Matched.Segment(fromStringRaw(s))))
//      )
//    )
//
//    def unmatched(tree: Tree[AST, Unit]): Template.Unmatched =
//      Template.Unmatched(
//        Shifted
//          .List1(Template.Unmatched.Segment(fromStringRaw(t), None), List()),
//        tree
//      )
//
//    def unmatched(lst: Seq[String]): Template.Unmatched = {
//      val args = lst.map(k => List(fromStringRaw(k)) -> (()))
//      val tree = Tree[AST, Unit](args: _*)
//      unmatched(tree)
//    }
//
//    def unmatched_lst(lst: Seq[List[String]]): Template.Unmatched = {
//      val args = lst.map(k => k.map(fromStringRaw) -> (()))
//      val tree = Tree[AST, Unit](args: _*)
//      unmatched(tree)
//    }
//
//    def II(s: String):          Template.Matched = empty(0, s)
//    def I_I(s: String):         Template.Matched = empty(1, s)
//    def I__I(s: String):        Template.Matched = empty(2, s)
//    def I___I(s: String):       Template.Matched = empty(3, s)
//    def I____I(s: String):      Template.Matched = empty(4, s)
//    def I_____I(s: String):     Template.Matched = empty(5, s)
//    def I______I(s: String):    Template.Matched = empty(6, s)
//    def I_______I(s: String):   Template.Matched = empty(7, s)
//    def I________I(s: String):  Template.Matched = empty(8, s)
//    def I_________I(s: String): Template.Matched = empty(9, s)
//
//    def Ix(t: Tree[AST, Unit]): Template.Unmatched = unmatched(t)
//    def Ix(t: String*):         Template.Unmatched = unmatched(t)
//    def Ixx(t: List[String]*):  Template.Unmatched = unmatched_lst(t)
//
//    def I(s: AST):          Template.Matched = fromStringRaw(t)._addSeg_(0)(s)
//    def I_(s: AST):         Template.Matched = fromStringRaw(t)._addSeg_(1)(s)
//    def I__(s: AST):        Template.Matched = fromStringRaw(t)._addSeg_(2)(s)
//    def I___(s: AST):       Template.Matched = fromStringRaw(t)._addSeg_(3)(s)
//    def I____(s: AST):      Template.Matched = fromStringRaw(t)._addSeg_(4)(s)
//    def I_____(s: AST):     Template.Matched = fromStringRaw(t)._addSeg_(5)(s)
//    def I______(s: AST):    Template.Matched = fromStringRaw(t)._addSeg_(6)(s)
//    def I_______(s: AST):   Template.Matched = fromStringRaw(t)._addSeg_(7)(s)
//    def I________(s: AST):  Template.Matched = fromStringRaw(t)._addSeg_(8)(s)
//    def I_________(s: AST): Template.Matched = fromStringRaw(t)._addSeg_(9)(s)
//
//    def I1(s: AST):          Template.Matched = fromStringRaw(t)._addSeg1_(0)(s)
//    def I1_(s: AST):         Template.Matched = fromStringRaw(t)._addSeg1_(1)(s)
//    def I1__(s: AST):        Template.Matched = fromStringRaw(t)._addSeg1_(2)(s)
//    def I1___(s: AST):       Template.Matched = fromStringRaw(t)._addSeg1_(3)(s)
//    def I1____(s: AST):      Template.Matched = fromStringRaw(t)._addSeg1_(4)(s)
//    def I1_____(s: AST):     Template.Matched = fromStringRaw(t)._addSeg1_(5)(s)
//    def I1______(s: AST):    Template.Matched = fromStringRaw(t)._addSeg1_(6)(s)
//    def I1_______(s: AST):   Template.Matched = fromStringRaw(t)._addSeg1_(7)(s)
//    def I1________(s: AST):  Template.Matched = fromStringRaw(t)._addSeg1_(8)(s)
//    def I1_________(s: AST): Template.Matched = fromStringRaw(t)._addSeg1_(9)(s)
//  }

//  implicit class MixfixBuilder_AST(t: AST) {
//    def _addSeg_(i: Int)(s: AST): Template.Matched = Template.Matched(
//      Shifted.List1(
//        Template.Matched.Segment(
//          t,
//          Template.Segment.Body.Expr(Shifted(i, s))
//        ),
//        Nil
//      )
//    )
//    def _addSeg1_(i: Int)(s: AST): Template.Matched = Template.Matched(
//      Shifted.List1(
//        Template.Matched.Segment(t, Template.Segment.Body.Expr(Shifted(i, s))),
//        Nil
//      )
//    )
//    val I          = _addSeg_(0)(_)
//    val I_         = _addSeg_(1)(_)
//    val I__        = _addSeg_(2)(_)
//    val I___       = _addSeg_(3)(_)
//    val I____      = _addSeg_(4)(_)
//    val I_____     = _addSeg_(5)(_)
//    val I______    = _addSeg_(6)(_)
//    val I_______   = _addSeg_(7)(_)
//    val I________  = _addSeg_(8)(_)
//    val I_________ = _addSeg_(9)(_)
//
//    val I1          = _addSeg1_(0)(_)
//    val I1_         = _addSeg1_(1)(_)
//    val I1__        = _addSeg1_(2)(_)
//    val I1___       = _addSeg1_(3)(_)
//    val I1____      = _addSeg1_(4)(_)
//    val I1_____     = _addSeg1_(5)(_)
//    val I1______    = _addSeg1_(6)(_)
//    val I1_______   = _addSeg1_(7)(_)
//    val I1________  = _addSeg1_(8)(_)
//    val I1_________ = _addSeg1_(9)(_)
//  }

//  implicit class MixfixBuilder_Mixfix(t: Template.Matched) {
//    type M[T]  = MixfixBldr[T]
//    type M1[T] = MixfixBldr1[T]
//
//    def add[T: M](i: Int, s: T)   = implicitly[M[T]].add(t, i, s)
//    def add1[T: M1](i: Int, s: T) = implicitly[M1[T]].add1(t, i, s)
//
//    def unmatched(tree: Tree[AST, Unit]): Template.Unmatched = {
//      val segments2 = t.segments.map {
//        case seg: Template.Matched.Segment =>
//          seg.body match {
//            case Template.Segment.Body.Expr(e) =>
//              Template.Unmatched.Segment(seg.head, Some(e))
//            case _ => Template.Unmatched.Segment(seg.head, None)
//          }
//      }
//      Template.Unmatched(segments2, tree)
//    }
//
//    def unmatched(lst: Seq[String]): Template.Unmatched = {
//      val args = lst.map(k => List(fromStringRaw(k)) -> (()))
//      val tree = Tree[AST, Unit](args: _*)
//      unmatched(tree)
//    }
//
//    def unmatched_lst(lst: Seq[List[String]]): Template.Unmatched = {
//      val args = lst.map(k => k.map(fromStringRaw) -> (()))
//      val tree = Tree[AST, Unit](args: _*)
//      unmatched(tree)
//    }
//
//    def Ix(t: String*):        Template.Unmatched = unmatched(t)
//    def Ixx(t: List[String]*): Template.Unmatched = unmatched_lst(t)
//
//    def I[T: M](s: T)          = add(0, s)
//    def I_[T: M](s: T)         = add(1, s)
//    def I__[T: M](s: T)        = add(2, s)
//    def I___[T: M](s: T)       = add(3, s)
//    def I____[T: M](s: T)      = add(4, s)
//    def I_____[T: M](s: T)     = add(5, s)
//    def I______[T: M](s: T)    = add(6, s)
//    def I_______[T: M](s: T)   = add(7, s)
//    def I________[T: M](s: T)  = add(8, s)
//    def I_________[T: M](s: T) = add(9, s)
//
//    def I1[T: M1](s: T)          = add1(0, s)
//    def I1_[T: M1](s: T)         = add1(1, s)
//    def I1__[T: M1](s: T)        = add1(2, s)
//    def I1___[T: M1](s: T)       = add1(3, s)
//    def I1____[T: M1](s: T)      = add1(4, s)
//    def I1_____[T: M1](s: T)     = add1(5, s)
//    def I1______[T: M1](s: T)    = add1(6, s)
//    def I1_______[T: M1](s: T)   = add1(7, s)
//    def I1________[T: M1](s: T)  = add1(8, s)
//    def I1_________[T: M1](s: T) = add1(9, s)
//  }
}

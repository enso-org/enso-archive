package org.enso.syntax.text.ast.meta

import org.enso.data
import org.enso.data.List1
import org.enso.data.Shifted
import org.enso.syntax.text.AST
import org.enso.syntax.text.AST.Ident
import org.enso.syntax.text.AST.Macro
import Pattern.streamShift

import scala.annotation.tailrec

/////////////////
//// Builder ////
/////////////////

class Builder(
  head: Ident,
  offset: Int                  = 0,
  lineBegin: Boolean           = false,
  val isModuleBuilder: Boolean = false
) {
  var context: Builder.Context           = Builder.Context()
  var macroDef: Option[Macro.Definition] = None
  var current: Builder.Segment           = new Builder.Segment(head, offset, lineBegin)
  var revSegs: List[Builder.Segment]     = List()

  def startSegment(ast: Ident, off: Int): Unit = {
    revSegs ::= current
    current        = new Builder.Segment(ast)
    current.offset = off
  }

  def merge(that: Builder): Unit = {
    val (newRevBody, thatStream) = that.build(current.revBody)
    current.revBody = thatStream.concat(newRevBody).toList
  }

  def build(revStreamL: AST.Stream): (AST.Stream, AST.Stream1) = {
    val revSegBldrs = List1(current, revSegs)
    val result = {
      macroDef match {
        case None =>
          val revSegs = revSegBldrs.map { segBldr =>
            val optAst = segBldr.buildAST()
            val seg    = Macro.Ambiguous.Segment(segBldr.ast, optAst)
            Shifted(segBldr.offset, seg)
          }
          val segments = revSegs.reverse
          val head     = segments.head
          val tail     = segments.tail
          val paths    = context.tree.dropValues()
          val stream   = Shifted.List1(head.el, tail)
          val template = Macro.Ambiguous(stream, paths)
          val newTok   = Shifted(head.off, template)
          (revStreamL, List1(newTok))

        case Some(mdef) =>
          val revSegTps     = mdef.fwdPats.reverse
          val revSegsOuts   = revSegBldrs.zipWith(revSegTps)(_.build(_))
          val revSegs       = revSegsOuts.map(_._1)
          val revSegStreams = revSegsOuts.map(_._2)
          val stream        = revSegStreams.head.reverse
          val segs          = revSegs.reverse

          val (segs2, pfxMatch, newLeftStream) = mdef.backPat match {
            case None => (segs, None, revStreamL)
            case Some(pat) =>
              val fstSegOff                = segs.head.off
              val (revStreamL2, lastLOff)  = streamShift(fstSegOff, revStreamL)
              val pfxMatch                 = pat.matchRev(revStreamL2)
              val revStreamL3              = pfxMatch.stream
              val streamL3                 = revStreamL3.reverse
              val (streamL4, newFstSegOff) = streamShift(lastLOff, streamL3)
              val revStreamL4              = streamL4.reverse
              val newFirstSeg              = segs.head.copy(off = newFstSegOff)
              val newSegs                  = segs.copy(head = newFirstSeg)
              (newSegs, Some(pfxMatch.elem), revStreamL4)

          }

          val shiftSegs = Shifted.List1(segs2.head.el, segs2.tail)

          if (!revSegStreams.tail.forall(_.isEmpty)) {
            throw new Error(
              "Internal error: not all template segments were fully matched"
            )
          }

          val template = Macro.Match(pfxMatch, shiftSegs)
          val newTok   = Shifted(segs2.head.off, template)

          val result = stream match {
            case Nil     => List1(newTok)
            case s :: ss => List1(s, ss) :+ newTok
          }
          (newLeftStream, result)
      }
    }
    result
  }

  if (isModuleBuilder)
    macroDef = Some(
      Macro.Definition(AST.Blank -> Pattern.Expr()) { (_, _) =>
        throw new scala.Error("Impossible happened")
      }
    )

  def buildAsModule(): AST = {
    build(List())._2.head.el match {
      case Macro.Match(_, segs) =>
        segs.head.body.toStream match {
          case Nil    => throw new scala.Error("Impossible happened.")
          case s :: _ => s.el
        }
      case _ => throw new scala.Error("Impossible happened.")
    }
  }
}

object Builder {
  def moduleBuilder(): Builder =
    new Builder(AST.Blank, isModuleBuilder = true, lineBegin = true)

  /////////////////
  //// Context ////
  /////////////////

  case class Context(tree: Registry.Tree, parent: Option[Context]) {
    def lookup(t: AST): Option[Registry.Tree] = tree.get(t)
    def isEmpty:        Boolean               = tree.isEmpty

    @tailrec
    final def parentLookup(t: AST): Boolean = {
      parent match {
        case None => false
        case Some(p) =>
          p.lookup(t) match {
            case None    => p.parentLookup(t)
            case Some(_) => true
          }
      }
    }
  }
  object Context {
    def apply():                    Context = Context(data.Tree(), None)
    def apply(tree: Registry.Tree): Context = Context(tree, None)
  }

  /////////////////
  //// Segment ////
  /////////////////

  class Segment(
    val ast: Ident,
    var offset: Int        = 0,
    val lineBegin: Boolean = false
  ) {
    import Macro._
    var revBody: AST.Stream = List()

    def buildAST(): Option[Shifted[AST]] = Pattern.buildASTFrom(revBody.reverse)

    def build(
      pat: Pattern,
      reversed: Boolean = false
    ): (Shifted[Match.Segment], AST.Stream) = {
      val stream = revBody.reverse
      pat.matchOpt(stream, lineBegin, reversed) match {
        case None =>
          throw new Error(
            "Internal error: template pattern segment was unmatched"
          )
        case Some(rr) =>
          (Shifted(offset, Match.Segment(ast, rr.elem)), rr.stream)
      }
    }

    //////////////////////////////////////

    override def toString: String =
      s"SegmentBuilder($offset, $revBody)"
  }
}

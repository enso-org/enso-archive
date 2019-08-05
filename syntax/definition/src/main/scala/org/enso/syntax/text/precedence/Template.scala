package org.enso.syntax.text.precedence

import org.enso.data.List1
import org.enso.data.Shifted
import org.enso.data.Tree
import org.enso.syntax.text.AST
import org.enso.syntax.text.AST._

import scala.annotation.tailrec

object Template {

  val Template = AST.Template

  def exprList(ast: AST): Shifted.List1[AST] = {
    @tailrec
    def go(ast: AST, out: AST.Stream): Shifted.List1[AST] = ast match {
      case App(fn, off, arg) => go(fn, Shifted(off, arg) :: out)
      case ast               => Shifted.List1(ast, out)
    }
    go(ast, List())
  }

  //////////////////
  //// Registry ////
  //////////////////

  final case class Registry() {
    var tree = Tree[AST, List1[Template.Segment.Pattern.Class]]()

    override def toString: String =
      tree.toString

    def insert(t: Template.Definition): Unit =
      tree += t.segments.toList.map(_._1) -> t.segments.map(_._2)
  }

  object Registry {
    type T = Tree[AST, List1[Template.Segment.Pattern.Class]]
    def apply(ts: Template.Definition*): Registry = {
      val registry = new Registry()
      ts.foreach(registry.insert)
      registry
    }
  }

  /////////////////
  //// Context ////
  /////////////////

  case class Context(tree: Registry.T, parent: Option[Context]) {
    def get(t: AST): Option[Registry.T] =
      tree.get(t)

    def isEmpty: Boolean =
      tree.isEmpty

    @tailrec
    final def parentCheck(t: AST): Boolean = {
      parent match {
        case None => false
        case Some(p) =>
          p.get(t) match {
            case None    => p.parentCheck(t)
            case Some(_) => true
          }
      }
    }
  }

  object Context {
    def apply():                 Context = Context(Tree(), None)
    def apply(tree: Registry.T): Context = Context(tree, None)
  }

  /////////////////
  //// Builder ////
  /////////////////

  class SegmentBuilder(val ast: AST) {
    import Template.Segment.Pattern
    import Template._

    var offset: Int         = 0
    var revBody: AST.Stream = List()

    def buildAST() = revBody match {
      case Nil           => None
      case seg1 :: seg2_ => Some(Operator.rebuild(List1(seg1, seg2_)))
    }

    def buildAST2(revLst: AST.Stream): Option[Shifted[AST]] =
      revLst match {
        case Nil           => None
        case seg1 :: seg2_ => Some(Operator.rebuild(List1(seg1, seg2_)))
      }

    def build(
      tp: Pattern.Class
//      last: Boolean
    ): Shifted[Segment.Class] = {
//      resolveStep(tp, revBody.reverse)

      val segment2 = tp.resolve(ast, revBody.reverse, resolver)

//      val optAst = buildAST()
//      val segment = tp match {
//        case t: Pattern.Empty =>
//          val empty = Segment(t, ast, ())
//          if (last) empty
//          else optAst.map(Segment.Empty.NonEmpty(ast, _)).getOrElse(empty)
//        case t: Pattern.Expr0 =>
//          Segment(t, ast, optAst)
//        case t: Pattern.Expr =>
//          optAst.map(Segment(t, ast, _)).getOrElse(Segment.Expr.Empty(ast))
//      }
      Shifted(offset, segment2)
    }

    case class ResolveResult[+T](elem: T, stream: AST.Stream) {
      def map[S](fn: T => S): ResolveResult[S] =
        ResolveResult(fn(elem), stream)
    }
    implicit def tupleToResolveResult[T](t: (T, AST.Stream)): ResolveResult[T] =
      ResolveResult(t._1, t._2)

    def resolveList[T](
      p: Pattern[T],
      stream: AST.Stream
    ): (List[T], AST.Stream) = {
      @tailrec
      def go(stream: AST.Stream, out: List[T]): (List[T], AST.Stream) =
        resolveStep(p, stream) match {
          case None    => (out, stream)
          case Some(t) => go(t.stream, t.elem :: out)
        }
      go(stream, Nil)
    }

    def resolveStep[T](
      p: Pattern[T],
      stream: AST.Stream
    ): Option[ResolveResult[T]] = p match {
      case Pattern.Empty =>
        Some(((), stream))
      case Pattern.Expr =>
        buildAST2(stream.reverse).map((_, Nil))
      case Pattern.Option(p2) =>
        val fail = (None, stream)
        Some(resolveStep(p2, stream).map(_.map(Some(_))).getOrElse(fail))
      case Pattern.List(p2) =>
        val (lst, stream2) = resolveList(p2, stream)
        Some((lst, stream2))

      case Pattern.List1(p2) =>
        resolveStep(p2, stream) match {
          case None => None
          case Some(t) =>
            val (tail, stream2) = resolveList(p2, t.stream)
            Some((List1(t.elem, tail), stream2))
        }
      case p: Pattern.Token[_] => {
        stream match {
          case Shifted(off, p.tag(t)) :: ss => Some((Shifted(off, t), ss))
          case _                            => None
        }
      }
      case _ => ??? // FIXME

    }

    val resolver: Pattern.Resolver = new Pattern.Resolver {
      def resolve[T](pat: Pattern[T], stream: Stream) = {
        val t = resolveStep(pat, stream)
        t.map(s => (s.elem, s.stream))
      }
    }

    override def toString: String =
      s"SegmentBuilder($offset, $revBody)"
  }

  class MixfixBuilder(ast: AST) {
    var context: Context                                      = Context()
    var mixfix: Option[List1[Template.Segment.Pattern.Class]] = None
    var current: SegmentBuilder                               = new SegmentBuilder(ast)
    var revSegs: List[SegmentBuilder]                         = List()
  }

  def partition(t: AST): AST = {

    var builder: MixfixBuilder = new MixfixBuilder(Blank)
    builder.mixfix = Some(List1(Template.Segment.Pattern.Expr, Nil))
    var builderStack: List[MixfixBuilder] = Nil

    def pushBuilder(ast: AST, off: Int): Unit = {
//      println(s"pushBuilder($off)")
      builderStack +:= builder
      builder                = new MixfixBuilder(ast)
      builder.current.offset = off
    }

    def popBuilder(): Unit = {
//      println("popBuilder")
      builder      = builderStack.head
      builderStack = builderStack.tail
    }

    def pushSegment(ast: AST, off: Int): Unit = {
//      println(s"pushSegment($off)")
      builder.revSegs ::= builder.current
      builder.current        = new SegmentBuilder(ast)
      builder.current.offset = off
    }

    import Template._

    def buildHardcodedRegistry(): Registry = {
      import Template.Segment.Pattern._

      Registry(
        Template.Definition(
          Opr("(") -> Option(Expr),
          Opr(")") -> Empty
        ),
        Template.Definition(
          Var("if") -> Expr,
          Var("then") -> Expr
        ),
        Template.Definition(
          Var("if") -> Expr,
          Var("then") -> Expr,
          Var("else") -> Expr
        ),
        Template.Definition(
          Var("import") -> Expr
        ),
        Template.Definition(
          Var("type") -> Token[AST.Ident]
        )
      )
    }

    val hardcodedRegistry = buildHardcodedRegistry()

    val root = Context(hardcodedRegistry.tree)

    def stripLastSegment(
      revSegs: List1[Shifted[Template.Segment.Class]]
    ): (List1[Shifted[Template.Segment.Class]], AST.Stream) = {
      val lastSeg                = revSegs.head
      val (lastSegEl, revStream) = lastSeg.el.stripped()
      val lastSeg2               = Shifted(lastSeg.off, lastSegEl)
      val revSegments            = List1(lastSeg2, revSegs.tail)
      (revSegments.reverse, revStream.reverse)
    }

    def close(): AST.Stream1 = {
//      println(s"\n\n-----------------------------------\n\n")
      val revSegBldrs = List1(builder.current, builder.revSegs)
//      println(s"revSegs =")
//      pprint.pprintln(revSegs, width    = 50, height = 10000)
//      pprint.pprintln(builder.mixfix, width = 50, height = 10000)
      val result = {
        builder.mixfix match {
          case None =>
            val revSegs = revSegBldrs.map { segBldr =>
              val optAst = segBldr.buildAST()
              Shifted(segBldr.offset, Unmatched.Segment(segBldr.ast, optAst))
            }
            val segments = revSegs.reverse
            val head     = segments.head
            val tail     = segments.tail
            val paths    = builder.context.tree.dropValues()
            val template =
              Template.Unmatched(Shifted.List1(head.el, tail), paths)
            val newTok = Shifted(head.off, template)
            List1(newTok)

          case Some(ts) =>
            val revSegTps      = ts.reverse
            val revSegs        = revSegBldrs.zipWith(revSegTps)(_.build(_))
            val (segs, stream) = stripLastSegment(revSegs)
            val template       = Template(Shifted.List1(segs.head.el, segs.tail))
            val newTok         = Shifted(segs.head.off, template)

            stream match {
              case Nil     => List1(newTok)
              case s :: ss => List1(s, ss) :+ newTok
            }
        }
      }

//      println("Close Result:")
//      pprint.pprintln(result, width = 50, height = 10000)
      result
    }

    def close2() = {
//      println("close2")
      val subAst = close()
      popBuilder()
      builder.current.revBody = subAst.concat(builder.current.revBody).toList
    }

    @tailrec
    def go(input: AST.Stream): AST = {
      input match {
        case Nil =>
          if (builderStack.isEmpty) {
//            println("End of input (not in stack)")
            close().head match {
              case Shifted(
                  _,
                  Template(
                    Shifted.List1(
                      Template.Segment(
                        Segment.Pattern.Expr,
                        _,
                        body
                      ),
                      Nil
                    )
                  )
                  ) =>
                body
                  .asInstanceOf[Shifted[AST]]
                  .el // FIXME: How to do it better?
              case _ => throw new Error("Impossible happened.")
            }

          } else {
//            println("End of input (in stack)")
            close2()
            go(input)
          }
        case t1 :: t2_ =>
//          println(s"> $t1")

          builder.context.get(t1.el) match {
            case Some(tr) =>
//              println(">> New segment")
              pushSegment(t1.el, t1.off)
//              builder.mixfix  = builder.mixfix.map(Some(_)).getOrElse(tr.value)
              builder.mixfix  = tr.value.map(Some(_)).getOrElse(builder.mixfix)
              builder.context = builder.context.copy(tree = tr)
              go(t2_)

            case None =>
              root.get(t1.el) match {
                case Some(tr) =>
//                  println(">> Root")
                  val context = builder.context
                  pushBuilder(t1.el, t1.off)
                  builder.mixfix  = tr.value
                  builder.context = Context(tr, Some(context))
                  go(t2_)
                case None =>
//                  println(s"PARENT CHECK (${builder.current.ast}, ${t1.el})")
                  val currentClosed = builder.context.isEmpty
                  val parentPrecWin = (builder.current.ast, t1.el) match {
                    case (_: Opr, _) => false
                    case (_, _: Opr) => true
                    case _           => false
                  }
                  val parentBreak = builder.context.parentCheck(t1.el)
                  (currentClosed || parentPrecWin) && parentBreak match {
                    case true =>
//                      println("Parent close")
                      close2()
                      go(input)
                    case false =>
//                      println(">> Add token")
                      builder.current.revBody ::= t1
                      go(t2_)
                  }
              }
          }

      }
    }

//      go(Context(registry.tree, None), exprList(t).toList(), List())
//    println("START")
    val elst = exprList(t).toList()
//    pprint.pprintln(elst, width = 50, height = 10000)
    go(elst)
  }

//    def partition(t: AST) = {
//
//      def go(context:Context, lst: SpacedList[AST], out: List[Spaced[AST]]): List[Spaced[AST]]= {
//        context.get(lst.head) match {
//          case None => lst.tail match {
//            case Nil => out
//            case t :: ts => go(context, SpacedList(t,ts), lst.head :: out)
//          }
//          case _ => ???
//        }
//      }
//
//      go(Context(registry.tree, None), exprList(t), List())
//    }

  // format: off


  
  def run(module:Module): Module =
    module.map(_.map(partition))
}
package org.enso.syntax.text.precedence

import org.enso.data.List1
import org.enso.data.Tree
import org.enso.data.Shifted
import org.enso.syntax.text.AST
import org.enso.syntax.text.AST._

import scala.annotation.tailrec

object Mixfix {

  val Mixfix = AST.Mixfix

  def exprList(ast: AST): Shifted.List1[AST] = {
    @tailrec
    def go(ast: AST, out: List[Shifted[AST]]): Shifted.List1[AST] = ast match {
      case App(fn, off, arg) => go(fn, Shifted(off, arg) :: out)
      case ast               => Shifted.List1(ast, out)
    }
    go(ast, List())
  }

  //////////////////
  //// Registry ////
  //////////////////

  final case class Registry() {
    var tree = Tree[AST, List1[Mixfix.Segment.Type.Any]]()

    override def toString: String =
      tree.toString

    def insert(t: Mixfix.Definition): Unit =
      tree += t.segments.toList.map(_._1) -> t.segments.map(_._2)
  }

  object Registry {
    type T = Tree[AST, List1[Mixfix.Segment.Type.Any]]
    def apply(ts: Mixfix.Definition*): Registry = {
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
    import Mixfix._

    var offset: Int                 = 0
    var revBody: List[Shifted[AST]] = List()

    def buildAST() = revBody match {
      case Nil => None
      case seg1 :: seg2_ =>
        Some(Operator.rebuild(List1(seg1, seg2_)))
    }

    def build(tp: Segment.Type.Any, last: Boolean): Shifted[Segment.Class] = {
      val optAst = buildAST()
      val segment = tp match {
        case t: Segment.Empty =>
          val empty = Segment(t, ast, ())
          if (last) empty
          else optAst.map(Segment.Empty.NonEmpty(ast, _)).getOrElse(empty)
        case t: Segment.Expr =>
          Segment(t, ast, optAst)
        case t: Segment.Expr1 =>
          optAst.map(Segment(t, ast, _)).getOrElse(Segment.Expr1.Empty(ast))
      }
      Shifted(offset, segment)
    }

    override def toString: String =
      s"SegmentBuilder($offset, $revBody)"
  }

  class MixfixBuilder(ast: AST) {
    var context: Context                               = Context()
    var mixfix: Option[List1[Mixfix.Segment.Type.Any]] = None
    var current: SegmentBuilder                        = new SegmentBuilder(ast)
    var revSegments: List[SegmentBuilder]              = List()
  }

  def partition(t: AST): AST = {

    var builder: MixfixBuilder = new MixfixBuilder(Blank)
    builder.mixfix = Some(List1(Mixfix.Segment.Expr1(), Nil))
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
      builder.revSegments ::= builder.current
      builder.current        = new SegmentBuilder(ast)
      builder.current.offset = off
    }

    import Mixfix._

    val hardcodedRegistry = Registry(
      Mixfix.Definition(
        Opr("(") -> Mixfix.Segment.Expr(),
        Opr(")") -> Mixfix.Segment.Empty()
      ),
      Mixfix.Definition(
        Var("if") -> Mixfix.Segment.Expr1(),
        Var("then") -> Mixfix.Segment.Expr1()
      ),
      Mixfix.Definition(
        Var("if") -> Mixfix.Segment.Expr1(),
        Var("then") -> Mixfix.Segment.Expr1(),
        Var("else") -> Mixfix.Segment.Expr1()
      ),
      Mixfix.Definition(
        Var("import") -> Mixfix.Segment.Expr1()
      )
    )

    val root = Context(hardcodedRegistry.tree)

    def close(): List[Shifted[AST]] = {
//      println(s"\n\n-----------------------------------\n\n")
      val revSegments = builder.current :: builder.revSegments
//      println(s"revSegments =")
//      pprint.pprintln(revSegments, width    = 50, height = 10000)
//      pprint.pprintln(builder.mixfix, width = 50, height = 10000)
      val result = {
        builder.mixfix match {
          case None =>
            val segments = revSegments.reverseMap { segBldr =>
              val optAst = segBldr.buildAST()
              Shifted(segBldr.offset, Unmatched.Segment(segBldr.ast, optAst))
            }

            val possiblePaths = builder.context.tree.dropValues()
            val mx = segments match {
              case s :: ss =>
                Shifted(
                  s.off,
                  Mixfix.Unmatched(Shifted.List1(s.el, ss), possiblePaths)
                )
            }
            List(mx)

          case Some(ts) =>
            val revSegTypes = ts.toList.reverse
            val revSegDefs  = revSegTypes.zip(revSegments)

            def reverseMap[T, S](t: List[T])(f: (T, Boolean) => S): List[S] = {
              @tailrec
              def go[T, S](
                f: (T, Boolean) => S,
                lst: List[T],
                out: List[S]
              ): List[S] = {
                lst match {
                  case Nil     => out
                  case l :: ls => go(f, ls, f(l, false) :: out)
                }
              }
              t match {
                case Nil     => Nil
                case l :: ls => go(f, ls, f(l, true) :: Nil)
              }

            }

            val segments = reverseMap(revSegDefs) {
              case ((tp, segBldr), t) => segBldr.build(tp, t)
            }

            val mx = segments match {
              case s :: ss => Shifted(s.off, Mixfix(Shifted.List1(s.el, ss)))
            }

            val suffix: List[Shifted[AST]] = revSegDefs.head match {
              case (tp, segBldr) =>
                tp match {
                  case t: Segment.Empty => segBldr.revBody
                  case _                => List()
                }
            }
            suffix :+ mx
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
      builder.current.revBody = subAst ++ builder.current.revBody
    }

    @tailrec
    def go(input: List[Shifted[AST]]): AST = {
      input match {
        case Nil =>
          if (builderStack.isEmpty) {
//            println("End of input (not in stack)")
            close() match {
              case Shifted(
                    _,
                    Mixfix(
                      Shifted.List1(
                        Mixfix.Segment(Segment.Expr1(), _, body: Shifted[AST]),
                        Nil
                      )
                    )
                  ) :: _ =>
                body.el
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
package org.enso.syntax.text.precedence

import org.enso.data.Compare._
import org.enso.data.List1
import org.enso.data.Shifted
import org.enso.syntax.text.AST._
import org.enso.syntax.text.AST
import org.enso.syntax.text.precedence

import scala.annotation.tailrec

// format: off
// Without it, the code is a mess:
// https://github.com/scalameta/scalafmt/issues/1454

object Operator {
  import Internal._
  
  /** Build a single AST node from AST stream by applying operator precedence
    * rules, including per-operator precedence and distance-based precedence.
    */
  def rebuild(astList: List1[Shifted[AST]]): Shifted[AST] = {
    val segments = precedence.Distance.partition(astList)
    val flatExpr = segments.map(_.map(rebuildSubExpr))
    flatExpr.map(rebuildExpr)
  }
  
  final object Internal {
    def oprToToken(ast: AST): Opr = ast match {
      case t: Opr => t
      case _      => Opr.app
    }

    def rebuildSubExpr(seg: Distance.Segment): AST =
      rebuildExpr(seg) match {
        case t: App.Sides => t.opr
        case t => t
      }

    def rebuildExpr(seg: Distance.Segment): AST =
      rebuildExpr(Shifted.List1(seg.head, seg.tail.map(Shifted(_))))

    def rebuildExpr(seg: Shifted.List1[AST]): AST = {
      final case class Input(seg: List[Shifted[AST]], stack: Shifted.List1[AST])
      implicit def _input(t: (List[Shifted[AST]], Shifted.List1[AST])): Input =
        Input(t._1, t._2)

      @tailrec
      def go(input: Input): AST = input.seg match {
        case Nil => reduceAll(input.stack)
        case seg1 :: seg2_ => 

          val shift  = (seg2_, seg1 +: input.stack)
          val reduce = (input.seg, reduceHead(input.stack))

          def handleAssoc(ast1: AST, ast2: AST) = {
            val op1 = oprToToken(ast1)
            val op2 = oprToToken(ast2)
            compare(op1.prec, op2.prec) match {
              case GT => shift
              case LT => reduce
              case EQ => (op1.assoc, op2.assoc) match {
                case (Assoc.Left, Assoc.Left) => reduce
                case _                        => shift
              }
            }
          }

          input.stack.head match {
            case stack1: Opr => seg1.el match {
              case seg1: Opr => go(handleAssoc(seg1, stack1))
              case _         => go(shift)
            }
            case _ => input.stack.tail match {
              case Nil         => go(shift)
              case stack2 :: _ => go(handleAssoc(seg1.el, stack2.el))
            }
          }
      }

      go(seg.tail, Shifted.List1(seg.head))
    }

    def reduceHead(stack: Shifted.List1[AST]): Shifted.List1[AST] = {
      stack.head match {
        case s1: Opr => stack.tail match {
          case Nil => (App.Sides(s1), Nil)
          case s2 :: s3_ => s2.el match {
            case _: Opr => (App.Sides(s1), s2 :: s3_)
            case _      => (App.Left(s2.el, s2.off, s1), s3_)
          }
        }
        case t1 => stack.tail match {
          case Nil => stack
          case s2 :: s3 :: s4_ => s2.el match {
            case v2: Opr => s3.el match {
              case _: Opr => (App.Right(v2, s2.off, t1), s3 :: s4_)
              case _      => (App.Infix(s3.el, s3.off, v2, s2.off, t1), s4_)
            }
            case v2 => (App(v2, s2.off, t1), s3 :: s4_)
          }

          case s2 :: s3_ => s2.el match {
            case v2: Opr => (App.Right(v2, s2.off, t1), s3_)
            case v2      => (App(v2, s2.off, t1), s3_)
          }
        }
      }
    }

    @tailrec
    def reduceAll(stack: Shifted.List1[AST]): AST = {
      stack.tail match {
        case Nil => reduceHead(stack).head
        case _   => reduceAll(reduceHead(stack))
      }
    }
  }
}

package org.enso.syntax.text.prec

import org.enso.Logger
import org.enso.data.Shifted
import org.enso.syntax.text.AST
import org.enso.syntax.text.ast.meta.Builder
import org.enso.syntax.text.ast.meta.Builtin

import scala.annotation.tailrec

object Macro {
  val logger = new Logger()

  //////////////////
  //// Registry ////
  //////////////////

  def run(module: AST.Module): AST.Module =
    module.map(transform(_))

  def transform(t: AST): AST = {
    val root                        = Builder.Context(Builtin.registry.tree)
    var builder: Builder            = Builder.moduleBuilder()
    var builderStack: List[Builder] = Nil

    def pushBuilder(name: AST.Ident, off: Int): Unit = logger.trace {
      builderStack +:= builder
      builder = new Builder(name, off)
    }

    def popBuilder(): Option[Builder] = logger.trace {
      builderStack match {
        case Nil => None
        case b :: bs =>
          val out = builder
          builder      = b
          builderStack = bs
          Some(out)
      }
    }
    @tailrec
    def finalize(): AST = {
      popBuilder() match {
        case Some(bldr) =>
          logger.log("End of input (in stack)")
          builder.merge(bldr)
          finalize()
        case None =>
          logger.log("End of input (not in stack)")
          builder.buildAsModule()
      }
    }

    @tailrec
    def go(input: AST.Stream): AST = {
      input match {
        case Nil => finalize()
        case (t1 @ Shifted(off, el1: AST.Ident)) :: t2_ =>
          logger.log(s"Token $t1")
          builder.context.lookup(el1) match {
            case Some(tr) =>
              logger.log("New segment")
              builder.startSegment(el1, off)
              builder.macroDef =
                tr.value.map(Some(_)).getOrElse(builder.macroDef)
              builder.context = builder.context.copy(tree = tr)
              go(t2_)

            case None =>
              root.lookup(el1) match {
                case Some(tr) =>
                  logger.log("New macro")
                  val context = builder.context
                  pushBuilder(el1, t1.off)
                  builder.macroDef = tr.value
                  builder.context  = Builder.Context(tr, Some(context))
                  go(t2_)

                case None =>
                  val currentClosed = builder.context.isEmpty
                  val parentPrecWin = (builder.current.ast, el1) match {
                    case (_: AST.Opr, _) => false
                    case (_, _: AST.Opr) => true
                    case _               => false
                  }
                  val parentBreak = builder.context.parentLookup(el1)
                  (currentClosed || parentPrecWin) && parentBreak match {
                    case true =>
                      logger.log("Parent close")
                      val subBuilder = builder
                      popBuilder()
                      builder.merge(subBuilder)
                      go(input)
                    case false =>
                      logger.log("Add token")
                      builder.current.revBody ::= t1
                      go(t2_)
                  }
              }
          }
        case (t1 @ Shifted(off, el1: AST.Block)) :: t2_ =>
          val nt1 = Shifted(off, el1.map(transform))
          builder.current.revBody ::= nt1
          go(t2_)

        case t1 :: t2_ =>
          builder.current.revBody ::= t1
          go(t2_)

      }
    }
    val stream = AST.tokenize(t).toList()
    go(stream)
//    val stream = AST.tokenize(t)
//    val ss     = List1(Shifted(stream.head), stream.tail)
//    val ss2    = Operator.rebuildNonSpaced(ss)
//    go(stream)
  }
}

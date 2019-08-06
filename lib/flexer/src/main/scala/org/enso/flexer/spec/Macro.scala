package org.enso.flexer.spec

import org.enso.flexer.Parser

import scala.reflect.macros.blackbox.Context
import scala.reflect.runtime.universe

// FIXME: Needs to be refactored. Contains deprecated API usage
object Macro {

  def compileImpl[P <: Parser](
    c: Context
  )(p: c.Expr[() => P]): c.Expr[() => P] = {
    import c.universe._
    val tree   = p.tree
    val expr   = q"$tree()"
    val parser = c.eval(c.Expr[P](c.untypecheck(expr.duplicate)))
    val groups = c.internal
      .createImporter(universe)
      .importTree(universe.Block(parser.state.registry.map(_.generate()): _*))

    val superClassName = tree match {
      case Select(_, name) => name
      case _ =>
        println("ERROR: Wrong shape")
        println("Expected Select(_, name), got:")
        println(showRaw(tree))
        throw new Error("Wrong shape")
    }

    val groupsRebind = new Transformer {
      override def transform(tree: Tree): Tree = tree match {
        case Select(Ident(base), name) =>
          val base2 = if (base == superClassName) q"this" else Ident(base)
          super.transform(Select(base2, name))
        case node => super.transform(node)
      }
    }

    val reboundGroups = groupsRebind.transform(groups)

    val addGroupDefs = new Transformer {
      override def transform(tree: Tree): Tree = tree match {
        case Template(parents, self, body) =>
          val exprs = q"..$reboundGroups;None".asInstanceOf[Block].stats
          Template(parents, self, body ++ exprs)
        case node => super.transform(node)
      }
    }

    val clsDef = c.parse(s"final class __Parser__ extends $tree")
    val tgtDef = addGroupDefs.transform(clsDef)
    c.Expr[() => P](q"$tgtDef; () => { new __Parser__ () }")
  }

}

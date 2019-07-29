package org.enso.flexer

import scala.reflect.macros.blackbox.Context
import scala.reflect.runtime.{universe => u}

object Macro {

  type Base[T] = ParserBase[T]
  type In[T]   = () => Base[T]
  type Out[T]  = () => Base[T]

  def compile[T](p: In[T]): Out[T] =
    macro compileImpl[T]

  def compileImpl[T: c.WeakTypeTag](
    c: Context
  )(p: c.Expr[In[T]]): c.Expr[Out[T]] = {
    import c.universe._
    val tree   = p.tree
    val expr   = q"$tree()"
    val parser = c.eval(c.Expr[Base[T]](c.untypecheck(expr.duplicate)))
    val groups = c.internal
      .createImporter(u)
      .importTree(u.Block(parser.groupsx.map(_.generate()): _*))

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
    c.Expr[Out[T]](q"$tgtDef; () => { new __Parser__ () }")
  }

}

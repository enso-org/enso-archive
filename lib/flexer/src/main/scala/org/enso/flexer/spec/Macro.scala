package org.enso.flexer.spec

import org.enso.flexer.Parser
import org.enso.lint.Unused

import scala.reflect.macros.blackbox.Context

// FIXME: Needs to be refactored. Contains deprecated API usage
object Macro {

  def print(c: Context, msg: String) = c.echo(c.enclosingPosition, msg)

  def quoteImpl[T](c: Context)(program: c.Expr[T]): c.Expr[String] = {
    import c.universe._
    val tree = new Transformer {
      override def transform(tree: Tree): Tree = tree match {
        case Select(This(TypeName(_)), name) =>
          super.transform(Ident(name))
        case node => super.transform(node)
      }
    }.transform(program.tree)
    c.Expr(q"${showCode(tree)}")
  }
  def quote[T](program: T): String = macro Macro.quoteImpl[T]

  def compileImpl[T: c.WeakTypeTag, P: c.WeakTypeTag](
    c: Context
  )(p: c.Expr[P])(ev: c.Expr[P <:< Parser[T]]): c.Expr[() => P] = {
    import c.universe._
    Unused(ev)
    val tree   = p.tree
    val expr   = q"$tree"
    val parser = c.eval(c.Expr[Parser[T]](c.untypecheck(expr.duplicate)))
    val groups = q"..${parser.state.registry.map(_.generate(c))}"
    val (superClassName, tree2) = tree match {
      case Apply(Select(tree2 @ Select(_, name), _), _) => (name, tree2)
      case _ =>
        throw new Error(
          s""" ERROR: Wrong shape
             | Expected Apply(Select(Select(_, name), _), _), got:
             | ${showRaw(tree)}
             |""".stripMargin
        )
    }

    val addGroupDefs = new Transformer {
      override def transform(tree: Tree): Tree = tree match {
        case Template(parents, self, body) =>
          val exprs = q"..$groups;None".asInstanceOf[Block].stats
          Template(parents, self, body ++ exprs)
        case node => super.transform(node)
      }
    }

    val clsDef = c.parse(s"final class __Parser__ extends $tree2")
    val tgtDef = addGroupDefs.transform(clsDef)
    c.Expr[() => P](q"$tgtDef; () => { new __Parser__ () }")
  }

}

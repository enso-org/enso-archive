package org.enso.macros

import scala.reflect.macros.blackbox.Context
import scala.language.experimental.macros

object Func {
  implicit def apply[P, R](fn: Function1[P, R]): Func[P, R] =
    macro apply_impl[P, R]

  def apply_impl[P: c.WeakTypeTag, R: c.WeakTypeTag](
    c: Context
  )(fn: c.Expr[Function1[P, R]]): c.Expr[Func[P, R]] = {
    import c.universe._
    var names    = List[String]()
    var namesNum = 0
    object transformer extends Transformer {
      override def transform(tree: Tree): Tree = {
        tree match {
          case Apply(Select(qual, name), args) => {
            if (name.toString == "beginGroup") {
              val gname = args.head.toString()
              val id    = namesNum
              names = gname :: names
              namesNum += 1
              Apply(
                Select(qual, name),
                List(Ident(TermName(s"___${id}___")))
              )
            } else super.transform(tree)
          }
          case _ => super.transform(tree)
        }
      }
    }

    val tree2 = transformer.transform(fn.tree)
    names = names.reverse

    var lst = Select(
      Select(
        Select(Ident(TermName("scala")), TermName("collection")),
        TermName("immutable")
      ),
      TermName("List")
    )

    val grpList = Apply(lst, names.map(s => Ident(TermName(s))))
    val fnCode  = show(tree2)
    c.Expr(q"new Func($fn, replaceGroupSymbols(${fnCode}, ${grpList}))")
  }
}

class Func[P, R](val fn: P => R, description: String) extends Function1[P, R] {
  def apply(p: P):       R      = fn(p)
  override def toString: String = description
}

object Func0 {
  implicit def apply[R](fn: => R): Func0[R] =
    macro apply_impl[R]

  def apply_impl[R: c.WeakTypeTag](
    c: Context
  )(fn: c.Tree): c.Expr[Func0[R]] = {
    import c.universe._
    var names    = List[String]()
    var namesNum = 0
    object transformer extends Transformer {
      override def transform(tree: Tree): Tree = {
        tree match {
          case Apply(Select(qual, name), args) => {
            if (name.toString == "beginGroup") {
              val gname = args.head.toString()
              val id    = namesNum
              names = gname :: names
              namesNum += 1
              Apply(
                Select(qual, name),
                List(Ident(TermName(s"___${id}___")))
              )
            } else super.transform(tree)
          }
          case _ => super.transform(tree)
        }
      }
    }

    val tree2 = transformer.transform(fn)
    names = names.reverse

    var lst = Select(
      Select(
        Select(Ident(TermName("scala")), TermName("collection")),
        TermName("immutable")
      ),
      TermName("List")
    )

    val grpList = Apply(lst, names.map(s => Ident(TermName(s))))
    val fnCode  = show(tree2)
    c.Expr[Func0[R]](
      q"new Func0($fn, replaceGroupSymbols(${fnCode}, ${grpList}))"
    )
//    c.Expr[Func0[R]](
//      q"new Func0($fn, ${fnCode})"
//    )
  }
}

class Func0[R](fn: => R, description: String) extends Function0[R] {
  def apply():           R      = fn
  override def toString: String = description
}

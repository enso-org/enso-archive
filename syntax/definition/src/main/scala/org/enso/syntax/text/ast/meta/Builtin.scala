package org.enso.syntax.text.ast.meta

import org.enso.data.List1
import org.enso.data.Shifted
import org.enso.syntax.text.AST
import org.enso.syntax.text.AST.Macro.Definition
import org.enso.syntax.text.AST.Cons
import org.enso.syntax.text.AST.Opr
import org.enso.syntax.text.AST.Var
import org.enso.syntax.text.ast.Repr

object Builtin {

  val registry: Registry = {

    def internalError = throw new Error("Internal error")

    val def_group = Definition(
      Opr("(") -> Pattern.Opt(Pattern.Expr()),
      Opr(")") -> Pattern.Nothing()
    ) {
      case (None, List(st1, _)) =>
        st1.body.toStream match {
          case List()  => AST.Group()
          case List(t) => AST.Group(t)
          case _       => internalError
        }
      case _ => internalError
    }

    val def_def = Definition(
      Var("def") -> {
        import Pattern._
        val head = Cls[Cons].or("missing name").tag("name")
        val args =
          Pattern.NonSpacedExpr_().tag("parameter").many.tag("parameters")
        val body = Cls[AST.Block].tag("body").opt
        head :: args :: body
      }
    ) {
      case (None, List(st1)) =>
        import Pattern.Match._
        st1.body match {
          case Seq(Cls(name), Seq(Many(argsMatches), bodyMatch)) =>
            val args = argsMatches.map {
              case Build(t) => t.el
              case _        => internalError
            }
            val body = bodyMatch match {
              case Cls(Shifted(off, block: AST.Block)) => Some(block)
              case Nothing()                           => None
              case _                                   => internalError
            }
            name.el match {
              case n: AST.Cons => AST.Def(n, args, body)
              case _           => internalError
            }

          case t => internalError
        }
    }

    val def_import = Definition(
      Var("import") ->
      Pattern.SepList(Pattern.Cls[Cons], AST.Opr("."), "expected module name")
    ) {
      case (None, List(s1)) =>
        import Pattern.Match._
        s1.body match {
          case Seq(headMatch, Many(tailMatch)) =>
            def unwrapSeg(lseg: Pattern.Match): Cons =
              lseg.toStream match {
                case List(Shifted(_, t @ Cons(_))) => t
                case _                             => internalError
              }
            val head = unwrapSeg(headMatch)
            val tail = tailMatch.map {
              case Seq(Tok(Shifted(_, Opr("."))), seg) => unwrapSeg(seg)
              case _                                   => internalError
            }
            AST.Import(head, tail)
        }
      case _ => internalError
    }

    val def_if_then = Definition(
      Var("if")   -> Pattern.Expr(),
      Var("then") -> Pattern.Expr()
    ) {
      case (None, List(s1, s2)) =>
        (s1.body.toStream, s2.body.toStream) match {
          case (List(t1), List(t2)) =>
            AST.Mixfix(List1(s1.head, s2.head), List1(t1.el, t2.el))
          case _ => internalError
        }
      case _ => internalError
    }

    val def_if_then_else = Definition(
      Var("if")   -> Pattern.Expr(),
      Var("then") -> Pattern.Expr(),
      Var("else") -> Pattern.Expr()
    ) {
      case (None, List(s1, s2, s3)) =>
        (s1.body.toStream, s2.body.toStream, s3.body.toStream) match {
          case (List(t1), List(t2), List(t3)) =>
            AST.Mixfix(
              List1(s1.head, s2.head, s3.head),
              List1(t1.el, t2.el, t3.el)
            )
          case _ => internalError
        }
      case _ => internalError
    }

    val def_arrow = Definition(
      Some(Pattern.NonSpacedExpr().or(Pattern.Expr())),
      Opr("->") -> Pattern.NonSpacedExpr().or(Pattern.Expr())
    ) {
      case (Some(pfx), List(s1)) =>
        (pfx.toStream, s1.body.toStream) match {
          case (List(l), List(r)) => AST.App(l.el, Opr("->"), r.el)
          case _                  => internalError
        }
    }

    // Unfortunately, assignment operator has to be defined as macro unless
    // operator - like macros behave like real operators and can have different
    // precedences. The precedence of assignment have to be lower than the
    // arrow. This design makes it behave like `->`, which may not be desirable.
    // For example, `f x= g h` will parse as `f (x = g h)`.

    val def_assign = Definition(
      Some(Pattern.NonSpacedExpr().or(Pattern.Expr())),
      Opr("=") -> Pattern.NonSpacedExpr().or(Pattern.Expr())
    ) {
      case (Some(pfx), List(s1)) =>
        (pfx.toStream, s1.body.toStream) match {
          case (List(l), List(r)) => AST.App(l.el, Opr("="), r.el)
          case t                  => internalError
        }
    }

    val def_skip = Definition(
      Some(Pattern.NonSpacedExpr().or(Pattern.Expr())),
      Opr("#=") -> Pattern.NonSpacedExpr().or(Pattern.Expr())
    ) {
      case (Some(pfx), List(s1)) =>
        (pfx.toStream, s1.body.toStream) match {
          case (List(l), List(r)) => AST.App(l.el, Opr("#="), r.el)
          case t                  => internalError
        }
    }

    val def_foreign = Definition(
      Var("foreign") -> (Pattern.Cls[AST.Cons]() :: Pattern.Cls[AST.Block]())
    ) {
      case (None, List(s1)) =>
        s1.body.toStream match {
          case List(langAST, Shifted(_, bodyAST: AST.Block)) =>
            val indent     = bodyAST.indent
            val lang       = langAST.el.show()
            val body       = bodyAST.show()
            val bodyLines  = body.split("\\r?\\n").toList.drop(1)
            val bodyLines2 = bodyLines.map(_.drop(indent))
            AST.Foreign(indent, lang, bodyLines2)
          case _ => internalError
        }
      case _ => internalError
    }

    val def_comment = Definition(
      Opr("#") -> Pattern
        .FromBegin(Pattern.Any().many)
        .or(Pattern.AnyBut(Pattern.Cls[AST.Block]).many)
    ) {
      case (None, List(s1)) =>
        val body = s1.body.toStream
          .map(t => (Repr.R + t.off + t.el.repr).show())
          .mkString("")
        AST.Comment(body)

      case _ => internalError
    }

    Registry(
      def_group,
      def_if_then,
      def_if_then_else,
      def_import,
      def_def,
      def_arrow,
      def_assign,
      def_foreign,
      def_comment
    )
  }

}

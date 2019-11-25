package org.enso.compiler.generate

import org.enso.syntax.text.{AST, Debug}

/** This object contains view patterns that allow matching on the parser AST for
  * more sophisticated constructs.
  *
  * These view patterns are implemented as custom unapply methods that only
  * return [[Some]] when more complex conditions are met.
  */
object AstView {
  object Assignment {
    val assignmentOpSym = AST.Ident.Opr("=")

    def unapply(ast: AST): Option[(AST, AST)] = {
      ast match {
        case AST.App.Infix.any(ast) =>
          val left  = ast.larg
          val op    = ast.opr
          val right = ast.rarg

          if (op == assignmentOpSym) {
            left match {
              case name @ AST.Ident.Var(_) => Some((name, right))
              case _                       => None
            }
          } else {
            None
          }
        case _ => None
      }
    }
  }

  object Lambda {
    val lambdaOpSym = AST.Ident.Opr("->")

    def unapply(ast: AST): Option[(List[AST], AST)] = {
      ast match {
        case AST.App.Infix.any(ast) =>
          val left  = ast.larg
          val op    = ast.opr
          val right = ast.rarg

          if (op == lambdaOpSym) {
            left match {
              case LambdaParamList(args) => Some((args, right))
              case _                     => None
            }
          } else {
            None
          }
        case _ => None
      }
    }
  }

  // TODO [AA] Return the AST representing each arg, in a flat list
  /** x y (z = def), ... */
  object ArgList {
    val argListSep = AST.Ident.Opr(",")

    def unapply(ast: AST): Option[List[AST]] = {
      val args = matchArgList(ast)

      if (args.isEmpty) {
        None
      } else {
        Some(args)
      }
    }

    // FIXME [AA] Not currently tested in a mixture of arg types:
    //  https://github.com/luna/enso/issues/343
    // TODO [AA] handle invalid things in the arg list properly
    // Irrefutable matches (including ones with no args), vars, and defaults
    def matchArgList(ast: AST): List[AST] = {
      ast match {
        case AST.Group(code) =>
          matchArgList(
            code.getOrElse(throw new RuntimeException("Empty group"))
          )
        case cons @ Application(_, _) => List(cons)
        case app @ AST.App.Prefix(fn, arg) =>
          fn match {
            case AST.Ident.Cons(_) => List(app)
            case _ =>
              matchArgList(fn) ++ matchArgList(arg)
          }
        case default @ Assignment(_, _) => List(default)
        case AST.Ident.Var.any(ast)     => List(ast)
        case _ =>
          println(Debug.pretty(ast.toString))
          throw new RuntimeException("should not happen")
      }
    }
  }

  object Constructor {
    def unapply(arg: AST): Option[(AST, List[AST])] = {
      arg match {
        case Application(fn, args) =>
          fn match {
            case AST.Ident.Cons(_) => Some((fn, args))
            case _                 => None
          }
        case _ => None
      }
    }
  }

  object LambdaParamList {
    def unapply(ast: AST): Option[List[AST]] = {
      ast match {
        case Application(fn, args) => Some(fn :: args)
      }
    }
  }

  object Application {

    //TODO named arguments
    /**
      *
      * @param ast
      * @return the constructor, and a list of its arguments
      */
    def unapply(ast: AST): Option[(AST, List[AST])] = {
      println("===== AST =====")
      println(Debug.pretty(ast.toString))
      println("===============")
      matchApplication(ast)

    }

    def matchApplication(ast: AST): Option[(AST, List[AST])] = {
      ast match {
        case AST.App.Prefix(fn, arg) =>
          val fnRecurse = matchApplication(fn)

          fnRecurse match {
            case Some((fnCons, fnArg)) => Some((fnCons, fnArg :+ arg))
            case None                  => Some((fn, List(arg)))
          }

        case _ => None
      }
    }
  }
}

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
        // TODO [AA] This really isn't true........
        case _ => Some(List(ast))
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

  object MethodDefinition {
    def unapply(ast: AST): Option[(List[AST], AST, AST)] = ast match {
      case Assignment(lhs, rhs) =>
        lhs match {
          case MethodReference(targetPath, name) => Some((targetPath, name, rhs))
          case _ =>
            None
        }
      case _ => None
    }
  }

  object MethodReference {
    val pathSeparator = AST.Ident.Opr(".")
    def unapply(ast: AST): Option[(List[AST], AST)] = {
      ast match {
        case AST.App.Infix(left, op, right) =>
          if (op == pathSeparator) {
            right match {
              case AST.Ident.Var(_) => Some((matchMethodReference(left), right))
              case _                => None

            }
          } else {
            None
          }
        case _ => None
      }
    }

    def matchMethodReference(ast: AST): List[AST] = {
      ast match {
        case AST.App.Infix(left, op, right) =>
          if (op == pathSeparator) {
            right match {
              case AST.Ident.Var(_) => matchMethodReference(left) :+ right
              case _                => List()

            }
          } else {
            List()
          }
        case _ => List()
      }
    }
  }
}

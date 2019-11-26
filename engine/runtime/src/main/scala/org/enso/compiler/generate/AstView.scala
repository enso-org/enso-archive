package org.enso.compiler.generate

import org.enso.syntax.text.{AST, Debug}

/** This object contains view patterns that allow matching on the parser AST for
  * more sophisticated constructs.
  *
  * These view patterns are implemented as custom unapply methods that only
  * return [[Some]] when more complex conditions are met.
  */
object AstView {
  object Binding {
    val bindingOpSym = AST.Ident.Opr("=")

    def unapply(ast: AST): Option[(AST, AST)] = {
      ast match {
        case AST.App.Infix.any(ast) =>
          val left  = ast.larg
          val op    = ast.opr
          val right = ast.rarg

          if (op == bindingOpSym) {
            Some((left, right))
          } else {
            None
          }
        case _ => None
      }
    }
  }

  object Assignment {
    val assignmentOpSym = AST.Ident.Opr("=")

    def unapply(ast: AST): Option[(AST, AST)] = {
      ast match {
        case Binding(left @ AST.Ident.Var(_), right) => Some((left, right))
        case _                                       => None
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
    //TODO suspended arguments

    def unapply(ast: AST): Option[List[AST]] = {
      ast match {
        case SpacedList(args) => Some(args)
        // TODO [AA] This really isn't true........
        case _ => Some(List(ast))
      }
    }
  }

  object AssignedArgument {
    def unapply(ast: AST): Option[(AST, AST)] = Assignment.unapply(ast)
  }

  object Application {
    //TODO named arguments
    def unapply(ast: AST): Option[(AST, List[AST])] =
      SpacedList.unapply(ast).flatMap {
        case fun :: args => Some((fun, args))
        case _           => None
      }
  }

  object SpacedList {

    /**
      *
      * @param ast
      * @return the constructor, and a list of its arguments
      */
    def unapply(ast: AST): Option[List[AST]] = {
      matchSpacedList(ast)
    }

    def matchSpacedList(ast: AST): Option[List[AST]] = {
      ast match {
        case AST.App.Prefix(fn, arg) =>
          val fnRecurse = matchSpacedList(fn)

          fnRecurse match {
            case Some(headItems) => Some(headItems :+ arg)
            case None            => Some(List(fn, arg))
          }

        case _ => None
      }
    }
  }

  object MethodDefinition {
    def unapply(ast: AST): Option[(List[AST], AST, AST)] = {
      ast match {
        case Binding(lhs, rhs) =>
          lhs match {
            case MethodReference(targetPath, name) =>
              Some((targetPath, name, rhs))
            case _ =>
              None
          }
        case _ =>
          println("NOT AN ASSIGNMENT")
          None
      }
    }
  }

  object Path {
    val pathSeparator = AST.Ident.Opr(".")

    def unapply(ast: AST): Option[List[AST]] = {
      val path = matchPath(ast)

      if (path.isEmpty) {
        None
      } else {
        Some(path)
      }
    }

    def matchPath(ast: AST): List[AST] = {
      ast match {
        case AST.App.Infix(left, op, right) =>
          if (op == pathSeparator) {
            right match {
              case AST.Ident.any(right) => matchPath(left) :+ right
              case _                    => List()
            }
          } else {
            List()
          }
        case AST.Ident.any(ast) => List(ast)
        case _                  => List()
      }
    }
  }

  object MethodReference {
    def unapply(ast: AST): Option[(List[AST], AST)] = {
      ast match {
        case Path(segments) =>
          if (segments.length >= 2) {
            val consPath = segments.dropRight(1)
            val maybeVar = segments.last

            val isValid = consPath.collect {
                case a @ AST.Ident.Cons(_) => a
              }.length == consPath.length

            if (isValid) {
              maybeVar match {
                case AST.Ident.Var(_) => Some((consPath, maybeVar))
                case _                => None
              }
            } else {
              None
            }
          } else {
            None
          }
        case _ => None
      }
    }
  }
}

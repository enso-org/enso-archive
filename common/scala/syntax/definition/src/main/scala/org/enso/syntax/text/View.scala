package org.enso.syntax.text

/** This object contains view patterns that allow matching on the parser AST for
  * more sophisticated constructs.
  *
  * These view patterns are implemented as custom unapply methods that only
  * return [[Some]] when more complex conditions are met.
  */
object View {
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
              case ArgList(args) => Some((args, right))
              case _             => None
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
            case _ => None
          }
        case _ => None
      }
    }
  }

  object Application {

    /**
      *
      * @param ast
      * @return the constructor, and a list of its arguments
      */
    def unapply(ast: AST): Option[(AST, List[AST])] = {
      println(Debug.pretty(ast.toString))

      val tmp@(cons, args) = matchApplication(ast)

      println(tmp)

      cons match {
        case Some(cons) => Some((cons, args))
        case None       => None
      }
    }

    def matchApplication(ast: AST): (Option[AST], List[AST]) = {
      ast match {
        case AST.App.Prefix(fn, arg) =>
          fn match {
//            case cons @ Constructor(_, _) => (None, List(cons))
            case arg @ AST.Ident.Var(_)   => (None, List(arg))
            case cons @ AST.Ident.Cons(_) => (Some(cons), List())
            case AST.App.Prefix(fn, arg) =>
              val t1@(fnCons, fnArgs)   = matchApplication(fn)
              val t2@(argCons, argArgs) = matchApplication(arg)

              println(t1)
              println(t2)

              fnCons match {
                case Some(fnName) =>
                  argCons match {
                    case Some(name) => throw new RuntimeException("oops")
                    case None       => (Some(fnName), fnArgs ++ argArgs)
                  }
                case None => argCons match {
                  case Some(argName) => (Some(argName), fnArgs ++ argArgs)
                  case None       => (None, fnArgs ++ argArgs)
                }
              }
            case _ => (None, List())
          }

        case _ => (None, List())
      }
    }
  }
}

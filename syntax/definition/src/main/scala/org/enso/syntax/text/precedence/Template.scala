package org.enso.syntax.text.precedence

import org.enso.data.List1
import org.enso.data.Shifted
import org.enso.data
import org.enso.syntax.text.AST
import org.enso.syntax.text.AST._
import org.enso.syntax.text.AST.Template.Segment.Body

import scala.annotation.tailrec

import cats.implicits._

object Template {

  val Template = AST.Template
  import Template._

  def exprList(ast: AST): Shifted.List1[AST] = {
    @tailrec
    def go(ast: AST, out: AST.Stream): Shifted.List1[AST] = ast match {
      case _App(fn, off, arg) => go(fn, Shifted(off, arg) :: out)
      case ast                => Shifted.List1(ast, out)
    }
    go(ast, List())
  }

  //////////////////
  //// Registry ////
  //////////////////

  final case class Registry() {
    var tree: Registry.Tree = data.Tree()

    override def toString: String =
      tree.toString

    def insert(t: Definition.Spec[Definition]): Unit =
      tree += t.el.segments.toList.map(_._1) -> t.map(_.segments.map(_._2))

    def get(path: List1[AST]): Option[Registry.Value] =
      tree.getValue(path.toList)

  }

  object Registry {
    type Value = Definition.Spec[List1[Template.Segment.Pattern]]
    type Tree  = data.Tree[AST, Value]
    def apply(ts: Definition.Spec[Definition]*): Registry = {
      val registry = new Registry()
      ts.foreach(registry.insert)
      registry
    }
  }

  /////////////////
  //// Context ////
  /////////////////

  case class Context(tree: Registry.Tree, parent: Option[Context]) {
    def get(t: AST): Option[Registry.Tree] =
      tree.get(t)

    def isEmpty: Boolean =
      tree.isEmpty

    @tailrec
    final def parentCheck(t: AST): Boolean = {
      parent match {
        case None => false
        case Some(p) =>
          p.get(t) match {
            case None    => p.parentCheck(t)
            case Some(_) => true
          }
      }
    }
  }

  object Context {
    def apply():                    Context = Context(data.Tree(), None)
    def apply(tree: Registry.Tree): Context = Context(tree, None)
  }

  /////////////////
  //// Builder ////
  /////////////////

  class SegmentBuilder(val ast: AST) {
    import Template.Segment.Pattern
    import Template._

    var offset: Int         = 0
    var revBody: AST.Stream = List()

    def buildAST() = revBody match {
      case Nil           => None
      case seg1 :: seg2_ => Some(Operator.rebuild(List1(seg1, seg2_)))
    }

    def buildAST2(revLst: AST.Stream): Option[Shifted[AST]] =
      revLst match {
        case Nil           => None
        case seg1 :: seg2_ => Some(Operator.rebuild(List1(seg1, seg2_)))
      }

    def build(tp: Pattern): Shifted[Segment] = {
      val stream = revBody.reverse
      val segment2 = resolveStep(tp, stream) match {
        case None => Segment.Unmatched(tp, ast, stream)
        case Some(rr) =>
          rr.stream match {
            case Nil     => Segment.Valid(ast, rr.elem)
            case s :: ss => Segment.Unsaturated(ast, rr.elem, List1(s, ss))
          }
      }

      Shifted(offset, segment2)
    }

    //////////////////////////////////////

    case class ResolveResult(
      elem: Body,
      stream: AST.Stream
    )

    def resolveList(
      p: Pattern,
      stream: AST.Stream
    ): (List[Body], AST.Stream) = {
      @tailrec
      def go(
        stream: AST.Stream,
        revOut: List[Body]
      ): (List[Body], AST.Stream) =
        resolveStep(p, stream) match {
          case None    => (revOut.reverse, stream)
          case Some(t) => go(t.stream, t.elem :: revOut)
        }
      go(stream, Nil)
    }

    def resolveStep(p: Pattern, stream: AST.Stream): Option[ResolveResult] = {
      import Body._

      def ret(result: Body, stream: AST.Stream) =
        Some(ResolveResult(result, stream))

      p match {

        case Pattern.Skip =>
          ret(Empty, stream)

        case Pattern.TokenList =>
          ret(Body.Many(stream.map(Expr)), Nil)

        case Pattern.AnyToken =>
          buildAST2(stream.reverse).flatMap(e => ret(Expr(e), Nil))

        case Pattern.Opt(p2) =>
          resolveStep(p2, stream) match {
            case None    => ret(Empty, stream)
            case Some(r) => Some(r)
          }

        case Pattern.Many(p2) =>
          resolveStep(p2, stream) match {
            case None => None
            case Some(t) =>
              val (tail, stream2) = resolveList(p2, t.stream)
              ret(Body.Many(t.elem :: tail), stream2)
          }

        case Pattern.Seq(pats) =>
          @tailrec
          def go(
            inp: scala.List[Pattern],
            revOut: scala.List[Body],
            stream: AST.Stream
          ): scala.Option[ResolveResult] = inp match {
            case Nil =>
              revOut.reverse match {
                case Nil     => None
                case s :: ss => ret(Many(s :: ss), stream)
              }
            case p :: ps =>
              resolveStep(p, stream) match {
                case None    => None
                case Some(r) => go(ps, r.elem :: revOut, r.stream)
              }
          }
          go(pats.head :: pats.tail, scala.List(), stream)

        case Pattern.Alt(pats) =>
          @tailrec
          def go(
            inp: scala.List[Pattern],
            stream: AST.Stream
          ): scala.Option[ResolveResult] = inp match {
            case Nil => None
            case p :: ps =>
              resolveStep(p, stream) match {
                case None    => go(ps, stream)
                case Some(r) => ret(r.elem, r.stream)
              }
          }
          go(pats.head :: pats.tail, stream)

        case p: Pattern.Token[_] =>
          stream match {
            case Shifted(off, p.tag(t)) :: ss => ret(Expr(Shifted(off, t)), ss)
            case _                            => None
          }

        case p: Pattern.NotToken[_] =>
          stream match {
            case Shifted(off, p.tag(t)) :: ss => None
            case Shifted(off, t) :: ss        => ret(Expr(Shifted(off, t)), ss)
            case _                            => None
          }
      }
    }

    override def toString: String =
      s"SegmentBuilder($offset, $revBody)"
  }

  class MixfixBuilder(ast: AST) {
    var context: Context               = Context()
    var mixfix: Option[Registry.Value] = None
    var current: SegmentBuilder        = new SegmentBuilder(ast)
    var revSegs: List[SegmentBuilder]  = List()
  }

  def buildHardcodedRegistry(): Registry = {
    import Template.Segment.Pattern
    import Body._

    val groupDef = Definition.Restricted(
      Opr("(") -> Pattern.Opt(Pattern.AnyToken),
      Opr(")") -> Pattern.Skip
    ) {
      case List(s1, s2) =>
        (s1.el.body, s2.el.body) match {
          case (Expr(e), Empty) => AST.Group(e.off, e.el, s2.off)
          case (Empty, Empty)   => AST.Group(s2.off)
        }
    }

    val defDef = Definition.Unrestricted(
      Var("def") -> Pattern.Seq(
        Pattern.Opt(Pattern.Token[AST.Cons]),
        Pattern.Opt(Pattern.Many(Pattern.NotToken[AST.Block])),
        Pattern.Opt(Pattern.Token[AST.Block])
      )
    ) {
      case List(s1) =>
        s1.el.body match {
          case Many(lst) =>
            val nameParam = lst(0)
            val argsParam = lst(1)
            val bodyParam = lst(2)
            val name = nameParam match {
              case Expr(Shifted(off, t @ AST.Cons(_))) =>
                Shifted(off, t)
              case Empty => Shifted(AST.Missing)
              case _     => throw new Error("Internal Parser Error")
            }
            val args = argsParam.toStream()
            val body: Option[Shifted[AST]] = bodyParam match {
              case Expr(n @ Shifted(_, _: AST.Block)) => Some(n)
              case Empty                              => None
              case _ =>
                throw new Error("Internal Parser Error")
            }
            AST.Def(name, args, body)
        }
    }

    def seqSplit[L, R <: L](lst: List[Either[L, R]]): Either[List[L], List[R]] =
      lst.sequence match {
        case Right(t) => Right(t)
        case Left(_)  => Left(lst.map(_.merge))
      }

    def splitOn[T, S <: T](lst: List[T])(
      test: T => Option[S]
    ): (List[List[T]], List[S]) = {
      @tailrec
      def go(
        lst: List[T],
        current: List[T],
        chunks: List[List[T]],
        divs: List[S]
      ): (List[List[T]], List[S]) =
        lst match {
          case Nil => (current :: chunks, divs)
          case l :: ls =>
            test(l) match {
              case Some(l) => go(ls, List(), current :: chunks, l :: divs)
              case None    => go(ls, l :: current, chunks, divs)
            }
        }
      go(lst, Nil, Nil, Nil)
    }

    val importDef = Definition.Unrestricted(
      Var("import") -> Pattern.TokenList
    ) {
      case List(s1) =>
        val stream = s1.el.body.toStream()
        val (chunks, divs) = splitOn(stream) { sast =>
          sast.el match {
            case t @ AST.Opr(".") => Some(sast.copy(el = t))
            case _                => None
          }
        }

        val chunks2: List[List[Either[SAST, Shifted[AST.Ident]]]] =
          chunks.map { chunk =>
            chunk.map { sast =>
              sast.el match {
                case t: AST.Cons => Right(sast.copy(el = t))
                case t: AST.Var  => Right(sast.copy(el = t))
                case _           => Left(sast)
              }
            }
          }

        val chunks3 = chunks2.map(seqSplit)
        pprint.pprintln(chunks3, width = 50, height = 10000)
//        stream.part
        ???
      case _ => throw new Error("Internal Parser Error")
    }

    Registry(
      groupDef,
      Definition.Unrestricted(
        Var("if")   -> Pattern.AnyToken,
        Var("then") -> Pattern.AnyToken
      )(a => ???),
      Definition.Unrestricted(
        Var("if")   -> Pattern.AnyToken,
        Var("then") -> Pattern.AnyToken,
        Var("else") -> Pattern.AnyToken
      )(a => ???),
      importDef,
      defDef
    )
  }

  val hardcodedRegistry = buildHardcodedRegistry()

  def partition(t: AST): AST = {

    var builder: MixfixBuilder = new MixfixBuilder(Blank)
    builder.mixfix = Some(
      Definition.Spec(
        Definition.Scope.Unrestricted, {
          case Shifted(_, Segment.Valid(_, Body.Expr(e))) :: Nil => e.el

          case _ => throw new scala.Error("Impossible happened")
        },
        List1(Template.Segment.Pattern.AnyToken, Nil)
      )
    )
    var builderStack: List[MixfixBuilder] = Nil

    def pushBuilder(ast: AST, off: Int): Unit = {
//      println(s"pushBuilder($off)")
      builderStack +:= builder
      builder                = new MixfixBuilder(ast)
      builder.current.offset = off
    }

    def popBuilder(): Unit = {
//      println("popBuilder")
      builder      = builderStack.head
      builderStack = builderStack.tail
    }

    def pushSegment(ast: AST, off: Int): Unit = {
//      println(s"pushSegment($off)")
      builder.revSegs ::= builder.current
      builder.current        = new SegmentBuilder(ast)
      builder.current.offset = off
    }

    import Template._

    val root = Context(hardcodedRegistry.tree)

    def stripLastSegment(
      scope: Definition.Scope,
      revSegs: List1[Shifted[Template.Segment]]
    ): (List1[Shifted[Template.Segment]], AST.Stream) = {
      if (scope == Definition.Scope.Unrestricted)
        (revSegs.reverse, List())
      else {
        val lastSeg                = revSegs.head
        val (lastSegEl, revStream) = lastSeg.el.strip()
        val lastSeg2               = Shifted(lastSeg.off, lastSegEl)
        val revSegments            = List1(lastSeg2, revSegs.tail)
        (revSegments.reverse, revStream.reverse)
      }
    }

    def close(): AST.Stream1 = {
//      println(s"\n\n-----------------------------------\n\n")
      val revSegBldrs = List1(builder.current, builder.revSegs)
//      println(s"revSegs =")
//      pprint.pprintln(revSegs, width    = 50, height = 10000)
//      pprint.pprintln(builder.mixfix, width = 50, height = 10000)
      val result = {
        builder.mixfix match {
          case None =>
            val revSegs = revSegBldrs.map { segBldr =>
              val optAst = segBldr.buildAST()
              Shifted(segBldr.offset, Partial.Segment(segBldr.ast, optAst))
            }
            val segments = revSegs.reverse
            val head     = segments.head
            val tail     = segments.tail
            val paths    = builder.context.tree.dropValues()
            val template = Partial(Shifted.List1(head.el, tail), paths)
            val newTok   = Shifted(head.off, template)
            List1(newTok)

          case Some(ts) =>
            val revSegTps      = ts.el.reverse
            val revSegs        = revSegBldrs.zipWith(revSegTps)(_.build(_))
            val (segs, stream) = stripLastSegment(ts.scope, revSegs)
            val shiftSegs      = Shifted.List1(segs.head.el, segs.tail)
            val optValSegs     = Template.validate(shiftSegs)

            val template = optValSegs match {
              case None => Template.Invalid(shiftSegs)
              case Some(validSegs) =>
                val validSegsList = validSegs.toList()
//                val ast           = ts.finalizer(validSegsList)
                Template.Valid(validSegs) //, ast)
            }

            val newTok = Shifted(segs.head.off, template)

            stream match {
              case Nil     => List1(newTok)
              case s :: ss => List1(s, ss) :+ newTok
            }
        }
      }

//      println("Close Result:")
//      pprint.pprintln(result, width = 50, height = 10000)
      result
    }

    def close2() = {
//      println("close2")
      val subAst = close()
      popBuilder()
      builder.current.revBody = subAst.concat(builder.current.revBody).toList
    }

//    def extractRoot[T](seg: Template.Segment[T]): Unit = {
//      import Template.Segment._
//      import Template.Segment
//      seg match {
//        case Segment(Pattern.Option(Pattern.Expr), _, body) => body.get
//      }
//    }

    @tailrec
    def go(input: AST.Stream): AST = {
      import Template.Segment.Body._
      input match {
        case Nil =>
          if (builderStack.isEmpty) {
//            println("End of input (not in stack)")
            close().head.el match {
              case Template.Valid(segs) =>
                segs.head.body.toStream() match {
                  case Nil    => throw new scala.Error("Impossible happened.")
                  case s :: _ => s.el
                }

              case _ => throw new scala.Error("Impossible happened.")
            }

          } else {
//            println("End of input (in stack)")
            close2()
            go(input)
          }
        case t1 :: t2_ =>
//          println(s"> $t1")

          builder.context.get(t1.el) match {
            case Some(tr) =>
//              println(">> New segment")
              pushSegment(t1.el, t1.off)
//              builder.mixfix  = builder.mixfix.map(Some(_)).getOrElse(tr.value)
              builder.mixfix  = tr.value.map(Some(_)).getOrElse(builder.mixfix)
              builder.context = builder.context.copy(tree = tr)
              go(t2_)

            case None =>
              root.get(t1.el) match {
                case Some(tr) =>
//                  println(">> Root")
                  val context = builder.context
                  pushBuilder(t1.el, t1.off)
                  builder.mixfix  = tr.value
                  builder.context = Context(tr, Some(context))
                  go(t2_)
                case None =>
//                  println(s"PARENT CHECK (${builder.current.ast}, ${t1.el})")
                  val currentClosed = builder.context.isEmpty
                  val parentPrecWin = (builder.current.ast, t1.el) match {
                    case (_: Opr, _) => false
                    case (_, _: Opr) => true
                    case _           => false
                  }
                  val parentBreak = builder.context.parentCheck(t1.el)
                  (currentClosed || parentPrecWin) && parentBreak match {
                    case true =>
//                      println("Parent close")
                      close2()
                      go(input)
                    case false =>
//                      println(">> Add token")
                      builder.current.revBody ::= t1
                      go(t2_)
                  }
              }
          }

      }
    }

//      go(Context(registry.tree, None), exprList(t).toList(), List())
//    println("START")
    val elst = exprList(t).toList()
//    pprint.pprintln(elst, width = 50, height = 10000)
    go(elst)
  }

//    def partition(t: AST) = {
//
//      def go(context:Context, lst: SpacedList[AST], out: List[Spaced[AST]]): List[Spaced[AST]]= {
//        context.get(lst.head) match {
//          case None => lst.tail match {
//            case Nil => out
//            case t :: ts => go(context, SpacedList(t,ts), lst.head :: out)
//          }
//          case _ => ???
//        }
//      }
//
//      go(Context(registry.tree, None), exprList(t), List())
//    }

  // format: off


  
  def run(module:Module): Module =
    module.map(_.map(partition))
}
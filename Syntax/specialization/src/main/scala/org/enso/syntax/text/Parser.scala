package org.enso.syntax.text

import cats.derived.semi
import org.enso.data.Index
import org.enso.data.Span
import org.enso.flexer
import org.enso.flexer.Reader
import org.enso.syntax.text.AST.ASTOf
import org.enso.syntax.text.AST.ID
import org.enso.syntax.text.AST.Ident.VarOf
import org.enso.syntax.text.AST.Invalid.UnrecognizedOf
import org.enso.syntax.text.ast.meta.Builtin
import org.enso.syntax.text.ast.opr.Prec
import org.enso.syntax.text.ast.text.Escape
import org.enso.syntax.text.ast.text.Escape.Quote.toString
import org.enso.syntax.text.ast.text.Escape.RawQuote.toString
import org.enso.syntax.text.ast.text.Escape.Slash.toString
import org.enso.syntax.text.ast.text.Escape.Unicode
import org.enso.syntax.text.prec.Distance
import org.enso.syntax.text.prec.Macro
import org.enso.syntax.text.prec.Operator
import org.enso.syntax.text.spec.ParserDef

import scala.math.Ordering.Implicits._
import scala.annotation.tailrec

////////////////////////////////

class InternalError(reason: String, cause: Throwable = None.orNull)
    extends Exception(s"Internal error $reason", cause)

////////////////
//// Parser ////
////////////////

/** This is the main Parser class.
  *
  * ==The Macro System==
  *
  * The parser bases on a very sophisticated Macro mechanism, allowing users for
  * unparalleled control and flexibility. The macro systems allows advanced
  * users to create new syntax or new domain-specific languages. In a similar
  * fashion to Lisp, Enso macros can freely transform the syntactic structure of
  * the program. In short, anything that Enso can do to a data structure, Enso
  * macros can do to code. In contrast, in most other languages, the parser's
  * output is purely internal to the language implementation and cannot be
  * manipulated by the programmer.
  *
  * Macro resolution steps:
  *
  * 1. Parser is executed by using the [[Parser#run]] function. It reads source
  * code and outputs a token stream [[AST.Stream]]. The token stream contains a
  * very narrow range of possible elements: [[AST.Blank]], [[AST.Var]],
  * [[AST.Cons]], [[AST.Opr]], [[AST.Number]], [[AST.Text]], and [[AST.Block]],
  * which contains lines of streams of these elements. Every other AST structure
  * is build by the macro system. Please note that the stream in this step is
  * encoded by using [[AST.App]] on subsequent elements.
  *
  * 2. Parser prepares [[Builtin.registry]] containing predefined set of macro
  * [[AST.Macro.Definition]], which define such constructs as comments, parensed
  * expressions, imports, new data definitions, if-then-else mixfix functions,
  * or even foreign languages support. During this step parser will be also
  * asking interpreter to fill the registry with definitions from other modules.
  * Each [[AST.Macro.Definition]] contains macro segment descriptions and a
  * finalizer, a function transforming matched tokens to final AST. Finalizer is
  * used only if all macro segments were matched correctly.
  *
  * 3. The token stream is partitioned according to registered macros segments.
  * Each macro contains multiple segments. A segment contains of an identifier,
  * like "if" or "then" and a macro [[Pattern]]. Patterns are not used in this
  * step. The AST stream is partitioned solely by segment identifiers. Macros
  * can overlap, for example, [[Builtin.registry]] contains both "if-then" and
  * "if-then-else" macro. When it is impossible to decide which macro to choose,
  * like for the input "(if a) b", [[AST.Macro.Ambiguous]] is returned.
  * Otherwise, each macro segment is matched against corresponding [[Pattern]]
  * and [[AST.Macro.Match]] is returned and stored back in the [[AST.Stream]].
  * Please note, that even if pattern matching fails, the [[AST.Macro.Match]]
  * will be the result. It will contain information about failed patterns.
  *
  * 4. The segment [[Pattern]] is similar to regular expression. It contains
  * around 10 building blocks, such as [[Pattern.Nothing]], which does not
  * consume any input, or [[Pattern.Tok]], allowing matching a specific token,
  * like the "if" keyword. The result, [[Pattern.Match]] is stored in
  * [[AST.Macro.Match]]. The [[Pattern.Match.Err]] is used to mark unsuccessful
  * pattern match fragments, while the [[Pattern.Match.Tok]] is used to provide
  * additional help messages to the end-user. Please note that it is impossible
  * for the pattern match mechanism to break even on malformed user
  * [[AST.Macro.Definition]]. Each definition contains a pre-process step inside
  * of [[AST.Macro.Definition]] constructor, which modifies the user provided
  * rules with checks if the pattern succeed and in case the pattern was used
  * between segments, if it consumed all tokens. In case either of validators
  * fail, all tokens are consumed and marked as an invalid match.
  *
  * 5. A very special pattern is the [[Pattern.Build]] construction, which tells
  * the pattern match mechanism that it should build a single [[AST]] expression
  * out of matched tokens. For example, a pattern
  * [[Pattern.Build(Pattern.Cls[AST.Opr])]] will match an operator token and
  * build a side-section AST from it. The [[Pattern.Build]] blocks are resolved
  * during the pattern match step. After this step is finished and
  * [[AST.Macro.Match]] or [[AST.Macro.Ambiguous]] is stored back in the
  * [[AST.Stream]], nothing more happens, parsing is done! It is important to
  * note, that there is a special module parsing macro, which runs
  * [[Pattern.Build]] on every line.
  *
  *
  *
  * ==Pattern Build Mechanism==
  *
  * The resolution of [[Pattern.Build]] is as interesting as the macro system.
  * It contains of the following stages:
  *
  * 1. First, the [[AST.Stream]] is partitioned byt the [[Distance]] processor
  * according to the spacing information. All non-spaced tokens are grouped
  * together and processed first. After their processing is done and each group
  * will be transformed to a single [[AST]], it is put back to the original
  * [[AST.Stream]] and the whole stream is processed the same way (described in
  * the following points).
  *
  * 2. Each token of a chosen stream is then processed by the
  * [[https://en.wikipedia.org/wiki/Shunting-yard_algorithm Shunting-yard
  * algorithm]]. Basically, it re-shuffles the [[AST]] stream to combination of
  * [[AST.App]], [[AST.App.Left]], [[AST.App.Right]], and [[AST.App.Sides]],
  * according to the operator precedence. Please note that the precedence of
  * user defined operators is fixed in Enso and depends on the shape of the
  * operator. For example, all "arrows" like "<-", "<-<", or "<=<", have the
  * same precedence. The associativity is inferred by the operator direction,
  * where both "=" and "," operators are considered right-associative. See
  * [[Operator]] and [[Prec]] for more information.
  *
  *
  *
  * ==Finalizers==
  *
  * A careful reader will notice that there was no description of how finalizers
  * (mentioned in the first section) are used. Finalizers are user-provided AST
  * transformations which are applied to valid AST Macro matches. After
  * finalizer is applied, the spacing information might be lost.
  *
  * ==Space-unaware AST===
  *
  * That's because they are NOT used during parsing. A very important design
  * decision is that Enso AST contains all information allowing for printing the
  * code back from the AST, while keeping all whitespaces as they were before
  * parsing. This is why each space-aware AST, like [[AST.App]] records all
  * positional information. For convenient usage, all space-aware [[AST]]
  * definitions end with "Of", like [[AST.App.PrefixOf]] and have a counterpart
  * without "Of" allowing for pattern matching without thinking about the
  * spacing information. Because macro system is end-user extensible, we cannot
  * assume that the end-user will care about recording valid spacing when
  * transforming [[AST]] to another form. That's why there are also
  * space-unaware [[AST]] structures, which are handy to work with by automated
  * tools like the interpreter, while all the spacing information is stored only
  * in the basic set of tokens and [[AST.Macro]] tokens. Each AST node has a
  * [[AST.map]] function for mapping over sub-nodes, which allows easy building
  * of AST traversals. The [[Parser#resolveMacros]] is such a traversal, which
  * applies [[AST.Macro.Definition.Resolver]] to each [[AST.Macro.Match]] found
  * in the AST, while loosing a lot of positional information.
  */
class Parser {
  import Parser._
  private val engine = newEngine()

  def run(input: Reader): AST.Module = run(input, Nil)

  def run(input: Reader, idMap: IDMap): AST.Module =
    engine.run(input).map(Macro.run) match {
      case flexer.Parser.Result(_, flexer.Parser.Result.Success(mod)) =>
        val mod2 = annotateModule(idMap, mod)
        resolveMacros(mod2).asInstanceOf[AST.Module]
      case _ => throw ParsingFailed
    }

  def annotateModule(
    idMap: IDMap,
    mod: AST.Module
  ): AST.Module = {
    var ids = idMap.sorted.toList
    mod.traverseWithOff { (off, ast) =>
      val key = Span(off, ast)

      while (ids.nonEmpty && ids.head._1 < key) ids = ids.tail

      ids match {
        case (k, id) :: _ if k == key =>
          ids = ids.tail
          ast.setID(id)
        case _ =>
          ast match {
            case AST.Macro.Match.any(_) => ast.withNewID()
            case _                      => ast
          }
      }
    }
  }

  /** Although this function does not use any Parser-specific API now, it will
    * use such in the future when the interpreter will provide information about
    * defined macros other than [[Builtin.registry]].
    */
  def resolveMacros(ast: AST): AST =
    ast match {
      case AST.Macro.Match.any(ast) =>
        val resolvedAST = ast.map(resolveMacros)
        Builtin.registry.get(resolvedAST.path) match {
          case None => throw MissingMacroDefinition
          case Some(spec) =>
            val id       = resolvedAST.id.getOrElse(throw new Error(s"Missing ID"))
            val segments = resolvedAST.segs.toList().map(_.el)
            val ctx      = AST.Macro.Resolver.Context(resolvedAST.pfx, segments, id)
            resolvedAST.copy(shape = resolvedAST.shape.copy[AST](resolved = {
              resolveMacros(spec.resolver(ctx))
            }))
        }
      case _ => ast.map(resolveMacros)
    }

  /** Drops macros metadata keeping only resolved macros in the AST.
    * WARNING: this transformation drops the information about AST spacing.
    */
  def dropMacroMeta(ast: AST.Module): AST.Module = {
    def go: AST => AST = {
      case AST.Macro.Match.any(t) => go(t.resolved)
      case t                      => t.map(go)
    }
    ast.map(go)
  }

}

object Parser {
  type IDMap = Seq[(Span, AST.ID)]
  def apply(): Parser   = new Parser()
  private val newEngine = flexer.Parser.compile(ParserDef())

  //// Exceptions ////

  case object ParsingFailed extends ParserError("parsing failed")
  case object MissingMacroDefinition
      extends ParserError("macro definition not found")
  class ParserError(reason: String, cause: Throwable = None.orNull)
      extends InternalError(s"in parser $reason", cause)
}

////////////////////////////////////////////////////////////////////////////////
//// Interactive Testing Utilities /////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////

//////////////
//// Main ////
//////////////

import io.circe._
//import io.circe.generic.auto._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.encoding.DerivedObjectEncoder
import shapeless.LabelledGeneric
import shapeless.Lazy
import cats.Functor

object Main extends App {

//  def pretty(str: String): String = {
//
//    def checkClosing(in: List[Char]): Int = {
//      @tailrec
//      def go(i: Int, rest: Int, in: List[Char], bias: Int): Int =
//        (rest, bias, in) match {
//          case (0, _, _)   => 0
//          case (_, 0, _)   => i
//          case (_, _, Nil) => i
//          case (_, _, s :: ss) =>
//            s match {
//              case '(' => go(i + 1, rest - 1, ss, bias - 1)
//              case ')' => go(i + 1, rest - 1, ss, bias + 1)
//              case _   => go(i + 1, rest - 1, ss, bias)
//            }
//
//        }
//      go(0, 10, in, -1)
//    }
//
//    @tailrec
//    def go(ind: Int, in: List[Char], out: List[String]): List[String] = {
//      def newline(i: Int) = "\n" + " " * i * 2
//      in match {
//        case Nil => out
//        case s :: ss =>
//          val s2 = s.toString
//          s match {
//            case '(' =>
//              checkClosing(ss) match {
//                case 0 => go(ind + 1, ss, newline(ind + 1) :: s2 :: out)
//                case i =>
//                  go(
//                    ind,
//                    ss.drop(i),
//                    ss.take(i).mkString("") :: s2 :: out
//                  )
//              }
//
//            case ')' => go(ind - 1, ss, s2 :: newline(ind - 1) :: out)
//            case ',' => go(ind, ss, newline(ind) :: s2 :: out)
//            case _   => go(ind, ss, s2 :: out)
//          }
//      }
//    }
//    go(0, str.toList, List()).reverse.mkString("")
//  }
//
//  println("--- START ---")
//
//  val parser = new Parser()
//
//  val in_def_maybe =
//    """## Foo bar baz
//      |   bax
//      |def Maybe a
//      |    ## test
//      |    def Just val:a
//      |    def Nothing
//    """.stripMargin
//
//  val in_arr1 = "a = b -> c d"
//
//  val in3  = "(a) b = c"
//  val in4  = "if a then (b)"
//  val in2  = "(a) b = c]"
//  val inp2 = "a (b (c)) x"
//
//  val inp = """## This function adds `x` to `y`
//              |add x y = x + y
//              |mul x y = x * y
//              |
//              |## This function divides `x` by `y`
//              |div x y = x / y
//              |
//              |## Just a comment
//              |
//              |## Doc for infix with empty lines between
//              |
//              |sub x y = x - y
//              |
//              |## Foo bar baz
//              |   bax
//              |def Maybe a
//              |    ## test attached to Just
//              |    def Just val:a
//              |    def Nothing
//              |""".stripMargin
//
//  println("--- PARSING ---")
//
//  val mod = parser.run(new Reader(inp))
//
//  println(pretty(mod.toString))
//
//  println("=========================")
//  println(pretty(parser.dropMacroMeta(mod).toString))
//  val rmod = parser.resolveMacros(mod)
//  if (mod != rmod) {
//    println("\n---\n")
//    println(pretty(rmod.toString))
//  }
//
//  println("------")
//  println(mod.show() == inp)
//  println("------")
//  println(mod.show())
//  println("------")

  val v1: AST.Ident.VarOf[AST] = AST.Var("foo").shape
  val v1_2: AST.Ident          = v1
  val v2: AST.Invalid.UnexpectedOf[AST] =
    AST.Invalid.UnexpectedOf("foo", List())
  val v3: AST.App.Prefix = AST.App.Prefix(v1, 10, v1)

//  implicitly[DerivedObjectEncoder[AST.Ident]](
//    DerivedObjectEncoder.deriveEncoder
//  )

//  implicitly[shapeless.LabelledGeneric[AST.Ident.VarOf[AST]]](
//    shapeless.LabelledGeneric.materializeProduct(
//      shapeless.DefaultSymbolicLabelling.apply,
//      shapeless.Generic.apply,
//      shapeless.ops.hlist.ZipWithKeys.apply,
//      scala.Predef.$conforms
//    )
//  )

//  implicitly[shapeless.LabelledGeneric[AST.Ident]]
//  implicitly[shapeless.LabelledGeneric[AST.Ident.VarOf[AST]]](
//    shapeless.LabelledGeneric.materializeProduct( // (
//      shapeless.DefaultSymbolicLabelling.mkDefaultSymbolicLabelling,
//      shapeless.Generic.materialize,
//      shapeless.ops.hlist.ZipWithKeys.hnilZipWithKeys,
//      scala.Predef.$conforms
//    )
//  )

//  implicitly[shapeless.DefaultSymbolicLabelling[AST.Ident]](
//    shapeless.DefaultSymbolicLabelling.mkDefaultSymbolicLabelling
//  )

//  println(v1.asJson(Encoder.importedEncoder(exportEncoder)))

//  /** Invoking the Enso Documentation Parser */
//  println("===== DOCUMENTATION =====")
//  val isGeneratingHTML = false
//  val droppedMeta      = parser.dropMacroMeta(mod)
//  val documentation    = DocParserRunner.createDocs(droppedMeta)
//  val documentationHTML =
//    DocParserRunner.generateHTMLForEveryDocumented(documentation)
//  println(pretty(documentation.toString))
//  println("------")
//  println(documentation.show())
//  println("=========================")
//
//  println()

  trait X[T] {}

  trait ASTClass[T] {
    def getJson(t: T): Json
  }
  object ASTClass {
    implicit def instance[T](
      implicit
      ev: Encoder[T],
      ev2: X[T]
    ): ASTClass[T] = new ASTClass[T] {
      def getJson(t: T): Json = {
        implicitly[Encoder[T]].apply(t)
      }
    }
  }

  AST.main()
  sealed trait ShapeOf2[T]
  type AST2 = ASTOf2[ShapeOf2]
  final case class ASTOf2[+T[_]](shape: T[AST2])(
    implicit cls: ASTClass[T[AST2]]
  ) {
    def myAsJson(): Json = cls.getJson(shape)
  }

  implicit def ASTOf2Encoder[T[_]]: Encoder[ASTOf2[T]] =
    Encoder.instance(_.myAsJson())
//
//  implicit def ASTEncoder: Encoder[AST2] =
//    Encoder.instance(_.myAsJson())

  final case class AOf[T](i: Int) extends ShapeOf2[T]
  final case class BOf[T](t: T)   extends ShapeOf2[T]

  implicit def XforA[T]: X[AOf[T]] = new X[AOf[T]] {}
  implicit def XforB[T]: X[BOf[T]] = new X[BOf[T]] {}

  implicit def ftorA: Functor[AOf] = semi.functor
  implicit def ftorB: Functor[BOf] = semi.functor

//  implicit def encoderGen[T[_]: Functor]: Encoder[T[AST2]] = Encoder.instance {
//    t =>
//      {
//        Functor[T].map(t)(_.asJson)
//        ???
//      }
//  }
  //  val t1: AST2 = ASTOf2(AOf(5))
//  println(t1.asJson)
  sealed trait Escape2 {
    val repr: String
  }

  sealed trait Unicode2 extends Escape2
  object Unicode2 {

    final case class Invalid(unicode: Unicode2) extends Unicode2 {
      val repr = unicode.repr
    }

    final case class U16(digits: String) extends Unicode2 {
      val pfx  = "u"
      val sfx  = ""
      val repr = pfx + digits + sfx
    }

    final case class U32(digits: String) extends Unicode2 {
      val pfx  = "U"
      val sfx  = ""
      val repr = pfx + digits + sfx
    }

    final case class U21(digits: String) extends Unicode2 {
      val pfx  = "u{"
      val sfx  = "}"
      val repr = pfx + digits + sfx
    }

  }

  case object Slash extends Escape2 {
    val code: Int     = '\\'
    def name          = toString
    override val repr = "\\"
  }
  case object Quote extends Escape2 {
    val code: Int     = '\''
    def name          = toString
    override val repr = "\'"
  }
  case object RawQuote extends Escape2 {
    val code: Int     = '"'
    def name          = toString
    override val repr = "\""
  }

  //  implicitly[Encoder[AST.Invalid.UnexpectedOf[AST]]]
  implicitly[Encoder[AST.App.PrefixOf[AST]]]
  implicitly[Encoder[BOf[AST2]]]
  implicitly[Encoder[org.enso.syntax.text.ast.text.Escape2]]
  implicitly[Encoder[Escape2]]

//  type Ident2 = ASTOf2[AST.IdentOf]

}

package org.enso.interpreter

import scala.util.parsing.combinator._

sealed trait EnsoAst

case class EnsoLong(l: Long)                                  extends EnsoAst
case class EnsoArithOp(op: String, l: EnsoAst, r: EnsoAst)    extends EnsoAst
case class EnsoForeign(lang: String, code: String)            extends EnsoAst
case class EnsoVariable(name: String)                         extends EnsoAst
case class EnsoFunction(body: EnsoAst)                        extends EnsoAst
case class EnsoReadVar(name: String)                          extends EnsoAst
case class EnsoApply(fun: EnsoAst, args: List[EnsoAst])       extends EnsoAst
case class EnsoBlock(statements: List[EnsoAst], ret: EnsoAst) extends EnsoAst
case class EnsoAssign(name: String, body: EnsoAst)            extends EnsoAst
case class EnsoPrint(body: EnsoAst)                           extends EnsoAst
case class EnsoRunBlock(block: EnsoAst)                       extends EnsoAst
case class EnsoJsCall(code: String, args: List[EnsoAst])      extends EnsoAst

class EnsoParserInternal extends JavaTokenParsers {

  override def skipWhitespace: Boolean = true

  def long: Parser[EnsoAst] = wholeNumber ^^ { numStr =>
    EnsoLong(numStr.toLong)
  }

  def foreign: Parser[EnsoAst] =
    ("js" | "rb" | "py") ~ foreignLiteral ^^ {
      case lang ~ code => EnsoForeign(lang, code)
    }

  def argList: Parser[List[EnsoAst]] =
    "[" ~> expression <~ "]" ^^ {
      case expr => List(expr)
    }

  def foreignLiteral: Parser[String] = "**" ~> "[^\\*]*".r <~ "**"

  def jsCall: Parser[EnsoAst] = "jsCall:" ~> foreignLiteral ~ argList ^^ {
    case code ~ args => EnsoJsCall(code, args)
  }

  def variable: Parser[EnsoAst] = ident ^^ EnsoReadVar

  def operand: Parser[EnsoAst] =
    long | foreign | variable | "(" ~> arith <~ ")" | call

  def arith: Parser[EnsoAst] =
    operand ~ ((("+" | "-" | "*" | "/") ~ operand) ?) ^^ {
      case a ~ Some(op ~ b) => EnsoArithOp(op, a, b)
      case a ~ None         => a
    }

  def expression: Parser[EnsoAst] = arith | block

  def call: Parser[EnsoAst] = "@" ~> expression ^^ EnsoRunBlock

  def assignment: Parser[EnsoAst] = ident ~ ("=" ~> expression) ^^ {
    case v ~ exp => EnsoAssign(v, exp)
  }

  def print: Parser[EnsoAst] = "print:" ~> expression ^^ EnsoPrint

  def block: Parser[EnsoAst] =
    "{" ~> (((statement <~ ";") *) ~ expression) <~ "}" ^^ {
      case stmts ~ expr => EnsoBlock(stmts, expr)
    }

  def statement: Parser[EnsoAst] = assignment | print | jsCall | expression

  def parse(code: String): EnsoAst = {
    parseAll(expression | block, code).get
  }
}

class EnsoParser {

  def parseEnso(code: String): EnsoAst = {
    new EnsoParserInternal().parse(code)
  }

  def example: EnsoAst =
    EnsoRunBlock(
      EnsoBlock(
        List(
          EnsoAssign(
            "foo",
            EnsoBlock(List(EnsoPrint(EnsoLong(20))), EnsoLong(20))
          ),
          EnsoRunBlock(EnsoReadVar("foo")),
          EnsoRunBlock(EnsoReadVar("foo"))
        ),
        EnsoLong(30)
      )
    )
}

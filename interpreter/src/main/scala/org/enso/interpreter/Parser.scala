package org.enso.interpreter

import scala.util.parsing.combinator._

trait AbstractExpressionFactory[T] {
  def makeLong(t: Long): T
  def makeArith(op: String, l: T, r: T): T
  def makeForeign(lang: String, code: String): T
}

class EnsoParserInternal[T](factory: AbstractExpressionFactory[T])
    extends JavaTokenParsers {

  override def skipWhitespace: Boolean = true

  def long: Parser[T] = wholeNumber ^^ { numStr =>
    factory.makeLong(numStr.toLong)
  }

  def foreign: Parser[T] =
    ("js" | "rb" | "py") ~ (">>" ~> "[^<>]*".r <~ "<<") ^^ {
      case lang ~ code => factory.makeForeign(lang, code)
    }

  def operand: Parser[T] = long | foreign | "(" ~> arith <~ ")"

  def arith: Parser[T] = operand ~ ((("+" | "-" | "*" | "/") ~ operand) ?) ^^ {
    case a ~ Some(op ~ b) => factory.makeArith(op, a, b)
    case a ~ None         => a
  }

  def parse(code: String): T = {
    parseAll(arith, code).get
  }
}

class EnsoParser[T](factory: AbstractExpressionFactory[T]) {

  def parseEnso(code: String): T = {
    new EnsoParserInternal(factory).parse(code)
  }
}

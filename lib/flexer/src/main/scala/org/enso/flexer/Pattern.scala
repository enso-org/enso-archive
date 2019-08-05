package org.enso.flexer

trait Pattern {
  def |(that: Pattern) = Or(this, that)

  def >>(that: Pattern) = Seq_(this, that)

  def many: Pattern = Many(this)

  def many1: Pattern = this >> many
}

case object None_ extends Pattern
case object Pass  extends Pattern

case class Ran(start: Int, end: Int) extends Pattern

case class Or(left: Pattern, right: Pattern) extends Pattern

case class Seq_(first: Pattern, second: Pattern) extends Pattern

case class Many(body: Pattern) extends Pattern

object Pattern {
  def range(start: Char, end: Char): Pattern = Ran(start.toInt, end.toInt)
  def range(start: Int, end: Int):   Pattern = Ran(start, end)
  def char(c: Char):                 Pattern = range(c, c)

  class ExtendedChar(_this: Char) {
    final def ||(that: Char): Pattern =
      Or(char(_this), char(that))
  }

  implicit def charToExpr(char: Char): Pattern =
    range(char, char)
  implicit def stringToExpr(s: String): Pattern =
    s.tail.foldLeft(char(s.head))(_ >> _)
  implicit def extendChar(i: Char): ExtendedChar =
    new ExtendedChar(i)
}

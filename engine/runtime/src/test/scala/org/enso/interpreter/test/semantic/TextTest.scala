package org.enso.interpreter.test.semantic

import org.enso.interpreter.test.InterpreterTest

class TextTest extends InterpreterTest {
  "Single line raw text literals" should "exist in the language" in {
    val code =
      """
        |main = IO.println "hello world!"
        |""".stripMargin

    eval(code)
    consumeOut shouldEqual List("hello world!")
  }

  "Single line interpolated literals" should "exist in the language" in {
    val code =
      """
        |main =
        |    w = "World"
        |    IO.println 'Hello, `w`!'
        |""".stripMargin
    eval(code)
    consumeOut shouldEqual List("Hello, World!")
  }

  "Interpolated literals" should "delegate to the `to_text` method on the expression result" in {
    val code =
      """
        |type My_Type a
        |
        |My_Type.to_text = case this of
        |    My_Type a -> 'SurpriseString::`a`'
        |
        |main = IO.println 'Hello, `My_Type 10`'
        |""".stripMargin
    eval(code)
    consumeOut shouldEqual List("Hello, SurpriseString::10")
  }

  "Block raw text literals" should "exist in the language" in {
    val code =
      s"""
         |main =
         |    x = $rawTQ
         |        Foo
         |        Bar
         |          Baz
         |
         |    IO.println x
         |""".stripMargin

    eval(code)
    consumeOut shouldEqual List("Foo", "Bar", "  Baz")
  }

  "Raw text literals" should "support escape sequences" in {
    val code =
      """
        |main = IO.println "\"Grzegorz Brzeczyszczykiewicz\""
        |""".stripMargin

    eval(code)
    consumeOut shouldEqual List("\"Grzegorz Brzeczyszczykiewicz\"")
  }
}

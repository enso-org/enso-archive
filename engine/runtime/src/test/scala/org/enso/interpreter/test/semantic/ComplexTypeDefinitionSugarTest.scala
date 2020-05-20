package org.enso.interpreter.test.semantic

import org.enso.interpreter.test.InterpreterTest

class ComplexTypeDefinitionSugarTest extends InterpreterTest {

  val subject: String = "Complex type definitions"

  subject should "work properly with simple method definitions" in {
    val code =
      """
        |type My_Type
        |    type Atom_One
        |    type Atom_Two
        |
        |    is_atom_one = case this of
        |        Atom_One -> 10
        |        Atom_Two -> -10
        |
        |main =
        |    r_1 = Atom_One.is_atom_one
        |    r_2 = Atom_Two.is_atom_one
        |    r_1 + r_2
        |""".stripMargin

    eval(code) shouldEqual 0
  }

  subject should "work properly with sugared method definitions" in {
    val code =
      """
        |type My_Type
        |    type Atom_One
        |    type Atom_Two
        |
        |    is_atom_one n = case this of
        |        Atom_One -> 10 + n
        |        Atom_Two -> -10 - n
        |
        |main =
        |    r_1 = Atom_One.is_atom_one 5
        |    r_2 = Atom_Two.is_atom_one 10
        |    r_1 + r_2
        |""".stripMargin

    eval(code) shouldEqual -5
  }

  subject should "work properly with atoms with fields" in {
    val code =
      """
        |type My_Type
        |    type My_Atom a
        |
        |    is_equal n = case this of
        |        My_Atom a -> n - a
        |
        |main =
        |    (My_Atom 5).is_equal 5
        |""".stripMargin

    eval(code) shouldEqual 0
  }
}

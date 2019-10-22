package org.enso.interpreter.test.semantic

import org.graalvm.polyglot.PolyglotException

class ImportsTest extends PackageTest {
  "Atoms and methods" should "be available for import" in {
    pending
    evalTestProject("TestSimpleImports") shouldEqual 20
  }

  "Overloaded methods" should "be imported transitively" in {
    pending
    evalTestProject("TestOverloadsTransitive") shouldEqual 30
  }

  "Methods defined together with atom" should "be visible even if not imported" in {
    pending
    evalTestProject("TestNonImportedOwnMethods") shouldEqual 10
  }

  "Overloaded methods" should "not be visible when not imported" in {
    pending
    the[PolyglotException] thrownBy evalTestProject("TestNonImportedOverloads") should have message "Object X does not define method method."
  }
}

package org.enso.interpreter.test.semantic

import org.enso.interpreter.test.{InterpreterException, PackageTest}

class StrictCompileErrorsTest extends PackageTest {
  "Compile errors in batch mode" should "be reported and abort execution" in {
    the[InterpreterException] thrownBy evalTestProject("TestCompileErrors") should have message "Compilation aborted due to errors."
    val _ :: errors = consumeOut
    errors.toSet shouldEqual Set(
      "Main.enso[2:9-2:10]: Parentheses can't be empty.",
      "Main.enso[3:5-3:9]: Variable x is being redefined.",
      "Main.enso[4:9-4:9]: Unrecognized token."
    )
  }
}

package org.enso.compiler.test.pass.optimise

import org.enso.compiler.core.IR
import org.enso.compiler.test.CompilerTest

class LambdaConsolidateTest extends CompilerTest {
  trait Foo extends IR.Metadata
  case class Bar(a: Int) extends Foo {
    override val metadataName: String = "Bar"
  }
  case class Baz(a: Int) extends Foo {
    override val metadataName: String = "Baz"
  }

  case class Quux(a: Int) extends IR.Metadata {
    override val metadataName: String = "Quux"
  }

  "thing" should {
    "thingy" in {
      val empty = IR
        .Empty(None)
        .addMetadata[Foo, Bar](Bar(1))
        .addMetadata[Foo, Baz](Baz(2))
        .addMetadata[Quux, Quux](Quux(1))

      println(empty.passData)
    }
  }

}

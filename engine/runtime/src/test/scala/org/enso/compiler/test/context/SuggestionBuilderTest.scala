package org.enso.compiler.test.context

import org.enso.compiler.Passes
import org.enso.compiler.context.{FreshNameSupply, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.PassManager
import org.enso.compiler.test.CompilerTest
import org.enso.interpreter.instrument.SuggestionBuilder
import org.enso.searcher.Suggestion

class SuggestionBuilderTest extends CompilerTest {

  implicit val passManager: PassManager = new Passes().passManager

  "SuggestionBuilder" should {

    "build function without arguments" in {
      implicit val moduleContext: ModuleContext = freshModuleContext

      val code   = """foo = 42""".stripMargin
      val module = code.preprocessModule

      build(module) should contain theSameElementsAs Seq(
        Suggestion.Method(
          name = "foo",
          arguments = Seq(
            Suggestion.Argument("this", "Any", false, false, None)
          ),
          selfType      = "here",
          returnType    = "Any",
          documentation = None
        )
      )
    }

    "build function with documentation" in {
      pending // fix documentation
      implicit val moduleContext: ModuleContext = freshModuleContext

      val code =
        """## The foo
          |foo = 42""".stripMargin
      val module = code.preprocessModule

      build(module) should contain theSameElementsAs Seq(
        Suggestion.Method(
          name = "foo",
          arguments = Seq(
            Suggestion.Argument("this", "Any", false, false, None)
          ),
          selfType      = "here",
          returnType    = "Any",
          documentation = Some(" The foo")
        )
      )
    }

    "build function with arguments" in {
      implicit val moduleContext: ModuleContext = freshModuleContext

      val code =
        """foo a b =
          |    x = a + 1
          |    y = b - 2
          |    x * y""".stripMargin
      val module = code.preprocessModule

      build(module) should contain theSameElementsAs Seq(
        Suggestion.Method(
          name = "foo",
          arguments = Seq(
            Suggestion.Argument("this", "Any", false, false, None),
            Suggestion.Argument("a", "Any", false, false, None),
            Suggestion.Argument("b", "Any", false, false, None)
          ),
          selfType      = "here",
          returnType    = "Any",
          documentation = None
        ),
        Suggestion.Local("x", "Any"),
        Suggestion.Local("y", "Any")
      )
    }

    "build function with default arguments" in {
      implicit val moduleContext: ModuleContext = freshModuleContext

      val code =
        """foo (a = 0) = a + 1""".stripMargin
      val module = code.preprocessModule

      build(module) should contain theSameElementsAs Seq(
        Suggestion.Method(
          name = "foo",
          arguments = Seq(
            Suggestion.Argument("this", "Any", false, false, None),
            Suggestion.Argument("a", "Any", false, true, Some("0"))
          ),
          selfType      = "here",
          returnType    = "Any",
          documentation = None
        )
      )
    }

    "build function with lazy arguments" in {
      implicit val moduleContext: ModuleContext = freshModuleContext

      val code =
        """foo ~a = a + 1""".stripMargin
      val module = code.preprocessModule

      build(module) should contain theSameElementsAs Seq(
        Suggestion.Method(
          name = "foo",
          arguments = Seq(
            Suggestion.Argument("this", "Any", false, false, None),
            Suggestion.Argument("a", "Any", true, false, None)
          ),
          selfType      = "here",
          returnType    = "Any",
          documentation = None
        )
      )
    }

    "build atom simple" in {
      implicit val moduleContext: ModuleContext = freshModuleContext

      val code   = """type MyType a b"""
      val module = code.preprocessModule

      build(module) should contain theSameElementsAs Seq(
        Suggestion.Atom(
          name = "MyType",
          arguments = Seq(
            Suggestion.Argument("a", "Any", false, false, None),
            Suggestion.Argument("b", "Any", false, false, None)
          ),
          returnType    = "MyType",
          documentation = None
        )
      )
    }

    "build atom with documentation" in {
      implicit val moduleContext: ModuleContext = freshModuleContext

      val code =
        """## My sweet type
          |type MyType a b""".stripMargin
      val module = code.preprocessModule

      build(module) should contain theSameElementsAs Seq(
        Suggestion.Atom(
          name = "MyType",
          arguments = Seq(
            Suggestion.Argument("a", "Any", false, false, None),
            Suggestion.Argument("b", "Any", false, false, None)
          ),
          returnType    = "MyType",
          documentation = Some(" My sweet type")
        )
      )
    }

    "build type simple" in {
      implicit val moduleContext: ModuleContext = freshModuleContext

      val code =
        """type Maybe
          |    type Nothing
          |    type Just a""".stripMargin
      val module = code.preprocessModule

      build(module) should contain theSameElementsAs Seq(
        Suggestion.Atom(
          name          = "Nothing",
          arguments     = Seq(),
          returnType    = "Nothing",
          documentation = None
        ),
        Suggestion.Atom(
          name = "Just",
          arguments = Seq(
            Suggestion.Argument("a", "Any", false, false, None)
          ),
          returnType    = "Just",
          documentation = None
        )
      )
    }

    "build type with documentation" in {
      implicit val moduleContext: ModuleContext = freshModuleContext

      val code =
        """## When in doubt
          |type Maybe
          |    ## Nothing here
          |    type Nothing
          |    ## Something there
          |    type Just a""".stripMargin
      val module = code.preprocessModule

      build(module) should contain theSameElementsAs Seq(
        Suggestion.Atom(
          name          = "Nothing",
          arguments     = Seq(),
          returnType    = "Nothing",
          documentation = Some(" Nothing here")
        ),
        Suggestion.Atom(
          name = "Just",
          arguments = Seq(
            Suggestion.Argument("a", "Any", false, false, None)
          ),
          returnType    = "Just",
          documentation = Some(" Something there")
        )
      )
    }

    "build type with methods" in {
      implicit val moduleContext: ModuleContext = freshModuleContext
      val code =
        """type Maybe
          |    type Nothing
          |    type Just a
          |
          |    map f = case this of
          |        Just a  -> Just (f a)
          |        Nothing -> Nothing""".stripMargin
      val module = code.preprocessModule

      build(module) should contain theSameElementsAs Seq(
        Suggestion.Atom(
          name          = "Nothing",
          arguments     = Seq(),
          returnType    = "Nothing",
          documentation = None
        ),
        Suggestion.Atom(
          name = "Just",
          arguments = Seq(
            Suggestion.Argument("a", "Any", false, false, None)
          ),
          returnType    = "Just",
          documentation = None
        ),
        Suggestion.Method(
          name = "map",
          arguments = Seq(
            Suggestion.Argument("this", "Any", false, false, None),
            Suggestion.Argument("f", "Any", false, false, None)
          ),
          selfType      = "Just",
          returnType    = "Any",
          documentation = None
        ),
        Suggestion.Method(
          name = "map",
          arguments = Seq(
            Suggestion.Argument("this", "Any", false, false, None),
            Suggestion.Argument("f", "Any", false, false, None)
          ),
          selfType      = "Nothing",
          returnType    = "Any",
          documentation = None
        )
      )
    }
  }

  private def build(ir: IR.Module): Vector[Suggestion] =
    new SuggestionBuilder().build(ir)

  private def freshModuleContext: ModuleContext =
    ModuleContext(freshNameSupply = Some(new FreshNameSupply))
}

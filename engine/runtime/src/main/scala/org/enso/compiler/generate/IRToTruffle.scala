package org.enso.compiler.generate

import java.util

import com.oracle.truffle.api.source.{Source, SourceSection}
import org.enso.compiler.core.IR
import org.enso.compiler.core.IR.DefinitionSiteArgument
import org.enso.interpreter.{Constants, Language}
import org.enso.interpreter.builder.{ArgDefinitionFactory, ExpressionFactory}
import org.enso.interpreter.node.{ExpressionNode => RuntimeExpression}
import org.enso.interpreter.runtime.callable.function.{
  Function => RuntimeFunction
}
import org.enso.interpreter.runtime.Context
import org.enso.interpreter.runtime.callable.argument.ArgumentDefinition
import org.enso.interpreter.runtime.callable.atom.AtomConstructor
import org.enso.interpreter.runtime.callable.function.FunctionSchema
import org.enso.interpreter.runtime.error.VariableDoesNotExistException
import org.enso.interpreter.runtime.scope.ModuleScope
import org.enso.syntax.text.Location

import scala.jdk.CollectionConverters._

class IRToTruffle(
  val language: Language,
  val source: Source,
  val moduleScope: ModuleScope
) {

  // ==========================================================================
  // === Top-Level Runners ====================================================
  // ==========================================================================

  def run(ir: IR): Unit = ir match {
    case mod @ IR.Module(_, _) => processModule(mod)
    case err: IR.Error         => processError(err)
    case _                     => processError(IR.Error.InvalidIR(ir))
  }

  def runInline(ir: IR): Unit = ???

  // ==========================================================================
  // === IR Processing Functions ==============================================
  // ==========================================================================

  def processModule(module: IR.Module): Unit = {
    val context: Context = language.getCurrentContext

    val imports = module.imports
    val atomDefs = module.bindings.collect {
      case atom: IR.AtomDef => atom
    }
    val methodDefs = module.bindings.collect {
      case method: IR.MethodDef => method
    }

    // Register the imports in scope
    imports.foreach(
      i => this.moduleScope.addImport(context.compiler.requestProcess(i.name))
    )

    // Register the atoms and their constructors in scope
    val atomConstructors =
      atomDefs.map(t => new AtomConstructor(t.name, moduleScope))
    atomConstructors.foreach(moduleScope.registerConstructor)

    atomConstructors
      .zip(atomDefs)
      .foreach {
        case (atomCons, atomDefn) => {
          val argFactory =
            new ArgDefinitionFactory(language, source, moduleScope)
          val argDefs =
            new Array[ArgumentDefinition](atomDefn.getArguments.size)

          for (idx <- Range(0, atomDefn.getArguments.size)) {
            argDefs(idx) = atomDefn.getArguments.get(idx).visit(argFactory, idx)
          }

          atomCons.initializeFields(argDefs: _*)
        }
      }

    // Register the method definitions in scope
    methodDefs.foreach(methodDef => {
      val thisArgument =
        new DefinitionSiteArgument(Constants.Names.THIS_ARGUMENT, None, false)

      val typeName = if (methodDef.typeName == Constants.Names.CURRENT_MODULE) {
        moduleScope.getAssociatedType.getName
      } else {
        methodDef.typeName
      }

      val expressionFactory = new ExpressionFactory(
        language,
        source,
        typeName + Constants.SCOPE_SEPARATOR + methodDef.methodName,
        moduleScope
      )

      val funNode = expressionFactory.processFunctionBody(
        methodDef.function.getLocation,
        (List(thisArgument) ++ methodDef.function.getArguments.asScala).asJava,
        methodDef.function.body
      )
      funNode.markTail()

      val function = new RuntimeFunction(
        funNode.getCallTarget,
        null,
        new FunctionSchema(
          FunctionSchema.CallStrategy.CALL_LOOP,
          funNode.getArgs: _*
        )
      )

      val cons = moduleScope
        .getConstructor(typeName)
        .orElseThrow(
          () => new VariableDoesNotExistException(methodDef.typeName)
        )
      moduleScope.registerMethod(cons, methodDef.methodName, function)
    })
  }

  def processError(error: IR.Error): Unit = {
    ??? // TODO [AA] Any remaining errors should be reported
  }

  // ==========================================================================
  // === Utility Functions ====================================================
  // ==========================================================================

  /** Creates a source section from a given location in the code.
    *
    * @param location the location to turn into a section
    * @return the source section corresponding to `location`
    */
  private def makeSection(location: Option[Location]): SourceSection = {
    location
      .map(loc => source.createSection(loc.start, loc.length))
      .getOrElse(source.createUnavailableSection())
  }

  /** Sets the source section for a given expression node to the provided
    * location.
    *
    * @param expr     the expression to set the location for
    * @param location the location to assign to `expr`
    * @tparam T the type of `expr`
    * @return `expr` with its location set to `location`
    */
  private def setLocation[T <: RuntimeExpression](
    expr: T,
    location: Option[Location]
  ): T = {
    if (location.isDefined) {
      val loc = location.get
      expr.setSourceLocation(loc.start, loc.length)
    }
    expr
  }
}

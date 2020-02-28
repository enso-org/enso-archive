package org.enso.compiler.codegen

import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.source.{Source, SourceSection}
import org.enso.compiler.core.IR
import org.enso.interpreter.node.callable.argument.ReadArgumentNode
import org.enso.interpreter.node.callable.function.{
  BlockNode,
  CreateFunctionNode
}
import org.enso.interpreter.node.callable.thunk.{CreateThunkNode, ForceNode}
import org.enso.interpreter.node.callable.{ApplicationNode, InvokeCallableNode}
import org.enso.interpreter.node.controlflow._
import org.enso.interpreter.node.expression.constant.{
  ConstructorNode,
  DynamicSymbolNode
}
import org.enso.interpreter.node.expression.literal.{
  IntegerLiteralNode,
  TextLiteralNode
}
import org.enso.interpreter.node.expression.operator._
import org.enso.interpreter.node.scope.{AssignmentNode, ReadLocalTargetNode}
import org.enso.interpreter.node.{
  ClosureRootNode,
  ExpressionNode => RuntimeExpression
}
import org.enso.interpreter.runtime.Context
import org.enso.interpreter.runtime.callable.UnresolvedSymbol
import org.enso.interpreter.runtime.callable.argument.{
  ArgumentDefinition,
  CallArgument
}
import org.enso.interpreter.runtime.callable.atom.AtomConstructor
import org.enso.interpreter.runtime.callable.function.{
  FunctionSchema,
  Function => RuntimeFunction
}
import org.enso.interpreter.runtime.error.{
  DuplicateArgumentNameException,
  VariableDoesNotExistException
}
import org.enso.interpreter.runtime.scope.{LocalScope, ModuleScope}
import org.enso.interpreter.{Constants, Language}
import org.enso.syntax.text.Location

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.jdk.OptionConverters._

/** This is an implementation of a codegeneration pass that lowers the Enso
  * [[IR]] into the truffle [[org.enso.compiler.core.Core.Node]] structures that
  * are actually executed.
  *
  * It should be noted that, as is, there is no support for cross-module links,
  * with each lowering pass operating solely on a single module.
  *
  * @param language the language instance for which this is executing
  * @param source the source code that corresponds to the text for which code
  *               is being generated
  * @param moduleScope the scope of the module for which code is being generated
  */
class IRToTruffle(
  val language: Language,
  val source: Source,
  val moduleScope: ModuleScope
) {

  // ==========================================================================
  // === Top-Level Runners ====================================================
  // ==========================================================================

  def run(ir: IR): Unit = ir match {
    case mod: IR.Module => processModule(mod)
    case err: IR.Error  => processError(err)
    case _              => processError(IR.Error.InvalidIR(ir))
  }

  def runInline(
    ir: IR.Expression,
    localScope: LocalScope,
    scopeName: String
  ): RuntimeExpression = {
    new ExpressionProcessor(localScope, scopeName).runInline(ir)
  }

  // ==========================================================================
  // === IR Processing Functions ==============================================
  // ==========================================================================

  private def processModule(module: IR.Module): Unit = {
    val context: Context = language.getCurrentContext

    val imports = module.imports
    val atomDefs = module.bindings.collect {
      case atom: IR.ModuleScope.Definition.Atom => atom
    }
    val methodDefs = module.bindings.collect {
      case method: IR.ModuleScope.Definition.Method => method
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
          val argFactory = new DefinitionArgumentProcessor()
          val argDefs =
            new Array[ArgumentDefinition](atomDefn.arguments.size)

          for (idx <- atomDefn.arguments.indices) {
            argDefs(idx) = argFactory.run(atomDefn.arguments(idx), idx)
          }

          atomCons.initializeFields(argDefs: _*)
        }
      }

    // Register the method definitions in scope
    methodDefs.foreach(methodDef => {
      val thisArgument =
        IR.DefinitionArgument.Specified(
          Constants.Names.THIS_ARGUMENT,
          None,
          suspended = false,
          None
        )

      val typeName = if (methodDef.typeName == Constants.Names.CURRENT_MODULE) {
        moduleScope.getAssociatedType.getName
      } else {
        methodDef.typeName
      }

      val expressionProcessor = new ExpressionProcessor(
        typeName + Constants.SCOPE_SEPARATOR + methodDef.methodName
      )

      val funNode = expressionProcessor.processFunctionBody(
        List(thisArgument) ++ methodDef.function.arguments,
        methodDef.function.body,
        methodDef.function.location
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

  private def processError(error: IR.Error): Unit = {
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

  // ==========================================================================
  // === Expression Processor =================================================
  // ==========================================================================

  sealed private class ExpressionProcessor(
    val scope: LocalScope,
    val scopeName: String
  ) {

    private var currentVarName = "anonymous";

    // === Construction =======================================================

    def this(scopeName: String) = {
      this(new LocalScope(), scopeName)
    }

    def createChild(name: String): ExpressionProcessor = {
      new ExpressionProcessor(this.scope.createChild(), name)
    }

    // === Runner =============================================================

    // TODO [AA] Better error handling here, but really all errors should be
    //  reported before codegen
    def run(ir: IR): RuntimeExpression = ir match {
      case IR.Tagged(ir, _, _, _)         => run(ir)
      case block: IR.Expression.Block     => processBlock(block)
      case literal: IR.Literal            => processLiteral(literal)
      case app: IR.Application            => processApplication(app)
      case name: IR.Name                  => processName(name)
      case function: IR.Function          => processFunction(function)
      case binding: IR.Expression.Binding => processBinding(binding)
      case caseExpr: IR.Case              => processCase(caseExpr)
      case comment: IR.Comment            => processComment(comment)
      case IR.Foreign.Definition(_, _, _, _) =>
        throw new RuntimeException("Foreign expressions not yet implemented.")
      case _ => throw new RuntimeException("Unhandled entity.")
    }

    def runInline(ir: IR.Expression): RuntimeExpression = {
      val expression = run(ir)
      expression.markNotTail()
      expression
    }

    // === Processing =========================================================

    def processComment(comment: IR.Comment): RuntimeExpression =
      this.run(comment.commented)

    def processBlock(block: IR.Expression.Block): RuntimeExpression = {
      if (block.suspended) {
        val childFactory = this.createChild("suspended-block")
        val childScope   = childFactory.scope

        val blockNode = childFactory.processBlock(block.copy(suspended = false))

        val defaultRootNode = ClosureRootNode.build(
          language,
          childScope,
          moduleScope,
          blockNode,
          null,
          s"default::$scopeName"
        )

        val callTarget = Truffle.getRuntime.createCallTarget(defaultRootNode)
        setLocation(CreateThunkNode.build(callTarget), block.location)
      } else {
        val statementExprs = block.expressions.map(this.run(_)).toArray
        val retExpr        = this.run(block.returnValue)

        val blockNode = BlockNode.build(statementExprs, retExpr)
        setLocation(blockNode, block.location)
      }
    }

    def processCase(caseExpr: IR.Case): RuntimeExpression = caseExpr match {
      case IR.Case.Expr(scrutinee, branches, fallback, location, _) =>
        val targetNode = this.run(scrutinee)

        val cases = branches
          .map(
            branch =>
              ConstructorCaseNode
                .build(this.run(branch.pattern), this.run(branch.expression))
          )
          .toArray[CaseNode]

        // Note [Pattern Match Fallbacks]
        val fallbackNode = fallback
          .map(fb => FallbackNode.build(this.run(fb)))
          .getOrElse(DefaultFallbackNode.build())

        val matchExpr = MatchNode.build(cases, fallbackNode, targetNode)
        setLocation(matchExpr, location)
      case IR.Case.Branch(_, _, _, _) =>
        throw new RuntimeException("A CaseBranch should never occur here.")
    }

    /* Note [Pattern Match Fallbacks]
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * Enso in its current state has no coverage checking for constructors on
     * pattern matches as it has no sense of what constructors contribute to
     * make a 'type'. This means that, in absence of a user-provided fallback or
     * catch-all case in a pattern match, the interpreter has to ensure that
     * it has one to catch that error.
     */

    def processBinding(binding: IR.Expression.Binding): RuntimeExpression = {
      currentVarName = binding.name

      val slot = scope.createVarSlot(currentVarName)

      setLocation(
        AssignmentNode.build(this.run(binding.expression), slot),
        binding.location
      )
    }

    def processFunction(function: IR.Function): RuntimeExpression = {
      val scopeName = if (function.canBeTCO) {
        currentVarName
      } else {
        "case_expression"
      }

      val child = this.createChild(scopeName)

      val fn = child.processFunctionBody(
        function.arguments,
        function.body,
        function.location
      )
      fn.setTail(function.canBeTCO)

      fn
    }

    def processName(name: IR.Name): RuntimeExpression = {
      val nameExpr = name match {
        case IR.Name.Literal(name, _, _) =>
          val slot     = scope.getSlot(name).toScala
          val atomCons = moduleScope.getConstructor(name).toScala

          if (name == Constants.Names.CURRENT_MODULE) {
            ConstructorNode.build(moduleScope.getAssociatedType)
          } else if (slot.isDefined) {
            ReadLocalTargetNode.build(slot.get)
          } else if (atomCons.isDefined) {
            ConstructorNode.build(atomCons.get)
          } else {
            DynamicSymbolNode.build(UnresolvedSymbol.build(name, moduleScope))
          }
        case IR.Name.Here(_, _) =>
          ConstructorNode.build(moduleScope.getAssociatedType)
        case IR.Name.This(location, passData) =>
          processName(IR.Name.Literal("this", location, passData))
      }

      setLocation(nameExpr, name.location)
    }

    def processLiteral[T](literal: IR.Literal): RuntimeExpression =
      literal match {
        case IR.Literal.Number(value, location, _) =>
          setLocation(IntegerLiteralNode.build(value.toLong), location)
        case IR.Literal.Text(text, location, _) =>
          setLocation(TextLiteralNode.build(text), location)
      }

    def processApplication(application: IR.Application): RuntimeExpression =
      application match {
        case IR.Application.Prefix(fn, args, hasDefaultsSuspended, loc, _) =>
          val callArgFactory = new CallArgumentProcessor(scope, scopeName)

          val arguments = args
          val callArgs  = new ArrayBuffer[CallArgument]()

          for ((unprocessedArg, position) <- arguments.view.zipWithIndex) {
            val arg = callArgFactory.run(unprocessedArg, position)
            callArgs.append(arg)
          }

          val defaultsExecutionMode = if (hasDefaultsSuspended) {
            InvokeCallableNode.DefaultsExecutionMode.IGNORE
          } else {
            InvokeCallableNode.DefaultsExecutionMode.EXECUTE
          }

          val appNode = ApplicationNode.build(
            this.run(fn),
            callArgs.toArray,
            defaultsExecutionMode
          )

          setLocation(appNode, loc)
        case IR.Application.Operator.Binary(left, operator, right, loc, _) =>
          val leftExpr  = this.run(left)
          val rightExpr = this.run(right)

          // This will be refactored away once operator desugaring exists
          val opExpr = if (operator == "+") {
            AddOperatorNode.build(leftExpr, rightExpr)
          } else if (operator == "-") {
            SubtractOperatorNode.build(leftExpr, rightExpr)
          } else if (operator == "*") {
            MultiplyOperatorNode.build(leftExpr, rightExpr)
          } else if (operator == "/") {
            DivideOperatorNode.build(leftExpr, rightExpr)
          } else if (operator == "%") {
            ModOperatorNode.build(leftExpr, rightExpr)
          } else {
            throw new RuntimeException(
              s"Unsupported operator $operator at codegen time."
            )
          }

          setLocation(opExpr, loc)
        case IR.Application.Force(expr, location, _) =>
          setLocation(ForceNode.build(this.run(expr)), location)
      }

    def processFunctionBody(
      arguments: List[IR.DefinitionArgument.Specified],
      body: IR.Expression,
      location: Option[Location]
    ): CreateFunctionNode = {
      val argFactory = new DefinitionArgumentProcessor(scope, scopeName)

      val argDefinitions = new Array[ArgumentDefinition](arguments.size)
      val argExpressions = new ArrayBuffer[RuntimeExpression]
      val seenArgNames   = mutable.Set[String]()

      // Note [Rewriting Arguments]
      for ((unprocessedArg, idx) <- arguments.view.zipWithIndex) {
        val arg = argFactory.run(unprocessedArg, idx)
        argDefinitions(idx) = arg

        val slot = scope.createVarSlot(arg.getName)
        val readArg =
          ReadArgumentNode.build(idx, arg.getDefaultValue.orElse(null))
        val assignArg = AssignmentNode.build(readArg, slot)

        argExpressions.append(assignArg)

        val argName = arg.getName

        if (seenArgNames contains argName) {
          throw new DuplicateArgumentNameException(argName)
        } else seenArgNames.add(argName)
      }

      val bodyExpr = this.run(body)

      val fnBodyNode = BlockNode.build(argExpressions.toArray, bodyExpr)
      val fnRootNode = ClosureRootNode.build(
        language,
        scope,
        moduleScope,
        fnBodyNode,
        makeSection(location),
        scopeName
      )
      val callTarget = Truffle.getRuntime.createCallTarget(fnRootNode)

      val expr = CreateFunctionNode.build(callTarget, argDefinitions)

      setLocation(expr, location)
    }

    /* Note [Rewriting Arguments]
   * ~~~~~~~~~~~~~~~~~~~~~~~~~~
   * While it would be tempting to handle function arguments as a special case
   * of a lookup, it is instead far simpler to rewrite them such that they
   * just become bindings in the function local scope. This occurs for both
   * explicitly passed argument values, and those that have been defaulted.
   *
   * For each argument, the following algorithm is executed:
   *
   * 1. Argument Conversion: Arguments are converted into their definitions so
   *    as to provide a compact representation of all known information about
   *    that argument.
   * 2. Frame Conversion: A variable slot is created in the function's local
   *    frame to contain the value of the function argument.
   * 3. Read Provision: A `ReadArgumentNode` is generated to allow that
   *    function argument to be treated purely as a local variable access. See
   *    Note [Handling Argument Defaults] for more information on how this
   *    works.
   * 4. Value Assignment: A `AssignmentNode` is created to connect the
   *    argument value to the frame slot created in Step 2.
   * 5. Body Rewriting: The expression representing the argument is written
   *    into the function body, thus allowing it to be read simply.
   */
  }

  // ==========================================================================
  // === Call Argument Processor ==============================================
  // ==========================================================================

  sealed private class CallArgumentProcessor(
    val scope: LocalScope,
    val scopeName: String
  ) {

    // === Runner =============================================================

    def run(arg: IR.CallArgument, position: Int): CallArgument = arg match {
      case IR.CallArgument.Specified(name, value, _, _) =>
        val result = value match {
          case term: IR.Application.Force =>
            new ExpressionProcessor(scope, scopeName).run(term.target)
          case _ =>
            val childScope = scope.createChild()
            val argumentExpression =
              new ExpressionProcessor(childScope, scopeName).run(value)
            argumentExpression.markTail()

            val displayName =
              s"call_argument<${name.getOrElse(String.valueOf(position))}>"

            val section = value.location
              .map(loc => source.createSection(loc.start, loc.end))
              .orNull

            val callTarget = Truffle.getRuntime.createCallTarget(
              ClosureRootNode.build(
                language,
                childScope,
                moduleScope,
                argumentExpression,
                section,
                displayName
              )
            )

            CreateThunkNode.build(callTarget)
        }

        new CallArgument(name.orNull, result)
    }
  }

  // ==========================================================================
  // === Definition Argument Processor ========================================
  // ==========================================================================

  sealed private class DefinitionArgumentProcessor(
    val scope: LocalScope,
    val scopeName: String
  ) {

    // === Construction =======================================================

    def this(scopeName: String) {
      this(new LocalScope(), scopeName)
    }

    def this() {
      this("<root>")
    }

    // === Runner =============================================================

    def run(
      arg: IR.DefinitionArgument.Specified,
      position: Int
    ): ArgumentDefinition = {
      val defaultExpression = arg.defaultValue
        .map(new ExpressionProcessor(scope, scopeName).run(_))
        .orNull

      // Note [Handling Suspended Defaults]
      val defaultedValue = if (arg.suspended && defaultExpression != null) {
        val defaultRootNode = ClosureRootNode.build(
          language,
          scope,
          moduleScope,
          defaultExpression,
          null,
          s"default::$scopeName::${arg.name}"
        )

        CreateThunkNode.build(
          Truffle.getRuntime.createCallTarget(defaultRootNode)
        )
      } else {
        defaultExpression
      }

      val executionMode = if (arg.suspended) {
        ArgumentDefinition.ExecutionMode.PASS_THUNK
      } else {
        ArgumentDefinition.ExecutionMode.EXECUTE
      }

      new ArgumentDefinition(position, arg.name, defaultedValue, executionMode)
    }

    /* Note [Handling Suspended Defaults]
   * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
   * Suspended defaults need to be wrapped in a thunk to ensure that they
   * behave properly with regards to the expected semantics of lazy arguments.
   *
   * Were they not wrapped in a thunk, they would be evaluated eagerly, and
   * hence the point at which the default would be evaluated would differ from
   * the point at which a passed-in argument would be evaluated.
   */
  }
}

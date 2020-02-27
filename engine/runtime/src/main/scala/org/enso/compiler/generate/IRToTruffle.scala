package org.enso.compiler.generate

import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.source.{Source, SourceSection}
import org.enso.compiler.core.IR
import org.enso.compiler.core.IR.DefinitionSiteArgument
import org.enso.interpreter.builder.{ArgDefinitionFactory, CallArgFactory}
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
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

class IRToTruffle(
  val language: Language,
  val source: Source,
  val moduleScope: ModuleScope
) {

  // ==========================================================================
  // === Top-Level Runners ====================================================
  // ==========================================================================

  def run(ir: IR): Unit = ir match {
    case mod @ IR.Module(_, _, _) => processModule(mod)
    case err: IR.Error            => processError(err)
    case _                        => processError(IR.Error.InvalidIR(ir))
  }

  def runInline(
    ir: IR.Expression,
    localScope: LocalScope,
    scopeName: String
  ): RuntimeExpression = {
    ExpressionProcessor(localScope, scopeName).runInline(ir)
  }

  // ==========================================================================
  // === IR Processing Functions ==============================================
  // ==========================================================================

  private def processModule(module: IR.Module): Unit = {
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
        DefinitionSiteArgument(
          Constants.Names.THIS_ARGUMENT,
          None,
          suspended = false
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
        List(thisArgument) ++ methodDef.function.getArguments.asScala,
        methodDef.function.body,
        methodDef.function.getLocation.toScala
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

  private case class ExpressionProcessor(
    scope: LocalScope,
    scopeName: String
  ) {

    private var currentVarName = "anonymous";

    // === Construction =======================================================

    def this(scopeName: String) = {
      this(new LocalScope(), scopeName)
    }

    def createChild(name: String): ExpressionProcessor = {
      this.copy(scope = this.scope.createChild(), name)
    }

    // === Runner =============================================================

    // TODO [AA] Better error handling here, but really all errors should be
    //  reported before codegen
    def run(ir: IR): RuntimeExpression = ir match {
      case IR.Tagged(ir, _, _)   => run(ir)
      case block: IR.Block       => processBlock(block)
      case literal: IR.Literal   => processLiteral(literal)
      case app: IR.Application   => processApplication(app)
      case name: IR.Name         => processName(name)
      case function: IR.Function => processFunction(function)
      case binding: IR.Binding   => processBinding(binding)
      case caseExpr: IR.Case     => processCase(caseExpr)
      case IR.ForeignDefinition(_, _, _, _) =>
        throw new RuntimeException("Foreign expressions not yet implemented.")
      case _ => ???
    }

    def runInline(ir: IR.Expression): RuntimeExpression = {
      val expression = run(ir)
      expression.markNotTail()
      expression
    }

    // === Processing =========================================================

    def processBlock(block: IR.Block): RuntimeExpression = {
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
      case IR.CaseExpr(scrutinee, branches, fallback, location, _) =>
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
      case IR.CaseBranch(_, _, _, _) =>
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

    def processBinding(binding: IR.Binding): RuntimeExpression = {
      currentVarName = binding.name

      val slot = scope.createVarSlot(currentVarName)

      setLocation(
        AssignmentNode.build(this.run(binding.expression), slot),
        binding.location
      )
    }

    def processFunction(function: IR.Function): RuntimeExpression = {
      val (scopeName, isTail, args, body, location) = function match {
        case IR.Lambda(arguments, body, location, _) =>
          (currentVarName, true, arguments, body, location)
        case IR.CaseFunction(arguments, body, location, _) =>
          ("case_expression", false, arguments, body, location)
      }

      val child = this.createChild(scopeName)

      val fn = child.processFunctionBody(args, body, location)
      fn.setTail(isTail)

      fn
    }

    def processName(name: IR.Name): RuntimeExpression = name match {
      case IR.LiteralName(name, location, _) =>
        val slot     = scope.getSlot(name).toScala
        val atomCons = moduleScope.getConstructor(name).toScala

        val variableRead = if (name == Constants.Names.CURRENT_MODULE) {
          ConstructorNode.build(moduleScope.getAssociatedType)
        } else if (slot.isDefined) {
          ReadLocalTargetNode.build(slot.get)
        } else if (atomCons.isDefined) {
          ConstructorNode.build(atomCons.get)
        } else {
          DynamicSymbolNode.build(UnresolvedSymbol.build(name, moduleScope))
        }

        setLocation(variableRead, location)
    }

    def processLiteral[T](literal: IR.Literal): RuntimeExpression =
      literal match {
        case IR.NumberLiteral(value, location, _) =>
          setLocation(IntegerLiteralNode.build(value.toLong), location)
        case IR.TextLiteral(text, location, _) =>
          setLocation(TextLiteralNode.build(text), location)
      }

    def processApplication(application: IR.Application): RuntimeExpression =
      application match {
        case IR.Prefix(fn, args, hasDefaultsSuspended, location, _) =>
          val callArgFactory =
            new CallArgFactory(scope, language, source, scopeName, moduleScope)

          val arguments = args
          val callArgs  = new ArrayBuffer[CallArgument]()

          for ((unprocessedArg, position) <- arguments.view.zipWithIndex) {
            val arg = unprocessedArg.visit(callArgFactory, position)
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

          setLocation(appNode, location)
        case IR.BinaryOperator(left, operator, right, location, _) =>
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

          setLocation(opExpr, location)
        case IR.ForcedTerm(expr, location, _) =>
          setLocation(ForceNode.build(this.run(expr)), location)
      }

    def processFunctionBody(
      arguments: List[IR.DefinitionSiteArgument],
      body: IR.Expression,
      location: Option[Location]
    ): CreateFunctionNode = {
      val argFactory = new ArgDefinitionFactory(
        scope,
        language,
        source,
        scopeName,
        moduleScope
      )

      val argDefinitions = new Array[ArgumentDefinition](arguments.size)
      val argExpressions = new ArrayBuffer[RuntimeExpression]
      val seenArgNames   = mutable.Set[String]()

      // Note [Rewriting Arguments]
      for ((unprocessedArg, idx) <- arguments.view.zipWithIndex) {
        val arg = unprocessedArg.visit(argFactory, idx)
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
}

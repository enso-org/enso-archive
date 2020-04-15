package org.enso.compiler.pass.analyse

import org.enso.compiler.InlineContext
import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass

// TODO [AA] Need to remove the syntax for explicit forces.
/** This pass implements demand analysis for Enso.
  *
  * Demand analysis is the process of determining _when_ a suspended term needs
  * to be forced (where the suspended value is _demanded_). It does the
  * following:
  *
  * This pass needs to be run after [[AliasAnalysis]], and also assumes that
  * all members of [[IR.IRKind.Primitive]] have been removed from the IR by the
  * time that it runs.
  */
case object DemandAnalysis extends IRPass {
  override type Metadata = IR.Metadata.Empty

  /** Executes the demand analysis process on an Enso module.
    *
    * @param ir the Enso IR to process
    * @return `ir`, transformed to correctly force terms
    */
  override def runModule(ir: IR.Module): IR.Module = {
    ir.copy(bindings =
      ir.bindings.map(t => t.mapExpressions(runExpression(_, InlineContext())))
    )
  }

  /** Executes the demand analysis process on an Enso expression.
    *
    * @param expression the Enso IR to process
    * @param inlineContext a context object that contains the information needed
    *                      for inline evaluation
    * @return `ir`, transformed to correctly force terms
    */
  override def runExpression(
    expression: IR.Expression,
    inlineContext: InlineContext
  ): IR.Expression = expression match {
    case fn: IR.Function     => analyseFunction(fn)
    case name: IR.Name       => analyseName(name)
    case app: IR.Application => analyseApplication(app)
    case typ: IR.Type        => analyseType(typ)
    case cse: IR.Case        => analyseCase(cse)
    case block @ IR.Expression.Block(_, _, _, _, _) =>
      block
    case binding @ IR.Expression.Binding(_, _, _, _) =>
      binding
    case lit: IR.Literal     => lit
    case err: IR.Error       => err
    case foreign: IR.Foreign => foreign
    case comment: IR.Comment =>
      comment.mapExpressions(x => runExpression(x, inlineContext))
  }

  def analyseFunction(function: IR.Function): IR.Function = function

  def analyseName(name: IR.Name): IR.Name = name

  def analyseApplication(application: IR.Application): IR.Application =
    application

  def analyseType(typ: IR.Type): IR.Type = typ

  def analyseCase(cse: IR.Case): IR.Case = cse
}

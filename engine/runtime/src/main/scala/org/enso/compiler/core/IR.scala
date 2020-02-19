package org.enso.compiler.core

import java.util.Optional

import org.enso.compiler.core.IR._
import org.enso.syntax.text.{AST, Location}
import shapeless.HList

import scala.jdk.CollectionConverters._

// TODO [AA] Refactor into a proper nested hierarchy once all clients are
//  written in scala.

/** [[IR]] is a temporary and fairly unsophisticated internal representation
  * format for Enso programs.
  *
  * It is a purely tree-based representation to support basic desugaring and
  * analysis passes that do not rely on the ability to create cycles in the IR
  * itself. Its existence is the natural evolution of the older AstExpression
  * format used during the initial development of the interpreter.
  *
  * In time, it will be replaced by [[Core]], but expediency dictates that we
  * retain and evolve this representation for the near future.
  */
sealed trait IR
object IR {

  // === Basic Shapes =========================================================
  sealed case class Empty() extends IR with IRKind.Primitive

  sealed case class Tagged[T <: HList](ir: IR, data: T)
      extends IR
      with IRKind.Primitive

  // === Module ===============================================================

  sealed case class Module(
    imports: List[AstImport],
    bindings: List[ModuleSymbol]
  ) extends IR
      with IRKind.Primitive {

    def visit[T](visitor: AstModuleScopeVisitor[T]): Unit = {
      val types = new java.util.ArrayList[TypeDef]()
      val defs  = new java.util.ArrayList[MethodDef]()

      bindings.foreach {
        case assignment: MethodDef => defs.add(assignment)
        case typeDef: TypeDef      => types.add(typeDef)
      }

      visitor.visitModuleScope(
        imports.asJava,
        types,
        defs
      )
    }
  }

  // === Module Symbols =======================================================

  sealed trait ModuleSymbol extends IR

  sealed case class TypeDef(
    name: String,
    arguments: List[DefinitionSiteArgument]
  ) extends ModuleSymbol
      with IRKind.Primitive {
    def getArguments: java.util.List[DefinitionSiteArgument] = arguments.asJava
  }

  sealed case class MethodDef(
    typeName: String,
    methodName: String,
    fun: Lambda
  ) extends ModuleSymbol
      with IRKind.Primitive

  sealed case class AstImport(name: String) extends IR with IRKind.Primitive

  // === Expression ===========================================================

  sealed trait Expression extends IR {
    def location: Option[Location]
    def getLocation: Optional[Location] = Optional.ofNullable(location.orNull)
    def visit[T](visitor: AstExpressionVisitor[T]): T
  }

  // === Function =============================================================

  sealed trait Function extends Expression

  case class Lambda(
    location: Option[Location],
    arguments: List[DefinitionSiteArgument],
    body: Expression
  ) extends Function
      with IRKind.Primitive {
    override def visit[T](visitor: AstExpressionVisitor[T]): T =
      visitor.visitLambda(this)

    def getArguments: java.util.List[DefinitionSiteArgument] = arguments.asJava
  }

  // TODO [AA] Get rid of case function
  case class CaseFunction(
    location: Option[Location],
    arguments: List[DefinitionSiteArgument],
    body: Expression
  ) extends Function
      with IRKind.Primitive {
    override def visit[T](visitor: AstExpressionVisitor[T]): T =
      visitor.visitCaseFunction(this)

    def getArguments: java.util.List[DefinitionSiteArgument] = arguments.asJava
  }

  // === Definition-Site Arguments ============================================

  sealed case class DefinitionSiteArgument(
    name: String,
    defaultValue: Option[Expression],
    suspended: Boolean
  ) extends IR
      with IRKind.Primitive {

    def visit[T](visitor: AstArgDefinitionVisitor[T], position: Int): T =
      visitor.visitArg(
        name,
        Optional.ofNullable(defaultValue.orNull),
        suspended,
        position
      )
  }

  // === Call-Site Arguments ==================================================

  sealed trait CallArgumentDefinition extends IR {
    def visit[T](visitor: AstCallArgVisitor[T], position: Int): T
  }

  // TODO [AA] Refactor the below cases into a single case
  sealed case class NamedCallArgument(name: String, value: Expression)
      extends CallArgumentDefinition
      with IRKind.Primitive {
    override def visit[T](visitor: AstCallArgVisitor[T], position: Int): T =
      visitor.visitCallArg(Optional.of(name), value, position)
  }

  sealed case class UnnamedCallArgument(value: Expression)
      extends CallArgumentDefinition
      with IRKind.Primitive {
    override def visit[T](visitor: AstCallArgVisitor[T], position: Int): T =
      visitor.visitCallArg(Optional.empty(), value, position)
  }

  // === Literals =============================================================

  sealed trait Literal extends Expression

  sealed case class NumberLiteral(location: Option[Location], value: String)
      extends Literal
      with IRKind.Primitive {
    override def visit[T](visitor: AstExpressionVisitor[T]): T =
      visitor.visitLong(this)
  }

  sealed case class StringLiteral(location: Option[Location], string: String)
      extends Literal
      with IRKind.Primitive {
    override def visit[T](visitor: AstExpressionVisitor[T]): T =
      visitor.visitStringLiteral(this)
  }

  // === Names ================================================================

  case class Name(location: Option[Location], name: String)
      extends Expression
      with IRKind.Primitive {
    override def visit[T](visitor: AstExpressionVisitor[T]): T =
      visitor.visitName(this)
  }

  // === Applications =========================================================

  sealed trait Application extends Expression

  sealed case class Prefix(
    location: Option[Location],
    fun: Expression,
    args: List[CallArgumentDefinition],
    hasDefaultsSuspended: Boolean
  ) extends Application
      with IRKind.Primitive {
    override def visit[T](visitor: AstExpressionVisitor[T]): T =
      visitor.visitFunctionApplication(this)
    def getArgs: java.util.List[CallArgumentDefinition] = args.asJava
  }

  sealed case class BinaryOperator(
    location: Option[Location],
    op: String,
    left: Expression,
    right: Expression
  ) extends Application
      with IRKind.Sugar {
    override def visit[T](visitor: AstExpressionVisitor[T]): T =
      visitor.visitArithOp(this)
  }

  sealed case class ForcedTerm(location: Option[Location], target: Expression)
      extends Application
      with IRKind.Primitive {
    override def visit[T](visitor: AstExpressionVisitor[T]): T =
      visitor.visitForce(this)
  }

  // === Pattern Match ========================================================

  sealed case class Match(
    location: Option[Location],
    scrutinee: Expression,
    branches: Seq[CaseBranch],
    fallback: Option[CaseFunction]
  ) extends Expression
      with IRKind.Primitive {
    override def visit[T](visitor: AstExpressionVisitor[T]): T =
      visitor.visitMatch(this)
    def getBranches: java.util.List[CaseBranch] = branches.asJava
    def getFallback: Optional[CaseFunction] =
      Optional.ofNullable(fallback.orNull)
  }

  // TODO [AA] Should become an expression
  sealed case class CaseBranch(
    location: Option[Location],
    cons: Expression,
    function: CaseFunction
  ) extends IRKind.Primitive

  // === Structure ============================================================

  sealed case class Block(
    location: Option[Location],
    statements: List[Expression],
    retVal: Expression,
    suspended: Boolean = false
  ) extends Expression
      with IRKind.Primitive {
    override def visit[T](visitor: AstExpressionVisitor[T]): T =
      visitor.visitBlock(statements.asJava, retVal, suspended)
  }

  case class Binding(
    location: Option[Location],
    name: String,
    body: Expression
  ) extends Expression
      with IRKind.Primitive {
    override def visit[T](visitor: AstExpressionVisitor[T]): T =
      visitor.visitAssignment(this)
  }

  // === Foreign ==============================================================

  sealed case class ForeignDefinition(
    location: Option[Location],
    lang: String,
    code: String
  ) extends Expression
      with IRKind.Primitive {
    override def visit[T](visitor: AstExpressionVisitor[T]): T =
      visitor.visitForeign(lang, code)
  }

  // === Errors ===============================================================

  sealed trait Error extends IR with IRKind.Primitive
  object Error {
    sealed case class Syntax(ast: AST) extends Error
  }

  // ==========================================================================
  // === Primitive / Sugar ====================================================
  // ==========================================================================

  sealed trait IRKind {}
  object IRKind       {

    /** This trait encodes that a given piece of the [[IR]] is considered to be
      * a primitive construct in Enso.
      */
    sealed trait Primitive extends IRKind

    /** This trait encodes that a given piece of the [[IR]] is considered to
      * represent syntax sugar in Enso.
      *
      * All [[Sugar]] constructs should be desugared into [[Primitive]]
      * constructs as soon as possible.
      */
    sealed trait Sugar extends IRKind
  }
}

// ============================================================================
// === Visitors ===============================================================
// ============================================================================

/** The visitor pattern for the [[Expression]] types.
  *
  * @tparam T the type resultant from the visitor
  */
trait AstExpressionVisitor[+T] {
  def visitLong(l: NumberLiteral): T

  def visitArithOp(astArithOp: BinaryOperator): T

  def visitForeign(lang: String, code: String): T

  def visitName(astVariable: Name): T

  def visitLambda(function: Lambda): T

  def visitCaseFunction(function: CaseFunction): T

  def visitFunctionApplication(application: Prefix): T

  def visitAssignment(assignment: Binding): T

  def visitMatch(astMatch: Match): T

  def visitForce(target: ForcedTerm): T

  def visitStringLiteral(string: StringLiteral): T

  def visitBlock(
    statements: java.util.List[Expression],
    retValue: Expression,
    suspended: Boolean
  ): T
}

/** The visitor pattern for the [[AstModuleScope]] types.
  *
  * @tparam T the type resultant from the visitor
  */
trait AstModuleScopeVisitor[T] {

  @throws(classOf[Exception])
  def visitModuleScope(
    imports: java.util.List[AstImport],
    typeDefs: java.util.List[TypeDef],
    bindings: java.util.List[MethodDef]
  ): Unit
}

/** The visitor pattern for the [[DefinitionSiteArgument]] types.
  *
  * @tparam T the type resultant from the visitor
  */
trait AstArgDefinitionVisitor[+T] {

  def visitArg(
    name: String,
    value: Optional[Expression],
    suspended: Boolean,
    position: Int
  ): T
}

/** The visitor pattern for the [[CallArgumentDefinition]] types.
  *
  * @tparam T the type resultant from the visitor
  */
trait AstCallArgVisitor[+T] {

  def visitCallArg(
    name: Optional[String],
    value: Expression,
    position: Int
  ): T
}

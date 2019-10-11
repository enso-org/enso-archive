package org.enso.compiler.generate

import org.enso.compiler.ir.HLIR
import org.enso.syntax.text.AST

/**
  * This is a representation of the raw conversion from the Parser [[AST AST]]
  * to the internal [[HLIR.IR IR]] used by the static transformation passes.
  */
object ASTToHLIR {

  /**
    * Transforms the input [[AST]] into the compiler's high-level intermediate
    * representation.
    *
    * @param inputAST the AST to transform
    * @return a representation of the program construct represented by
    *         `inputAST` in the compiler's [[HLIR.IR IR]]
    */
  def process(inputAST: AST): HLIR.IR = inputAST match {
    case AST.App.any(inputAST)     => processApplication(inputAST)
    case AST.Block.any(inputAST)   => processBlock(inputAST)
    case AST.Comment.any(inputAST) => processComment(inputAST)
    case AST.Ident.any(inputAST)   => processIdent(inputAST)
    case AST.Import.any(inputAST)  => processBinding(inputAST)
    case AST.Invalid.any(inputAST) => processInvalid(inputAST)
    case AST.Literal.any(inputAST) => processLiteral(inputAST)
    case AST.Mixfix.any(inputAST)  => processApplication(inputAST)
    case AST.Module.any(inputAST)  => processModule(inputAST)
    case AST.Group.any(inputAST)   => processGroup(inputAST)
    case AST.Def.any(inputAST)     => processBinding(inputAST)
    case AST.Foreign.any(inputAST) => processBlock(inputAST)
    case _ =>
      HLIR.Error.UnhandledAST(inputAST)
  }

  /**
    * Transforms invalid entities from the parser AST.
    *
    * @param invalid the invalid entity
    * @return a representation of `invalid` in the compiler's [[HLIR.IR IR]]
    */
  def processInvalid(invalid: AST.Invalid): HLIR.Error = invalid match {
    case AST.Invalid.Unexpected(str, unexpectedTokens) =>
      HLIR.Error.UnexpectedToken(str, unexpectedTokens.map(t => process(t.el)))
    case AST.Invalid.Unrecognized(str) => HLIR.Error.UnrecognisedSymbol(str)
    case AST.Ident.InvalidSuffix(identifier, suffix) =>
      HLIR.Error.InvalidSuffix(processIdent(identifier), suffix)
    case AST.Literal.Text.Unclosed(text) =>
      HLIR.Error.UnclosedText(text.body.lines.toList.map(processLine))
    case _ =>
      throw new RuntimeException(
        "Fatal: Unhandled entity in processInvalid = " + invalid
      )
  }

  /**
    * Transforms identifiers from the parser AST.
    *
    * @param identifier the identifier
    * @return a representation of `identifier` in the compiler's [[HLIR.IR IR]]
    */
  def processIdent(identifier: AST.Ident): HLIR.Identifier = identifier match {
    case AST.Ident.Blank(_)             => HLIR.Identifier.Blank()
    case AST.Ident.Var(name)            => HLIR.Identifier.Variable(name)
    case AST.Ident.Cons.any(identifier) => processIdentConstructor(identifier)
    case AST.Ident.Opr.any(identifier)  => processIdentOperator(identifier)
    case AST.Ident.Mod(name)            => HLIR.Identifier.Module(name)
    case _ =>
      throw new RuntimeException(
        "Fatal: Unhandled entity in processIdent = " + identifier
      )
  }

  /**
    * Transforms an operator identifier from the parser AST.
    *
    * @param operator the operator to transform
    * @return a representation of `operator` in the compiler's [[HLIR.IR IR]]
    */
  def processIdentOperator(
    operator: AST.Ident.Opr
  ): HLIR.Identifier.Operator = HLIR.Identifier.Operator(operator.name)

  /**
    * Transforms a constructor identifier from the parser AST.
    *
    * @param constructor the constructor name to transform
    * @return a representation of `constructor` in the compiler's [[HLIR.IR IR]]
    */
  def processIdentConstructor(
    constructor: AST.Ident.Cons
  ): HLIR.Identifier.Constructor = HLIR.Identifier.Constructor(constructor.name)

  /**
    * Transforms a literal from the parser AST.
    *
    * @param literal the literal to transform
    * @return a representation of `literal` in the compiler's [[HLIR.IR IR]]
    */
  def processLiteral(literal: AST.Literal): HLIR.Literal = {
    literal match {
      case AST.Literal.Number(base, number) => HLIR.Literal.Number(number, base)
      case AST.Literal.Text.Raw(body) => {
        HLIR.Literal.Text.Raw(body.lines.toList.map(processLine))
      }
      case AST.Literal.Text.Fmt(body) => {
        HLIR.Literal.Text.Format(body.lines.toList.map(processLine))
      }
      case _ =>
        throw new RuntimeException(
          "Fatal: Unhandled entity in processLiteral = " + literal
        )
    }
  }

  /**
    * Transforms a line of a text literal from the parser AST.
    *
    * @param line the literal line to transform
    * @return a representation of `line` in the compiler's [[HLIR.IR IR]]
    */
  def processLine(
    line: AST.Literal.Text.LineOf[AST.Literal.Text.Segment[AST]]
  ): HLIR.Literal.Text.Line =
    HLIR.Literal.Text.Line(line.elem.map(processTextSegment))

  /**
    * Transforms a segment of text from the parser AST.
    *
    * @param segment the text segment to transform
    * @return a representation of `segment` in the compiler's [[HLIR.IR IR]]
    */
  def processTextSegment(
    segment: AST.Literal.Text.Segment[AST]
  ): HLIR.Literal.Text.Segment = segment match {
    case AST.Literal.Text.Segment._Plain(str) =>
      HLIR.Literal.Text.Segment.Plain(str)
    case AST.Literal.Text.Segment._Expr(expr) =>
      HLIR.Literal.Text.Segment.Expression(expr.map(process))
    case AST.Literal.Text.Segment._Escape(code) =>
      HLIR.Literal.Text.Segment.EscapeCode(code)
    case _ =>
      throw new RuntimeException(
        "Fatal: Unhandled entity in processTextSegment = " + segment
      )
  }

  /**
    * Transforms a function application from the parser AST.
    *
    * @param application the function application to transform
    * @return a representation of `application` in the compiler's [[HLIR.IR IR]]
    */
  def processApplication(application: AST): HLIR.Application =
    application match {
      case AST.App.Prefix(fn, arg) =>
        HLIR.Application.Prefix(process(fn), process(arg))
      case AST.App.Infix(leftArg, fn, rightArg) =>
        HLIR.Application.Infix(
          process(leftArg),
          processIdentOperator(fn),
          process(rightArg)
        )
      case AST.App.Section.Left(arg, fn) =>
        HLIR.Application.Section.Left(process(arg), processIdentOperator(fn))
      case AST.App.Section.Right(fn, arg) =>
        HLIR.Application.Section.Right(processIdentOperator(fn), process(arg))
      case AST.App.Section.Sides(fn) =>
        HLIR.Application.Section.Sides(processIdentOperator(fn))
      case AST.Mixfix(fnSegments, args) =>
        HLIR.Application
          .Mixfix(fnSegments.toList.map(processIdent), args.toList.map(process))
      case _ =>
        throw new RuntimeException(
          "Fatal: Unhandled entity in processApplication = " + application
        )
    }

  /**
    * Transforms a source code block from the parser AST.
    *
    * This handles both blocks of Enso-native code, and blocks of foreign
    * language code.
    *
    * @param block the block to transform
    * @return a representation of `block` in the compiler's [[HLIR.IR IR]]
    */
  def processBlock(block: AST): HLIR.Block = block match {
    case AST.Block(_, _, firstLine, lines) =>
      HLIR.Block
        .Enso(
          process(firstLine.elem) ::
          lines.filter(t => t.elem.isDefined).map(t => process(t.elem.get))
        )
    case AST.Foreign(_, language, code) => HLIR.Block.Foreign(language, code)
    case _ =>
      throw new RuntimeException(
        "Fatal: Unhandled-entity in ProcessBlock = " + block
      )
  }

  /**
    * Transforms a module top-level from the parser AST.
    *
    * @param module the module to transform
    * @return a representation of `module` in the compiler's [[HLIR.IR IR]]
    */
  def processModule(module: AST.Module): HLIR.Module = module match {
    case AST.Module(lines) =>
      HLIR.Module(
        lines.filter(t => t.elem.isDefined).map(t => process(t.elem.get))
      )
    case _ =>
      throw new RuntimeException(
        "Fatal: Unhandled entity in processModule = " + module
      )
  }

  /**
    * Transforms a comment from the parser AST.
    *
    * @param comment the comment to transform
    * @return a representation of `comment` in the compiler's [[HLIR.IR IR]]
    */
  def processComment(comment: AST): HLIR.Comment = comment match {
    case AST.Comment(lines) => HLIR.Comment(lines)
    case _ =>
      throw new RuntimeException(
        "Fatal: Unhandled entity in processComment = " + comment
      )
  }

  /**
    * Transforms a group from the parser AST.
    *
    * In [[HLIR]], groups are actually non-entities, as all grouping is handled
    * implicitly by the IR format. A valid group is replaced by its contents in
    * the IR, while invalid groups are replaced by error nodes.
    *
    * @param group the group to transform
    * @return a representation of `group` in the compiler's [[HLIR.IR IR]]
    */
  def processGroup(group: AST): HLIR.IR = group match {
    case AST.Group(maybeAST) =>
      maybeAST match {
        case Some(ast) => process(ast)
        case None      => HLIR.Error.EmptyGroup()
      }
    case _ =>
      throw new RuntimeException(
        "Fatal: Unhandled entity in processGroup = " + group
      )
  }

  /**
    * Transforms a binding from the parser AST.
    *
    * Bindings are any constructs that give some Enso language construct a name.
    * This includes type definitions, imports, assignments, and so on.
    *
    * @param binding the binding to transform
    * @return a representation of `binding` in the compiler's [[HLIR.IR IR]]
    */
  def processBinding(binding: AST): HLIR.Binding = binding match {
    case AST.Def(constructor, arguments, optBody) =>
      HLIR.Binding.RawType(
        processIdentConstructor(constructor),
        arguments.map(process),
        optBody.map(process)
      )
    case AST.Import(components) => {
      HLIR.Binding.Import(
        components.toList.map(t => processIdentConstructor(t))
      )
    }
    case _ =>
      throw new RuntimeException(
        "Fatal: Unhandled entity in processBinding = " + binding
      )
  }
}

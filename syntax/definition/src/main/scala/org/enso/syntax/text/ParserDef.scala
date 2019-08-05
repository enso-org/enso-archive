package org.enso.syntax.text

import org.enso.flexer.Pattern.char
import org.enso.flexer.Pattern.range
import org.enso.flexer.Pattern._
import org.enso.flexer._
import org.enso.syntax.text.AST.Text.Quote
import org.enso.syntax.text.AST._

import scala.annotation.tailrec
import scala.reflect.runtime.universe.reify

case class ParserDef() extends ParserBase[AST] {

  val ast = AST

  val any: Pattern  = range(5, Int.MaxValue) // FIXME 5 -> 0
  val pass: Pattern = Pass
  val eof: Pattern  = char('\u0000')
  val none: Pattern = None_

  final def anyOf(chars: String): Pattern =
    anyOf(chars.map(char))

  final def anyOf(alts: Seq[Pattern]): Pattern =
    alts.fold(none)(_ | _)

  final def noneOf(chars: String): Pattern = {
    val pointCodes  = chars.map(_.toInt).sorted
    val startPoints = 5 +: pointCodes.map(_ + 1) // FIXME 5 -> 0
    val endPoints   = pointCodes.map(_ - 1) :+ Int.MaxValue
    val ranges      = startPoints.zip(endPoints)
    val validRanges = ranges.filter { case (s, e) => e >= s }
    val patterns    = validRanges.map { case (s, e) => range(s, e) }
    anyOf(patterns)
  }

  final def not(char: Char): Pattern =
    noneOf(char.toString)

  def repeat(p: Pattern, min: Int, max: Int): Pattern = {
    val minPat = repeat(p, min)
    _repeatAlt(p, max - min, minPat, minPat)
  }

  def repeat(p: Pattern, num: Int): Pattern =
    _repeat(p, num, pass)

  @tailrec
  final def _repeat(p: Pattern, num: Int, out: Pattern): Pattern = num match {
    case 0 => out
    case _ => _repeat(p, num - 1, out >> p)
  }

  @tailrec
  final def _repeatAlt(p: Pattern, i: Int, ch: Pattern, out: Pattern): Pattern =
    i match {
      case 0 => out
      case _ =>
        val ch2 = ch >> p
        _repeatAlt(p, i - 1, ch2, out | ch2)
    }

  final def withSome[T, S](opt: Option[T])(f: T => S): S = opt match {
    case None    => throw new Error("Internal Error")
    case Some(a) => f(a)
  }

  //// Cleaning ////

  final override def initialize(): Unit = {
    onBlockBegin(0)
  }

  ////////////////
  //// Result ////
  ////////////////

  override def getResult() = result

  var result: Option[AST]         = None
  var astStack: List[Option[AST]] = Nil

  final def pushAST(): Unit = logger.trace {
    astStack +:= result
    result = None
  }

  final def popAST(): Unit = logger.trace {
    result   = astStack.head
    astStack = astStack.tail
  }

  final def app(fn: String => AST): Unit =
    app(fn(currentMatch))

  final def app(t: AST): Unit = logger.trace {
    result = Some(result match {
      case None    => t
      case Some(r) => App(r, useLastOffset(), t)
    })
  }

  ///////////////////////////////////
  //// Basic Char Classification ////
  ///////////////////////////////////

  val NORMAL = defineGroup("Normal")

  val lowerLetter: Pattern = range('a', 'z')
  val upperLetter: Pattern = range('A', 'Z')
  val digit: Pattern       = range('0', '9')
  val hex: Pattern         = digit | range('a', 'f') | range('A', 'F')
  val alphaNum: Pattern    = digit | lowerLetter | upperLetter
  val whitespace0: Pattern = ' '.many
  val whitespace: Pattern  = ' '.many1
  val newline: Pattern     = '\n'

  ////////////////
  //// Offset ////
  ////////////////

  var lastOffset: Int            = 0
  var lastOffsetStack: List[Int] = Nil

  final def pushLastOffset(): Unit = logger.trace {
    lastOffsetStack +:= lastOffset
    lastOffset = 0
  }

  final def popLastOffset(): Unit = logger.trace {
    lastOffset      = lastOffsetStack.head
    lastOffsetStack = lastOffsetStack.tail
  }

  final def useLastOffset(): Int = logger.trace {
    val offset = lastOffset
    lastOffset = 0
    offset
  }

  final def onWhitespace(): Unit = onWhitespace(0)
  final def onWhitespace(shift: Int): Unit = logger.trace {
    val diff = currentMatch.length + shift
    lastOffset += diff
    logger.log(s"lastOffset + $diff = $lastOffset")
  }

  ////////////////////
  //// IDENTIFIER ////
  ////////////////////

  var identBody: Option[Ident] = None

  final def onIdent(cons: String => Ident): Unit = logger.trace {
    onIdent(cons(currentMatch))
  }

  final def onIdent(ast: Ident): Unit = logger.trace {
    identBody = Some(ast)
    beginGroup(IDENT_SFX_CHECK)
  }

  final def submitIdent(): Unit = logger.trace {
    withSome(identBody) { body =>
      app(body)
      identBody = None
    }
  }

  final def onIdentErrSfx(): Unit = logger.trace {
    withSome(identBody) { body =>
      val ast = Ident.InvalidSuffix(body, currentMatch)
      app(ast)
      identBody = None
      endGroup()
    }
  }

  final def onNoIdentErrSfx(): Unit = logger.trace {
    submitIdent()
    endGroup()
  }

  final def finalizeIdent(): Unit = logger.trace {
    if (identBody != None) submitIdent()
  }

  val indentChar: Pattern  = alphaNum | '_'
  val identBody_ : Pattern = indentChar.many >> '\''.many
  val variable: Pattern    = lowerLetter >> identBody_
  val constructor: Pattern = upperLetter >> identBody_
  val identBreaker: String = "^`!@#$%^&*()-=+[]{}|;:<>,./ \t\r\n\\"
  val identErrSfx: Pattern = noneOf(identBreaker).many1

  val IDENT_SFX_CHECK = defineGroup("Identifier Suffix Check")

  // format: off
  NORMAL          rule variable    run reify { onIdent(ast.Var(_)) }
  NORMAL          rule constructor run reify { onIdent(ast.Cons(_)) }
  NORMAL          rule "_"         run reify { onIdent(ast.Blank) }
  IDENT_SFX_CHECK rule identErrSfx run reify { onIdentErrSfx() }
  IDENT_SFX_CHECK rule pass        run reify { onNoIdentErrSfx() }
  // format: on

  //////////////////
  //// Operator ////
  //////////////////

  final def onOp(cons: String => Ident): Unit = logger.trace {
    onOp(cons(currentMatch))
  }

  final def onNoModOp(cons: String => Ident): Unit = logger.trace {
    onNoModOp(cons(currentMatch))
  }

  final def onOp(ast: Ident): Unit = logger.trace {
    identBody = Some(ast)
    beginGroup(OPERATOR_MOD_CHECK)
  }

  final def onNoModOp(ast: Ident): Unit = logger.trace {
    identBody = Some(ast)
    beginGroup(OPERATOR_SFX_CHECK)
  }

  final def onModifier(): Unit = logger.trace {
    withSome(identBody) { body =>
      identBody = Some(Opr.Mod(body.asInstanceOf[Opr].name))
    }
  }

  val operatorChar: Pattern    = anyOf("!$%&*+-/<>?^~|:\\")
  val operatorErrChar: Pattern = operatorChar | "=" | "," | "."
  val operatorErrSfx: Pattern  = operatorErrChar.many1
  val eqOperators: Pattern     = "=" | "==" | ">=" | "<=" | "/=" | "#="
  val dotOperators: Pattern    = "." | ".." | "..." | ","
  val operator: Pattern        = operatorChar.many1
  val groupOperators: Pattern  = anyOf("()[]{}")
  val noModOperator: Pattern   = eqOperators | dotOperators | groupOperators

  val OPERATOR_SFX_CHECK = defineGroup("Operator Suffix Check")
  val OPERATOR_MOD_CHECK = defineGroup("Operator Modifier Check")
  OPERATOR_MOD_CHECK.setParent(OPERATOR_SFX_CHECK)

  // format: off
  NORMAL             rule operator       run reify { onOp(ast.Opr(_)) }
  NORMAL             rule noModOperator  run reify { onNoModOp(ast.Opr(_)) }
  OPERATOR_MOD_CHECK rule "="            run reify { onModifier() }
  OPERATOR_SFX_CHECK rule operatorErrSfx run reify { onIdentErrSfx() }
  OPERATOR_SFX_CHECK rule pass           run reify { onNoIdentErrSfx() }
  // format: on

  ////////////////////////////////////
  //// NUMBER (e.g. 16_ff0000.ff) ////
  ////////////////////////////////////

  var numberPart1: String = ""
  var numberPart2: String = ""

  final def numberReset(): Unit = logger.trace {
    numberPart1 = ""
    numberPart2 = ""
  }

  final def submitNumber(): Unit = logger.trace {
    val base = if (numberPart1 == "") None else Some(numberPart1)
    app(Number(base, numberPart2))
    numberReset()
  }

  final def onDanglingBase(): Unit = logger.trace {
    endGroup()
    app(Number.DanglingBase(numberPart2))
    numberReset()
  }

  final def onDecimal(): Unit = logger.trace {
    numberPart2 = currentMatch
    beginGroup(NUMBER_PHASE2)
  }

  final def onExplicitBase(): Unit = logger.trace {
    endGroup()
    numberPart1 = numberPart2
    numberPart2 = currentMatch.substring(1)
    submitNumber()
  }

  final def onNoExplicitBase(): Unit = logger.trace {
    endGroup()
    submitNumber()
  }

  val decimal: Pattern = digit.many1

  val NUMBER_PHASE2: Group = defineGroup("Number Phase 2")

  // format: off
  NORMAL        rule decimal                 run reify { onDecimal() }
  NUMBER_PHASE2 rule ("_" >> alphaNum.many1) run reify { onExplicitBase() }
  NUMBER_PHASE2 rule ("_")                   run reify { onDanglingBase() }
  NUMBER_PHASE2 rule pass                    run reify { onNoExplicitBase() }
  // format: on

  //////////////
  //// Text ////
  //////////////

  var textStateStack: List[Text.Interpolated] = Nil

  final def currentText = textStateStack.head
  final def withCurrentText(f: Text.Interpolated => Text.Interpolated) =
    textStateStack = f(textStateStack.head) :: textStateStack.tail

  final def pushTextState(quoteSize: Quote): Unit = logger.trace {
    textStateStack +:= Text.Interpolated(quoteSize)
  }

  final def popTextState(): Unit = logger.trace {
    textStateStack = textStateStack.tail
  }

  final def insideOfText: Boolean =
    textStateStack.nonEmpty

  final def submitEmptyText(quoteNum: Quote): Unit = logger.trace {
    app(Text.Interpolated(quoteNum))
  }

  final def finishCurrentTextBuilding(): Text.Interpolated = logger.trace {
    withCurrentText(t => t.copy(segments = t.segments.reverse))
    val txt = currentText
    popTextState()
    endGroup()
    txt
  }

  final def submitText(): Unit = logger.trace {
    app(finishCurrentTextBuilding())
  }

  final def submitUnclosedText(): Unit = logger.trace {
    app(Text.Unclosed(finishCurrentTextBuilding()))
  }

  final def onTextBegin(quoteSize: Quote): Unit = logger.trace {
    pushTextState(quoteSize)
    beginGroup(TEXT)
  }

  final def submitPlainTextSegment(segment: Text.Interpolated.Segment): Unit =
    logger.trace { withCurrentText(_.prependMergeReversed(segment)) }

  final def submitTextSegment(segment: Text.Interpolated.Segment): Unit =
    logger.trace { withCurrentText(_.prepend(segment)) }

  final def onPlainTextSegment(): Unit = logger.trace {
    submitPlainTextSegment(Text.Segment.Plain(currentMatch))
  }

  final def onTextQuote(quoteSize: Quote): Unit = logger.trace {
    if (currentText.quote == Text.Quote.Triple
        && quoteSize == Text.Quote.Single) onPlainTextSegment()
    else if (currentText.quote == Text.Quote.Single
             && quoteSize == Text.Quote.Triple) {
      submitText()
      submitEmptyText(Text.Quote.Single)
    } else {
      submitText()
    }
  }

  final def onTextEscape(code: Text.Segment.Escape): Unit = logger.trace {
    submitTextSegment(code)
  }

  final def onTextEscapeU16(): Unit = logger.trace {
    val code = currentMatch.drop(2)
    submitTextSegment(Text.Segment.Escape.Unicode.U16(code))
  }

  final def onTextEscapeU32(): Unit = logger.trace {
    val code = currentMatch.drop(2)
    submitTextSegment(Text.Segment.Escape.Unicode.U32(code))
  }

  final def onTextEscapeInt(): Unit = logger.trace {
    val int = currentMatch.drop(1).toInt
    submitTextSegment(Text.Segment.Escape.Number(int))
  }

  final def onInvalidEscape(): Unit = logger.trace {
    val str = currentMatch.drop(1)
    submitTextSegment(Text.Segment.Escape.Invalid(str))
  }

  final def onEscapeSlash(): Unit = logger.trace {
    submitTextSegment(Text.Segment.Escape.Slash)
  }

  final def onEscapeQuote(): Unit = logger.trace {
    submitTextSegment(Text.Segment.Escape.Quote)
  }

  final def onEscapeRawQuote(): Unit = logger.trace {
    submitTextSegment(Text.Segment.Escape.RawQuote)
  }

  final def onInterpolateBegin(): Unit = logger.trace {
    pushAST()
    pushLastOffset()
    beginGroup(INTERPOLATE)
  }

  final def terminateGroupsTill(g: Group): Unit = logger.trace {
    terminateGroupsTill(g.groupIx)
  }

  final def terminateGroupsTill(g: Int): Unit = logger.trace {
    while (g != group) {
      getGroup(group).finish()
      endGroup()
    }
  }

  final def onInterpolateEnd(): Unit = logger.trace {
    if (insideOfGroup(INTERPOLATE)) {
      terminateGroupsTill(INTERPOLATE)
      submitTextSegment(Text.Segment.Interpolation(result))
      popAST()
      popLastOffset()
      endGroup()
    } else {
      onUnrecognized()
    }
  }

  final def onTextEOF(): Unit = logger.trace {
    submitUnclosedText()
    rewind()
  }

  final def fixme_onTextDoubleQuote(): Unit = logger.trace {
    currentMatch = "'"
    onTextQuote(Text.Quote.Single)
    rewind(1)
  }

  val stringChar    = noneOf("'`\"\n\\")
  val stringSegment = stringChar.many1
  val escape_int    = "\\" >> decimal
  val escape_u16    = "\\u" >> repeat(stringChar, 0, 4)
  val escape_u32    = "\\U" >> repeat(stringChar, 0, 8)

  val TEXT: Group        = defineGroup("Text")
  val INTERPOLATE: Group = defineGroup("Interpolate")
  INTERPOLATE.setParent(NORMAL)

  // format: off
  NORMAL rule "'"           run reify { onTextBegin(ast.Text.Quote.Single) }
  NORMAL rule "''"          run reify { submitEmptyText(ast.Text.Quote.Single) } // FIXME: Remove after fixing DFA Gen
  NORMAL rule "'''"         run reify { onTextBegin(ast.Text.Quote.Triple) }
  NORMAL rule '`'           run reify { onInterpolateEnd() }
  TEXT   rule '`'           run reify { onInterpolateBegin() }
  TEXT   rule "'"           run reify { onTextQuote(ast.Text.Quote.Single) }
  TEXT   rule "''"          run reify { fixme_onTextDoubleQuote() } // FIXME: Remove after fixing DFA Gen
  TEXT   rule "'''"         run reify { onTextQuote(ast.Text.Quote.Triple) }
  TEXT   rule stringSegment run reify { onPlainTextSegment() }
  TEXT   rule eof           run reify { onTextEOF() }

  Text.Segment.Escape.Character.codes.foreach { ctrl =>
    import scala.reflect.runtime.universe._
  val name = TermName(ctrl.toString)
    val func = q"onTextEscape(ast.Text.Segment.Escape.Character.$name)"
    TEXT rule s"\\$ctrl" run func
  }

  Text.Segment.Escape.Control.codes.foreach { ctrl =>
    import scala.reflect.runtime.universe._
  val name = TermName(ctrl.toString)
    val func = q"onTextEscape(ast.Text.Segment.Escape.Control.$name)"
    TEXT rule s"\\$ctrl" run func
  }

  TEXT rule escape_u16           run reify { onTextEscapeU16() }
  TEXT rule escape_u32           run reify { onTextEscapeU32() }
  TEXT rule escape_int           run reify { onTextEscapeInt() }
  TEXT rule "\\\\"               run reify { onEscapeSlash() }
  TEXT rule "\\'"                run reify { onEscapeQuote() }
  TEXT rule "\\\""               run reify { onEscapeRawQuote() }
  TEXT rule ("\\" >> stringChar) run reify { onInvalidEscape() }
  TEXT rule "\\"                 run reify { onPlainTextSegment() }
  // format: on

//  //////////////
//  /// Groups ///
//  //////////////
//
//  var groupLeftOffsetStack: List[Int] = Nil
//
//  final def pushGroupLeftOffset(offset: Int): Unit = logger.trace {
//    groupLeftOffsetStack +:= offset
//  }
//
//  final def popGroupLeftOffset(): Int = logger.trace {
//    val offset = groupLeftOffsetStack.head
//    groupLeftOffsetStack = groupLeftOffsetStack.tail
//    offset
//  }
//
//  final def isInsideOfGroup(): Boolean =
//    groupLeftOffsetStack != Nil
//
//  final def onGroupBegin(): Unit = logger.trace {
//    val leftOffset = currentMatch.length - 1
//    pushGroupLeftOffset(leftOffset)
//    pushAST()
//    pushLastOffset()
//    beginGroup(PARENSED)
//  }
//
//  final def onGroupEnd(): Unit = logger.trace {
//    val leftOffset  = popGroupLeftOffset()
//    val rightOffset = useLastOffset()
//    val group       = Group(leftOffset, result, rightOffset)
//    popLastOffset()
//    popAST()
//    app(group)
//    endGroup()
//  }
//
//  final def onGroupFinalize(): Unit = logger.trace {
//    val leftOffset  = popGroupLeftOffset()
//    var rightOffset = useLastOffset()
//
//    val group = result match {
//      case Some(_) =>
//        Group.Unclosed(leftOffset, result)
//      case None =>
//        rightOffset += leftOffset
//        Group.Unclosed()
//    }
//    popLastOffset()
//    popAST()
//    app(group)
//    lastOffset = rightOffset
//  }
//
//  final def onGroupEOF(): Unit = logger.trace {
//    onGroupFinalize()
//    endGroup()
//    rewind()
//  }
//
//  final def onGroupUnmatchedClose(): Unit = logger.trace {
//    app(Group.UnmatchedClose)
//  }
//
//  val PARENSED = defineGroup("Parensed", { onGroupFinalize() })
//  PARENSED.setParent(NORMAL)
//
//  // format: off
//  NORMAL   rule ("(" >> whitespace0) run reify { onGroupBegin() }
//  NORMAL   rule ")"                  run reify { onGroupUnmatchedClose() }
//  PARENSED rule ")"                  run reify { onGroupEnd() }
//  PARENSED rule eof                  run reify { onGroupEOF() }
//  // format: on

  //////////////
  /// Blocks ///
  //////////////

  class BlockState(
    var isValid: Boolean,
    var indent: Int,
    var emptyLines: List[Int],
    var firstLine: Option[Block.Line.Required],
    var lines: List[Block.Line]
  )
  var blockStack: List[BlockState] = Nil
  var emptyLines: List[Int]        = Nil
  var currentBlock: BlockState     = new BlockState(true, 0, Nil, None, Nil)

  final def pushBlock(newIndent: Int): Unit = logger.trace {
    blockStack +:= currentBlock
    currentBlock =
      new BlockState(true, newIndent, emptyLines.reverse, None, Nil)
    emptyLines = Nil
  }

  final def popBlock(): Unit = logger.trace {
    currentBlock = blockStack.head
    blockStack   = blockStack.tail
  }

  final def buildBlock(): Block = logger.trace {
    submitLine()
    withSome(currentBlock.firstLine) { firstLine =>
      Block(
        currentBlock.indent,
        currentBlock.emptyLines.reverse,
        firstLine,
        currentBlock.lines.reverse
      )
    }
  }

  final def submitBlock(): Unit = logger.trace {
    val block = buildBlock()
    val block2 =
      if (currentBlock.isValid) block
      else Block.InvalidIndentation(block)

    popAST()
    popBlock()
    app(block2)
    logger.endGroup()
  }

  final def submitModule(): Unit = logger.trace {
    submitLine()
    val el  = currentBlock.emptyLines.reverse.map(Block.Line(_))
    val el2 = emptyLines.reverse.map(Block.Line(_))
    val firstLines = currentBlock.firstLine match {
      case None            => el
      case Some(firstLine) => firstLine.toOptional :: el
    }
    val lines  = firstLines ++ currentBlock.lines.reverse ++ el2
    val module = Module(lines.head, lines.tail)
    result = Some(module)
    logger.endGroup()
  }

  final def submitLine(): Unit = logger.trace {
    result match {
      case None => pushEmptyLine()
      case Some(r) =>
        currentBlock.firstLine match {
          case None =>
            val line = Block.Line.Required(r, useLastOffset())
            currentBlock.emptyLines = emptyLines
            currentBlock.firstLine  = Some(line)
          case Some(_) =>
            emptyLines.foreach(currentBlock.lines +:= Block.Line(None, _))
            currentBlock.lines +:= Block.Line(result, useLastOffset())
        }
        emptyLines = Nil
    }
    result = None
  }

  def pushEmptyLine(): Unit = logger.trace {
    emptyLines +:= useLastOffset()
  }

  final def onBlockBegin(newIndent: Int): Unit = logger.trace {
    pushAST()
    pushBlock(newIndent)
    logger.beginGroup()
  }

  final def onEmptyLine(): Unit = logger.trace {
    onWhitespace(-1)
    pushEmptyLine()
  }

  final def onEOFLine(): Unit = logger.trace {
    endGroup()
    onWhitespace(-1)
    onEOF()
  }

  final def onNewLine(): Unit = logger.trace {
    submitLine()
    beginGroup(NEWLINE)
  }

  final def onBlockNewline(): Unit = logger.trace {
    endGroup()
    onWhitespace()
    if (lastOffset == currentBlock.indent) {
      submitLine()
    } else if (lastOffset > currentBlock.indent) {
      onBlockBegin(useLastOffset())
    } else {
      onBlockEnd(useLastOffset())
    }
  }

  final def onBlockEnd(newIndent: Int): Unit = logger.trace {
    while (newIndent < currentBlock.indent) {
      submitBlock()
    }
    if (newIndent > currentBlock.indent) {
      logger.log("Block with invalid indentation")
      onBlockBegin(newIndent)
      currentBlock.isValid = false
    }
  }

  val NEWLINE = defineGroup("Newline")

  // format: off
  NORMAL  rule newline                        run reify { onNewLine() }
  NEWLINE rule ((whitespace|pass) >> newline) run reify { onEmptyLine() }
  NEWLINE rule ((whitespace|pass) >> eof)     run reify { onEOFLine() }
  NEWLINE rule  (whitespace|pass)             run reify { onBlockNewline() }
  // format: on

  ////////////////
  /// Defaults ///
  ////////////////

  final def onUnrecognized(): Unit = logger.trace {
    app(Unrecognized)
  }

  final def onEOF(): Unit = logger.trace {
    finalizeIdent()
    onBlockEnd(0)
    submitModule()
  }

  NORMAL rule whitespace run reify { onWhitespace() }
  NORMAL rule eof run reify { onEOF() }
  NORMAL rule any run reify { onUnrecognized() }
}

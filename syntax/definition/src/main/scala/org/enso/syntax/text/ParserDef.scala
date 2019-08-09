package org.enso.syntax.text

import org.enso.flexer.Pattern.char
import org.enso.flexer.Pattern.range
import org.enso.flexer.Pattern._
import org.enso.flexer._
import org.enso.syntax.text.AST._
import org.enso.syntax.text.ast.Repr.R

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

  final def repeat(p: Pattern, min: Int, max: Int): Pattern = {
    val minPat = repeat(p, min)
    _repeatAlt(p, max - min, minPat, minPat)
  }

  final def repeat(p: Pattern, num: Int): Pattern =
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

  final def unwrap[A](opt: Option[A]): A = opt match {
    case None    => throw new Error("Internal Error")
    case Some(a) => a
  }

  //// Cleaning ////

  final override def initialize(): Unit = {
    beginGroup(INDENT)
  }

  ///////////////////////////////////////////////////////////////////////////
  /////////////////////////////// RESULT ////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////

  override def getResult() = result

  var result: Option[AST]         = None
  var astStack: List[Option[AST]] = Nil

  final def pushAST(): Unit = logger.trace {
    logger.log(s"Pushed: $result")
    astStack +:= result
    result = None
  }

  final def popAST(): Unit = logger.trace {
    result   = astStack.head
    astStack = astStack.tail
    logger.log(s"New result: $result")
  }

  final def app(fn: String => AST): Unit =
    app(fn(currentMatch))

  final def app(t: AST): Unit = logger.trace {
    result = Some(result match {
      case None    => t
      case Some(r) => App(r, useLastOffset(), t)
    })
  }

  ///////////////////////////////////////////////////////////////////////////
  /////////////////////// Basic Char Classification /////////////////////////
  ///////////////////////////////////////////////////////////////////////////

  val NORMAL = defineGroup("Normal")

  val lowerLetter: Pattern = range('a', 'z')
  val upperLetter: Pattern = range('A', 'Z')
  val digit: Pattern       = range('0', '9')
  val hex: Pattern         = digit | range('a', 'f') | range('A', 'F')
  val alphaNum: Pattern    = digit | lowerLetter | upperLetter
  val whitespace0: Pattern = ' '.many
  val whitespace: Pattern  = ' '.many1
  val newline: Pattern     = '\n'

  ///////////////////////////////////////////////////////////////////////////
  //////////////////////////////// OFFSET ///////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////

  var lastOffset: Int            = 0
  var lastOffsetStack: List[Int] = List(0)

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

  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////// IDENTIFIERS /////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////

  final object Ident {

    import AST.Ident._

    var body: Option[Ident] = None

    def submit(cons: String => Ident): Unit = logger.trace_ {
      submit(cons(currentMatch))
    }

    def submit(ast: Ident): Unit = logger.trace {
      body = Some(ast)
      beginGroup(IDENT_SFX_CHECK)
    }

    def submit(): Unit = logger.trace {
      app(unwrap(body))
      body = None
    }

    def onErrSfx(): Unit = logger.trace {
      val ast = InvalidSuffix(unwrap(body), currentMatch)
      app(ast)
      body = None
      endGroup()
    }

    def onNoErrSfx(): Unit = logger.trace {
      submit()
      endGroup()
    }

    def finish(): Unit = logger.trace {
      if (body != None) submit()
    }

    val char: Pattern        = alphaNum | '_'
    val body_ : Pattern      = char.many >> '\''.many
    val variable: Pattern    = lowerLetter >> body_
    val constructor: Pattern = upperLetter >> body_
    val breaker: String      = "^`!@#$%^&*()-=+[]{}|;:<>,./ \t\r\n\\"
    val errSfx: Pattern      = noneOf(breaker).many1

  }

  val IDENT_SFX_CHECK = defineGroup("Identifier Suffix Check")

  // format: off
  NORMAL          rule Ident.variable    run reify { Ident.submit(ast.Var) }
  NORMAL          rule Ident.constructor run reify { Ident.submit(ast.Cons) }
  NORMAL          rule "_"               run reify { Ident.submit(ast.Blank) }
  IDENT_SFX_CHECK rule Ident.errSfx      run reify { Ident.onErrSfx() }
  IDENT_SFX_CHECK rule pass              run reify { Ident.onNoErrSfx() }
  // format: on

  ///////////////////////////////////////////////////////////////////////////
  ////////////////////////////// OPERATORS //////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////

  final object Opr {

    import AST.Opr._

    def onOp(cons: String => Ident): Unit = logger.trace {
      onOp(cons(currentMatch))
    }

    def onNoModOp(cons: String => Ident): Unit = logger.trace {
      onNoModOp(cons(currentMatch))
    }

    def onOp(ast: Ident): Unit = logger.trace {
      Ident.body = Some(ast)
      beginGroup(OPR_MOD_CHECK)
    }

    def onNoModOp(ast: Ident): Unit = logger.trace {
      Ident.body = Some(ast)
      beginGroup(OPR_SFX_CHECK)
    }

    def onModifier(): Unit = logger.trace {
      Ident.body = Some(Mod(unwrap(Ident.body).asInstanceOf[Opr].name))
    }

    val char: Pattern     = anyOf("!$%&*+-/<>?^~|:\\")
    val errChar: Pattern  = char | "=" | "," | "."
    val errSfx: Pattern   = errChar.many1
    val operator: Pattern = char.many1
    val eq: Pattern       = "=" | "==" | ">=" | "<=" | "/=" | "#="
    val dot: Pattern      = "." | ".." | "..." | ","
    val group: Pattern    = anyOf("()[]{}")
    val noMod: Pattern    = eq | dot | group | "##"

  }

  val OPR_SFX_CHECK = defineGroup("Operator Suffix Check")
  val OPR_MOD_CHECK = defineGroup("Operator Modifier Check")
  OPR_MOD_CHECK.setParent(OPR_SFX_CHECK)

  // format: off
  NORMAL        rule Opr.operator run reify { Opr.onOp(ast.Opr(_)) }
  NORMAL        rule Opr.noMod    run reify { Opr.onNoModOp(ast.Opr(_)) }
  OPR_MOD_CHECK rule "="          run reify { Opr.onModifier() }
  OPR_SFX_CHECK rule Opr.errSfx   run reify { Ident.onErrSfx() }
  OPR_SFX_CHECK rule pass         run reify { Ident.onNoErrSfx() }
  // format: on

  ///////////////////////////////////////////////////////////////////////////
  //////////////////////////////// NUMBERS //////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////

  final object Num {

    var numberPart1: String = ""
    var numberPart2: String = ""

    def numberReset(): Unit = logger.trace {
      numberPart1 = ""
      numberPart2 = ""
    }

    def submitNumber(): Unit = logger.trace {
      val base = if (numberPart1 == "") None else Some(numberPart1)
      app(Number(base, numberPart2))
      numberReset()
    }

    def onDanglingBase(): Unit = logger.trace {
      endGroup()
      app(Number.DanglingBase(numberPart2))
      numberReset()
    }

    def onDecimal(): Unit = logger.trace {
      numberPart2 = currentMatch
      beginGroup(NUMBER_PHASE_2)
    }

    def onExplicitBase(): Unit = logger.trace {
      endGroup()
      numberPart1 = numberPart2
      numberPart2 = currentMatch.substring(1)
      submitNumber()
    }

    def onNoExplicitBase(): Unit = logger.trace {
      endGroup()
      submitNumber()
    }

    val decimal: Pattern = digit.many1

  }

  val NUMBER_PHASE_2: Group = defineGroup("Number Phase 2")

  // format: off
  NORMAL         rule Num.decimal             run reify { Num.onDecimal() }
  NUMBER_PHASE_2 rule ("_" >> alphaNum.many1) run reify { Num.onExplicitBase() }
  NUMBER_PHASE_2 rule ("_")                   run reify { Num.onDanglingBase() }
  NUMBER_PHASE_2 rule pass                    run reify { Num.onNoExplicitBase() }
  // format: on

  ///////////////////////////////////////////////////////////////////////////
  //////////////////////////////// TEXT /////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////

  final object Text {

    import AST.Text._

    var stack: List[Interpolated] = Nil

    def currentText = stack.head

    def withCurrentText(f: Interpolated => Interpolated) =
      stack = f(stack.head) :: stack.tail

    def pushStack(quoteSize: Quote): Unit = logger.trace {
      stack +:= Interpolated(quoteSize)
    }

    def popStack(): Unit = logger.trace {
      stack = stack.tail
    }

    def insideOfText: Boolean = stack.nonEmpty

    def submitEmpty(quoteNum: Quote): Unit = logger.trace {
      app(Interpolated(quoteNum))
    }

    def build(): Interpolated = logger.trace {
      withCurrentText(t => t.copy(segments = t.segments.reverse))
      val txt = currentText
      popStack()
      endGroup()
      txt
    }

    def submit(): Unit = logger.trace {
      app(build())
    }

    def submitUnclosed(): Unit = logger.trace {
      app(Unclosed(build()))
    }

    def onBegin(quoteSize: Quote): Unit = logger.trace {
      pushStack(quoteSize)
      beginGroup(TEXT)
    }

    def submitPlainSegment(segment: Interpolated.Segment): Unit =
      logger.trace {
        withCurrentText(_.prependMergeReversed(segment))
      }

    def submitSegment(segment: Interpolated.Segment): Unit = logger.trace {
      withCurrentText(_.prepend(segment))
    }

    def onPlainSegment(): Unit = logger.trace {
      submitPlainSegment(Segment.Plain(currentMatch))
    }

    def onQuote(quoteSize: Quote): Unit = logger.trace {
      if (currentText.quote == Quote.Triple
          && quoteSize == Quote.Single) onPlainSegment()
      else if (currentText.quote == Quote.Single
               && quoteSize == Quote.Triple) {
        submit()
        submitEmpty(Quote.Single)
      } else {
        submit()
      }
    }

    def onEscape(code: Segment.Escape): Unit = logger.trace {
      submitSegment(code)
    }

    def onEscapeU16(): Unit = logger.trace {
      val code = currentMatch.drop(2)
      submitSegment(Segment.Escape.Unicode.U16(code))
    }

    def onEscapeU32(): Unit = logger.trace {
      val code = currentMatch.drop(2)
      submitSegment(Segment.Escape.Unicode.U32(code))
    }

    def onEscapeInt(): Unit = logger.trace {
      val int = currentMatch.drop(1).toInt
      submitSegment(Segment.Escape.Number(int))
    }

    def onInvalidEscape(): Unit = logger.trace {
      val str = currentMatch.drop(1)
      submitSegment(Segment.Escape.Invalid(str))
    }

    def onEscapeSlash(): Unit = logger.trace {
      submitSegment(Segment.Escape.Slash)
    }

    def onEscapeQuote(): Unit = logger.trace {
      submitSegment(Segment.Escape.Quote)
    }

    def onEscapeRawQuote(): Unit = logger.trace {
      submitSegment(Segment.Escape.RawQuote)
    }

    def onInterpolateBegin(): Unit = logger.trace {
      pushAST()
      pushLastOffset()
      beginGroup(INTERPOLATE)
    }

    def terminateGroupsTill(g: Group): Unit = logger.trace {
      terminateGroupsTill(g.groupIx)
    }

    def terminateGroupsTill(g: Int): Unit = logger.trace {
      while (g != group) {
        getGroup(group).finish()
        endGroup()
      }
    }

    def onInterpolateEnd(): Unit = logger.trace {
      if (insideOfGroup(INTERPOLATE)) {
        terminateGroupsTill(INTERPOLATE)
        submitSegment(Segment.Interpolation(result))
        popAST()
        popLastOffset()
        endGroup()
      } else {
        onUnrecognized()
      }
    }

    def onEOF(): Unit = logger.trace {
      submitUnclosed()
      rewind()
    }

    def fixme_onTextDoubleQuote(): Unit = logger.trace {
      currentMatch = "'"
      onQuote(Quote.Single)
      rewind(1)
    }

    val char       = noneOf("'`\"\n\\")
    val segment    = char.many1
    val escape_int = "\\" >> Num.decimal
    val escape_u16 = "\\u" >> repeat(char, 0, 4)
    val escape_u32 = "\\U" >> repeat(char, 0, 8)

  }

  val TEXT: Group        = defineGroup("Text")
  val INTERPOLATE: Group = defineGroup("Interpolate")
  INTERPOLATE.setParent(NORMAL)

  // format: off
  NORMAL rule "'"           run reify { Text.onBegin(ast.Text.Quote.Single) }
  NORMAL rule "''"          run reify { Text.submitEmpty(ast.Text.Quote.Single) } // FIXME: Remove after fixing DFA Gen
  NORMAL rule "'''"         run reify { Text.onBegin(ast.Text.Quote.Triple) }
  NORMAL rule '`'           run reify { Text.onInterpolateEnd() }
  TEXT   rule '`'           run reify { Text.onInterpolateBegin() }
  TEXT   rule "'"           run reify { Text.onQuote(ast.Text.Quote.Single) }
  TEXT   rule "''"          run reify { Text.fixme_onTextDoubleQuote() } // FIXME: Remove after fixing DFA Gen
  TEXT   rule "'''"         run reify { Text.onQuote(ast.Text.Quote.Triple) }
  TEXT   rule Text.segment  run reify { Text.onPlainSegment() }
  TEXT   rule eof           run reify { Text.onEOF() }

  AST.Text.Segment.Escape.Character.codes.foreach { ctrl =>
    import scala.reflect.runtime.universe._
    val name = TermName(ctrl.toString)
    val func = q"Text.onEscape(ast.Text.Segment.Escape.Character.$name)"
    TEXT rule s"\\$ctrl" run func
  }

  AST.Text.Segment.Escape.Control.codes.foreach { ctrl =>
    import scala.reflect.runtime.universe._
    val name = TermName(ctrl.toString)
    val func = q"Text.onEscape(ast.Text.Segment.Escape.Control.$name)"
    TEXT rule s"\\$ctrl" run func
  }

  TEXT rule Text.escape_u16      run reify { Text.onEscapeU16() }
  TEXT rule Text.escape_u32      run reify { Text.onEscapeU32() }
  TEXT rule Text.escape_int      run reify { Text.onEscapeInt() }
  TEXT rule "\\\\"               run reify { Text.onEscapeSlash() }
  TEXT rule "\\'"                run reify { Text.onEscapeQuote() }
  TEXT rule "\\\""               run reify { Text.onEscapeRawQuote() }
  TEXT rule ("\\" >> Text.char)  run reify { Text.onInvalidEscape() }
  TEXT rule "\\"                 run reify { Text.onPlainSegment() }
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

  ///////////////////////////////////////////////////////////////////////////
  //////////////////////////////// BLOCKS ///////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////

  final object Block {

    import AST.Block._

    class BlockState(
      var isValid: Boolean,
      var indent: Int,
      var emptyLines: List[Int],
      var firstLine: Option[Line.Required],
      var lines: List[Line]
    )

    var stack: List[BlockState]  = Nil
    var emptyLines: List[Int]    = Nil
    var currentBlock: BlockState = new BlockState(true, 0, Nil, None, Nil)

    def pushBlock(newIndent: Int): Unit = logger.trace {
      stack +:= currentBlock
      currentBlock =
        new BlockState(true, newIndent, emptyLines, None, Nil)
      emptyLines = Nil
    }

    def popBlock(): Unit = logger.trace {
      currentBlock = stack.head
      stack        = stack.tail
    }

    def build(): Block = logger.trace {
      submitLine()
      AST.Block(
        currentBlock.indent,
        currentBlock.emptyLines.reverse,
        unwrap(currentBlock.firstLine),
        currentBlock.lines.reverse
      )
    }

    def submit(): Unit = logger.trace {
      val block = build()
      val block2 =
        if (currentBlock.isValid) block
        else InvalidIndentation(block)
      popAST()
      popBlock()
      app(block2)
      logger.endGroup()
    }

    def submitModule(): Unit = logger.trace {
      val el = emptyLines.reverse.map(Line(_))
      val lines = currentBlock.firstLine match {
        case None => el ++ currentBlock.lines
        case Some(line) => el ++ (line.toOptional +: currentBlock.lines)
      }
      val module = Module(lines.head, lines.tail)
      result = Some(module)
      logger.endGroup()
    }

    def submitLine(): Unit = logger.trace {
      result.foreach { r =>
        popLastOffset()
        currentBlock.firstLine match {
          case None =>
            currentBlock.firstLine = Some(Line.Required(r, useLastOffset()))
          case Some(_) =>
            submitEmptyLines()
            currentBlock.lines +:= Line(result, useLastOffset())
        }
      }
      result = None
    }

    def submitEmptyLines(): Unit = logger.trace {
      emptyLines.foreach(currentBlock.lines +:= Line(_))
      emptyLines = Nil
    }

    def onBegin(newIndent: Int): Unit = logger.trace {
      pushAST()
      pushBlock(newIndent)
      logger.beginGroup()
    }

    def onEmptyLine(): Unit = logger.trace {
      onWhitespace(-1)
      emptyLines +:= useLastOffset()
    }

    def onEOFLine(): Unit = logger.trace {
      onEmptyLine()
      emptyLines.foreach(currentBlock.lines +:= Line(_))
      emptyLines = Nil
      endGroup()
      onEOF()
    }

    def onEndLine(): Unit = logger.trace {
      onWhitespace(-1)
      pushLastOffset()
      beginGroup(INDENT)
    }

    def onIndent(): Unit = logger.trace {
      endGroup()
      onWhitespace()
      val offset = useLastOffset()
      if (offset == currentBlock.indent)
        submitLine()
      else if (offset > currentBlock.indent)
        onBegin(offset)
      else if (offset < currentBlock.indent)
        onEnd(offset)
      beginGroup(FIRSTCHAR)
    }

    def onEnd(newIndent: Int): Unit = logger.trace {
      while (newIndent < currentBlock.indent) {
        submit()
      }
      if (newIndent > currentBlock.indent) {
        logger.log("Block with invalid indentation")
        onBegin(newIndent)
        currentBlock.isValid = false
      }
    }

  }

  val FIRSTCHAR = defineGroup("FirstChar")
  val INDENT    = defineGroup("Indent")

  // format: off
  FIRSTCHAR rule pass                     run reify { endGroup() }
  NORMAL    rule (whitespace0 >> newline) run reify { Block.onEndLine() }
  INDENT    rule (whitespace0 >> newline) run reify { Block.onEmptyLine() }
  INDENT    rule (whitespace0 >> eof)     run reify { Block.onEOFLine() }
  INDENT    rule (whitespace | pass)      run reify { Block.onIndent() }
  // format: on

  ///////////////////////////////////////////////////////////////////////////
  /////////////////////////////// COMMENTS //////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////

  final object Comment {

    import AST.Comment._

    var firstLine: String   = ""
    var lines: List[String] = Nil
    var line: String        = ""

    def submit(): Unit = logger.trace {
      app(AST.Comment(currentMatch.drop(1)))
    }

    def submitMultiLine(): Unit = logger.trace {
      result = Some(MultiLine(Block.currentBlock.indent, lines.reverse))
      lines  = Nil
      line   = ""
      rewind()
      endGroup()
    }

    def submitLine(): Unit = logger.trace {
      if (currentMatch.takeWhile(_ == ' ').length > Block.currentBlock.indent)
        line = currentMatch.drop(Block.currentBlock.indent + 1)
      else {
        submitMultiLine()
        offset -= 1 // FIXME
        beginGroup(INDENT)
      }
    }

    def onMultiLineBegin(): Unit = logger.trace {
      line = currentMatch.drop(1)
      beginGroup(COMMENT)
    }

    def onNewLine(): Unit = logger.trace {
      lines +:= line
      line = ""
    }

    def onEOF(): Unit = logger.trace {
      lines +:= line
      submitMultiLine()
    }

    val comment = "#" >> noneOf("\n").many

  }

  val COMMENT = defineGroup("BlockComment")

  // format: off
  NORMAL    rule Comment.comment     run reify { Comment.submit() }
  FIRSTCHAR rule Comment.comment     run reify { Comment.onMultiLineBegin() }
  FIRSTCHAR rule "#="                run reify { endGroup(); rewind() }
  COMMENT   rule noneOf("\n").many1  run reify { Comment.submitLine() }
  COMMENT   rule newline             run reify { Comment.onNewLine()}
  COMMENT   rule eof                 run reify { Comment.onEOF() }
  // format: on

  ///////////////////////////////////////////////////////////////////////////
  /////////////////////////////// DEFAULTS //////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////

  def onUnrecognized(): Unit = logger.trace {
    app(Unrecognized)
  }

  def onEOF(): Unit = logger.trace {
    Ident.finish()
    Block.submitLine()
    Block.onEnd(0)
    Block.submitModule()
  }

  def onEOFoffset(): Unit = logger.trace {
    onWhitespace(-1)
    pushLastOffset()
    onEOF()
  }

  // format: off
  NORMAL rule whitespace           run reify { onWhitespace() }
  NORMAL rule (whitespace0 >> eof) run reify { onEOFoffset() }
  NORMAL rule eof                  run reify { onEOF() }
  NORMAL rule any                  run reify { onUnrecognized() }
  // format: on
}

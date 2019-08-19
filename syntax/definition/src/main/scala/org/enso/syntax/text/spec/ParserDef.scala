package org.enso.syntax.text.spec

import java.io.DataInputStream

import org.enso.data.VectorMap
import org.enso.flexer
import org.enso.flexer.State
import org.enso.flexer.automata.Pattern
import org.enso.flexer.automata.Pattern._
import org.enso.syntax.text.AST
import org.enso.syntax.text.AST.Text.Segment.EOL

import scala.annotation.tailrec
import scala.reflect.runtime.universe.reify

case class ParserDef() extends flexer.Parser[AST.Module] {
  import ParserDef2._

  final def unwrap[T](opt: Option[T]): T = opt match {
    case None    => throw new Error("Internal Error")
    case Some(t) => t
  }

  /////////////
  //// API ////
  /////////////

  def run(input: ParserReader, markers: Markers): Result[AST.Module] = {
    result.markers = VectorMap(markers)
    run(input)
  }

  override def run(input: ParserReader): Result[AST.Module] = {
    block.onBegin(0)
    state.begin(block.FIRSTCHAR)
    super.run(input)
  }

  ///////////////////////////////////
  //// Basic Char Classification ////
  ///////////////////////////////////

  val lowerLetter: Pattern = range('a', 'z')
  val upperLetter: Pattern = range('A', 'Z')
  val digit: Pattern       = range('0', '9')
  val hex: Pattern         = digit | range('a', 'f') | range('A', 'F')
  val alphaNum: Pattern    = digit | lowerLetter | upperLetter
  val whitespace0: Pattern = ' '.many
  val space: Pattern       = ' '.many1
  val newline: Pattern     = '\n'

  ////////////////
  //// Result ////
  ////////////////

  override def getResult() = result.current.flatMap {
    case mod: AST.Module => Some(mod)
    case _               => None
  }

  final object result {

    var markers: VectorMap[Int, AST.Marker] = VectorMap()
    var current: Option[AST]                = None
    var stack: List[Option[AST]]            = Nil

    def push(): Unit = logger.trace {
      logger.log(s"Pushed: $current")
      stack +:= current
      current = None
    }

    def pop(): Unit = logger.trace {
      current = stack.head
      stack   = stack.tail
      logger.log(s"New result: $current")
    }

    def app(fn: String => AST): Unit =
      app(fn(currentMatch))

    def app(ast: AST): Unit = logger.trace {
      val marked = markers.get(reader.charOffset - ast.byteSpan) match {
        case None         => ast
        case Some(marker) => AST.Marked(marker, ast)
      }
      current = Some(current match {
        case None    => marked
        case Some(r) => AST.App(r, off.use(), marked)
      })
    }

    def last(): Option[AST] = {
      @tailrec
      def go(ast: AST): AST = ast match {
        case AST.Marked(_, t)  => go(t)
        case AST._App(_, _, t) => go(t)
        case t                 => t
      }
      current.map(go)
    }
  }

  ////////////////
  //// Offset ////
  ////////////////

  final object off {
    var current: Int     = 0
    var stack: List[Int] = Nil

    def push(): Unit = logger.trace {
      stack +:= current
      current = 0
    }

    def pop(): Unit = logger.trace {
      current = stack.head
      stack   = stack.tail
    }

    def use(): Int = logger.trace {
      val offset = current
      current = 0
      offset
    }

    def on(): Unit = on(0)

    def on(shift: Int): Unit = logger.trace {
      val diff = currentMatch.length + shift
      current += diff
      logger.log(s"lastOffset + $diff = $current")
    }
  }

  ////////////////////
  //// IDENTIFIER ////
  ////////////////////

  final object ident {
    import AST.Ident._

    var current: Option[AST.Ident] = None

    def on(cons: String => AST.Ident): Unit = logger.trace_ {
      on(cons(currentMatch))
    }

    def on(ast: AST.Ident): Unit = logger.trace {
      current = Some(ast)
      state.begin(SFX_CHECK)
    }

    def submit(): Unit = logger.trace {
      result.app(unwrap(current))
      current = None
    }

    def onErrSfx(): Unit = logger.trace {
      val ast = InvalidSuffix(unwrap(current), currentMatch)
      result.app(ast)
      current = None
      state.end()
    }

    def onNoErrSfx(): Unit = logger.trace {
      submit()
      state.end()
    }

    def finalizer(): Unit = logger.trace {
      if (current.isDefined) submit()
    }

    val char: Pattern   = alphaNum | '_'
    val body: Pattern   = char.many >> '\''.many
    val _var: Pattern   = lowerLetter >> body
    val cons: Pattern   = upperLetter >> body
    val breaker: String = "^`!@#$%^&*()-=+[]{}|;:<>,./ \t\r\n\\"
    val errSfx: Pattern = noneOf(breaker).many1

    val SFX_CHECK = state.define("Identifier Suffix Check")
  }

  ROOT            || ident._var   || reify { ident.on(AST.Var(_)) }
  ROOT            || ident.cons   || reify { ident.on(AST.Cons(_)) }
  ROOT            || "_"          || reify { ident.on(AST.Blank) }
  ident.SFX_CHECK || ident.errSfx || reify { ident.onErrSfx() }
  ident.SFX_CHECK || always       || reify { ident.onNoErrSfx() }

  //////////////////
  //// Operator ////
  //////////////////

  final object opr {
    def on(cons: String => AST.Ident): Unit = logger.trace {
      on(cons(currentMatch))
    }

    def onNoMod(cons: String => AST.Ident): Unit = logger.trace {
      onNoMod(cons(currentMatch))
    }

    def on(ast: AST.Ident): Unit = logger.trace {
      ident.current = Some(ast)
      state.begin(MOD_CHECK)
    }

    def onNoMod(ast: AST.Ident): Unit = logger.trace {
      ident.current = Some(ast)
      state.begin(SFX_CHECK)
    }

    def onMod(): Unit = logger.trace {
      val opr = AST.Opr.Mod(unwrap(ident.current).asInstanceOf[AST.Opr].name)
      ident.current = Some(opr)
    }

    val char: Pattern     = anyOf("!$%&*+-/<>?^~|:\\")
    val errChar: Pattern  = char | "=" | "," | "."
    val errSfx: Pattern   = errChar.many1
    val body: Pattern     = char.many1
    val opsEq: Pattern    = "=" | "==" | ">=" | "<=" | "/=" | "#="
    val opsDot: Pattern   = "." | ".." | "..." | ","
    val opsGrp: Pattern   = anyOf("()[]{}")
    val opsCmm: Pattern   = "#" | "##"
    val opsNoMod: Pattern = opsEq | opsDot | opsGrp | opsCmm

    val SFX_CHECK = state.define("Operator Suffix Check")
    val MOD_CHECK = state.define("Operator Modifier Check")
    MOD_CHECK.parent = SFX_CHECK
  }

  ROOT          || opr.body     || reify { opr.on(AST.Opr(_)) }
  ROOT          || opr.opsNoMod || reify { opr.onNoMod(AST.Opr(_)) }
  opr.MOD_CHECK || "="          || reify { opr.onMod() }
  opr.SFX_CHECK || opr.errSfx   || reify { ident.onErrSfx() }
  opr.SFX_CHECK || always       || reify { ident.onNoErrSfx() }

  ////////////////
  //// NUMBER ////
  ////////////////

  final object num {

    var part1: String = ""
    var part2: String = ""

    def reset(): Unit = logger.trace {
      part1 = ""
      part2 = ""
    }

    def submit(): Unit = logger.trace {
      val base = if (part1 == "") None else Some(part1)
      result.app(AST.Number(base, part2))
      reset()
    }

    def onDanglingBase(): Unit = logger.trace {
      state.end()
      result.app(AST.Number.DanglingBase(part2))
      reset()
    }

    def onDecimal(): Unit = logger.trace {
      part2 = currentMatch
      state.begin(PHASE2)
    }

    def onExplicitBase(): Unit = logger.trace {
      state.end()
      part1 = part2
      part2 = currentMatch.substring(1)
      submit()
    }

    def onNoExplicitBase(): Unit = logger.trace {
      state.end()
      submit()
    }

    val decimal: Pattern = digit.many1

    val PHASE2: State = state.define("Number Phase 2")
  }

  ROOT       || num.decimal           || reify { num.onDecimal() }
  num.PHASE2 || "_" >> alphaNum.many1 || reify { num.onExplicitBase() }
  num.PHASE2 || "_"                   || reify { num.onDanglingBase() }
  num.PHASE2 || always                || reify { num.onNoExplicitBase() }

  //////////////
  //// Text ////
  //////////////

  import AST.Text.Quote

  final object text {
    var stack: List[AST.Text.Interpolated] = Nil

    def current = stack.head

    def withCurrent(f: AST.Text.Interpolated => AST.Text.Interpolated) =
      stack = f(stack.head) :: stack.tail

    def push(quoteSize: Quote): Unit = logger.trace {
      stack +:= AST.Text.Interpolated(quoteSize)
    }

    def pop(): Unit = logger.trace {
      stack = stack.tail
    }

    def submitEmpty(groupIx: State, quoteNum: Quote): Unit =
      logger.trace {
        if (groupIx == RAW)
          result.app(AST.Text.Raw(quoteNum))
        else
          result.app(AST.Text.Interpolated(quoteNum))
      }

    def finishCurrent(): AST.Text.Class[_] = logger.trace {
      withCurrent(t => t.copy(segments = t.segments.reverse))
      val txt = if (state.current == RAW) current.raw else current
      pop()
      state.end()
      val singleLine = !txt.segments.contains(EOL())
      if (singleLine || block.current.firstLine.isDefined || result.current.isDefined)
        txt
      else {
        val segs =
          AST.Text.MultiLine.stripOffset(block.current.indent, txt.segments)
        AST.Text.MultiLine(block.current.indent, txt.quoteChar, txt.quote, segs)
      }
    }

    def submit(): Unit = logger.trace {
      result.app(finishCurrent())
    }

    def submit(segment: AST.Text.Interpolated.Segment): Unit =
      logger.trace {
        withCurrent(_.prepend(segment))
      }

    def submitUnclosed(): Unit = logger.trace {
      result.app(AST.Text.Unclosed(finishCurrent()))
    }

    def onBegin(grp: State, quoteSize: Quote): Unit = logger.trace {
      push(quoteSize)
      state.begin(grp)
    }

    def submitPlainSegment(
      segment: AST.Text.Interpolated.Segment
    ): Unit =
      logger.trace {
        withCurrent(_.prependMergeReversed(segment))
      }

    def onPlainSegment(): Unit = logger.trace {
      submitPlainSegment(AST.Text.Segment.Plain(currentMatch))
    }

    def onQuote(quoteSize: Quote): Unit = logger.trace {
      if (current.quote == Quote.Triple
          && quoteSize == Quote.Single) onPlainSegment()
      else if (current.quote == Quote.Single
               && quoteSize == Quote.Triple) {
        val groupIx = state.current
        submit()
        submitEmpty(groupIx, Quote.Single)
      } else
        submit()
    }

    def onEscape(code: AST.Text.Segment.Escape): Unit = logger.trace {
      submit(code)
    }

    def onEscapeU16(): Unit = logger.trace {
      val code = currentMatch.drop(2)
      submit(AST.Text.Segment.Escape.Unicode.U16(code))
    }

    def onEscapeU32(): Unit = logger.trace {
      val code = currentMatch.drop(2)
      submit(AST.Text.Segment.Escape.Unicode.U32(code))
    }

    def onEscapeInt(): Unit = logger.trace {
      val int = currentMatch.drop(1).toInt
      submit(AST.Text.Segment.Escape.Number(int))
    }

    def onInvalidEscape(): Unit = logger.trace {
      val str = currentMatch.drop(1)
      submit(AST.Text.Segment.Escape.Invalid(str))
    }

    def onEscapeSlash(): Unit = logger.trace {
      submit(AST.Text.Segment.Escape.Slash)
    }

    def onEscapeQuote(): Unit = logger.trace {
      submit(AST.Text.Segment.Escape.Quote)
    }

    def onEscapeRawQuote(): Unit = logger.trace {
      submit(AST.Text.Segment.Escape.RawQuote)
    }

    def onInterpolateBegin(): Unit = logger.trace {
      result.push()
      off.push()
      state.begin(INTERPOLATE)
    }

    def onInterpolateEnd(): Unit = logger.trace {
      if (state.isInside(INTERPOLATE)) {
        state.endTill(INTERPOLATE)
        submit(AST.Text.Segment.Interpolation(result.current))
        result.pop()
        off.pop()
        state.end()
      } else {
        onUnrecognized()
      }
    }

    def onEOF(): Unit = logger.trace {
      submitUnclosed()
      rewind()
    }

    def onEOL(): Unit = logger.trace {
      submitPlainSegment(AST.Text.Segment.EOL())
    }

    val stringChar = noneOf("'`\"\\\n")
    val seg        = stringChar.many1
    val escape_int = "\\" >> num.decimal
    val escape_u16 = "\\u" >> repeat(stringChar, 0, 4)
    val escape_u32 = "\\U" >> repeat(stringChar, 0, 8)

    val INTP: State        = state.define("Text")
    val RAW: State         = state.define("Raw Text")
    val INTERPOLATE: State = state.define("Interpolate")
    INTERPOLATE.parent = ROOT
  }

  ROOT      || '`'      || reify { text.onInterpolateEnd() }
  text.INTP || '`'      || reify { text.onInterpolateBegin() }
  ROOT      || "'"      || reify { text.onBegin(text.INTP, Quote.Single) }
  ROOT      || "'''"    || reify { text.onBegin(text.INTP, Quote.Triple) }
  text.INTP || "'"      || reify { text.onQuote(Quote.Single) }
  text.INTP || "'''"    || reify { text.onQuote(Quote.Triple) }
  text.INTP || text.seg || reify { text.onPlainSegment() }
  text.INTP || eof      || reify { text.onEOF() }
  text.INTP || '\n'     || reify { text.onEOL() }

  ROOT     || "\""           || reify { text.onBegin(text.RAW, Quote.Single) }
  ROOT     || "\"\"\""       || reify { text.onBegin(text.RAW, Quote.Triple) }
  text.RAW || "\""           || reify { text.onQuote(Quote.Single) }
  text.RAW || "\"\"\""       || reify { text.onQuote(Quote.Triple) }
  text.RAW || noneOf("\"\n") || reify { text.onPlainSegment() }
  text.RAW || eof            || reify { text.onEOF() }
  text.RAW || '\n'           || reify { text.onEOL() }

  AST.Text.Segment.Escape.Character.codes.foreach { ctrl =>
    import scala.reflect.runtime.universe._
    val name = TermName(ctrl.toString)
    val func = q"text.onEscape(AST.Text.Segment.Escape.Character.$name)"
    text.INTP || s"\\$ctrl" || func
  }

  AST.Text.Segment.Escape.Control.codes.foreach { ctrl =>
    import scala.reflect.runtime.universe._
    val name = TermName(ctrl.toString)
    val func = q"text.onEscape(AST.Text.Segment.Escape.Control.$name)"
    text.INTP || s"\\$ctrl" || func
  }

  text.INTP || text.escape_u16           || reify { text.onEscapeU16() }
  text.INTP || text.escape_u32           || reify { text.onEscapeU32() }
  text.INTP || text.escape_int           || reify { text.onEscapeInt() }
  text.INTP || "\\\\"                    || reify { text.onEscapeSlash() }
  text.INTP || "\\'"                     || reify { text.onEscapeQuote() }
  text.INTP || "\\\""                    || reify { text.onEscapeRawQuote() }
  text.INTP || ("\\" >> text.stringChar) || reify { text.onInvalidEscape() }
  text.INTP || "\\"                      || reify { text.onPlainSegment() }

  //////////////
  /// Blocks ///
  //////////////

  final object block {

    class State(
      var isValid: Boolean,
      var indent: Int,
      var emptyLines: List[Int],
      var firstLine: Option[AST.Block.Line.NonEmpty],
      var lines: List[AST.Block.Line]
    )

    var stack: List[State]    = Nil
    var emptyLines: List[Int] = Nil
    var current: State        = new State(true, 0, Nil, None, Nil)

    def push(newIndent: Int): Unit = logger.trace {
      stack +:= current
      current    = new State(true, newIndent, emptyLines.reverse, None, Nil)
      emptyLines = Nil
    }

    def pop(): Unit = logger.trace {
      current = stack.head
      stack   = stack.tail
    }

    def build(): AST.Block = logger.trace {
      submitLine()
      AST.Block(
        AST.Block.Continuous,
        current.indent,
        current.emptyLines.reverse,
        unwrap(current.firstLine),
        current.lines.reverse
      )
    }

    def submit(): Unit = logger.trace {
      val block   = build()
      val isValid = current.isValid

      result.pop()
      off.pop()
      pop()

      val block2 = result.last() match {
        case None => block
        case Some(ast) =>
          if (!ast.isInstanceOf[AST.Opr]) block
          else block.copy(tp = AST.Block.Discontinuous)
      }

      val block3 =
        if (isValid) block2
        else AST.Block.InvalidIndentation(block2)

      result.app(block2)
      logger.endGroup()
    }

    def submitModule(): Unit = logger.trace {
      submitLine()
      val el  = current.emptyLines.reverse.map(AST.Block.Line(_))
      val el2 = emptyLines.reverse.map(AST.Block.Line(_))
      val firstLines = current.firstLine match {
        case None            => el
        case Some(firstLine) => firstLine.toOptional :: el
      }
      val lines  = firstLines ++ current.lines.reverse ++ el2
      val module = AST.Module(lines.head, lines.tail)
      result.current = Some(module)
      logger.endGroup()
    }

    def submitLine(): Unit = logger.trace {
      result.current match {
        case None => pushEmptyLine()
        case Some(r) =>
          current.firstLine match {
            case None =>
              val line = AST.Block.Line.Required(r, off.use())
              current.emptyLines = emptyLines
              current.firstLine  = Some(line)
            case Some(_) =>
              emptyLines.foreach(current.lines +:= AST.Block.Line(None, _))
              current.lines +:= AST.Block
                .Line(result.current, off.use())
          }
          emptyLines = Nil
      }
      result.current = None
    }

    def pushEmptyLine(): Unit = logger.trace {
      emptyLines +:= off.use()
    }

    def onBegin(newIndent: Int): Unit = logger.trace {
      result.push()
      push(newIndent)
      logger.beginGroup()
    }

    def onEmptyLine(): Unit = logger.trace {
      pushEmptyLine()
      off.on(-1)
    }

    def onEOFLine(): Unit = logger.trace {
      submitLine()
      state.end()
      off.on()
      onEOF()
    }

    def onNewLine(): Unit = logger.trace {
      state.begin(NEWLINE)
    }

    def onBlockNewline(): Unit = logger.trace {
      state.end()
      off.push()
      off.on()
      if (off.current == current.indent) {
        off.pop()
        submitLine()
      } else if (off.current > current.indent)
        onBegin(off.use())
      else
        onEnd(off.use())
      state.begin(FIRSTCHAR)
    }

    def onEnd(newIndent: Int): Unit = logger.trace {
      while (newIndent < current.indent) {
        submit()
      }
      if (newIndent > current.indent) {
        logger.log("Block with invalid indentation")
        onBegin(newIndent)
        current.isValid = false
      }
    }

    val NEWLINE   = state.define("Newline")
    val FIRSTCHAR = state.define("First Char")
  }

  ROOT            || newline              || reify { block.onNewLine() }
  block.NEWLINE   || space.opt >> newline || reify { block.onEmptyLine() }
  block.NEWLINE   || space.opt >> eof     || reify { block.onEOFLine() }
  block.NEWLINE   || space.opt            || reify { block.onBlockNewline() }
  block.FIRSTCHAR || always               || reify { state.end() }

  ////////////////
  /// Defaults ///
  ////////////////

  final def onUnrecognized(): Unit = logger.trace {
    result.app(AST.Unrecognized)
  }

  final def onEOF(): Unit = logger.trace {
    ident.finalizer()
    block.onEnd(0)
    block.submitModule()
  }

  ROOT || space || reify { off.on() }
  ROOT || eof   || reify { onEOF() }
  ROOT || any   || reify { onUnrecognized() }
}

object ParserDef2 {
  type Result[T] = flexer.Parser.Result[T]
  type Markers   = scala.Seq[(Int, AST.Marker)]
}

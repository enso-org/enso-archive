package org.enso.parser.docsParser

import org.enso.flexer._
import org.enso.parser.docsParser.DocAST._

import scala.reflect.runtime.universe._
import scala.annotation.tailrec

case class DocParser() extends ParserBase[AST] {

  implicit final def charToExpr(char: Char): Pattern =
    Ran(char, char)
  implicit final def stringToExpr(s: String): Pattern =
    s.tail.foldLeft(char(s.head))(_ >> _)

  class ExtendedChar(_this: Char) {
    final def ||(that: Char): Pattern =
      Or(char(_this), char(that))
  }
  implicit final def extendChar(i: Char): ExtendedChar = new ExtendedChar(i)
  final def char(c: Char):                Pattern      = range(c, c)
  final def range(start: Char, end: Char): Pattern =
    Ran(start, end)
  final def range(start: Int, end: Int): Pattern = Ran(start, end)
  val any: Pattern  = range(5, Int.MaxValue) // FIXME 5 -> 0
  val pass: Pattern = Pass
  val eof: Pattern  = char('\0')
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

  final def replaceGroupSymbols(
    s: String,
    lst: List[Group]
  ): String = {
    var out = s
    for ((grp, ix) <- lst.zipWithIndex) {
      out = out.replaceAll(s"___${ix}___", grp.groupIx.toString)
    }
    out
  }

  final def withSome[T, S](opt: Option[T])(f: T => S): S = opt match {
    case None    => throw new Error("Internal Error")
    case Some(a) => f(a)
  }

  final override def initialize(): Unit = {}

  override def getResult(): Option[AST] = result

  //////////////
  /// Result ///
  //////////////

  var result: Option[AST]        = None
  var workingASTStack: List[AST] = Nil

  var sectionsStack: List[Section]             = Nil
  var textFormattersStack: List[FormatterType] = Nil

  var currentSection: List[AST] => Section = TextBlock(_)

  final def pushAST(): Unit = logger.trace {
    if (result.isDefined) {
      workingASTStack +:= result.get
      result = None
    }
  }

  final def popAST(): Unit = logger.trace {
    if (workingASTStack.nonEmpty) {
      result          = Some(workingASTStack.head)
      workingASTStack = workingASTStack.tail
    } else {
      //throw new Error("Internal error: trying to pop empty AST stack")
    }
  }

  //////////////////////////////////////
  ///// Unclosed symbols checking  /////
  //////////////////////////////////////

  def checksOnLineEnd(): Unit = logger.trace {
    checkForUnclosed(Bold)
    checkForUnclosed(Italic)
    checkForUnclosed(Strikethrough)
  }

  def checkForUnclosed(formatterType: FormatterType): Unit = logger.trace {
    if (textFormattersStack.nonEmpty) {
      if (textFormattersStack.head == formatterType) {
        popAST()
        result              = Some(UnclosedFormatter(formatterType, result))
        textFormattersStack = textFormattersStack.tail
        pushAST()
      }
    }
  }

  //////////////////
  ///// Groups /////
  //////////////////

  val NORMAL: Group  = defineGroup("Normal")
  val CODE: Group    = defineGroup("Code")
  val NEWLINE: Group = defineGroup("Newline")

  ////////////////////////////
  ///// Pattern matching /////
  ////////////////////////////

  val lowerLetter: Pattern = range('a', 'z')
  val upperLetter: Pattern = range('A', 'Z')
  val digit: Pattern       = range('0', '9')

  val specialCharacters
    : Pattern             = "," | "." | ":" | "/" | "’" | "=" | "'" | "|" | "+" | "-"
  val whitespace: Pattern = ' '.many1()
  val newline             = '\n'

  val alphanumeric
    : Pattern             = lowerLetter | upperLetter | digit | whitespace | specialCharacters
  val normalText: Pattern = alphanumeric.many1()

  //////////////////////////
  ////// Text pushing //////
  //////////////////////////

  def pushNewLine(): Unit = logger.trace { pushNormalText(newline.toString) }

  def pushNormalText(in: String): Unit = logger.trace {
    result = Some(Text(in))
    pushAST()

    tagsList.foreach(tagType => {
      if (in.contains(tagType(None).name)) {
        pushTag(tagType, in.replaceFirst(tagType(None).name, ""))
      }
    })
  }

  NORMAL rule normalText run reify { pushNormalText(currentMatch) }

  def pushCodeLine(in: String): Unit = logger.trace {
    result = Some(CodeLine(in))
    pushAST()
  }

  /////////////////////////////
  ////// Text formatting //////
  /////////////////////////////

  def pushFormatter(formatterType: FormatterType): Unit =
    logger.trace {
      val unclosedFormattersToCheck = formatterType match {
        case Strikethrough => List(Bold, Italic)
        case Italic        => List(Bold, Strikethrough)
        case Bold          => List(Italic, Strikethrough)
      }

      if (textFormattersStack.contains(formatterType)) {
        unclosedFormattersToCheck foreach { formatterToCheck =>
          checkForUnclosed(formatterToCheck)
        }

        popAST()
        result              = Some(Formatter(formatterType, result))
        textFormattersStack = textFormattersStack.tail

        pushAST()
      } else {
        textFormattersStack +:= formatterType
      }
    }

  val boldTrigger: Char          = Bold.showableMarker
  val italicTrigger: Char        = Italic.showableMarker
  val strikethroughTrigger: Char = Strikethrough.showableMarker
  val inlineCodeTrigger          = '`'

  // format: off
  NORMAL rule (inlineCodeTrigger >> not('`').many() >> inlineCodeTrigger) run reify {
    pushCodeLine(currentMatch.substring(1).dropRight(1))
  }

  NORMAL rule boldTrigger          run reify { pushFormatter(Bold) }
  NORMAL rule italicTrigger        run reify { pushFormatter(Italic) }
  NORMAL rule strikethroughTrigger run reify { pushFormatter(Strikethrough) }
  // format: on

  /////////////////////
  ////// Tagging //////
  /////////////////////

  val tagsList: List[Option[String] => TagType] =
    List(Deprecated(_), Added(_), Modified(_), Removed(_))
  var tagsStack: List[TagType] = Nil

  def pushTag(tagType: Option[String] => TagType, version: String): Unit =
    logger.trace {
      popAST()
      if (version.replaceAll("\\s", "").length == 0) {
        tagsStack +:= tagType(None)
      } else {
        tagsStack +:= tagType(Some(version.substring(1)))
      }
      result = Some(Text(""))
    }

  ////////////////////////////////
  ////// Section management //////
  ////////////////////////////////

  def onNewSection(sectionType: List[AST] => Section): Unit =
    logger.trace {
      popAST()
      currentSection = sectionType(_)
    }

  def onEndOfSection(): Unit = logger.trace {
    checksOnLineEnd()
    reverseASTStack()
    createSectionHeader()
    cleanupEndOfSection()
  }

  def cleanupEndOfSection(): Unit = logger.trace {
    if (workingASTStack.nonEmpty) {
      sectionsStack +:= Some(currentSection(workingASTStack)).orNull
    } else {
      sectionsStack +:= Some(currentSection(Nil)).orNull
    }
    result          = None
    workingASTStack = Nil

    onNewSection(TextBlock(_))
    textFormattersStack = Nil
  }

  def reverseASTStack(): Unit = logger.trace {
    workingASTStack = workingASTStack.reverse
  }

  def reverseFinalASTStack(): Unit = logger.trace {
    sectionsStack = sectionsStack.reverse
  }

  def reverseTagsStack(): Unit = logger.trace {
    tagsStack = tagsStack.reverse
  }

  def onEOF(): Unit = logger.trace {
    onEndOfSection()
    reverseFinalASTStack()
    reverseTagsStack()

    if (sectionsStack.head == TextBlock()) {
      result = Some(
        Documentation(
          Tags(tagsStack),
          Synopsis(Nil),
          Body(Nil)
        )
      )
    } else {
      sectionsStack.length match {
        case 1 =>
          result = Some(
            Documentation(
              Tags(tagsStack),
              Synopsis(sectionsStack),
              Body(Nil)
            )
          )
        case _ =>
          result = Some(
            Documentation(
              Tags(tagsStack),
              Synopsis(sectionsStack.head),
              Body(sectionsStack.tail)
            )
          )
      }
    }
  }

  val importantTrigger: Char = Important().readableMarker
  val infoTrigger: Char      = Info().readableMarker
  val exampleTrigger: Char   = Example().readableMarker

  // format: off
  NORMAL rule importantTrigger run reify { onNewSection(Important(_)) }
  NORMAL rule infoTrigger      run reify { onNewSection(Info(_))}
  NORMAL rule exampleTrigger   run reify { onNewSection(Example(_))}

  NORMAL rule newline          run reify { checksOnLineEnd(); beginGroup(NEWLINE) }
  NORMAL rule eof              run reify { onEOF() }
  // format: on

  ////////////////////
  ////// Header //////
  ////////////////////

  def createSectionHeader(): Unit = logger.trace {
    popAST()
    if (result.contains(Text(newline.toString))) {
      popAST()
      result = Some(DocAST.Header(result.get))
      pushAST()
    } else if (result == Some(Text(""))) {
      popAST()
    } else {
      pushAST()
    }
  }

  ///////////////////
  ////// Links //////
  ///////////////////

  def createURL(name: String, url: String, linkType: LinkType): Unit = logger.trace {
    result = Some(Link(name, url, linkType))
    pushAST()
  }

  val imageNameTrigger: String = Image.readableMarker
  val linkNameTrigger: String  = URL.readableMarker

  NORMAL rule (imageNameTrigger >> not(')')
    .many1() >> ')') run reify {
    val in   = currentMatch.substring(2).dropRight(1).split(']')
    val name = in(0)
    val url  = in(1).substring(1)
    createURL(name, url, Image)
  }
  NORMAL rule (linkNameTrigger >> not(')')
    .many1() >> ')') run reify {
    val in   = currentMatch.substring(1).dropRight(1).split(']')
    val name = in(0)
    val url  = in(1).substring(1)
    createURL(name, url, URL)
  }

  //////////////////
  ////// CODE //////
  //////////////////

  // format: off
  CODE rule newline              run reify { beginGroup(NEWLINE)}
  CODE rule not(newline).many1() run reify { pushCodeLine(currentMatch) }
  CODE rule eof                  run reify { onEOF() }
  // format: on

  ///////////////////
  ///// NEWLINE /////
  ///////////////////

  var lastOffset: Int = 0
  var lastDiff: Int   = 0

  val codeIndent = 4
  val listIndent = 2

  var inListFlag: Boolean = false

  final def onIndent(): Unit = logger.trace {
    val diff = currentMatch.length - lastOffset

    if (diff >= codeIndent) {
      lastDiff = diff
      // new indented code
      onEndOfSection()
      onNewSection(Code(_))
      beginGroup(CODE)
    } else if (diff == 0 && lastDiff == codeIndent) {
      // code continued
      beginGroup(CODE)
    } else if (diff <= -codeIndent && lastDiff == codeIndent) {
      lastDiff = diff
      //end code
      onEndOfSection()
      endGroup()
    } else if (diff == -listIndent) {
      lastDiff = diff
      addOneListToAnother()
    } else {
      lastDiff = diff
      endGroup()
    }

    lastOffset = currentMatch.length
  }

  def onEmptyLine(): Unit = logger.trace {
    if (inListFlag) {
      addOneListToAnother()
      inListFlag = false
    }
    pushNewLine()
    onEndOfSection()
    endGroup()
  }

  val emptyLine: Pattern     = (whitespace | pass) >> newline
  val indentPattern: Pattern = (whitespace | pass).many()

  NEWLINE rule emptyLine run reify { onEmptyLine() }

  NEWLINE rule indentPattern run reify {
    if (workingASTStack == Nil && !result.contains(Text(""))) {
      pushNewLine()
      endGroup()
    } else {
      popAST()
      if (!result.contains(Text(newline.toString))) {
        pushAST()
        pushNewLine()
      } else {
        pushAST()
      }
      onIndent()
    }

  }

  NEWLINE rule eof run reify { onEOF() }

  /////////////////
  ///// Lists /////
  /////////////////

  final def onIndent(indent: Int, listType: ListType, content: AST): Unit =
    logger.trace {
      val diff = indent - lastOffset

      if (diff == listIndent) {
        if (!inListFlag) {
          pushNewLine()
        }
        inListFlag = true
        lastDiff   = diff
        addList(indent, listType, content)
      } else if (diff == 0) {
        addContentToList(content)
      } else if (diff == -listIndent) {
        lastDiff = diff
        addOneListToAnother()
        addContentToList(content)
      } else {
        if (inListFlag) {
          addContentToList(InvalidIndent(indent, content))
          return
        }
        endGroup()
      }

      lastOffset = indent
    }

  def addList(indent: Int, listType: ListType, content: AST): Unit =
    logger.trace {
      result = Some(ListBlock(indent, listType, List(content)))
      pushAST()
    }

  def addContentToList(content: AST): Unit = logger.trace {
    if (inListFlag) {
      popAST()
      val currentResult = result.orNull
      var currentContent = currentResult
        .asInstanceOf[ListBlock]
        .elems
      currentContent = (content :: currentContent.reverse).reverse

      result = Some(
        ListBlock(
          currentResult
            .asInstanceOf[ListBlock]
            .indent,
          currentResult
            .asInstanceOf[ListBlock]
            .listType,
          currentContent
        )
      )
      pushAST()
    }
  }

  def addOneListToAnother(): Unit = logger.trace {
    if (inListFlag) {
      popAST()
      val innerList = result.orNull
      popAST()
      val outerList    = result.orNull
      var outerContent = outerList.asInstanceOf[ListBlock].elems
      outerContent = (innerList :: outerContent.reverse).reverse

      result = Some(
        ListBlock(
          outerList
            .asInstanceOf[ListBlock]
            .indent,
          outerList
            .asInstanceOf[ListBlock]
            .listType,
          outerContent
        )
      )

      pushAST()
    }
  }

  val orderedListTrigger: Char   = Ordered.readableMarker
  val unorderedListTrigger: Char = Unordered.readableMarker

  NEWLINE rule (indentPattern >> orderedListTrigger >> not(newline)
    .many1()) run reify {
    val content = currentMatch.split(orderedListTrigger)
    onIndent(content(0).length, Ordered, content(1))
    endGroup()
  }

  NEWLINE rule (indentPattern >> unorderedListTrigger >> not(newline)
    .many1()) run reify {
    val content = currentMatch.split(unorderedListTrigger)
    onIndent(content(0).length, Unordered, content(1))
    endGroup()
  }
}

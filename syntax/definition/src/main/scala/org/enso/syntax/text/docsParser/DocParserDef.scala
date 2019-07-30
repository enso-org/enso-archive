package org.enso.syntax.text.docsParser

import org.enso.flexer._
import org.enso.flexer.Pattern._
import org.enso.syntax.text.DocAST
import org.enso.syntax.text.DocAST._

import scala.reflect.runtime.universe._
import scala.annotation.tailrec

case class DocParserDef() extends ParserBase[AST] {

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

  final def withSome[T, S](opt: Option[T])(f: T => S): S = opt match {
    case None    => throw new Error("Internal Error")
    case Some(a) => f(a)
  }

  final override def initialize(): Unit = {}

  //////////////
  /// Result ///
  //////////////

  override def getResult(): Option[AST] = result

  var result: Option[AST]        = None
  var workingASTStack: List[AST] = Nil

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
      logger.log("Trying to pop empty AST stack")
    }
  }

  //////////////////
  ///// Groups /////
  //////////////////

  val NORMAL: Group        = defineGroup("Normal")
  val MULTILINECODE: Group = defineGroup("Code")
  val NEWLINE: Group       = defineGroup("Newline")

  /////////////////////////////////
  /// Basic Char Classification ///
  /////////////////////////////////

  val lowerLetter: Pattern = range('a', 'z')
  val upperLetter: Pattern = range('A', 'Z')
  val digit: Pattern       = range('0', '9')

  val specialChars: String       = ",.:/’='|+-"
  val specialCharacters: Pattern = anyOf(specialChars)
  val whitespace: Pattern        = ' '.many1
  val newline                    = '\n'

  val possibleChars
    : Pattern             = lowerLetter | upperLetter | digit | whitespace | specialCharacters
  val normalText: Pattern = possibleChars.many1

  //////////////////////////
  ////// Text pushing //////
  //////////////////////////

  def pushNormalText(in: String): Unit = logger.trace {
    var text = in
    if (workingASTStack.isEmpty) {
      if (text.head == ' ') {
        text = text.tail
      }
    }
    result = Some(Text(text))
    pushAST()
    possibleTagsList.foreach(
      tagType => {
        // FIXME - bacause tagType is object, i need to drop '$'
        if (in.contains(
              tagType.getClass.getSimpleName.toUpperCase.dropRight(1)
            )) {
          pushTag(
            tagType,
            in.replaceFirst(
              tagType.getClass.getSimpleName.toUpperCase.dropRight(1),
              ""
            )
          )
        }
      }
    )
  }

  NORMAL rule normalText run reify { pushNormalText(currentMatch) }

  //////////////////////////
  ////// Code pushing //////
  //////////////////////////

  def pushCodeLine(in: String, isInlineCode: Boolean): Unit = logger.trace {
    if (isInlineCode) {
      result = Some(CodeLine(in))
    } else {
      result = Some(MultilineCodeLine(in))
    }
    pushAST()
  }
  val inlineCodeTrigger = '`'
  NORMAL rule (inlineCodeTrigger >> not('`').many >> inlineCodeTrigger) run reify {
    pushCodeLine(currentMatch.substring(1).dropRight(1), true)
  }

  // format: off
  MULTILINECODE rule newline              run reify { beginGroup(NEWLINE)}
  MULTILINECODE rule not(newline).many1   run reify { pushCodeLine(currentMatch, false) }
  MULTILINECODE rule eof                  run reify { onEOF() }
  // format: on

  /////////////////////////////
  ////// Text formatting //////
  /////////////////////////////

  var textFormattersStack: List[FormatterType] = Nil

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

  val boldTrigger: Char          = Bold.marker
  val italicTrigger: Char        = Italic.marker
  val strikethroughTrigger: Char = Strikethrough.marker

  // format: off
  NORMAL rule boldTrigger          run reify { pushFormatter(Bold) }
  NORMAL rule italicTrigger        run reify { pushFormatter(Italic) }
  NORMAL rule strikethroughTrigger run reify { pushFormatter(Strikethrough) }
  // format: on

  /////////////////////
  ////// Tagging //////
  /////////////////////

  val possibleTagsList: List[TagType] =
    List(Deprecated, Added, Modified, Removed, Upcoming)
  var tagsStack: List[TagClass] = Nil
  var tagsIndent: Int           = 0

  def pushTag(tagType: TagType, version: String): Unit =
    logger.trace {
      popAST()
      if (version.replaceAll("\\s", "").length == 0) {
        tagsStack +:= TagClass(tagType)
      } else {
        tagsStack +:= TagClass(tagType, Some(version.substring(1)))
      }
      result = Some("")
    }

  /////////////////////////
  ////// New section //////
  /////////////////////////

  var sectionsStack: List[Section] = Nil
  var currentSection: SectionType  = TextBlock
  var currentSectionIndent: Int    = 0

  def onNewSection(sectionType: SectionType): Unit =
    logger.trace {
      popAST()
      currentSection = sectionType
    }

  val importantTrigger: Char = Important.marker.get
  val infoTrigger: Char      = Info.marker.get
  val exampleTrigger: Char   = Example.marker.get

  // format: off
  NORMAL rule importantTrigger run reify { onNewSection(Important) }
  NORMAL rule infoTrigger      run reify { onNewSection(Info)}
  NORMAL rule exampleTrigger   run reify { onNewSection(Example)}
  // format: on

  ////////////////////////////
  ////// End of section //////
  ////////////////////////////

  def onEndOfSection(): Unit = logger.trace {
    checksOfUnclosedFormattersOnEndOfSection()
    reverseASTStack()
    createSectionHeader()
    cleanupEndOfSection()
  }

  def checksOfUnclosedFormattersOnEndOfSection(): Unit = logger.trace {
    checkForUnclosed(Bold)
    checkForUnclosed(Italic)
    checkForUnclosed(Strikethrough)
  }

  def cleanupEndOfSection(): Unit = logger.trace {
    if (sectionsStack.isEmpty) {
      tagsIndent = currentSectionIndent
    }
    sectionsStack +:= Some(
      Section(currentSectionIndent, currentSection, workingASTStack)
    ).orNull
    result          = None
    workingASTStack = Nil
    onNewSection(TextBlock)
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
    if (sectionsStack.head.elems == Nil) {
      result = Some(
        Documentation(
          Tags(tagsIndent, tagsStack),
          Synopsis(Nil),
          Details(Nil)
        )
      )
    } else {
      sectionsStack.length match {
        case 1 =>
          result = Some(
            Documentation(
              Tags(tagsIndent, tagsStack),
              Synopsis(sectionsStack),
              Details(Nil)
            )
          )
        case _ =>
          result = Some(
            Documentation(
              Tags(tagsIndent, tagsStack),
              Synopsis(sectionsStack.head),
              Details(sectionsStack.tail)
            )
          )
      }
    }
  }

  NORMAL rule eof run reify { onEOF() }

  ////////////////////
  ////// Header //////
  ////////////////////

  def createSectionHeader(): Unit = logger.trace {
    popAST()
    if (result.contains(Text(newline.toString))) {
      popAST()
      result = Some(DocAST.Header(result.get))
      pushAST()
    } else if (result.contains(Text(""))) {
      popAST()
    } else {
      pushAST()
    }
  }

  ///////////////////
  ////// Links //////
  ///////////////////

  def createURL(name: String, url: String): Unit =
    logger.trace {
      result = Some(URL(name, url))
      pushAST()
    }

  def createImage(name: String, url: String): Unit =
    logger.trace {
      result = Some(Image(name, url))
      pushAST()
    }

  val imageNameTrigger: String = Image().marker
  val urlNameTrigger: String   = URL().marker

  NORMAL rule (imageNameTrigger >> not(')').many1 >> ')') run reify {
    val in   = currentMatch.substring(2).dropRight(1).split(']')
    val name = in(0)
    val url  = in(1).substring(1)
    createImage(name, url)
  }
  NORMAL rule (urlNameTrigger >> not(')').many1 >> ')') run reify {
    val in   = currentMatch.substring(1).dropRight(1).split(']')
    val name = in(0)
    val url  = in(1).substring(1)
    createURL(name, url)
  }

  /////////////////////////////////////////
  ///// Indent Management & NEWLINE ///////
  /////////////////////////////////////////

  var lastOffset: Int     = 0
  var lastDiff: Int       = 0
  val codeIndent: Int     = 4
  val listIndent: Int     = 2
  var inListFlag: Boolean = false

  final def onIndent(): Unit = logger.trace {
    val diff = currentMatch.length - lastOffset
    if (diff >= codeIndent) {
      lastDiff = codeIndent
      onEndOfSection()
      currentSectionIndent = currentMatch.length
      onNewSection(MultilineCode)
      beginGroup(MULTILINECODE)
    } else if (diff == 0 && lastDiff == codeIndent) {
      beginGroup(MULTILINECODE)
    } else if (diff <= -codeIndent && lastDiff == codeIndent) {
      lastDiff = diff
      onEndOfSection()
      endGroup()
    } else if (diff == -listIndent && inListFlag) {
      lastDiff = diff
      addOneListToAnother()
    } else {
      lastDiff             = diff
      currentSectionIndent = currentMatch.length
      endGroup()
    }
    lastOffset = currentMatch.length
  }

  final def onIndentForListCreation(
    indent: Int,
    listType: ListType,
    content: AST
  ): Unit =
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
      } else if (diff == -listIndent && inListFlag) {
        lastDiff = diff
        addOneListToAnother()
        addContentToList(content)
      } else {
        if (inListFlag) {
          addContentToList(
            InvalidIndent(indent, content, listType)
          )
          return
        }
        endGroup()
      }
      lastOffset = indent
    }

  final def onEmptyLine(): Unit = logger.trace {
    if (inListFlag) {
      addOneListToAnother()
      inListFlag = !inListFlag
    }
    pushNewLine()
    onEndOfSection()
    endGroup()
  }

  final def pushNewLine(): Unit = logger.trace {
    pushNormalText(newline.toString)
  }

  val emptyLine: Pattern     = (whitespace | pass) >> newline
  val indentPattern: Pattern = (whitespace | pass).many

  NORMAL rule newline run reify { beginGroup(NEWLINE) }

  NEWLINE rule emptyLine run reify { onEmptyLine() }
  NEWLINE rule ((whitespace | pass) >> eof) run reify { onEOF() }
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

  /////////////////
  ///// Lists /////
  /////////////////

  def addList(indent: Int, listType: ListType, content: AST): Unit =
    logger.trace {
      result = Some(ListBlock(indent, listType, content))
      pushAST()
    }

  def addContentToList(content: AST): Unit = logger.trace {
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

  def addOneListToAnother(): Unit = logger.trace {
    popAST()
    val innerList = result.orNull
    if (workingASTStack.head.isInstanceOf[ListBlock]) {
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
    }
    pushAST()
  }

  val orderedListTrigger: Char   = Ordered.marker
  val unorderedListTrigger: Char = Unordered.marker

  NEWLINE rule (indentPattern >> orderedListTrigger >> not(newline).many1) run reify {
    val content = currentMatch.split(orderedListTrigger)
    onIndentForListCreation(content(0).length, Ordered, content(1))
    endGroup()
  }

  NEWLINE rule (indentPattern >> unorderedListTrigger >> not(newline).many1) run reify {
    val content = currentMatch.split(unorderedListTrigger)
    onIndentForListCreation(content(0).length, Unordered, content(1))
    endGroup()
  }
}

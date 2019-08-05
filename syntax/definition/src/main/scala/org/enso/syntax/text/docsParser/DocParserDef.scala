package org.enso.syntax.text.docsParser

import org.enso.flexer._
import org.enso.flexer.Pattern._
import org.enso.syntax.text.ast.Doc._
import org.enso.syntax.text.ast.Doc
import org.enso.syntax.text.ast.Doc.AST._

import scala.reflect.runtime.universe._
import scala.annotation.tailrec

case class DocParserDef() extends ParserBase[AST] {

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
        if (in.contains(
              tagType.toString.toUpperCase
            )) {
          pushTag(
            tagType,
            in.replaceFirst(
              tagType.toString.toUpperCase,
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
      result = Some(InlineCode(in))
    } else {
      result = Some(Section.Code.Line(in))
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

  var textFormattersStack: List[Formatter.Type] = Nil

  def pushFormatter(tp: Formatter.Type): Unit =
    logger.trace {
      val unclosedFormattersToCheck = tp match {
        case Formatter.Strikethrough => List(Formatter.Bold, Formatter.Italic)
        case Formatter.Italic        => List(Formatter.Bold, Formatter.Strikethrough)
        case Formatter.Bold          => List(Formatter.Italic, Formatter.Strikethrough)
      }
      if (textFormattersStack.contains(tp)) {
        unclosedFormattersToCheck foreach { formatterToCheck =>
          checkForUnclosed(formatterToCheck)
        }
        popAST()
        result              = Some(Formatter(tp, result))
        textFormattersStack = textFormattersStack.tail
        pushAST()
      } else {
        textFormattersStack +:= tp
      }
    }

  def checkForUnclosed(tp: Formatter.Type): Unit = logger.trace {
    if (textFormattersStack.nonEmpty) {
      if (textFormattersStack.head == tp) {
        popAST()
        result              = Some(Formatter.Unclosed(tp, result))
        textFormattersStack = textFormattersStack.tail
        pushAST()
      }
    }
  }

  val boldTrigger: Char          = Formatter.Bold.marker
  val italicTrigger: Char        = Formatter.Italic.marker
  val strikethroughTrigger: Char = Formatter.Strikethrough.marker

  // format: off
  NORMAL rule boldTrigger          run reify { pushFormatter(Formatter.Bold) }
  NORMAL rule italicTrigger        run reify { pushFormatter(Formatter.Italic) }
  NORMAL rule strikethroughTrigger run reify { pushFormatter(Formatter.Strikethrough) }
  // format: on

  /////////////////////
  ////// Tagging //////
  /////////////////////

  val possibleTagsList: List[Tags.Tag.Type] =
    List(
      Tags.Tag.Deprecated,
      Tags.Tag.Added,
      Tags.Tag.Modified,
      Tags.Tag.Removed,
      Tags.Tag.Upcoming
    )
  var tagsStack: List[Tags.Tag] = Nil
  var tagsIndent: Int           = 0

  def pushTag(tagType: Tags.Tag.Type, details: String): Unit =
    logger.trace {
      popAST()
      if (details.replaceAll("\\s", "").length == 0) {
        tagsStack +:= Tags.Tag(tagType)
      } else {
        tagsStack +:= Tags.Tag(tagType, Some(details))
      }
      result = Some("")
    }

  /////////////////////////
  ////// New section //////
  /////////////////////////

  var sectionsStack: List[Section] = Nil
  var currentSection: Section.Type = Section.Raw
  var currentSectionIndent: Int    = 0

  def onNewSection(st: Section.Type): Unit =
    logger.trace {
      popAST()
      currentSection = st
    }

  val importantTrigger: Char = Section.Important.marker.get
  val infoTrigger: Char      = Section.Info.marker.get
  val exampleTrigger: Char   = Section.Example.marker.get

  // format: off
  NORMAL rule importantTrigger run reify { onNewSection(Section.Important) }
  NORMAL rule infoTrigger      run reify { onNewSection(Section.Info)}
  NORMAL rule exampleTrigger   run reify { onNewSection(Section.Example)}
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
    checkForUnclosed(Formatter.Bold)
    checkForUnclosed(Formatter.Italic)
    checkForUnclosed(Formatter.Strikethrough)
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
    onNewSection(Section.Raw)
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
        Doc(
          Tags(tagsIndent, tagsStack),
          Synopsis(Nil),
          Body(Nil)
        )
      )
    } else {
      sectionsStack.length match {
        case 1 =>
          result = Some(
            Doc(
              Tags(tagsIndent, tagsStack),
              Synopsis(sectionsStack),
              Body(Nil)
            )
          )
        case _ =>
          result = Some(
            Doc(
              Tags(tagsIndent, tagsStack),
              Synopsis(sectionsStack.head),
              Body(sectionsStack.tail)
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
      result = Some(Section.Header(result.get))
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
      result = Some(Link.URL(name, url))
      pushAST()
    }

  def createImage(name: String, url: String): Unit =
    logger.trace {
      result = Some(Link.Image(name, url))
      pushAST()
    }

  val imageNameTrigger: String = Link.Image().marker
  val urlNameTrigger: String   = Link.URL().marker

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
      onNewSection(Section.Code)
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
    tp: ListBlock.Type,
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
        addList(indent, tp, content)
      } else if (diff == 0) {
        addContentToList(content)
      } else if (diff == -listIndent && inListFlag) {
        lastDiff = diff
        addOneListToAnother()
        addContentToList(content)
      } else {
        if (inListFlag) {
          addContentToList(
            ListBlock.Indent.Invalid(indent, content, tp)
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

  def addList(indent: Int, listType: ListBlock.Type, content: AST): Unit =
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
          .tp,
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
            .tp,
          outerContent
        )
      )
    }
    pushAST()
  }

  val orderedListTrigger: Char   = ListBlock.Ordered.marker
  val unorderedListTrigger: Char = ListBlock.Unordered.marker

  NEWLINE rule (indentPattern >> orderedListTrigger >> not(newline).many1) run reify {
    val content = currentMatch.split(orderedListTrigger)
    onIndentForListCreation(content(0).length, ListBlock.Ordered, content(1))
    endGroup()
  }

  NEWLINE rule (indentPattern >> unorderedListTrigger >> not(newline).many1) run reify {
    val content = currentMatch.split(unorderedListTrigger)
    onIndentForListCreation(content(0).length, ListBlock.Unordered, content(1))
    endGroup()
  }
}

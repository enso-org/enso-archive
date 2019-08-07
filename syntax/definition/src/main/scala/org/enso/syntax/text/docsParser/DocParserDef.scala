package org.enso.syntax.text.docsParser

import org.enso.flexer._
import org.enso.flexer.Pattern._
import org.enso.syntax.text.ast.Doc._
import org.enso.syntax.text.ast.Doc

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
    result = Some(AST.Text(text))
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

  def pushCodeLine(in: String): Unit = logger.trace {
    result = Some(AST.Code.Inline(in))
    pushAST()
  }

  def pushMultilineCodeLine(in: String): Unit = logger.trace {
    do {
      popAST()
    } while (result.contains(AST.Text(newline.toString)))

    result.get match {
      case _: AST.Code.Multiline => {
        val inMultilineCode = result.get.asInstanceOf[AST.Code.Multiline].elems
        val indent          = result.get.asInstanceOf[AST.Code.Multiline].indent
        result = Some(AST.Code.Multiline(indent, in :: inMultilineCode))
      }
      case _ => pushAST()
    }
    pushAST()
  }

  val inlineCodeTrigger = '`'
  NORMAL rule (inlineCodeTrigger >> not('`').many >> inlineCodeTrigger) run reify {
    pushCodeLine(currentMatch.substring(1).dropRight(1))
  }

  // format: off
  MULTILINECODE rule newline              run reify { endGroup() }
  MULTILINECODE rule not(newline).many1   run reify { pushMultilineCodeLine(currentMatch) }
  MULTILINECODE rule eof                  run reify { onEOF() }
  // format: on

  /////////////////////////////
  ////// Text formatting //////
  /////////////////////////////

  var textFormattersStack: List[AST.Formatter.Type] = Nil

  def pushFormatter(tp: AST.Formatter.Type): Unit =
    logger.trace {
      val unclosedFormattersToCheck = tp match {
        case AST.Formatter.Strikethrough =>
          List(AST.Formatter.Bold, AST.Formatter.Italic)
        case AST.Formatter.Italic =>
          List(AST.Formatter.Bold, AST.Formatter.Strikethrough)
        case AST.Formatter.Bold =>
          List(AST.Formatter.Italic, AST.Formatter.Strikethrough)
      }
      if (textFormattersStack.contains(tp)) {
        unclosedFormattersToCheck foreach { formatterToCheck =>
          checkForUnclosed(formatterToCheck)
        }
        popAST()
        result              = Some(AST.Formatter(tp, result))
        textFormattersStack = textFormattersStack.tail
        pushAST()
      } else {
        textFormattersStack +:= tp
      }
    }

  def checkForUnclosed(tp: AST.Formatter.Type): Unit = logger.trace {
    if (textFormattersStack.nonEmpty) {
      if (textFormattersStack.head == tp) {
        popAST()
        result              = Some(AST.Formatter.Unclosed(tp, result))
        textFormattersStack = textFormattersStack.tail
        pushAST()
      }
    }
  }

  val boldTrigger: Char          = AST.Formatter.Bold.marker
  val italicTrigger: Char        = AST.Formatter.Italic.marker
  val strikethroughTrigger: Char = AST.Formatter.Strikethrough.marker

  // format: off
  NORMAL rule boldTrigger          run reify { pushFormatter(AST.Formatter.Bold) }
  NORMAL rule italicTrigger        run reify { pushFormatter(AST.Formatter.Italic) }
  NORMAL rule strikethroughTrigger run reify { pushFormatter(AST.Formatter.Strikethrough) }
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

  var sectionsStack: List[Section]                = Nil
  var currentSection: Option[Section.Marked.Type] = None
  var currentSectionIndent: Int                   = 0

  def onNewSection(st: Option[Section.Marked.Type]): Unit =
    logger.trace {
      popAST()
      currentSection = st
    }

  val importantTrigger: Char = Section.Marked.Important.marker
  val infoTrigger: Char      = Section.Marked.Info.marker
  val exampleTrigger: Char   = Section.Marked.Example.marker

  // format: off
  NORMAL rule importantTrigger run reify { onNewSection(Some(Section.Marked.Important)) }
  NORMAL rule infoTrigger      run reify { onNewSection(Some(Section.Marked.Info))}
  NORMAL rule exampleTrigger   run reify { onNewSection(Some(Section.Marked.Example))}
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
    checkForUnclosed(AST.Formatter.Bold)
    checkForUnclosed(AST.Formatter.Italic)
    checkForUnclosed(AST.Formatter.Strikethrough)
  }

  def cleanupEndOfSection(): Unit = logger.trace {
    if (sectionsStack.isEmpty) {
      tagsIndent = currentSectionIndent
    }
    currentSection match {
      case _: Some[Section.Marked.Type] =>
        sectionsStack +:= Some(
          Section.Marked(
            currentSectionIndent,
            currentSection.get,
            workingASTStack
          )
        ).orNull
      case None =>
        sectionsStack +:= Some(
          Section.Raw(
            currentSectionIndent,
            workingASTStack
          )
        ).orNull
    }

    result          = None
    workingASTStack = Nil
    onNewSection(None)
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

    val _tags = Tags(tagsIndent, tagsStack)

    val _synopsis = sectionsStack.length match {
      case 0 => Synopsis(Nil)
      case 1 => Synopsis(sectionsStack)
      case _ => Synopsis(sectionsStack.head)
    }

    val _body = sectionsStack.length match {
      case 0 | 1 => Body(Nil)
      case _     => Body(sectionsStack.tail)
    }
    result = Some(
      Doc(
        _tags,
        _synopsis,
        _body
      )
    )
  }

  NORMAL rule eof run reify { onEOF() }

  ////////////////////
  ////// Header //////
  ////////////////////

  def createSectionHeader(): Unit = logger.trace {
    popAST()
    if (result.contains(AST.Text(newline.toString))) {
      var listForHeader: List[AST] = Nil
      do {
        popAST()
        listForHeader +:= result.get
      } while (!result.contains(AST.Text(newline.toString)))
      pushAST()
      listForHeader = listForHeader.tail
      result        = Some(Section.Header(listForHeader.reverse))
      pushAST()
    } else if (result.contains(AST.Text(""))) {
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
      result = Some(AST.Link.URL(name, url))
      pushAST()
    }

  def createImage(name: String, url: String): Unit =
    logger.trace {
      result = Some(AST.Link.Image(name, url))
      pushAST()
    }

  val imageNameTrigger: String = AST.Link.Image().marker
  val urlNameTrigger: String   = AST.Link.URL().marker

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
      result   = Some(AST.Code.Multiline(currentMatch.length, Nil))
      pushAST()
      beginGroup(MULTILINECODE)
    } else if (diff == 0 && lastDiff == codeIndent) {
      beginGroup(MULTILINECODE)
    } else if (diff <= -codeIndent && lastDiff == codeIndent) {
      lastDiff = diff
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
    tp: AST.List.Type,
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
            AST.List.Indent.Invalid(indent, content, tp)
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
    if (workingASTStack == Nil && !result.contains(AST.Text(""))) {
      pushNewLine()
      endGroup()
    } else {
      popAST()
      if (!result.contains(AST.Text(newline.toString))) {
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

  def addList(indent: Int, listType: AST.List.Type, content: AST): Unit =
    logger.trace {
      result = Some(AST.List(indent, listType, content))
      pushAST()
    }

  def addContentToList(content: AST): Unit = logger.trace {
    popAST()
    val currentResult = result.orNull
    var currentContent = currentResult
      .asInstanceOf[AST.List]
      .elems
    currentContent = (content :: currentContent.reverse).reverse
    result = Some(
      AST.List(
        currentResult
          .asInstanceOf[AST.List]
          .indent,
        currentResult
          .asInstanceOf[AST.List]
          .tp,
        currentContent
      )
    )
    pushAST()
  }

  def addOneListToAnother(): Unit = logger.trace {
    popAST()
    val innerList = result.orNull
    if (workingASTStack.head.isInstanceOf[AST.List]) {
      popAST()
      val outerList    = result.orNull
      var outerContent = outerList.asInstanceOf[AST.List].elems
      outerContent = (innerList :: outerContent.reverse).reverse
      result = Some(
        AST.List(
          outerList
            .asInstanceOf[AST.List]
            .indent,
          outerList
            .asInstanceOf[AST.List]
            .tp,
          outerContent
        )
      )
    }
    pushAST()
  }

  val orderedListTrigger: Char   = AST.List.Ordered.marker
  val unorderedListTrigger: Char = AST.List.Unordered.marker

  NEWLINE rule (indentPattern >> orderedListTrigger >> not(newline).many1) run reify {
    val content = currentMatch.split(orderedListTrigger)
    onIndentForListCreation(content(0).length, AST.List.Ordered, content(1))
    endGroup()
  }

  NEWLINE rule (indentPattern >> unorderedListTrigger >> not(newline).many1) run reify {
    val content = currentMatch.split(unorderedListTrigger)
    onIndentForListCreation(content(0).length, AST.List.Unordered, content(1))
    endGroup()
  }
}

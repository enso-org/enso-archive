package org.enso.syntax.text.spec

import org.enso.flexer._
import org.enso.flexer.automata.Pattern
import org.enso.flexer.automata.Pattern._
import org.enso.data.List1
import org.enso.syntax.text.ast.Doc._
import org.enso.syntax.text.ast.Doc

import scala.reflect.runtime.universe.reify

case class DocParserDef() extends Parser[Doc] {

  //////////////
  /// Result ///
  //////////////

  override def getResult(): Option[Doc] = result.doc

  final object result {
    var current: Option[Elem]       = None
    var doc: Option[Doc]            = None
    var workingASTStack: List[Elem] = Nil

    def push(): Unit = logger.trace {
      if (current.isDefined) {
        logger.log(s"Pushed: $current")
        workingASTStack +:= current.get
        current = None
      } else {
        logger.err("Undefined current")
      }
    }

    def pop(): Unit = logger.trace {
      if (workingASTStack.nonEmpty) {
        current         = Some(workingASTStack.head)
        workingASTStack = workingASTStack.tail
        logger.log(s"New result: $current")
      } else {
        logger.err("Trying to pop empty AST stack")
      }
    }
  }

  //////////////////
  ///// Groups /////
  //////////////////

  val CODE: State    = state.define("Code")
  val NEWLINE: State = state.define("Newline")

  /////////////////////////////////
  /// Basic Char Classification ///
  /////////////////////////////////

  val lowerLetter: Pattern = range('a', 'z')
  val upperLetter: Pattern = range('A', 'Z')
  val digit: Pattern       = range('0', '9')

  val specialCharacters
    : Pattern             = "," | "." | ":" | "/" | "’" | "=" | "'" | "|" | "+" | "-"
  val whitespace: Pattern = ' '.many1
  val newline             = '\n'

  val possibleChars
    : Pattern             = lowerLetter | upperLetter | digit | whitespace | specialCharacters
  val normalText: Pattern = possibleChars.many1

  //////////////////////////
  ////// Text pushing //////
  //////////////////////////

  def onPushingNormalText(in: String): Unit = logger.trace {
    val isDocBeginning = result.workingASTStack.isEmpty && sectionsStack.isEmpty // to create tags on file beginning
    val isSectionBeginning = result.workingASTStack.isEmpty || result.workingASTStack.head
        .isInstanceOf[Section.Header] // to remove unnecessary indent from first line as yet onIndent hasn't been called

    if (isDocBeginning) {
      if (checkForTag(in) == false) {
        val text = removeWhitespaces(in)
        pushNormalText(text)
      }
    } else if (isSectionBeginning) {
      val text = removeWhitespaces(in)
      pushNormalText(text)
    } else {
      pushNormalText(in)
    }
  }

  def removeWhitespaces(in: String): String = logger.trace {
    var text = in
    if (text.nonEmpty) {
      while (text.head == ' ' && text.length > 1) {
        text = text.tail
      }
    }
    text
  }

  def pushNormalText(in: String): Unit = logger.trace {
    result.current = Some(Elem.Text(in))
    result.push()
  }

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

  def pushTag(indent: Int, tagType: Tags.Tag.Type, details: String): Unit =
    logger.trace {
      if (details.replaceAll("\\s", "").length == 0) {
        tagsStack +:= Tags.Tag(indent, tagType)
      } else {
        if (details.nonEmpty) {
          var det = removeWhitespaces(details)
          if (tagType != Tags.Tag.Unrecognized) {
            det = ' ' + det
          }
          tagsStack +:= Tags.Tag(indent, tagType, Some(det))
        } else {
          Tags.Tag(indent, tagType, None)
        }
      }
      result.current = Some("")
    }

  def checkForTag(in: String): Boolean = logger.trace {
    val inArray = in.split(" ")
    inArray.foreach(elem => {
      if (elem.isEmpty) {
        currentSectionIndent += 1
      } else if (elem == elem.toUpperCase) {
        var containsTag = false
        for (tagType <- possibleTagsList) {
          if (elem == tagType.toString.toUpperCase) {
            containsTag = true
            val tagDetail = in.replaceFirst(
              tagType.toString.toUpperCase,
              ""
            )

            pushTag(
              currentSectionIndent,
              tagType,
              tagDetail
            )
          }
        }
        if (!containsTag && !elem.contains(newline)) {
          pushTag(currentSectionIndent, Tags.Tag.Unrecognized, in)
        }
        return true
      }
    })
    return false
  }

  ROOT || normalText || reify { onPushingNormalText(currentMatch) }

  //////////////////////////
  ////// Code pushing //////
  //////////////////////////

  def pushCodeLine(in: String): Unit = logger.trace {
    result.current = Some(Elem.Code.Inline(in))
    result.push()
  }

  def pushMultilineCodeLine(in: String): Unit = logger.trace {
    do {
      result.pop()
    } while (result.current.get == Elem.Newline)
    result.current match {
      case Some(_: Elem.Code) =>
        val elems   = result.current.get.asInstanceOf[Elem.Code].elems
        val newElem = Elem.Code.Line(latestIndent, in)
        result.current = Some(Elem.Code(elems :+ newElem))
      case Some(_) | None => result.push()
    }
    result.push()
  }

  val inlineCodeTrigger = '`'
  val inlineCodePattern
    : Pattern = inlineCodeTrigger >> not(inlineCodeTrigger).many >> inlineCodeTrigger
  ROOT || inlineCodePattern || reify {
    pushCodeLine(currentMatch.substring(1).dropRight(1))
  }

  // format: off
  CODE || newline              || reify { state.end(); state.begin(NEWLINE) }
  CODE || not(newline).many1   || reify { pushMultilineCodeLine(currentMatch) }
  CODE || eof                  || reify { onEOF() }
  // format: on

  /////////////////////////////
  ////// Text formatting //////
  /////////////////////////////

  var textFormattersStack: List[Elem.Formatter.Type] = Nil

  def pushFormatter(tp: Elem.Formatter.Type): Unit =
    logger.trace {
      val unclosedFormattersToCheck = tp match {
        case Elem.Formatter.Strikethrough =>
          List(Elem.Formatter.Bold, Elem.Formatter.Italic)
        case Elem.Formatter.Italic =>
          List(Elem.Formatter.Bold, Elem.Formatter.Strikethrough)
        case Elem.Formatter.Bold =>
          List(Elem.Formatter.Italic, Elem.Formatter.Strikethrough)
      }
      if (textFormattersStack.contains(tp)) {
        unclosedFormattersToCheck foreach { formatterToCheck =>
          checkForUnclosed(formatterToCheck)
        }
        var listOfFormattedAST: List[Elem] = Nil

        while (result.workingASTStack.head != Elem.Formatter(tp) && result.workingASTStack.nonEmpty) {
          result.pop()
          result.current match {
            case Some(value) => listOfFormattedAST +:= value
            case None        =>
          }
        }
        result.pop()

        result.current      = Some(Elem.Formatter(tp, listOfFormattedAST))
        textFormattersStack = textFormattersStack.tail
        result.push()
      } else {
        textFormattersStack +:= tp
        result.current = Some(Elem.Formatter(tp))
        result.push()
      }
    }

  def checkForUnclosed(tp: Elem.Formatter.Type): Unit = logger.trace {
    if (textFormattersStack.nonEmpty) {
      if (textFormattersStack.head == tp) {
        var listOfFormattedAST: List[Elem] = Nil
        while (result.workingASTStack.head != Elem
                 .Formatter(tp) && result.workingASTStack.nonEmpty) {
          result.pop()
          result.current match {
            case Some(value) => listOfFormattedAST +:= value
            case None        =>
          }
        }
        result.pop()

        result.current      = Some(Elem.Formatter.Unclosed(tp, listOfFormattedAST))
        textFormattersStack = textFormattersStack.tail
        result.push()
      }
    }
  }

  val boldTrigger: Char          = Elem.Formatter.Bold.marker
  val italicTrigger: Char        = Elem.Formatter.Italic.marker
  val strikethroughTrigger: Char = Elem.Formatter.Strikethrough.marker

  // format: off
  ROOT || boldTrigger          || reify { pushFormatter(Elem.Formatter.Bold) }
  ROOT || italicTrigger        || reify { pushFormatter(Elem.Formatter.Italic) }
  ROOT || strikethroughTrigger || reify { pushFormatter(Elem.Formatter.Strikethrough) }
  // format: on

  /////////////////////////
  ////// New section //////
  /////////////////////////

  var sectionsStack: List[Section]                = Nil
  var currentSection: Option[Section.Marked.Type] = None
  var currentSectionIndent: Int                   = 0

  def onNewSection(st: Option[Section.Marked.Type]): Unit =
    logger.trace {
      result.pop()
      currentSection = st
    }

  final def onEmptyLine(): Unit = logger.trace {
    if (inListFlag) {
      addOneListToAnother()
      inListFlag = !inListFlag
    }
    pushNewLine()
    onEndOfSection()
  }

  final def onNewRawSection(): Unit = logger.trace {
    onEmptyLine()
    onNewSection(None)
  }

  final def onNewRawSectionWithHeader(): Unit = logger.trace {
    onNewRawSection()
    result.current = Some(Section.Header())
    result.push()
  }

  val importantTrigger: Char = Section.Marked.Important.marker
  val infoTrigger: Char      = Section.Marked.Info.marker
  val exampleTrigger: Char   = Section.Marked.Example.marker

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
    checkForUnclosed(Elem.Formatter.Bold)
    checkForUnclosed(Elem.Formatter.Italic)
    checkForUnclosed(Elem.Formatter.Strikethrough)
  }

  def cleanupEndOfSection(): Unit = logger.trace {
    if (result.workingASTStack.nonEmpty) {
      currentSection match {
        case _: Some[Section.Marked.Type] =>
          sectionsStack +:= Some(
            Section.Marked(
              currentSectionIndent,
              currentSection.get,
              result.workingASTStack
            )
          ).orNull
        case None =>
          sectionsStack +:= Some(
            Section.Raw(
              currentSectionIndent,
              result.workingASTStack
            )
          ).orNull
      }
    }

    result.current         = None
    result.workingASTStack = Nil
    textFormattersStack    = Nil
  }

  def reverseASTStack(): Unit = logger.trace {
    result.workingASTStack = result.workingASTStack.reverse
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
    createDoc()
  }

  def createDoc(): Unit = logger.trace {
    val tags = tagsStack.length match {
      case 0 => None
      case _ => Some(Tags(List1(tagsStack.head, tagsStack.tail)))
    }

    val synopsis = sectionsStack.length match {
      case 0 => None
      case _ => Some(Synopsis(List1(sectionsStack.head)))
    }

    val body = sectionsStack.length match {
      case 0 | 1 => None
      case _ =>
        val bodyHead = sectionsStack.tail.head
        val bodyTail = sectionsStack.tail.tail
        Some(Body(List1(bodyHead, bodyTail)))
    }
    result.doc = Some(
      Doc(
        tags,
        synopsis,
        body
      )
    )
  }

  ROOT || eof || reify { onEOF() }

  ////////////////////
  ////// Header //////
  ////////////////////

  def createSectionHeader(): Unit = logger.trace {
    currentSection match {
      case Some(_) => loopThroughASTForUnmarkedSectionHeader()
      case _ =>
        result.pop()
        result.current match {
          case Some(_: Section.Header) =>
            loopThroughASTForUnmarkedSectionHeader()
          case Some(Elem.Text("")) => // Used if there is nothing but tags
          case _                   => result.push()
        }
    }
  }

  def loopThroughASTForUnmarkedSectionHeader(): Unit = logger.trace {
    var listForHeader: List[Elem] = Nil
    do {
      result.pop()
      listForHeader +:= result.current.get
    } while (result.current.get != Elem.Newline && result.workingASTStack.nonEmpty)
    if (result.current.get == Elem.Newline) {
      result.push()
      listForHeader = listForHeader.tail
    }
    result.current = Some(Section.Header(listForHeader.reverse))
    result.push()
  }

  ///////////////////
  ////// Links //////
  ///////////////////

  def createURL(name: String, url: String): Unit =
    logger.trace {
      result.current = Some(Elem.Link.URL(name, url))
      result.push()
    }

  def createImage(name: String, url: String): Unit =
    logger.trace {
      result.current = Some(Elem.Link.Image(name, url))
      result.push()
    }

  val imageNameTrigger: String  = Elem.Link.Image().marker + "["
  val urlNameTrigger: String    = Elem.Link.URL().marker + "["
  val imageLinkPattern: Pattern = imageNameTrigger >> not(')').many1 >> ')'
  val urlLinkPattern: Pattern   = urlNameTrigger >> not(')').many1 >> ')'

  ROOT || imageLinkPattern || reify {
    val in   = currentMatch.substring(2).dropRight(1).split(']')
    val name = in(0)
    val url  = in(1).substring(1)
    createImage(name, url)
  }
  ROOT || urlLinkPattern || reify {
    val in   = currentMatch.substring(1).dropRight(1).split(']')
    val name = in(0)
    val url  = in(1).substring(1)
    createURL(name, url)
  }

  //////////////////////////////////////////
  ///// Indent Management & New line ///////
  //////////////////////////////////////////

  var latestIndent: Int   = 0
  val listIndent: Int     = 2
  var inListFlag: Boolean = false

  final def onIndent(): Unit = logger.trace {
    val diff = currentMatch.length - latestIndent
    if (diff == -listIndent && inListFlag) {
      addOneListToAnother()
      latestIndent = currentMatch.length
    } else if (currentMatch.length > currentSectionIndent && result.workingASTStack.nonEmpty) {
      result.pop()
      if (!result.workingASTStack.head.isInstanceOf[Elem.Code]) {
        result.push()
        result.current = Some(Elem.Code(Nil))
      }
      result.push()
      state.begin(CODE)
    } else {
      currentSectionIndent = currentMatch.length
    }
    latestIndent = currentMatch.length
  }

  final def onIndentForListCreation(
    indent: Int,
    tp: Elem.List.Type,
    content: Elem
  ): Unit =
    logger.trace {
      val diff = indent - latestIndent
      if (diff == listIndent) {
        if (!inListFlag) {
          pushNewLine()
        }
        inListFlag = true
        addList(indent, tp, content)
      } else if (diff == 0 && inListFlag) {
        addContentToList(content)
      } else if (diff == -listIndent && inListFlag) {
        addOneListToAnother()
        addContentToList(content)
      } else {
        if (inListFlag) {
          addContentToList(
            Elem.List.Indent.Invalid(indent, tp, content)
          )
        } else {
          pushNewLine()
          result.current = Some(" " * indent + tp.marker + content.show())
          result.push()
        }
        return
      }
      latestIndent = indent
    }

  final def pushNewLine(): Unit = logger.trace {
    result.current = Some(Elem.Newline)
    result.push()
  }

  val emptyLine: Pattern     = whitespace.opt >> newline
  val indentPattern: Pattern = whitespace.opt.many

  ROOT    || newline || reify { state.begin(NEWLINE) }
  NEWLINE || (indentPattern >> eof) || reify {
    state.end(); pushNewLine(); onEOF()
  }
  NEWLINE || emptyLine || reify { onNewRawSection() }
  ROOT    || indentPattern >> importantTrigger >> indentPattern || reify {
    onNewSection(Some(Section.Marked.Important))
    currentSectionIndent += currentMatch.length
  }
  ROOT || indentPattern >> infoTrigger >> indentPattern || reify {
    onNewSection(Some(Section.Marked.Info))
    currentSectionIndent += currentMatch.length
  }
  ROOT || indentPattern >> exampleTrigger >> indentPattern || reify {
    onNewSection(Some(Section.Marked.Example))
    currentSectionIndent += currentMatch.length
  }
  NEWLINE || emptyLine >> emptyLine || reify {
    state.end()
    onNewRawSectionWithHeader()
  }
  NEWLINE || indentPattern || reify {
    state.end()
    if (result.workingASTStack.nonEmpty) {
      pushNewLine()
    }
    onIndent()
  }

  /////////////////
  ///// Lists /////
  /////////////////

  def addList(indent: Int, listType: Elem.List.Type, content: Elem): Unit =
    logger.trace {
      result.current = Some(Elem.List(indent, listType, content))
      result.push()
    }

  def addContentToList(content: Elem): Unit = logger.trace {
    result.pop()
    val currentResult  = result.current.orNull.asInstanceOf[Elem.List]
    var currentContent = currentResult.elems
    currentContent = currentContent :+ content
    result.current = Some(
      Elem.List(
        currentResult.indent,
        currentResult.tp,
        currentContent
      )
    )
    result.push()
  }

  def addOneListToAnother(): Unit = logger.trace {
    result.pop()
    val innerList = result.current.orNull
    if (result.workingASTStack.head.isInstanceOf[Elem.List]) {
      result.pop()
      val outerList    = result.current.orNull.asInstanceOf[Elem.List]
      var outerContent = outerList.elems
      outerContent = outerContent :+ innerList
      result.current = Some(
        Elem.List(
          outerList.indent,
          outerList.tp,
          outerContent
        )
      )
    }
    result.push()
  }

  val orderedListTrigger: Char   = Elem.List.Ordered.marker
  val unorderedListTrigger: Char = Elem.List.Unordered.marker

  val orderedListPattern: Pattern = indentPattern >> orderedListTrigger >> not(
      newline
    ).many1
  val unorderedListPattern
    : Pattern = indentPattern >> unorderedListTrigger >> not(
      newline
    ).many1
  NEWLINE || orderedListPattern || reify {
    state.end()
    val content = currentMatch.split(orderedListTrigger)
    onIndentForListCreation(content(0).length, Elem.List.Ordered, content(1))
  }

  NEWLINE || unorderedListPattern || reify {
    state.end()
    val content = currentMatch.split(unorderedListTrigger)
    onIndentForListCreation(content(0).length, Elem.List.Unordered, content(1))
  }
}

package org.enso.syntax.text.docsParser

import org.enso.flexer._
import org.enso.flexer.automata.Pattern
import org.enso.flexer.automata.Pattern._
import org.enso.syntax.text.ast.Doc._
import org.enso.syntax.text.ast.Doc

import scala.reflect.runtime.universe.reify

case class DocParserDef() extends Parser[AST] {

  //////////////
  /// Result ///
  //////////////

  override def getResult(): Option[AST] = result.current

  final object result {
    var current: Option[AST]       = None
    var workingASTStack: List[AST] = Nil

    def push(): Unit = logger.trace {
      if (current.isDefined) {
        logger.log(s"Pushed: $current")
        workingASTStack +:= current.get
        current = None
      } else {
        logger.log("Undefined current")
      }
    }

    def pop(): Unit = logger.trace {
      if (workingASTStack.nonEmpty) {
        current         = Some(workingASTStack.head)
        workingASTStack = workingASTStack.tail
        logger.log(s"New result: $current")
      } else {
        logger.log("Trying to pop empty AST stack")
      }
    }
  }

  //////////////////
  ///// Groups /////
  //////////////////

  val MULTILINECODE: State = state.define("Code")
  val NEWLINE: State       = state.define("Newline")

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
    // to create tags on file beginning
    if (result.workingASTStack.isEmpty && sectionsStack.isEmpty) {
      if (checkForTag(in)) {
        return
      }
    }
    // to remove unnecessary indent from first line as yet onIndent hasn't been called
    if (result.workingASTStack.isEmpty) {
      if (text.nonEmpty) {
        while (text.head == ' ' && text.length > 1) {
          text = text.tail
        }
      }
    }
    result.current = Some(AST.Text(text))
    result.push()
  }

  def pushTextLine(): Unit = logger.trace {
    var elems: List[AST] = Nil
    while (result.workingASTStack.nonEmpty && !result.workingASTStack.head
             .isInstanceOf[AST.Line] && !result.workingASTStack.head
             .isInstanceOf[AST.Code.Multiline] && !result.workingASTStack.head
             .isInstanceOf[AST.List]) {
      result.pop()
      result.current match {
        case Some(value) => elems +:= value
        case None        =>
      }
    }
    result.current = Some(AST.Line(elems))
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
        var det = details
        if (det.nonEmpty) {
          while (det.head == ' ' && det.length > 1) {
            det = det.tail
          }
          if (tagType != Tags.Tag.Unrecognized) {
            det = ' ' + det
          }
        }
        tagsStack +:= Tags.Tag(indent, tagType, Some(det))
      }
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

  ROOT || normalText || reify { pushNormalText(currentMatch) }

  //////////////////////////
  ////// Code pushing //////
  //////////////////////////

  def pushCodeLine(in: String): Unit = logger.trace {
    result.current = Some(AST.Code.Inline(in))
    result.push()
  }

  def pushMultilineCodeLine(in: String): Unit = logger.trace {
    do {
      result.pop()
    } while (result.current.get.isInstanceOf[AST.Line])
    result.current match {
      case Some(_: AST.Code.Multiline) => {
        val inMultilineCode =
          result.current.get.asInstanceOf[AST.Code.Multiline].elems
        val indent = result.current.get.asInstanceOf[AST.Code.Multiline].indent
        result.current = Some(AST.Code.Multiline(indent, inMultilineCode :+ in))
      }
      case Some(_) | None => result.push()
    }
    result.push()
  }

  val inlineCodeTrigger = '`'
  val inlineCodePattern = inlineCodeTrigger >> not(inlineCodeTrigger).many >> inlineCodeTrigger
  ROOT || inlineCodePattern || reify {
    pushCodeLine(currentMatch.substring(1).dropRight(1))
  }

  // format: off
  MULTILINECODE || newline              || reify { state.end() }
  MULTILINECODE || not(newline).many1   || reify { pushMultilineCodeLine(currentMatch) }
  MULTILINECODE || eof                  || reify { onEOF() }
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
        result.pop()
        result.current      = Some(AST.Formatter(tp, result.current))
        textFormattersStack = textFormattersStack.tail
        result.push()
      } else {
        textFormattersStack +:= tp
      }
    }

  def checkForUnclosed(tp: AST.Formatter.Type): Unit = logger.trace {
    if (textFormattersStack.nonEmpty) {
      if (textFormattersStack.head == tp) {
        result.pop()
        result.current      = Some(AST.Formatter.Unclosed(tp, result.current))
        textFormattersStack = textFormattersStack.tail
        result.push()
      }
    }
  }

  val boldTrigger: Char          = AST.Formatter.Bold.marker
  val italicTrigger: Char        = AST.Formatter.Italic.marker
  val strikethroughTrigger: Char = AST.Formatter.Strikethrough.marker

  // format: off
  ROOT || boldTrigger          || reify { pushFormatter(AST.Formatter.Bold) }
  ROOT || italicTrigger        || reify { pushFormatter(AST.Formatter.Italic) }
  ROOT || strikethroughTrigger || reify { pushFormatter(AST.Formatter.Strikethrough) }
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

  val importantTrigger: Char = Section.Marked.Important.marker
  val infoTrigger: Char      = Section.Marked.Info.marker
  val exampleTrigger: Char   = Section.Marked.Example.marker

  // format: off
  ROOT || importantTrigger || reify { onNewSection(Some(Section.Marked.Important)) }
  ROOT || infoTrigger      || reify { onNewSection(Some(Section.Marked.Info))}
  ROOT || exampleTrigger   || reify { onNewSection(Some(Section.Marked.Example))}
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
    onNewSection(None)
    textFormattersStack = Nil
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

    val _tags = tagsStack.length match {
      case 0 => None
      case _ => Some(Tags(tagsStack))
    }

    val _synopsis = sectionsStack.length match {
      case 0 => None
      case 1 => Some(Synopsis(sectionsStack))
      case _ => Some(Synopsis(sectionsStack.head))
    }

    val _body = sectionsStack.length match {
      case 0 | 1 => None
      case _     => Some(Body(sectionsStack.tail))
    }
    result.current = Some(
      Doc(
        _tags,
        _synopsis,
        _body
      )
    )
  }

  ROOT || eof || reify { onEOF() }

  ////////////////////
  ////// Header //////
  ////////////////////

  def createSectionHeader(): Unit = logger.trace {
    currentSection match {
      case Some(_) => createMarkedSectionHeader()
      case _ =>
        result.pop()
        result.current match {
          case Some(v: AST.Line) => {
            createUnmarkedSectionHeader(v.elems)
          }
          case _ =>
        }
        result.push()
    }
  }

  def createMarkedSectionHeader(): Unit = logger.trace {
    result.pop()
    result.current = Some(Section.Header(result.current.get))
    result.push()
  }

  def createUnmarkedSectionHeader(elems: List[AST]): Unit = logger.trace {
    if (elems.head == AST.Text(newline.toString)) {
      result.current = Some(Section.Header(AST.Line(elems.tail)))
    }
  }

  ///////////////////
  ////// Links //////
  ///////////////////

  def createURL(name: String, url: String): Unit =
    logger.trace {
      result.current = Some(AST.Link.URL(name, url))
      result.push()
    }

  def createImage(name: String, url: String): Unit =
    logger.trace {
      result.current = Some(AST.Link.Image(name, url))
      result.push()
    }

  val imageNameTrigger: String = AST.Link.Image().marker + "["
  val urlNameTrigger: String   = AST.Link.URL().marker + "["
  val imageLinkPattern         = imageNameTrigger >> not(')').many1 >> ')'
  val urlLinkPattern           = urlNameTrigger >> not(')').many1 >> ')'

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

  var lastOffset: Int     = 0
  var lastDiff: Int       = 0
  val codeIndent: Int     = 4
  val listIndent: Int     = 2
  var inListFlag: Boolean = false

  final def onIndent(): Unit = logger.trace {
    val diff = currentMatch.length - lastOffset
    if (diff >= codeIndent) {
      lastDiff       = codeIndent
      result.current = Some(AST.Code.Multiline(currentMatch.length, Nil))
      result.push()
      state.begin(MULTILINECODE)
    } else if (diff == 0 && lastDiff == codeIndent) {
      state.begin(MULTILINECODE)
    } else if (diff <= -codeIndent && lastDiff == codeIndent) {
      lastDiff = diff
      state.end()
    } else if (diff == -listIndent && inListFlag) {
      lastDiff = diff
      addOneListToAnother()
    } else {
      lastDiff             = diff
      currentSectionIndent = currentMatch.length
      state.end()
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
          pushTextLine()
        }
        inListFlag = true
        lastDiff   = diff
        addList(indent, tp, content)
      } else if (diff == 0 && inListFlag) {
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
        } else {
          pushNewLine()
          result.current = Some(" " * indent + tp.marker + content.show())
          result.push()
        }
        return
      }
      lastOffset = indent
    }

  final def onEmptyLine(): Unit = logger.trace {
    if (inListFlag) {
      addOneListToAnother()
      inListFlag = !inListFlag
    }
    pushTextLine()
    onEndOfSection()
    state.end()
  }

  final def pushNewLine(): Unit = logger.trace {
    pushNormalText(newline.toString)
  }

  val emptyLine: Pattern     = whitespace.opt >> newline
  val indentPattern: Pattern = whitespace.opt.many

  ROOT    || newline || reify { state.begin(NEWLINE) }
  NEWLINE || emptyLine || reify { onEmptyLine() }
  NEWLINE || (indentPattern >> eof) || reify { onEOF(); state.end() }
  NEWLINE || indentPattern || reify {
    if (result.workingASTStack == Nil) {
      currentSectionIndent = currentMatch.length
      pushNewLine()
      state.end()
    } else {
      pushTextLine()
      onIndent()
    }
  }

  /////////////////
  ///// Lists /////
  /////////////////

  def addList(indent: Int, listType: AST.List.Type, content: AST): Unit =
    logger.trace {
      result.current = Some(AST.List(indent, listType, content))
      result.push()
    }

  def addContentToList(content: AST): Unit = logger.trace {
    result.pop()
    val currentResult = result.current.orNull
    var currentContent = currentResult
      .asInstanceOf[AST.List]
      .elems
    currentContent = (content :: currentContent.reverse).reverse
    result.current = Some(
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
    result.push()
  }

  def addOneListToAnother(): Unit = logger.trace {
    result.pop()
    val innerList = result.current.orNull
    if (result.workingASTStack.head.isInstanceOf[AST.List]) {
      result.pop()
      val outerList    = result.current.orNull
      var outerContent = outerList.asInstanceOf[AST.List].elems
      outerContent = (innerList :: outerContent.reverse).reverse
      result.current = Some(
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
    result.push()
  }

  val orderedListTrigger: Char   = AST.List.Ordered.marker
  val unorderedListTrigger: Char = AST.List.Unordered.marker

  val orderedListPattern = indentPattern >> orderedListTrigger >> not(newline).many1
  val unorderedListPattern = indentPattern >> unorderedListTrigger >> not(
      newline
    ).many1
  NEWLINE || orderedListPattern || reify {
    val content = currentMatch.split(orderedListTrigger)
    onIndentForListCreation(content(0).length, AST.List.Ordered, content(1))
    state.end()
  }

  NEWLINE || unorderedListPattern || reify {
    val content = currentMatch.split(unorderedListTrigger)
    onIndentForListCreation(content(0).length, AST.List.Unordered, content(1))
    state.end()
  }
}

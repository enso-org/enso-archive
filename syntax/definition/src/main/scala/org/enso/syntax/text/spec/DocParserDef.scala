package org.enso.syntax.text.spec

import org.enso.flexer._
import org.enso.flexer.automata.Pattern
import org.enso.flexer.automata.Pattern._
import org.enso.data.List1
import org.enso.syntax.text.ast.Doc._
import org.enso.syntax.text.ast.Doc

import scala.reflect.runtime.universe.reify

case class DocParserDef() extends Parser[Doc] {

  //////////////////////////////////////////////////////////////////////////////
  ////// Basic Char Classification /////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

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

  //////////////////////////////////////////////////////////////////////////////
  ////// Result ////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  override def getResult(): Option[Doc] = result.doc

  final object result {
    var current: Option[Elem] = None
    var doc: Option[Doc]      = None
    var stack: List[Elem]     = Nil

    def push(): Unit = logger.trace {
      if (current.isDefined) {
        logger.log(s"Pushed: $current")
        stack +:= current.get
        current = None
      } else {
        logger.err("Undefined current")
      }
    }

    def pop(): Unit = logger.trace {
      if (stack.nonEmpty) {
        current = Some(stack.head)
        stack   = stack.tail
        logger.log(s"New result: $current")
      } else {
        logger.err("Trying to pop empty AST stack")
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  ////// Text //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  final object text {
    def onPushingNormalText(in: String): Unit = logger.trace {
      val isDocBeginning
        : Boolean = result.stack.isEmpty && section.stack.isEmpty
      val isSectionBeginning
        : Boolean = result.stack.isEmpty || result.stack.head
          .isInstanceOf[Section.Header]

      if (isDocBeginning) {
        if (!tags.checkIfTagExistInPushedText(in)) {
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
  }

  ROOT || normalText || reify { text.onPushingNormalText(currentMatch) }

  //////////////////////////////////////////////////////////////////////////////
  ////// Tags //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  final object tags {
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
            var det = text.removeWhitespaces(details)
            if (tagType != Tags.Tag.Unrecognized) {
              det = ' ' + det
            }
            tagsStack +:= Tags.Tag(indent, tagType, Some(det))
          } else {
            Tags.Tag(indent, tagType, None)
          }
        }
        result.current = None
      }

    def checkIfTagExistInPushedText(in: String): Boolean = logger.trace {
      val inArray     = in.split(" ")
      var containsTag = false
      for (elem <- inArray) {
        if (elem.isEmpty) {
          section.currentIndent += 1
        } else if (elem == elem.toUpperCase) {
          for (tagType <- possibleTagsList) {
            if (elem == tagType.toString.toUpperCase) {
              containsTag = true
              val tagDet = in.replaceFirst(tagType.toString.toUpperCase, "")
              pushTag(section.currentIndent, tagType, tagDet)
            }
          }
          if (!containsTag && !elem.contains(newline)) {
            pushTag(section.currentIndent, Tags.Tag.Unrecognized, in)
            containsTag = true
          }
        }
      }
      containsTag
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  ////// Code //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  final object code {
    def onPushingInlineCode(in: String): Unit = logger.trace {
      val code = in.substring(1).dropRight(1)
      result.current = Some(Elem.Code.Inline(code))
      result.push()
    }

    def onPushingCodeLine(in: String): Unit = logger.trace {
      do {
        result.pop()
      } while (result.current.get == Elem.Newline)
      result.current match {
        case Some(_: Elem.Code) =>
          val elems   = result.current.get.asInstanceOf[Elem.Code].elems
          val newElem = Elem.Code.Line(indent.latest, in)
          result.current = Some(Elem.Code(elems :+ newElem))
        case Some(_) | None => result.push()
      }
      result.push()
    }

    val inlineCodeTrigger = '`'
    val inlinePattern
      : Pattern = inlineCodeTrigger >> not(inlineCodeTrigger).many >> inlineCodeTrigger
  }

  val notN: Pattern = not(newline).many1
  val CODE: State   = state.define("Code")

  ROOT || code.inlinePattern || reify { code.onPushingInlineCode(currentMatch) }
  CODE || newline            || reify { state.end(); state.begin(NEWLINE) }
  CODE || notN               || reify { code.onPushingCodeLine(currentMatch) }
  CODE || eof                || reify { state.end(); endOfFile.onEOF() }

  //////////////////////////////////////////////////////////////////////////////
  ////// Formatter /////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  final object formatter {
    var stack: List[Elem.Formatter.Type] = Nil

    def onPushingFormatter(tp: Elem.Formatter.Type): Unit =
      logger.trace {
        val unclosedFormattersToCheck = decideWhichToCheckIfUnclosed(tp)
        if (stack.contains(tp)) {
          unclosedFormattersToCheck.foreach(checkForUnclosed)
          val listOfFormattedAST: List[Elem] = getElemsToFormatter(tp)
          result.pop()
          result.current = Some(Elem.Formatter(tp, listOfFormattedAST))
          stack          = stack.tail
          result.push()
        } else {
          addEmptyFormatterToStack(tp)
        }
      }

    def getElemsToFormatter(tp: Elem.Formatter.Type): List[Elem] =
      logger.trace {
        var listOfFormattedAST: List[Elem] = Nil
        while (result.stack.head != Elem.Formatter(tp) && result.stack.nonEmpty) {
          result.pop()
          result.current match {
            case Some(value) => listOfFormattedAST +:= value
          }
        }
        listOfFormattedAST
      }

    def addEmptyFormatterToStack(tp: Elem.Formatter.Type): Unit = logger.trace {
      stack +:= tp
      result.current = Some(Elem.Formatter(tp))
      result.push()
    }

    def decideWhichToCheckIfUnclosed(
      tp: Elem.Formatter.Type
    ): List[Elem.Formatter.Type] = logger.trace {
      tp match {
        case Elem.Formatter.Strikeout =>
          List(Elem.Formatter.Bold, Elem.Formatter.Italic)
        case Elem.Formatter.Italic =>
          List(Elem.Formatter.Bold, Elem.Formatter.Strikeout)
        case Elem.Formatter.Bold =>
          List(Elem.Formatter.Italic, Elem.Formatter.Strikeout)
      }
    }

    def checkForUnclosed(tp: Elem.Formatter.Type): Unit = logger.trace {
      if (stack.nonEmpty) {
        if (stack.head == tp) {
          val listOfFormattedAST: List[Elem] = getElemsToFormatter(tp)
          result.pop()
          result.current = Some(Elem.Formatter.Unclosed(tp, listOfFormattedAST))
          stack          = stack.tail
          result.push()
        }
      }
    }

    val boldTrigger: Char      = Elem.Formatter.Bold.marker
    val italicTrigger: Char    = Elem.Formatter.Italic.marker
    val strikeoutTrigger: Char = Elem.Formatter.Strikeout.marker
  }

  ROOT || formatter.boldTrigger || reify {
    formatter.onPushingFormatter(Elem.Formatter.Bold)
  }
  ROOT || formatter.italicTrigger || reify {
    formatter.onPushingFormatter(Elem.Formatter.Italic)
  }
  ROOT || formatter.strikeoutTrigger || reify {
    formatter.onPushingFormatter(Elem.Formatter.Strikeout)
  }

  //////////////////////////////////////////////////////////////////////////////
  ////// Header ////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  final object header {
    def createSectionHeader(): Unit = logger.trace {
      section.current match {
        case Some(_) => loopThroughASTForSectionHeader()
        case None =>
          result.pop()
          result.current match {
            case Some(_: Section.Header) => loopThroughASTForSectionHeader()
            case _                       => result.push()
          }
      }
    }

    def loopThroughASTForSectionHeader(): Unit = logger.trace {
      var listForHeader: List[Elem] = Nil
      do {
        result.pop()
        listForHeader +:= result.current.get
      } while (result.current.get != Elem.Newline && result.stack.nonEmpty)
      if (result.current.get == Elem.Newline) {
        result.push()
        listForHeader = listForHeader.tail
      }
      result.current = Some(Section.Header(listForHeader.reverse))
      result.push()
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  ////// Links /////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  final object link {
    def onCreatingURL(): Unit = logger.trace {
      val in   = currentMatch.substring(1).dropRight(1).split(']')
      val name = in(0)
      val url  = in(1).substring(1)
      pushURL(name, url)
    }

    def pushURL(name: String, url: String): Unit = logger.trace {
      result.current = Some(Elem.Link.URL(name, url))
      result.push()
    }

    def onCreatingImage(): Unit = logger.trace {
      val in   = currentMatch.substring(2).dropRight(1).split(']')
      val name = in(0)
      val url  = in(1).substring(1)
      pushImage(name, url)
    }

    def pushImage(name: String, url: String): Unit = logger.trace {
      result.current = Some(Elem.Link.Image(name, url))
      result.push()
    }

    val imageNameTrigger: String  = Elem.Link.Image().marker + "["
    val urlNameTrigger: String    = Elem.Link.URL().marker + "["
    val imageLinkPattern: Pattern = imageNameTrigger >> not(')').many1 >> ')'
    val urlLinkPattern: Pattern   = urlNameTrigger >> not(')').many1 >> ')'

  }

  ROOT || link.imageLinkPattern || reify { link.onCreatingImage() }
  ROOT || link.urlLinkPattern   || reify { link.onCreatingURL() }

  //////////////////////////////////////////////////////////////////////////////
  ////// Indent Management & New line //////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  final object indent {
    var latest: Int         = 0
    val listIndent: Int     = 2
    var inListFlag: Boolean = false

    def onIndent(): Unit = logger.trace {
      val diff = currentMatch.length - latest
      if (diff == -listIndent && inListFlag) {
        list.addInnerListToOuter()
        latest = currentMatch.length
      } else if (currentMatch.length > section.currentIndent && result.stack.nonEmpty) {
        tryToFindCodeInStack()
        state.begin(CODE)
      } else {
        section.currentIndent = currentMatch.length
      }
      latest = currentMatch.length
    }

    def tryToFindCodeInStack(): Unit = logger.trace {
      result.pop()
      if (!result.stack.head.isInstanceOf[Elem.Code]) {
        result.push()
        result.current = Some(Elem.Code(Nil))
      }
      result.push()
    }

    def onIndentForListCreation(
      indent: Int,
      tp: Elem.List.Type,
      content: Elem
    ): Unit = logger.trace {
      var wantToChangeIndent = true
      val diff               = indent - latest
      if (diff == listIndent) {
        if (!inListFlag) onPushingNewLine() // Creating first list
        inListFlag = true
        list.addList(indent, tp, content)
      } else if (diff == 0 && inListFlag) {
        list.addContentToList(content)
      } else if (diff == -listIndent && inListFlag) {
        list.addInnerListToOuter()
        list.addContentToList(content)
      } else {
        onInvalidIndent(indent, tp, content)
        wantToChangeIndent = false
      }
      if (wantToChangeIndent) latest = indent
    }

    def onInvalidIndent(
      indent: Int,
      tp: Elem.List.Type,
      content: Elem
    ): Unit = {
      if (inListFlag) {
        list.addContentToList(Elem.List.Indent.Invalid(indent, tp, content))
      } else {
        onPushingNewLine()
        if (tp == Elem.List.Ordered) {
          formatter.onPushingFormatter(Elem.Formatter.Bold)
          result.current = Some(content)
          result.push()
        } else {
          result.current = Some(" " * indent + tp.marker + content.show())
          result.push()
        }
      }
    }

    def onPushingNewLine(): Unit = logger.trace {
      result.current = Some(Elem.Newline)
      result.push()
    }

    def onEmptyLine(): Unit = logger.trace {
      if (inListFlag) {
        list.addInnerListToOuter()
        inListFlag = !inListFlag
      }
      onPushingNewLine()
      section.onEOS()
    }

    val emptyLine: Pattern     = whitespace.opt >> newline
    val indentPattern: Pattern = whitespace.opt.many
    val EOFPattern: Pattern    = indentPattern >> eof
  }

  val NEWLINE: State = state.define("Newline")

  ROOT    || newline || reify { state.begin(NEWLINE) }
  NEWLINE || indent.EOFPattern || reify {
    state.end()
    indent.onPushingNewLine()
    endOfFile.onEOF()
  }
  NEWLINE || indent.indentPattern || reify {
    state.end()
    if (result.stack.nonEmpty) {
      indent.onPushingNewLine()
    }
    indent.onIndent()
  }

  //////////////////////////////////////////////////////////////////////////////
  ////// Lists /////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  final object list {
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

    def addInnerListToOuter(): Unit = logger.trace {
      result.pop()
      val innerList = result.current.orNull
      if (result.stack.head.isInstanceOf[Elem.List]) {
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

    def onOrderedList(): Unit = logger.trace {
      state.end()
      val content = currentMatch.split(orderedListTrigger)
      indent
        .onIndentForListCreation(
          content(0).length,
          Elem.List.Ordered,
          content(1)
        )
    }
    def onUnorderedList(): Unit = logger.trace {
      state.end()
      val content = currentMatch.split(unorderedListTrigger)
      indent.onIndentForListCreation(
        content(0).length,
        Elem.List.Unordered,
        content(1)
      )
    }

    val orderedListTrigger: Char   = Elem.List.Ordered.marker
    val unorderedListTrigger: Char = Elem.List.Unordered.marker

    val orderedPattern
      : Pattern = indent.indentPattern >> orderedListTrigger >> notN
    val unorderedPattern
      : Pattern = indent.indentPattern >> unorderedListTrigger >> notN
  }

  NEWLINE || list.orderedPattern   || reify { list.onOrderedList() }
  NEWLINE || list.unorderedPattern || reify { list.onUnorderedList() }

  //////////////////////////////////////////////////////////////////////////////
  ////// Section ///////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  final object section {
    var stack: List[Section]                 = Nil
    var current: Option[Section.Marked.Type] = None
    var currentIndent: Int                   = 0

    ////// Section Beginning /////
    def onNewSection(st: Option[Section.Marked.Type]): Unit =
      logger.trace {
        result.pop()
        current = st
      }

    def onNewMarkedSection(tp: Section.Marked.Type): Unit = logger.trace {
      onNewSection(Some(tp))
      currentIndent += currentMatch.length
    }

    def onNewRawSection(): Unit = logger.trace {
      indent.onEmptyLine()
      onNewSection(None)
    }

    def onNewRawSectionWithHeader(): Unit = logger.trace {
      onNewRawSection()
      result.current = Some(Section.Header())
      result.push()
    }

    ////// End of Section //////
    def checkForUnclosedFormattersOnEOS(): Unit = logger.trace {
      formatter.checkForUnclosed(Elem.Formatter.Bold)
      formatter.checkForUnclosed(Elem.Formatter.Italic)
      formatter.checkForUnclosed(Elem.Formatter.Strikeout)
    }

    def reverseStackOnEOS(): Unit = logger.trace {
      result.stack = result.stack.reverse
    }

    def push(): Unit = logger.trace {
      result.stack match {
        case Nil =>
        case _ =>
          section.current match {
            case Some(marker) =>
              section.stack +:= Section
                .Marked(currentIndent, marker, result.stack)
            case None =>
              section.stack +:= Section.Raw(currentIndent, result.stack)
          }
      }
    }

    def cleanupOnEOS(): Unit = logger.trace {
      result.current  = None
      result.stack    = Nil
      formatter.stack = Nil
    }

    def onEOS(): Unit = logger.trace {
      checkForUnclosedFormattersOnEOS()
      reverseStackOnEOS()
      header.createSectionHeader()
      push()
      cleanupOnEOS()
    }

    val importantTrigger: Char = Section.Marked.Important.marker
    val infoTrigger: Char      = Section.Marked.Info.marker
    val exampleTrigger: Char   = Section.Marked.Example.marker

    val importantPattern
      : Pattern = indent.indentPattern >> importantTrigger >> indent.indentPattern
    val infoPattern
      : Pattern = indent.indentPattern >> infoTrigger >> indent.indentPattern
    val examplePattern
      : Pattern = indent.indentPattern >> exampleTrigger >> indent.indentPattern
  }

  NEWLINE || indent.emptyLine || reify { section.onNewRawSection() }
  NEWLINE || indent.emptyLine >> indent.emptyLine || reify {
    state.end()
    section.onNewRawSectionWithHeader()
  }
  ROOT || section.importantPattern || reify {
    section.onNewMarkedSection(Section.Marked.Important)
  }
  ROOT || section.infoPattern || reify {
    section.onNewMarkedSection(Section.Marked.Info)
  }
  ROOT || section.examplePattern || reify {
    section.onNewMarkedSection(Section.Marked.Example)
  }

  //////////////////////////////////////////////////////////////////////////////
  ////// End of File ///////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  final object endOfFile {
    def reverseSectionsStackOnEOF(): Unit = logger.trace {
      section.stack = section.stack.reverse
    }

    def reverseTagsStackOnEOF(): Unit = logger.trace {
      tags.tagsStack = tags.tagsStack.reverse
    }

    def createDoc(): Unit = logger.trace {
      val tags: Option[Tags]         = createTags()
      val synopsis: Option[Synopsis] = createSynopsis()
      val body: Option[Body]         = createBody()
      result.doc = Some(Doc(tags, synopsis, body))
    }

    def createTags(): Option[Tags] = logger.trace {
      tags.tagsStack.length match {
        case 0 => None
        case _ => Some(Tags(List1(tags.tagsStack.head, tags.tagsStack.tail)))
      }
    }

    def createSynopsis(): Option[Synopsis] = logger.trace {
      section.stack.length match {
        case 0 => None
        case _ => Some(Synopsis(List1(section.stack.head)))
      }
    }

    def createBody(): Option[Body] = logger.trace {
      section.stack.length match {
        case 0 | 1 => None
        case 2 =>
          val bodyHead = section.stack.tail.head
          Some(Body(List1(bodyHead)))
        case _ =>
          val bodyHead = section.stack.tail.head
          val bodyTail = section.stack.tail.tail
          Some(Body(List1(bodyHead, bodyTail)))
      }
    }

    def onEOF(): Unit = logger.trace {
      section.onEOS()
      reverseSectionsStackOnEOF()
      reverseTagsStackOnEOF()
      createDoc()
    }
  }

  ROOT || eof || reify { endOfFile.onEOF() }
}

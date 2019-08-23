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

  val lowerChar: Pattern = range('a', 'z')
  val upperChar: Pattern = range('A', 'Z')
  val digit: Pattern     = range('0', '9')

  val specialChars = "," | "." | ":" | "/" | "’" | "=" | "'" | "|" | "+" | "-"
  val whitespace   = ' '.many1
  val newline      = '\n'

  val possibleChars = lowerChar | upperChar | digit | whitespace | specialChars
  val normalText    = possibleChars.many1

  //////////////////////////////////////////////////////////////////////////////
  ////// Result ////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  override def getResult(): Option[Doc] = result.doc

  /** result - used to manage result from Doc Parser
    *
    * current - used to hold elem parser works on
    * doc - used to hold ready to get Doc after parsing
    * stack - used to hold stack of elems
    */
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

  /** text - used to manage normal text, made of Strings
    */
  final object text {
    def onPushing(in: String): Unit = logger.trace {
      val isDocBeginning
        : Boolean = result.stack.isEmpty && section.stack.isEmpty
      val isSectionBeginning
        : Boolean = result.stack.isEmpty || result.stack.head
          .isInstanceOf[Section.Header]

      if (isDocBeginning) {
        if (!tags.checkIfTagExistInPushedText(in)) {
          val text = removeWhitespaces(in)
          push(text)
        }
      } else if (isSectionBeginning) {
        val text = removeWhitespaces(in)
        push(text)
      } else {
        push(in)
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

    def push(in: String): Unit = logger.trace {
      result.current = Some(Elem.Text(in))
      result.push()
    }
  }

  ROOT || normalText || reify { text.onPushing(currentMatch) }

  //////////////////////////////////////////////////////////////////////////////
  ////// Tags //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  /** tags - used to manage potentially tagged documentation
    *
    * possibleTagsList - holds every correct tag possible to create
    * stack - holds applied tags
    */
  final object tags {
    val possibleTagsList: List[Tags.Tag.Type] =
      List(
        Tags.Tag.Deprecated,
        Tags.Tag.Added,
        Tags.Tag.Modified,
        Tags.Tag.Removed,
        Tags.Tag.Upcoming
      )
    var stack: List[Tags.Tag] = Nil

    def pushTag(indent: Int, tagType: Tags.Tag.Type, details: String): Unit =
      logger.trace {
        if (details.replaceAll("\\s", "").length == 0) {
          stack +:= Tags.Tag(indent, tagType)
        } else {
          if (details.nonEmpty) {
            var det = text.removeWhitespaces(details)
            if (tagType != Tags.Tag.Unrecognized) {
              det = ' ' + det
            }
            stack +:= Tags.Tag(indent, tagType, Some(det))
          } else {
            Tags.Tag(indent, tagType, None)
          }
        }
        result.current = None
      }

    def checkIfTagExistInPushedText(in: String): Boolean = logger.trace {
      val inArray     = in.split(" ")
      var containsTag = false

      def tryFindingTagInAvailableTags(elem: String): Unit = logger.trace {
        for (tagType <- possibleTagsList) {
          if (elem == tagType.toString.toUpperCase) {
            containsTag = true
            val tagDet = in.replaceFirst(tagType.toString.toUpperCase, "")
            pushTag(section.currentIndentRaw, tagType, tagDet)
          }
        }
        if (!containsTag && !elem.contains(newline)) {
          pushTag(section.currentIndentRaw, Tags.Tag.Unrecognized, in)
          containsTag = true
        }
      }

      for (elem <- inArray) {
        if (elem.isEmpty) {
          section.currentIndentRaw += 1
        } else if (elem == elem.toUpperCase) {
          tryFindingTagInAvailableTags(elem)
        }
      }
      containsTag
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  ////// Code //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  /** code - used to manage code in documentation
    */
  final object code {
    def onPushingInline(in: String): Unit = logger.trace {
      val code = in.substring(1).dropRight(1)
      result.current = Some(Elem.Code.Inline(code))
      result.push()
    }

    def onPushingMultiline(in: String): Unit = logger.trace {
      val dummyLine = Elem.Code.Line(0, "")
      do {
        result.pop()
      } while (result.current.get == Elem.Newline)
      result.current match {
        case Some(code @ (_: Elem.Code)) =>
          val newElem = Elem.Code.Line(indent.latest, in)
          if (code.elems.head == dummyLine) {
            result.current = Some(Elem.Code(newElem))
          } else {
            result.current = Some(Elem.Code(code.elems.append(newElem)))
          }
        case Some(_) | None => result.push()
      }
      result.push()
    }

    val inlineCodeTrigger = '`'
    val inlinePattern
      : Pattern = inlineCodeTrigger >> not(inlineCodeTrigger).many >> inlineCodeTrigger
  }

  val notNewLine: Pattern = not(newline).many1
  val CODE: State         = state.define("Code")

  ROOT || code.inlinePattern || reify { code.onPushingInline(currentMatch) }
  CODE || newline            || reify { state.end(); state.begin(NEWLINE) }
  CODE || notNewLine         || reify { code.onPushingMultiline(currentMatch) }
  CODE || eof                || reify { state.end(); endOfFile.onEOF() }

  //////////////////////////////////////////////////////////////////////////////
  ////// Formatter /////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  /** formatter - used to manage text formatters
    *
    * stack - holds applied formatters until they're closed
    */
  final object formatter {
    var stack: List[Elem.Formatter.Type] = Nil

    def onPushing(tp: Elem.Formatter.Type): Unit =
      logger.trace {
        val unclosedFormattersToCheck = decideWhichToCheckIfUnclosed(tp)
        if (stack.contains(tp)) {
          unclosedFormattersToCheck.foreach(checkForUnclosed)
          val listOfFormattedAST: List[Elem] = getElemsFromStack(tp)
          result.pop()
          result.current = Some(Elem.Formatter(tp, listOfFormattedAST))
          stack          = stack.tail
          result.push()
        } else {
          addEmptyToStack(tp)
        }
      }

    def getElemsFromStack(tp: Elem.Formatter.Type): List[Elem] =
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

    def addEmptyToStack(tp: Elem.Formatter.Type): Unit = logger.trace {
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
          val listOfFormattedAST: List[Elem] = getElemsFromStack(tp)
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
    formatter.onPushing(Elem.Formatter.Bold)
  }
  ROOT || formatter.italicTrigger || reify {
    formatter.onPushing(Elem.Formatter.Italic)
  }
  ROOT || formatter.strikeoutTrigger || reify {
    formatter.onPushing(Elem.Formatter.Strikeout)
  }

  //////////////////////////////////////////////////////////////////////////////
  ////// Header ////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  /** header - used to create section headers in Documentation
    */
  final object header {
    def create(): Unit = logger.trace {
      section.current match {
        case Some(_) => loopThroughStackToFindHeader()
        case None =>
          result.pop()
          result.current match {
            case Some(_: Section.Header) => loopThroughStackToFindHeader()
            case _                       => result.push()
          }
      }
    }

    def loopThroughStackToFindHeader(): Unit = logger.trace {
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

  /** link - used to create links in Documentation
    *
    * there are 2 possible link types - Image and normal URL
    */
  final object link {
    def onCreatingURL(): Unit = logger.trace {
      if (currentMatch.contains("]") && currentMatch.contains("(")) {
        val in   = currentMatch.substring(1).dropRight(1).split(']')
        val name = in(0)
        val url  = in(1).substring(1)
        pushURL(name, url)
      } else {
        onInvalidLink()
      }
    }

    def pushURL(name: String, url: String): Unit = logger.trace {
      result.current = Some(Elem.Link.URL(name, url))
      result.push()
    }

    def onCreatingImage(): Unit = logger.trace {
      if (currentMatch.contains("]") && currentMatch.contains("(")) {
        val in   = currentMatch.substring(2).dropRight(1).split(']')
        val name = in(0)
        val url  = in(1).substring(1)
        pushImage(name, url)
      } else {
        onInvalidLink()
      }
    }

    def pushImage(name: String, url: String): Unit = logger.trace {
      result.current = Some(Elem.Link.Image(name, url))
      result.push()
    }

    def onInvalidLink(): Unit = logger.trace {
      result.current = Some(Elem.Link.Invalid(currentMatch))
      result.push()
    }

    def onInvalidLinkNewline(): Unit = logger.trace {
      result.current = Some(Elem.Link.Invalid(currentMatch.dropRight(1)))
      result.push()
      indent.onPushingNewLine()
    }

    def onInvalidLinkEOF(): Unit = logger.trace {
      onInvalidLink()
      endOfFile.onEOF()
    }

    val imageNameTrigger: String = Elem.Link.Image().marker + "["
    val urlNameTrigger: String   = Elem.Link.URL().marker + "["
    val imagePattern: Pattern    = imageNameTrigger >> not(')').many1 >> ')'
    val urlPattern: Pattern      = urlNameTrigger >> not(')').many1 >> ')'
    val invalidPatternNewline
      : Pattern = (imageNameTrigger | urlNameTrigger) >> not(
        ')'
      ).many1 >> newline
    val invalidPatternEOF: Pattern = (imageNameTrigger | urlNameTrigger) >> not(
        ')'
      ).many1 >> eof
  }

  ROOT || link.imagePattern          || reify { link.onCreatingImage() }
  ROOT || link.urlPattern            || reify { link.onCreatingURL() }
  ROOT || link.invalidPatternNewline || reify { link.onInvalidLinkNewline() }
  ROOT || link.invalidPatternEOF     || reify { link.onInvalidLinkEOF() }

  //////////////////////////////////////////////////////////////////////////////
  ////// Indent Management & New line //////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  /** indent - used to manage text and block indentation
    *
    * latest - holds last found indent
    * inListFlag - used to check if currently creating list
    */
  final object indent {
    var latest: Int     = 0
    val listIndent: Int = 2

    def onIndent(): Unit = logger.trace {
      val diff = currentMatch.length - latest
      if (diff == -listIndent && list.inListFlag) {
        list.appendInnerToOuter()
        latest = currentMatch.length
      } else if (currentMatch.length > section.currentIndentRaw && result.stack.nonEmpty) {
        tryToFindCodeInStack()
        state.begin(CODE)
      } else {
        section.currentIndentRaw = currentMatch.length
      }
      latest = currentMatch.length
    }

    def tryToFindCodeInStack(): Unit = logger.trace {
      result.pop()
      if (!result.stack.head.isInstanceOf[Elem.Code]) {
        result.push()
        val dummyLine = Elem.Code.Line(0, "")
        result.current = Some(Elem.Code(dummyLine))
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
        /* NOTE
         * Used to push new line before pushing first list
         */
        if (!list.inListFlag) onPushingNewLine()
        list.inListFlag = true
        list.addNew(indent, tp, content)
      } else if (diff == 0 && list.inListFlag) {
        list.addContent(content)
      } else if (diff == -listIndent && list.inListFlag) {
        list.appendInnerToOuter()
        list.addContent(content)
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
      if (list.inListFlag) {
        list.addContent(Elem.List.Indent.Invalid(indent, tp, content))
      } else {
        onPushingNewLine()
        if (tp == Elem.List.Ordered) {
          formatter.onPushing(Elem.Formatter.Bold)
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
      if (list.inListFlag) {
        list.appendInnerToOuter()
        list.inListFlag = false
      }
      onPushingNewLine()
      section.onEOS()
    }

    def onIndentPattern(): Unit = logger.trace {
      state.end()
      if (result.stack.nonEmpty) {
        indent.onPushingNewLine()
      }
      indent.onIndent()
    }

    def onEOFPattern(): Unit = logger.trace {
      state.end()
      indent.onPushingNewLine()
      endOfFile.onEOF()
    }

    val emptyLine: Pattern     = whitespace.opt >> newline
    val indentPattern: Pattern = whitespace.opt.many
    val EOFPattern: Pattern    = indentPattern >> eof
  }

  val NEWLINE: State = state.define("Newline")

  ROOT    || newline              || reify { state.begin(NEWLINE) }
  NEWLINE || indent.EOFPattern    || reify { indent.onEOFPattern() }
  NEWLINE || indent.indentPattern || reify { indent.onIndentPattern() }

  //////////////////////////////////////////////////////////////////////////////
  ////// Lists /////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  /** list - used to create lists for documentation
    *
    * there are 2 possible types of lists - ordered and unordered
    */
  final object list {
    var inListFlag: Boolean = false

    def addNew(indent: Int, listType: Elem.List.Type, content: Elem): Unit =
      logger.trace {
        result.current = Some(Elem.List(indent, listType, content))
        result.push()
      }

    def addContent(content: Elem): Unit = logger.trace {
      result.pop()
      result.current match {
        case Some(list @ (_: Elem.List)) =>
          var currentContent = list.elems
          currentContent = currentContent.append(content)
          result.current = Some(Elem.List(list.indent, list.tp, currentContent))
      }
      result.push()
    }

    def appendInnerToOuter(): Unit = logger.trace {
      result.pop()
      val innerList = result.current.orNull
      if (result.stack.head.isInstanceOf[Elem.List]) {
        result.pop()
        val outerList    = result.current.orNull.asInstanceOf[Elem.List]
        var outerContent = outerList.elems
        outerContent = outerContent.append(innerList)
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

    def onOrdered(): Unit = logger.trace {
      state.end()
      val content = currentMatch.split(orderedListTrigger)
      indent
        .onIndentForListCreation(
          content(0).length,
          Elem.List.Ordered,
          content(1)
        )
    }
    def onUnordered(): Unit = logger.trace {
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
      : Pattern = indent.indentPattern >> orderedListTrigger >> notNewLine
    val unorderedPattern
      : Pattern = indent.indentPattern >> unorderedListTrigger >> notNewLine
  }

  NEWLINE || list.orderedPattern   || reify { list.onOrdered() }
  NEWLINE || list.unorderedPattern || reify { list.onUnordered() }

  //////////////////////////////////////////////////////////////////////////////
  ////// Section ///////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  /** section - used to manage sections in Documentation
    *
    * there are 2 possible types of sections - marked and raw.
    * there are 3 possible types of marked sections:
    *   - important
    *   - info
    *   - example
    *
    * stack - holds every section in document
    * current - holds current section type
    * currentIndentRaw - holds indent for Raw
    * indentBeforeM & indentAfterM - holds appropriate indents for Marked
    */
  final object section {
    var stack: List[Section]                 = Nil
    var current: Option[Section.Marked.Type] = None
    var currentIndentRaw: Int                = 0
    var indentBeforeM: Int                   = 0
    var indentAfterM: Int                    = 0

    ////// Section Beginning /////
    def onNew(st: Option[Section.Marked.Type]): Unit =
      logger.trace {
        result.pop()
        current = st
      }

    def onNewMarked(tp: Section.Marked.Type): Unit = logger.trace {
      createMarkedSectionIndent(tp)
      onNew(Some(tp))
      currentIndentRaw += currentMatch.length
    }

    def createMarkedSectionIndent(tp: Section.Marked.Type): Unit =
      logger.trace {
        /* NOTE
         * We are adding here '_' in front and end in case there was no
         * indent on one side or another, and then remove this added char
         * from calculation.
         * We also add currentIndentRaw as for some reason
         * it may be the left indent
         */
        val in    = "_" + currentMatch + "_"
        val inArr = in.split(tp.marker)
        indentBeforeM = currentIndentRaw + inArr.head.length - 1
        indentAfterM  = inArr.tail.head.length - 1
      }

    def onNewRaw(): Unit = logger.trace {
      indent.onEmptyLine()
      onNew(None)
    }

    def onNewRawWithHeader(): Unit = logger.trace {
      state.end()
      onNewRaw()
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
        /* NOTE
         * We don't want to push an empty section into stack
         * in case of parsing for example empty file
         * Then we want to get back Doc(None) and not Doc(Section())
         */
        case _ =>
          section.current match {
            case Some(marker) =>
              section.stack +:= Section
                .Marked(
                  indentBeforeM,
                  indentAfterM,
                  marker,
                  result.stack
                )
            case None =>
              section.stack +:= Section.Raw(currentIndentRaw, result.stack)
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
      header.create()
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

  NEWLINE || indent.emptyLine || reify { section.onNewRaw() }
  NEWLINE || indent.emptyLine >> indent.emptyLine || reify {
    section.onNewRawWithHeader()
  }
  ROOT || section.importantPattern || reify {
    section.onNewMarked(Section.Marked.Important)
  }
  ROOT || section.infoPattern || reify {
    section.onNewMarked(Section.Marked.Info)
  }
  ROOT || section.examplePattern || reify {
    section.onNewMarked(Section.Marked.Example)
  }

  //////////////////////////////////////////////////////////////////////////////
  ////// End of File ///////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  /** endOfFile - used to manage every action in case of end of file
    * prepares data to be ready to output to user
    */
  final object endOfFile {
    def reverseSectionsStackOnEOF(): Unit = logger.trace {
      section.stack = section.stack.reverse
    }

    def reverseTagsStackOnEOF(): Unit = logger.trace {
      tags.stack = tags.stack.reverse
    }

    def createDoc(): Unit = logger.trace {
      val tags: Option[Tags]         = createTags()
      val synopsis: Option[Synopsis] = createSynopsis()
      val body: Option[Body]         = createBody()
      result.doc = Some(Doc(tags, synopsis, body))
    }

    def createTags(): Option[Tags] = logger.trace {
      tags.stack match {
        case Nil     => None
        case x :: xs => Some(Tags(List1(x, xs)))
      }
    }

    def createSynopsis(): Option[Synopsis] = logger.trace {
      section.stack match {
        case Nil    => None
        case x :: _ => Some(Synopsis(x))
      }
    }

    def createBody(): Option[Body] = logger.trace {
      section.stack match {
        case Nil => None
        case _ :: xs =>
          xs match {
            case Nil     => None
            case y :: ys => Some(Body(List1(y, ys)))
          }
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

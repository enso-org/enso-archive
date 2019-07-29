package org.enso.syntax.text

import cats.data.NonEmptyList
import org.enso.syntax.text.ast.Repr

object DocAST {

  //////////////
  /// Symbol ///
  //////////////

  trait HasRepr extends Repr.Provider {
    val repr: Repr
    val htmlRepr: Repr
  }

  trait Symbol extends HasRepr {
    def span:         Int    = repr.span
    def show():       String = repr.show()
    def renderHTML(): String = htmlRepr.show()
  }

  implicit final class _OptionAST_(val self: Option[AST]) extends Symbol {
    val repr: Repr     = self.map(_.repr).getOrElse(Repr())
    val htmlRepr: Repr = self.map(_.htmlRepr).getOrElse(Repr())
  }

  ///////////
  /// AST ///
  ///////////

  trait AST        extends Symbol
  trait InvalidAST extends AST

  final case class Text(text: String) extends AST {
    val repr: Repr     = text
    val htmlRepr: Repr = text
  }

  implicit def stringToText(str: String): Text = Text(str)

  //////////////////////
  /// Text Formatter ///
  //////////////////////

  trait FormatterType {
    val showableMarker: Char
    val HTMLMarker: Char
  }

  case object Bold extends FormatterType {
    val showableMarker: Char = '*'
    val HTMLMarker: Char     = 'b'
  }
  case object Italic extends FormatterType {
    val showableMarker: Char = '_'
    val HTMLMarker: Char     = 'i'
  }
  case object Strikethrough extends FormatterType {
    val showableMarker: Char = '~'
    val HTMLMarker: Char     = 's'
  }

  final case class Formatter(formatterType: FormatterType, elem: Option[AST])
      extends AST {
    val repr
      : Repr = Repr(formatterType.showableMarker) + elem.repr + formatterType.showableMarker
    val htmlRepr
      : Repr = Repr("<") + formatterType.HTMLMarker + ">" + elem.htmlRepr + "</" + formatterType.HTMLMarker + ">"
  }
  object Formatter {
    def apply(formatterType: FormatterType): Formatter =
      Formatter(formatterType, None)
    def apply(formatterType: FormatterType, elem: AST): Formatter =
      Formatter(formatterType, Some(elem))
  }

  //////////////////////////
  /// Unclosed Formatter ///
  //////////////////////////

  final case class UnclosedFormatter(
    formatterType: FormatterType,
    elem: Option[AST]
  ) extends InvalidAST {
    val repr: Repr = Repr(formatterType.showableMarker) + elem.repr
    val htmlRepr
      : Repr = Repr("<div class=\"unclosed_") + formatterType.HTMLMarker + "\">" + elem.htmlRepr + "</div>"
  }
  object UnclosedFormatter {
    def apply(formatterType: FormatterType): UnclosedFormatter =
      UnclosedFormatter(formatterType, None)
    def apply(formatterType: FormatterType, elem: AST): UnclosedFormatter =
      UnclosedFormatter(formatterType, Option(elem))
  }

  //////////////////////
  /// Invalid Indent ///
  //////////////////////

  final case class InvalidIndent(indent: Int, elem: AST, listType: ListType)
      extends InvalidAST {
    val repr: Repr = Repr(" " * indent) + listType.readableMarker + elem.repr
    val htmlRepr
      : Repr = Repr("<div class=\"invalidIndent\">") + elem.htmlRepr + "</div>"
  }

  ///////////////////////
  ////// Code Line //////
  ///////////////////////

  final case class CodeLine(code: String, inMultilineCode: Boolean)
      extends AST {
    val repr: Repr = {
      if (inMultilineCode) {
        code
      } else {
        Repr("`") + code + "`"
      }
    }
    val htmlRepr: Repr = Repr("<code>") + code + "</code>"
  }

  ////////////////////
  ////// Header //////
  ////////////////////

  final case class Header(elem: AST) extends AST {
    val repr: Repr = elem.repr
    val htmlRepr
      : Repr = Repr("<div class=\"") + this.getClass.getSimpleName + "\">" + elem.htmlRepr + "</div>"
  }

  ///////////////////
  ////// Links //////
  ///////////////////

  trait LinkType {
    val readableMarker: String
  }
  final case object URL extends LinkType {
    val readableMarker = "["
  }
  final case object Image extends LinkType {
    val readableMarker = "!["
  }

  final case class Link(name: String, url: String, linkType: LinkType)
      extends AST {
    val repr: Repr = Repr() + linkType.readableMarker + name + "](" + url + ")"
    val htmlRepr: Repr = linkType match {
      case URL =>
        Repr("<a href=\"") + url + "\">" + name + "</a>"
      case Image =>
        Repr("<img src=\"") + url + "\" alt=\"" + name + "\">"
    }
  }

  ///////////////////
  ////// Lists //////
  ///////////////////

  trait ListType {
    val readableMarker: Char
    val HTMLMarker: String
  }
  final case object Unordered extends ListType {
    val readableMarker = '-'
    val HTMLMarker     = "ul"
  }
  final case object Ordered extends ListType {
    val readableMarker = '*'
    val HTMLMarker     = "ol"
  }

  final case class ListBlock(
    indent: Int,
    listType: ListType,
    elems: NonEmptyList[AST]
  ) extends AST {
    val repr: Repr = {
      var _repr = Repr()
      elems.toList.foreach(elem => {
        if (elem.show().contains(" " * (indent + 2))) {
          _repr += '\n'
          _repr += elem.repr
        } else if (elem.isInstanceOf[InvalidIndent]) {
          _repr += '\n'
          _repr += elem.repr
        } else {
          if (elems.head != elem) {
            _repr += '\n'
          }
          _repr += " " * indent
          _repr += listType.readableMarker
          _repr += elem.repr
        }
      })
      _repr
    }
    val htmlRepr: Repr = {
      var _repr = Repr("<") + listType.HTMLMarker + ">"
      elems.toList.foreach(elem => {
        if (elem.isInstanceOf[ListBlock]) {
          _repr += elem.htmlRepr
        } else {
          _repr += "<li>"
          _repr += elem.htmlRepr
          _repr += "</li>"
        }
      })
      _repr += "</"
      _repr += listType.HTMLMarker
      _repr += ">"
      _repr
    }

  }
  object ListBlock {
    def apply(indent: Int, listType: ListType, elem: AST): ListBlock =
      ListBlock(indent, listType, NonEmptyList(elem, Nil))
    def apply(indent: Int, listType: ListType, elems: AST*): ListBlock =
      ListBlock(indent, listType, NonEmptyList(elems.head, elems.tail.to[List]))
  }

  //////////////////////
  ////// Sections //////
  //////////////////////

  abstract class Section extends Symbol {
    val indent: Int
    val elems: List[AST]
    def readableMarker: Option[Char] = None

    val repr: Repr = {
      var _repr = Repr()
      if (indent >= 2) {
        _repr += " " * (indent - 2) + readableMarker
          .map(_.toString)
          .getOrElse("") + " "
      } else if (indent == 1) {
        _repr += readableMarker
          .map(_.toString)
          .getOrElse(" ")
      } else {
        _repr += readableMarker
          .map(_.toString)
          .getOrElse("")
      }
      for (i <- elems.indices) {
        if (elems(i).isInstanceOf[Header]) {
          _repr += "\n" + " " * indent
        }
        if (i >= 1) {
          if (elems(i - 1) == Text("\n")) {
            if (!elems(i).isInstanceOf[ListBlock]) {
              _repr += " " * indent
            }
          }
        }
        _repr += elems(i).repr
      }
      _repr
    }
    val htmlRepr: Repr = {
      var _repr = Repr("<div class=\"") + this.getClass.getSimpleName + "\">"
      elems.foreach(elem => _repr += elem.htmlRepr)
      _repr += "</div>"
      _repr
    }
  }

  ///// Important /////
  final case class Important(indent: Int, elems: List[AST]) extends Section {
    override def readableMarker = Some('!')
  }
  object Important {
    def apply():            Important = Important(0, Nil)
    def apply(indent: Int): Important = Important(indent, Nil)
    def apply(indent: Int, elem: AST): Important =
      Important(indent, elem :: Nil)
    def apply(indent: Int, elems: AST*): Important =
      Important(indent, elems.to[List])
  }

  ///// Info /////
  final case class Info(indent: Int, elems: List[AST]) extends Section {
    override def readableMarker = Some('?')
  }
  object Info {
    def apply():            Info = Info(0, Nil)
    def apply(indent: Int): Info = Info(indent, Nil)
    def apply(indent: Int, elem: AST): Info =
      Info(indent, elem :: Nil)
    def apply(indent: Int, elems: AST*): Info =
      Info(indent, elems.to[List])
  }

  ///// Example /////
  final case class Example(indent: Int, elems: List[AST]) extends Section {
    override def readableMarker = Some('>')
  }
  object Example {
    def apply():            Example = Example(0, Nil)
    def apply(indent: Int): Example = Example(indent, Nil)
    def apply(indent: Int, elem: AST): Example =
      Example(indent, elem :: Nil)
    def apply(indent: Int, elems: AST*): Example =
      Example(indent, elems.to[List])
  }

  ///// Multiline Code /////
  final case class MultilineCode(indent: Int, elems: List[AST])
      extends Section {
    override def readableMarker = Some(' ')

    override val htmlRepr: Repr = {
      var _repr = Repr("<div class=\"") + this.getClass.getSimpleName + "\" style=\"margin-left:" + (10 * indent).toString + "px\" >"
      elems.foreach({ elem =>
        val r = elem.show() match {
          case "\n" => Repr("<br />")
          case _    => elem.htmlRepr
        }
        _repr += r
      })
      _repr += "</div>"
      _repr
    }
  }
  object MultilineCode {
    def apply():            MultilineCode = MultilineCode(0, Nil)
    def apply(indent: Int): MultilineCode = MultilineCode(indent, Nil)
    def apply(indent: Int, elem: AST): MultilineCode =
      MultilineCode(indent, elem :: Nil)
    def apply(indent: Int, elems: AST*): MultilineCode =
      MultilineCode(indent, elems.to[List])
  }

  ///// Text Block /////
  final case class TextBlock(indent: Int, elems: List[AST]) extends Section
  object TextBlock {
    def apply():            TextBlock = TextBlock(0, Nil)
    def apply(indent: Int): TextBlock = TextBlock(indent, Nil)
    def apply(indent: Int, elem: AST): TextBlock =
      TextBlock(indent, elem :: Nil)
    def apply(indent: Int, elems: AST*): TextBlock =
      TextBlock(indent, elems.to[List])
  }

  //////////////////
  ////// Body //////
  //////////////////

  final case class Body(elems: List[Section]) extends AST {
    val repr: Repr = {
      var _repr = Repr()
      elems.foreach(elem => {
        _repr += elem.repr
        if (elems.last != elem) {
          _repr += "\n"
        }
      })
      _repr
    }
    val htmlRepr: Repr = {
      var _repr = Repr("<div class=\"") + this.getClass.getSimpleName + "\">"
      elems.foreach(elem => _repr += elem.htmlRepr)
      _repr += "</div>"
      _repr
    }
    def exists(): Boolean = Body(elems) != Body()
  }

  object Body {
    def apply():                Body = Body(Nil)
    def apply(elem: Section):   Body = Body(elem :: Nil)
    def apply(elems: Section*): Body = Body(elems.to[List])
  }

  //////////////////////
  ////// Synopsis //////
  //////////////////////

  final case class Synopsis(elems: List[Section]) extends AST {
    val repr: Repr = {
      var _repr = Repr()
      elems.foreach(elem => {
        _repr += elem.repr
        if (elems.last != elem) {
          _repr += "\n"
        }
      })
      _repr
    }
    val htmlRepr: Repr = {
      var _repr = Repr("<div class=\"") + this.getClass.getSimpleName + "\">"
      elems.foreach(elem => _repr += elem.htmlRepr)
      _repr += "</div>"
      _repr
    }
    def exists(): Boolean = Synopsis(elems) != Synopsis()
  }
  object Synopsis {
    def apply():                Synopsis = Synopsis(Nil)
    def apply(elem: Section):   Synopsis = Synopsis(elem :: Nil)
    def apply(elems: Section*): Synopsis = Synopsis(elems.to[List])
  }

  ////////////
  /// Tags ///
  ////////////

  trait TagType extends AST {
    val version: Option[String]
    val name: String = this.getClass.getSimpleName.toUpperCase
    val repr: Repr = {
      if (version.repr == Repr()) {
        Repr(name)
      } else {
        Repr(name) + ' ' + version.repr
      }
    }
    val htmlRepr
      : Repr = Repr("<div class=\"") + name + "\">" + name + version.htmlRepr + "</div>"
  }

  case class Deprecated(version: Option[String]) extends TagType
  object Deprecated {
    def apply():                Deprecated = Deprecated(None)
    def apply(version: String): Deprecated = Deprecated(Option(version))
  }
  case class Added(version: Option[String]) extends TagType
  object Added {
    def apply():                Added = Added(None)
    def apply(version: String): Added = Added(Option(version))
  }
  case class Removed(version: Option[String]) extends TagType
  object Removed {
    def apply():                Removed = Removed(None)
    def apply(version: String): Removed = Removed(Option(version))
  }
  case class Modified(version: Option[String]) extends TagType
  object Modified {
    def apply():                Modified = Modified(None)
    def apply(version: String): Modified = Modified(Option(version))
  }
  case class Upcoming(version: Option[String]) extends TagType
  object Upcoming {
    def apply():                Upcoming = Upcoming(None)
    def apply(version: String): Upcoming = Upcoming(Option(version))
  }

  final case class Tags(indent: Int, elems: List[TagType]) extends AST {
    val repr: Repr = {
      var _repr = Repr()
      elems.foreach(elem => {
        _repr += " " * indent
        _repr += elem.repr
        if (elems.last != elem) {
          _repr += "\n"
        }
      })
      _repr
    }
    val htmlRepr: Repr = {
      var _repr = Repr("<div class=\"") + this.getClass.getSimpleName + "\">"
      elems.foreach(elem => _repr += elem.htmlRepr)
      _repr += "</div>"
      _repr
    }
    def exists(): Boolean = Tags(indent, elems) != Tags(indent)
  }
  object Tags {
    def apply():                             Tags = Tags(0, Nil)
    def apply(indent: Int):                  Tags = Tags(indent, Nil)
    def apply(elem: TagType):                Tags = Tags(0, elem :: Nil)
    def apply(indent: Int, elem: TagType):   Tags = Tags(indent, elem :: Nil)
    def apply(elems: TagType*):              Tags = Tags(0, elems.to[List])
    def apply(indent: Int, elems: TagType*): Tags = Tags(indent, elems.to[List])
  }

  implicit final class _OptionTagType_(val self: Option[String]) extends AST {
    val repr: Repr = self.map(Repr(_)).getOrElse(Repr())
    val htmlRepr: Repr =
      self
        .map(Repr() + "<div class=\"Version\"> " + Repr(_) + "</div>")
        .getOrElse(Repr())
  }

  ///////////////////////////
  ////// Documentation //////
  ///////////////////////////

  final case class Documentation(tags: Tags, synopsis: Synopsis, body: Body)
      extends AST {
    val repr: Repr = {
      var _repr = Repr()
      if (tags.exists()) {
        _repr += tags.repr
        if (synopsis.exists() || body.exists()) {
          _repr += "\n"
        }
      }

      if (synopsis.exists()) {
        _repr += synopsis.repr
      }

      if (body.exists()) {
        _repr += "\n"
        _repr += body.repr
      }
      _repr
    }
    val htmlRepr: Repr = {
      var _repr = Repr("<div class=\"") + this.getClass.getSimpleName + "\">"

      if (tags.exists()) {
        _repr += tags.htmlRepr
      }
      if (synopsis.exists()) {
        _repr += synopsis.htmlRepr
      }
      if (body.exists()) {
        _repr += body.htmlRepr
      }

      _repr += "</div>"

      _repr
    }
  }

  object Documentation {
    def apply(tags: Tags, synopsis: Synopsis, body: Body): Documentation =
      new Documentation(tags, synopsis, body)
    def apply(synopsis: Synopsis, body: Body): Documentation =
      new Documentation(Tags(), synopsis, body)
    def apply(synopsis: Synopsis): Documentation =
      new Documentation(Tags(), synopsis, Body(Nil))
    def apply(tags: Tags): Documentation =
      new Documentation(tags, Synopsis(Nil), Body(Nil))
    def apply(tags: Tags, synopsis: Synopsis): Documentation =
      new Documentation(tags, synopsis, Body(Nil))
  }
}

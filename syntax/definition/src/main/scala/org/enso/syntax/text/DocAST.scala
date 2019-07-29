package org.enso.syntax.text

import cats.data.NonEmptyList
import org.enso.data.List1
import org.enso.syntax.text.ast.Repr

object DocAST {

  //////////////
  /// Symbol ///
  //////////////

  trait Provider extends Repr.Provider {
    val htmlRepr: Repr
  }

  trait Symbol extends AST.Symbol {
    val htmlRepr: Repr
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
    val marker: Char
    val htmlMarker: Char
  }

  abstract class FormatterClass(val marker: Char, val htmlMarker: Char) extends FormatterType
  case object Bold extends FormatterClass('*','b')
  case object Italic extends FormatterClass('_','i')
  case object Strikethrough extends FormatterClass('~','s')

  final case class Formatter(tp: FormatterType, elem: Option[AST])
    extends AST {
    val repr
    : Repr = Repr(tp.marker) + elem.repr + tp.marker
    val htmlRepr
    : Repr = Repr("<") + tp.htmlMarker + ">" + elem.htmlRepr + "</" + tp.htmlMarker + ">"
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

  final case class UnclosedFormatter(tp: FormatterType,elem: Option[AST]) extends InvalidAST {
    val repr: Repr = Repr(tp.marker) + elem.repr
    val htmlRepr
    : Repr = Repr("<div class=\"unclosed_") + tp.htmlMarker + "\">" + elem.htmlRepr + "</div>"
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
    val repr: Repr = Repr(" " * indent) + listType.marker + elem.repr
    val htmlRepr
    : Repr = Repr("<div class=\"invalidIndent\">") + elem.htmlRepr + "</div>"
  }

  ///////////////////////
  ////// Code Line //////
  ///////////////////////

  final case class CodeLine(code: String)
    extends AST {
    val repr: Repr = Repr("`") + code + "`"
    val htmlRepr: Repr = Repr("<code>") + code + "</code>"
  }

  final case class MultilineCodeLine(code: String)
    extends AST {
    val repr: Repr = code
    val htmlRepr: Repr = Repr("<code>") + code + "</code>"
  }

  ///////////////////
  ////// Links //////
  ///////////////////

  trait LinkType {
    val marker: String
  }
  final case object URL extends LinkType {
    val marker = "["
  }
  final case object Image extends LinkType {
    val marker = "!["
  }

  final case class Link(name: String, url: String, linkType: LinkType)
    extends AST {
    val repr: Repr = Repr() + linkType.marker + name + "](" + url + ")"
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
    val marker: Char
    val HTMLMarker: String
  }
  final case object Unordered extends ListType {
    val marker = '-'
    val HTMLMarker     = "ul"
  }
  final case object Ordered extends ListType {
    val marker = '*'
    val HTMLMarker     = "ol"
  }

  final case class ListBlock(indent: Int, listType: ListType, elems: List1[AST]) extends AST {
    val repr: Repr = {
      var _repr = Repr()
      elems.toList.foreach {
        case elem@(t: InvalidAST) =>
          _repr += '\n'
          _repr += elem.repr
        case elem@(t: ListBlock) =>
          _repr += '\n'
          _repr += elem.repr
        case elem =>
          if (elems.head != elem) {
            _repr += '\n'
          }
          _repr += " " * indent
          _repr += listType.marker
          _repr += elem.repr
      }
      _repr
    }

    val htmlRepr: Repr = {
      var _repr = Repr("<") + listType.HTMLMarker + ">"
      elems.toList.foreach {
        case elem@(t: ListBlock) =>
          _repr += elem.htmlRepr
        case elem =>
          _repr += "<li>"
          _repr += elem.htmlRepr
          _repr += "</li>"
      }
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

  ////////////////////
  ////// Header //////
  ////////////////////

  final case class Header(elem: AST) extends AST {
    val repr: Repr = elem.repr
    val htmlRepr
    : Repr = Repr("<div class=\"") + this.getClass.getSimpleName + "\">" + elem.htmlRepr + "</div>"
  }

  //////////////////////
  ////// Sections //////
  //////////////////////

  abstract class Section extends Symbol {
    val indent: Int
    val elems: List[AST]
    def marker: Option[Char] = None

    val repr: Repr = {
      var _repr = Repr()

      _repr += (indent match {
        case 0 => marker.map(_.toString).getOrElse("")
        case 1 => marker.map(_.toString).getOrElse(" ")
        case _ =>  " " * (indent - 2) + marker.map(_.toString).getOrElse("") + " "
      })

      for (i <- elems.indices) {
        val elem = elems(i)
        elem match {
          case (t: Header) =>
            _repr += "\n" + " " * indent
            _repr += elem.repr
          case (t: ListBlock) =>
            _repr += elem.repr
          case _ =>
            if (i >= 1 && elems(i - 1) == Text("\n")) {
              _repr += " " * indent
            }
            _repr += elem.repr
        }
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
    override def marker = Some('!')
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
    override def marker = Some('?')
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
    override def marker = Some('>')
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
    override def marker = Some(' ')

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

  trait TagType
  case object Deprecated extends TagType
  case object Added extends TagType
  case object Removed extends TagType
  case object Modified extends TagType
  case object Upcoming extends TagType

  final case class TagClass(tp: TagType, version: Option[String]) extends AST {
    val name: String = tp.getClass.getSimpleName.toUpperCase.dropRight(1)
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
  object TagClass {
    def apply(tp: TagType): TagClass = TagClass(tp, None)
    def apply(tp: TagType, version: String): TagClass = TagClass(tp, Option(version))
  }

  final case class Tags(indent: Int, elems: List[TagClass]) extends AST {
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
    def apply(elem: TagClass):                Tags = Tags(0, elem :: Nil)
    def apply(indent: Int, elem: TagClass):   Tags = Tags(indent, elem :: Nil)
    def apply(elems: TagClass*):              Tags = Tags(0, elems.to[List])
    def apply(indent: Int, elems: TagClass*): Tags = Tags(indent, elems.to[List])
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

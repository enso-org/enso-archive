package org.enso.syntax.text

import cats.data.NonEmptyList
import org.enso.data.List1
import org.enso.syntax.text.ast.Repr
import scalatags.Text.all._

object DocAST {

  //////////////
  /// Symbol ///
  //////////////

  trait Provider extends Repr.Provider {
    val html: Repr
  }

  trait Symbol extends AST.Symbol with Provider {
    def renderHTML(): String = html.show()
  }

  implicit final class _OptionAST_(val self: Option[AST]) extends Symbol {
    val repr: Repr = self.map(_.repr).getOrElse(Repr())
    val html: Repr = self.map(_.html).getOrElse(Repr())
  }

  ///////////
  /// AST ///
  ///////////

  trait AST        extends Symbol
  trait InvalidAST extends AST

  final case class Text(text: String) extends AST {
    val repr: Repr = text
    val html: Repr = text
  }

  implicit def stringToText(str: String): Text = Text(str)

  //////////////////////
  /// Text Formatter ///
  //////////////////////

  trait FormatterType {
    val marker: Char
    val htmlMarker: Char
  }

  abstract class FormatterClass(val marker: Char, val htmlMarker: Char)
      extends FormatterType
  case object Bold          extends FormatterClass('*', 'b')
  case object Italic        extends FormatterClass('_', 'i')
  case object Strikethrough extends FormatterClass('~', 's')

  final case class Formatter(tp: FormatterType, elem: Option[AST]) extends AST {
    val repr: Repr = Repr(tp.marker) + elem.repr + tp.marker
    val html
      : Repr = Repr("<") + tp.htmlMarker + ">" + elem.html + "</" + tp.htmlMarker + ">"
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

  final case class UnclosedFormatter(tp: FormatterType, elem: Option[AST])
      extends InvalidAST {
    val repr: Repr = Repr(tp.marker) + elem.repr
    val html
      : Repr = Repr("<div class=\"unclosed_") + tp.htmlMarker + "\">" + elem.html + "</div>"
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
    val html
      : Repr = Repr("<div class=\"invalidIndent\">") + elem.html + "</div>"
  }

  ///////////////////////
  ////// Code Line //////
  ///////////////////////

  final case class CodeLine(code: String) extends AST {
    val repr: Repr = Repr("`") + code + "`"
    val html: Repr = Repr("<code>") + code + "</code>"
  }

  final case class MultilineCodeLine(code: String) extends AST {
    val repr: Repr = code
    val html: Repr = Repr("<code>") + code + "</code><br />"
  }

  ///////////////////
  ////// Links //////
  ///////////////////

  abstract class Link(name: String, url: String, marker: String) extends AST {
    val repr: Repr = Repr() + marker + name + "](" + url + ")"
    val html: Repr = this match {
      case (t: URL)   => Repr("<a href=\"") + url + "\">" + name + "</a>"
      case (t: Image) => Repr("<img src=\"") + url + "\" alt=\"" + name + "\">"
    }
  }

  final case class URL(name: String, url: String) extends Link(name, url, "[") {
    val marker = "["
  }
  object URL {
    def apply():                          URL = new URL("", "")
    def apply(name: String, url: String): URL = new URL(name, url)
  }

  final case class Image(name: String, url: String)
      extends Link(name, url, "![") {
    val marker = "!["
  }
  object Image {
    def apply():                          Image = new Image("", "")
    def apply(name: String, url: String): Image = new Image(name, url)
  }

  ///////////////////
  ////// Lists //////
  ///////////////////

  trait ListType {
    val marker: Char
    val HTMLMarker: String
  }
  final case object Unordered extends ListType {
    val marker     = '-'
    val HTMLMarker = "ul"
  }
  final case object Ordered extends ListType {
    val marker     = '*'
    val HTMLMarker = "ol"
  }

  final case class ListBlock(indent: Int, listType: ListType, elems: List1[AST])
      extends AST {
    val repr: Repr = {
      var _repr = Repr()
      elems.toList.foreach {
        case elem @ (t: InvalidAST) =>
          _repr += '\n'
          _repr += elem.repr
        case elem @ (t: ListBlock) =>
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

    val html: Repr = {
      var _repr = Repr("<") + listType.HTMLMarker + ">"
      elems.toList.foreach {
        case elem @ (t: ListBlock) =>
          _repr += elem.html
        case elem =>
          _repr += "<li>"
          _repr += elem.html
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
    val html
      : Repr = Repr("<div class=\"") + this.getClass.getSimpleName + "\">" + elem.html + "</div>"
  }

  //////////////////////
  ////// Sections //////
  //////////////////////

  case class Section(indent: Int, st: SectionType, elems: List[AST])
      extends Symbol {
    val marker  = st.marker.map(_.toString).getOrElse("")
    val newline = Text("\n")

    val repr: Repr = {
      var _repr = Repr()

      _repr += (indent match {
        case 0 => marker
        case 1 =>
          if (marker.isEmpty) {
            " "
          } else {
            marker
          }
        case _ => " " * (indent - 2) + marker + " "
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
            if (i >= 1 && elems(i - 1) == newline) {
              _repr += " " * indent
            }
            _repr += elem.repr
        }
      }
      _repr
    }
    val html: Repr = {
      var _repr = Repr("<div class=\"") + st.getClass.getSimpleName
          .dropRight(1) + "\">"
      elems.foreach(elem => _repr += elem.html)
      _repr += "</div>"
      _repr
    }
  }

  object Section {
    def apply(indent: Int, sectionType: SectionType, elem: AST): Section =
      Section(indent, sectionType, elem :: Nil)
    def apply(indent: Int, sectionType: SectionType, elems: AST*): Section =
      Section(indent, sectionType, elems.to[List])
  }

  trait SectionType {
    val marker: Option[Char]
  }
  case object Important extends SectionType {
    val marker: Option[Char] = Some('!')
  }
  case object Info extends SectionType {
    val marker: Option[Char] = Some('?')
  }
  case object Example extends SectionType {
    val marker: Option[Char] = Some('>')
  }
  case object MultilineCode extends SectionType {
    val marker: Option[Char] = Some(' ')
  }
  case object TextBlock extends SectionType {
    val marker: Option[Char] = None
  }

  /////////////////////
  ////// Details //////
  /////////////////////

  final case class Details(elems: List[Section]) extends AST {
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
    val html: Repr = {
      var _repr = Repr("<div class=\"") + this.getClass.getSimpleName + "\">"
      elems.foreach(elem => _repr += elem.html)
      _repr += "</div>"
      _repr
    }
    def exists(): Boolean = Details(elems) != Details()
  }

  object Details {
    def apply():                Details = Details(Nil)
    def apply(elem: Section):   Details = Details(elem :: Nil)
    def apply(elems: Section*): Details = Details(elems.to[List])
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
    val html: Repr = {
      var _repr = Repr("<div class=\"") + this.getClass.getSimpleName + "\">"
      elems.foreach(elem => _repr += elem.html)
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
  case object Added      extends TagType
  case object Removed    extends TagType
  case object Modified   extends TagType
  case object Upcoming   extends TagType

  final case class TagClass(tp: TagType, version: Option[String]) extends AST {
    val name: String = tp.getClass.getSimpleName.toUpperCase.dropRight(1)
    val repr: Repr = {
      if (version.repr == Repr()) {
        Repr(name)
      } else {
        Repr(name) + ' ' + version.repr
      }
    }
    val html
      : Repr = Repr("<div class=\"") + name + "\">" + name + version.html + "</div>"
  }
  object TagClass {
    def apply(tp: TagType): TagClass = TagClass(tp, None)
    def apply(tp: TagType, version: String): TagClass =
      TagClass(tp, Option(version))
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
    val html: Repr = {
      var _repr = Repr("<div class=\"") + this.getClass.getSimpleName + "\">"
      elems.foreach(elem => _repr += elem.html)
      _repr += "</div>"
      _repr
    }
    def exists(): Boolean = Tags(indent, elems) != Tags(indent)
  }
  object Tags {
    def apply():                            Tags = Tags(0, Nil)
    def apply(indent: Int):                 Tags = Tags(indent, Nil)
    def apply(elem: TagClass):              Tags = Tags(0, elem :: Nil)
    def apply(indent: Int, elem: TagClass): Tags = Tags(indent, elem :: Nil)
    def apply(elems: TagClass*):            Tags = Tags(0, elems.to[List])
    def apply(indent: Int, elems: TagClass*): Tags =
      Tags(indent, elems.to[List])
  }

  implicit final class _OptionTagType_(val self: Option[String]) extends AST {
    val repr: Repr = self.map(Repr(_)).getOrElse(Repr())
    val html: Repr =
      self
        .map(Repr() + "<div class=\"Version\"> " + Repr(_) + "</div>")
        .getOrElse(Repr())
  }

  ///////////////////////////
  ////// Documentation //////
  ///////////////////////////

  final case class Documentation(tags: Tags, synopsis: Synopsis, body: Details)
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
    val html: Repr = {
      var _repr = Repr("<div class=\"") + this.getClass.getSimpleName + "\">"

      if (tags.exists()) {
        _repr += tags.html
      }
      if (synopsis.exists()) {
        _repr += synopsis.html
      }
      if (body.exists()) {
        _repr += body.html
      }

      _repr += "</div>"

      _repr
    }
  }

  object Documentation {
    def apply(tags: Tags, synopsis: Synopsis, body: Details): Documentation =
      new Documentation(tags, synopsis, body)
    def apply(synopsis: Synopsis, body: Details): Documentation =
      new Documentation(Tags(), synopsis, body)
    def apply(synopsis: Synopsis): Documentation =
      new Documentation(Tags(), synopsis, Details(Nil))
    def apply(tags: Tags): Documentation =
      new Documentation(tags, Synopsis(Nil), Details(Nil))
    def apply(tags: Tags, synopsis: Synopsis): Documentation =
      new Documentation(tags, synopsis, Details(Nil))
  }
}

package org.enso.syntax.text

import cats.data.NonEmptyList
import org.enso.data.List1
import org.enso.syntax.text.ast.Repr
import scalatags.Text._
import scalatags.Text.all._

object DocAST {

  //////////////
  /// Symbol ///
  //////////////

  trait Provider {
    val html: Seq[Modifier]
  }

  trait Symbol extends AST.Symbol with Provider {
    def renderHTML(): TypedTag[String] =
      scalatags.Text.all.html(
        head(
          meta(httpEquiv := "Content-Type")(content := "text/html")(
            charset := "UTF-8"
          ),
          link(rel := "stylesheet")(href := "style.css")
        ),
        body(html)
      )
  }

  implicit final class _OptionAST_(val self: Option[AST]) extends Symbol {
    val repr: Repr          = self.map(_.repr).getOrElse(Repr())
    val html: Seq[Modifier] = self.map(_.html).getOrElse("".html)
  }

  ///////////
  /// AST ///
  ///////////

  trait AST        extends Symbol
  trait InvalidAST extends AST

  final case class Text(text: String) extends AST {
    val repr: Repr          = text
    val html: Seq[Modifier] = Seq(text)
  }

  implicit def stringToText(str: String): Text = Text(str)

  //////////////////////
  /// Text Formatter ///
  //////////////////////

  trait FormatterType {
    val marker: Char
    val htmlMarker: ConcreteHtmlTag[String]
  }

  abstract class FormatterClass(
    val marker: Char,
    val htmlMarker: ConcreteHtmlTag[String]
  ) extends FormatterType
  case object Bold          extends FormatterClass('*', b)
  case object Italic        extends FormatterClass('_', i)
  case object Strikethrough extends FormatterClass('~', s)

  final case class Formatter(tp: FormatterType, elem: Option[AST]) extends AST {
    val repr: Repr          = Repr(tp.marker) + elem.repr + tp.marker
    val html: Seq[Modifier] = Seq(tp.htmlMarker(elem.html))
  }
  object Formatter {
    def apply(formatterType: FormatterType): Formatter =
      Formatter(formatterType, None)
    def apply(formatterType: FormatterType, elem: AST): Formatter =
      Formatter(formatterType, Some(elem))

    final case class Unclosed(tp: FormatterType, elem: Option[AST])
        extends InvalidAST {
      val repr: Repr = Repr(tp.marker) + elem.repr
      val html: Seq[Modifier] =
        Seq(
          div(`class` := s"unclosed_${tp.htmlMarker.tag}")(
            elem.html
          )
        )
    }
    object Unclosed {
      def apply(formatterType: FormatterType): Unclosed =
        Unclosed(formatterType, None)
      def apply(formatterType: FormatterType, elem: AST): Unclosed =
        Unclosed(formatterType, Option(elem))
    }
  }

  //////////////////////
  /// Invalid Indent ///
  //////////////////////

  final case class InvalidIndent(indent: Int, elem: AST, listType: ListType)
      extends InvalidAST {
    val repr: Repr = Repr(" " * indent) + listType.marker + elem.repr
    val html: Seq[Modifier] =
      Seq(div(`class` := "InvalidIndent")(elem.html))
  }

  ///////////////////////
  ////// Code Line //////
  ///////////////////////

  final case class CodeLine(str: String) extends AST {
    val repr: Repr          = Repr("`") + str + "`"
    val html: Seq[Modifier] = Seq(code(str))
  }

  final case class MultilineCodeLine(str: String) extends AST {
    val repr: Repr          = str
    val html: Seq[Modifier] = Seq(code(str), br)
  }

  ///////////////////
  ////// Links //////
  ///////////////////

  abstract class Link(name: String, url: String, marker: String) extends AST {
    val repr: Repr = Repr() + marker + name + "](" + url + ")"
    val html: Seq[Modifier] = this match {
      case (t: Link.URL)   => Seq(a(href := url)(name))
      case (t: Link.Image) => Seq(img(src := url), name)
    }
  }

  object Link {
    final case class URL(name: String, url: String)
        extends Link(name, url, "[") {
      val marker = "["
    }
    object URL {
      def apply(): URL = new URL("", "")
    }

    final case class Image(name: String, url: String)
        extends Link(name, url, "![") {
      val marker = "!["
    }
    object Image {
      def apply(): Image = new Image("", "")
    }
  }

  ///////////////////
  ////// Lists //////
  ///////////////////

  trait ListType {
    val marker: Char
    val HTMLMarker: ConcreteHtmlTag[String]
  }
  final case object Unordered extends ListType {
    val marker     = '-'
    val HTMLMarker = ul
  }
  final case object Ordered extends ListType {
    val marker     = '*'
    val HTMLMarker = ol
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

    val html: Seq[Modifier] = Seq(
      listType.HTMLMarker({
        for (elem <- elems.toList) yield {
          elem match {
            case (t: ListBlock) => elem.html
            case _              => Seq(li(elem.html))
          }
        }
      })
    )
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

  case class Section(indent: Int, st: SectionType, elems: List[AST])
      extends Symbol {
    val marker0 = st.marker.map(_.toString).getOrElse("")
    val marker1 = st.marker.map(_.toString).getOrElse(" ")
    val newline = Text("\n")

    val repr: Repr = {
      var _repr = Repr()

      _repr += (indent match {
        case 0 => marker0
        case 1 => marker1
        case _ => " " * (indent - 2) + marker1 + " "
      })

      for (i <- elems.indices) {
        val elem = elems(i)
        elem match {
          case (t: Section.Header) =>
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
    val html: Seq[Modifier] = Seq(
      div(
        `class` := st.getClass.getSimpleName
          .dropRight(1)
      )(for (elem <- elems) yield elem.html)
    )

  }

  object Section {
    def apply(indent: Int, sectionType: SectionType, elem: AST): Section =
      Section(indent, sectionType, elem :: Nil)
    def apply(indent: Int, sectionType: SectionType, elems: AST*): Section =
      Section(indent, sectionType, elems.to[List])

    final case class Header(elem: AST) extends AST {
      val repr: Repr = elem.repr
      val html: Seq[Modifier] =
        Seq(div(`class` := "Header")(elem.html))
    }
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
    val marker: Option[Char] = None
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
    val html: Seq[Modifier] =
      Seq(
        div(`class` := this.getClass.getSimpleName)(
          for (elem <- elems) yield elem.html
        )
      )
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
    val html: Seq[Modifier] =
      Seq(
        div(`class` := this.getClass.getSimpleName)(
          for (elem <- elems) yield elem.html
        )
      )
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

  final case class TagClass(tp: TagType, details: Option[String]) extends AST {
    val name: String = tp.getClass.getSimpleName.toUpperCase.dropRight(1)
    val repr: Repr = {
      if (details.repr == Repr()) {
        Repr(name)
      } else {
        Repr(name) + ' ' + details.repr
      }
    }
    val html: Seq[Modifier] =
      Seq(div(`class` := name)(name)(details.html))
  }
  object TagClass {
    def apply(tp: TagType): TagClass = TagClass(tp, None)
    def apply(tp: TagType, details: String): TagClass =
      TagClass(tp, Option(details))
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
    val html: Seq[Modifier] =
      Seq(
        div(`class` := this.getClass.getSimpleName)(
          for (elem <- elems) yield elem.html
        )
      )
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
    val html: Seq[Modifier] =
      Seq(self.map(div(`class` := "Details")(_)).getOrElse("".html))
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

    // FIXME - I need to use else { "".htmlRepr } to silence Intellij warnings, works without it in sbt
    val html: Seq[Modifier] =
      Seq(
        div(`class` := this.getClass.getSimpleName)(
          if (tags.exists()) tags.html else "".html
        )(if (synopsis.exists()) synopsis.html else "".html)(
          if (body.exists()) body.html else "".html
        )
      )
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

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
    val htmlRepr: Seq[Modifier]
  }

  trait Symbol extends AST.Symbol with Provider {
    def renderHTML(): TypedTag[String] =
      html(
        head(
          meta(httpEquiv := "Content-Type")(content := "text/html")(
            charset := "UTF-8"
          ),
          link(rel := "stylesheet")(href := "styleA.css")
        ),
        body(htmlRepr)
      )
  }

  implicit final class _OptionAST_(val self: Option[AST]) extends Symbol {
    val repr: Repr              = self.map(_.repr).getOrElse(Repr())
    val htmlRepr: Seq[Modifier] = self.map(_.htmlRepr).getOrElse("".htmlRepr)
  }

  ///////////
  /// AST ///
  ///////////

  trait AST        extends Symbol
  trait InvalidAST extends AST

  final case class Text(text: String) extends AST {
    val repr: Repr              = text
    val htmlRepr: Seq[Modifier] = Seq(text)
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
    val repr: Repr              = Repr(tp.marker) + elem.repr + tp.marker
    val htmlRepr: Seq[Modifier] = Seq(tp.htmlMarker(elem.htmlRepr))
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
    val htmlRepr: Seq[Modifier] =
      Seq(
        div(`class` := s"unclosed_${tp.htmlMarker.tag}")(
          elem.htmlRepr
        )
      )
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
    val htmlRepr: Seq[Modifier] =
      Seq(div(`class` := "InvalidIndent")(elem.htmlRepr))
  }

  ///////////////////////
  ////// Code Line //////
  ///////////////////////

  final case class CodeLine(str: String) extends AST {
    val repr: Repr              = Repr("`") + str + "`"
    val htmlRepr: Seq[Modifier] = Seq(code(str))
  }

  final case class MultilineCodeLine(str: String) extends AST {
    val repr: Repr              = str
    val htmlRepr: Seq[Modifier] = Seq(code(str), br)
  }

  ///////////////////
  ////// Links //////
  ///////////////////

  abstract class Link(name: String, url: String, marker: String) extends AST {
    val repr: Repr = Repr() + marker + name + "](" + url + ")"
    val htmlRepr: Seq[Modifier] = this match {
      case (t: URL)   => Seq(a(href := url)(name))
      case (t: Image) => Seq(img(src := url), name)
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

    val htmlRepr: Seq[Modifier] = Seq(
      listType.HTMLMarker({
        for (elem <- elems.toList) yield {
          elem match {
            case (t: ListBlock) => elem.htmlRepr
            case _              => Seq(li(elem.htmlRepr))
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

  ////////////////////
  ////// Header //////
  ////////////////////

  final case class Header(elem: AST) extends AST {
    val repr: Repr = elem.repr
    val htmlRepr: Seq[Modifier] =
      Seq(div(`class` := this.getClass.getSimpleName)(elem.htmlRepr))
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
    val htmlRepr: Seq[Modifier] = Seq(
      div(
        `class` := st.getClass.getSimpleName
          .dropRight(1)
      )(for (elem <- elems) yield elem.htmlRepr)
    )

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
    val htmlRepr: Seq[Modifier] =
      Seq(
        div(`class` := this.getClass.getSimpleName)(
          for (elem <- elems) yield elem.htmlRepr
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
    val htmlRepr: Seq[Modifier] =
      Seq(
        div(`class` := this.getClass.getSimpleName)(
          for (elem <- elems) yield elem.htmlRepr
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

  final case class TagClass(tp: TagType, version: Option[String]) extends AST {
    val name: String = tp.getClass.getSimpleName.toUpperCase.dropRight(1)
    val repr: Repr = {
      if (version.repr == Repr()) {
        Repr(name)
      } else {
        Repr(name) + ' ' + version.repr
      }
    }
    val htmlRepr: Seq[Modifier] =
      Seq(div(`class` := name)(name)(version.htmlRepr))
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
    val htmlRepr: Seq[Modifier] =
      Seq(
        div(`class` := this.getClass.getSimpleName)(
          for (elem <- elems) yield elem.htmlRepr
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
    val htmlRepr: Seq[Modifier] =
      Seq(self.map(div(`class` := "Version")(_)).getOrElse("".htmlRepr))
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
    val htmlRepr: Seq[Modifier] =
      Seq(
        div(`class` := this.getClass.getSimpleName)(if (tags.exists()) {
          tags.htmlRepr
        } else {
          "".htmlRepr
        })(if (synopsis.exists()) {
          synopsis.htmlRepr
        } else {
          "".htmlRepr
        })(if (body.exists()) {
          body.htmlRepr
        } else {
          "".htmlRepr
        })
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

package org.enso.syntax.text.ast

import org.enso.data.List1
import scalatags.Text.TypedTag
import scalatags.Text.{all => HTML}
import HTML._
import org.enso.syntax.text.ast.Doc.Tags.Tag.Unrecognized

////////////////
////// Doc /////
////////////////

final case class Doc(
  tags: Doc.Tags,
  synopsis: Doc.Synopsis,
  details: Doc.Body
) extends Doc.AST {
  val repr: Repr = tags.repr + synopsis.repr + details.repr

  val html: Doc.HTML = {
    val className = this.getClass.getSimpleName
    val htmlCls   = HTML.`class` := className
    val html      = HTML.div(htmlCls)(tags.html)(synopsis.html)(details.html)
    Seq(html)
  }
}

object Doc {
  def apply(tags: Tags, synopsis: Synopsis, body: Body): Doc =
    new Doc(tags, synopsis, body)
  def apply(synopsis: Synopsis, body: Body): Doc =
    new Doc(Tags(), synopsis, body)
  def apply(synopsis: Synopsis): Doc =
    new Doc(Tags(), synopsis, Body())
  def apply(tags: Tags, synopsis: Synopsis): Doc =
    new Doc(tags, synopsis, Body())

  type HTML    = Seq[Modifier]
  type HTMLTag = TypedTag[String]

  def makeIndent(size: Int): String = " " * size

  //////////////
  /// Symbol ///
  //////////////

  trait Symbol extends org.enso.syntax.text.AST.Symbol {
    val html: HTML

    val docMeta: HTMLTag =
      HTML.meta(HTML.httpEquiv := "Content-Type")(HTML.content := "text/html")(
        HTML.charset := "UTF-8"
      )
    val cssLink: HTMLTag =
      HTML.link(HTML.rel := "stylesheet")(HTML.href := "style.css")

    def renderHTML(): HTMLTag =
      HTML.html(HTML.head(docMeta, cssLink), HTML.body(html))
  }

  implicit final class _OptionAST_(val self: Option[AST]) extends Symbol {
    val repr: Repr = self.map(_.repr).getOrElse(Repr())
    val html: HTML = self.map(_.html).getOrElse("".html)
  }

  ///////////
  /// AST ///
  ///////////

  sealed trait AST extends Symbol
  object AST {
    trait Invalid extends AST

    final case class Text(text: String) extends AST {
      val repr: Repr = text
      val html: HTML = Seq(text.replaceAll("\n", " "))
    }
    val newline = AST.Text("\n")

    //////////////////////
    /// Text Formatter ///
    //////////////////////

    // TODO - Proposed for next PR
    // Generating css classes for the elements is really great idea.
    // Please think how you can generalize it, so for all other types they
    // will be generated automatically. Of course sometimes, like here,
    // you would like to override the automatic generation (unclosed) with
    // something custom.
    // Here for example, I'd vote for applying multiple CSS classes
    // both .bold as well as .unclosed
    // this way you can write generic CSS rules.
    final case class Formatter(tp: Formatter.Type, elem: Option[AST])
        extends AST {
      val repr: Repr = Repr(tp.marker) + elem.repr + tp.marker
      val html: HTML = Seq(tp.htmlMarker(elem.html))
    }

    object Formatter {
      def apply(tp: Type): Formatter = Formatter(tp, None)
      def apply(tp: Type, elem: AST): Formatter =
        Formatter(tp, Some(elem))

      abstract class Type(
        val marker: Char,
        val htmlMarker: HTMLTag
      )
      case object Bold          extends Type('*', HTML.b)
      case object Italic        extends Type('_', HTML.i)
      case object Strikethrough extends Type('~', HTML.s)

      final case class Unclosed(tp: Type, elem: Option[AST])
          extends AST.Invalid {
        val repr: Repr = Repr(tp.marker) + elem.repr
        val html: HTML = Seq {
          val htmlCls = HTML.`class` := s"unclosed_${tp.htmlMarker.tag}"
          HTML.div(htmlCls)(elem.html)
        }
      }

      object Unclosed {
        def apply(tp: Type): Unclosed = Unclosed(tp, None)
        def apply(tp: Type, elem: AST): Unclosed =
          Unclosed(tp, Option(elem))
      }
    }

    //////////////////
    ////// Code //////
    //////////////////

    object Code {
      final case class Inline(str: String) extends AST {
        val marker     = '`'
        val repr: Repr = Repr(marker) + str + marker
        val html: HTML = Seq(HTML.code(str))
      }
      final case class Multiline(indent: Int, elems: scala.List[String])
          extends AST {
        val repr: Repr = elems.reverse
          .map(makeIndent(indent) + _)
          .mkString(AST.newline.text)
        val html: HTML = {
          val htmlCls = HTML.`class` := this.getClass.getName.split('$').last
          val btn     = HTML.button("Show")
          Seq(
            HTML.div(
              btn,
              HTML.div(htmlCls)(
                elems.reverse.map(elem => Seq(HTML.code(elem), HTML.br))
              )
            )
          )
        }
      }
    }

    ///////////////////
    ////// Links //////
    ///////////////////

    abstract class Link(name: String, url: String, marker: String) extends AST {
      val repr: Repr = Repr() + marker + name + "](" + url + ")"
      val html: HTML = this match {
        case _: Link.URL   => Seq(HTML.a(HTML.href := url)(name))
        case _: Link.Image => Seq(HTML.img(HTML.src := url), name)
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

    final case class List(indent: Int, tp: List.Type, elems: List1[AST])
        extends AST {

      val repr: Repr = {
        Repr() + elems.toList.map {
          case elem @ (_: AST.Invalid) =>
            newline.repr + elem.repr
          case elem @ (_: List) =>
            newline.repr + elem.repr
          case elem =>
            if (elems.head != elem) {
              newline.repr + makeIndent(indent) + tp.marker + elem.repr
            } else {
              Repr(makeIndent(indent)) + tp.marker + elem.repr
            }
        }
      }

      val html: HTML = Seq(tp.HTMLMarker {
        elems.toList.map {
          case elem @ (_: List) => elem.html
          case elem             => Seq(HTML.li(elem.html))
        }
      })
    }

    object List {
      def apply(indent: Int, listType: Type, elem: AST): List =
        List(indent, listType, List1(elem))
      def apply(indent: Int, listType: Type, elems: AST*): List =
        List(indent, listType, List1(elems.head, elems.tail.toList))

      abstract class Type(
        val marker: Char,
        val HTMLMarker: HTMLTag
      )
      final case object Unordered extends Type('-', HTML.ul)
      final case object Ordered   extends Type('*', HTML.ol)

      object Indent {
        final case class Invalid(
          indent: Int,
          elem: AST,
          tp: Type
        ) extends AST.Invalid {
          val repr: Repr = Repr(makeIndent(indent)) + tp.marker + elem.repr
          val html: HTML = {
            val htmlCls = HTML.`class` := "InvalidIndent"
            Seq(
              HTML.div(htmlCls)(elem.html)
            )
          }
        }
      }
    }
  }
  implicit def stringToText(str: String): AST.Text = AST.Text(str)

  //////////////////////
  ////// Sections //////
  //////////////////////

  trait Section extends Symbol {
    type Type
    val indent: Int
    val tp: Type
    val elems: List[AST]
  }

  object Section {
    final case class Header(elems: List[AST]) extends AST {
      val repr: Repr = Repr() + elems.map(_.repr)
      val html: HTML = {
        val htmlCls = HTML.`class` := this.getClass.getName.split('$').last
        Seq(HTML.div(htmlCls)(elems.map(_.html)))
      }
    }
    object Header {
      def apply(elem: AST): Header =
        Header(elem :: Nil)
      def apply(elems: AST*): Header =
        Header(elems.to[List])
    }

    final case class Marked(
      indent: Int,
      tp: Marked.Type,
      elems: List[AST]
    ) extends Section {
      type Type = Marked.Type
      val marker = tp.marker.toString
      val firstIndentRepr = Repr(indent match {
        case 0 | 1 => marker
        case _ =>
          val indentBeforeMarker: String = makeIndent(1)
          indentBeforeMarker + marker + makeIndent(
            indent - (marker.length + indentBeforeMarker.length)
          )
      })

      val elemsRepr = elems.zipWithIndex.map {
        case (elem @ (_: AST.List), _) =>
          elem.repr
        case (elem @ (_: AST.Code.Multiline), _) => elem.repr
        case (elem, index) =>
          if (index > 0) {
            val previousElem = elems(index - 1)
            if (previousElem == AST.newline) {
              Repr(makeIndent(indent)) + elem.repr
            } else elem.repr
          } else elem.repr
      }

      val repr: Repr = firstIndentRepr + elemsRepr
      val html: HTML = {
        val htmlCls = HTML.`class` := tp.toString
        Seq(HTML.div(htmlCls)(elems.map(_.html)))
      }
    }

    object Marked {
      def apply(indent: Int, st: Type): Marked =
        Marked(indent, st, Nil)
      def apply(indent: Int, st: Type, elem: AST): Marked =
        Marked(indent, st, elem :: Nil)
      def apply(indent: Int, st: Type, elems: AST*): Marked =
        Marked(indent, st, elems.to[List])

      abstract class Type(val marker: Char)
      case object Important extends Type('!')
      case object Info      extends Type('?')
      case object Example   extends Type('>')
    }

    final case class Raw(
      indent: Int,
      elems: List[AST]
    ) extends Section {
      type Type = this.type
      override val tp: Raw.this.type = Raw.this

      val elemsRepr = elems.zipWithIndex.map {
        case (elem @ (_: Section.Header), _) =>
          AST.newline.repr + makeIndent(indent) + elem.repr
        case (elem @ (_: AST.List), _)           => elem.repr
        case (elem @ (_: AST.Code.Multiline), _) => elem.repr
        case (elem, index) =>
          if (index > 0) {
            val previousElem = elems(index - 1)
            if (previousElem == AST.newline) {
              Repr(makeIndent(indent)) + elem.repr
            } else elem.repr
          } else elem.repr
      }

      val repr: Repr = Repr(makeIndent(indent)) + elemsRepr
      val html: HTML = {
        val htmlCls = HTML.`class` := this.getClass.getName.split('$').last
        Seq(HTML.div(htmlCls)(elems.map(_.html)))
      }
    }

    object Raw {
      def apply(indent: Int): Raw =
        Raw(indent, Nil)
      def apply(indent: Int, elem: AST): Raw =
        Raw(indent, elem :: Nil)
      def apply(indent: Int, elems: AST*): Raw =
        Raw(indent, elems.to[List])
    }
  }

  //////////////////
  ////// Body //////
  //////////////////

  final case class Body(elems: List[Section]) {
    val head: Repr = if (elems == Nil) Repr.R else AST.newline.repr
    val repr: Repr = head + elems.map(_.repr.show()).mkString(AST.newline.text)
    val html: HTML =
      if (elems == Nil) "".html
      else {
        val htmlCls = HTML.`class` := this.getClass.getSimpleName
        Seq(
          HTML.div(htmlCls)(
            elems.map(_.html)
          )
        )
      }
  }

  object Body {
    def apply():                Body = Body(Nil)
    def apply(elem: Section):   Body = Body(elem :: Nil)
    def apply(elems: Section*): Body = Body(elems.to[List])
  }

  //////////////////////
  ////// Synopsis //////
  //////////////////////

  final case class Synopsis(elems: List[Section]) {
    val repr: Repr =
      elems.map(_.repr.show()).mkString(AST.newline.text)
    val html: HTML =
      if (elems == Nil) "".html
      else {
        val htmlCls = HTML.`class` := this.getClass.getSimpleName
        Seq(
          HTML.div(htmlCls)(
            elems.map(_.html)
          )
        )
      }
  }
  object Synopsis {
    def apply():                Synopsis = Synopsis(Nil)
    def apply(elem: Section):   Synopsis = Synopsis(elem :: Nil)
    def apply(elems: Section*): Synopsis = Synopsis(elems.to[List])
  }

  ////////////
  /// Tags ///
  ////////////

  final case class Tags(indent: Int, elems: List[Tags.Tag]) {
    val tail: String = if (elems == Nil) "" else AST.newline.text
    val repr: Repr =
      elems
        .map(makeIndent(indent) + _.repr.show())
        .mkString("\n") + tail
    val html: HTML =
      if (elems == Nil) "".html
      else {
        val htmlCls = HTML.`class` := this.getClass.getSimpleName
        Seq(
          HTML.div(htmlCls)(elems.map(_.html))
        )
      }
  }
  object Tags {
    def apply():                       Tags = Tags(0, Nil)
    def apply(indent: Int):            Tags = Tags(indent, Nil)
    def apply(elem: Tag):              Tags = Tags(0, elem :: Nil)
    def apply(indent: Int, elem: Tag): Tags = Tags(indent, elem :: Nil)
    def apply(elems: Tag*):            Tags = Tags(0, elems.to[List])
    def apply(indent: Int, elems: Tag*): Tags =
      Tags(indent, elems.to[List])

    final case class Tag(tp: Tag.Type, details: Option[String]) {
      val name: String = tp.toString.toUpperCase
      val repr: Repr = tp match {
        case _: Unrecognized =>
          Repr(Tag.asInstanceOf[Unrecognized].str) + details.repr
        case _ => Repr(name) + details.repr
      }
      val html: HTML = Seq(HTML.div(HTML.`class` := name)(name)(details.html))
    }
    object Tag {
      def apply(tp: Type): Tag = Tag(tp, None)
      def apply(tp: Type, details: String): Tag =
        Tag(tp, Option(details))

      sealed trait Type
      case object Deprecated               extends Type
      case object Added                    extends Type
      case object Removed                  extends Type
      case object Modified                 extends Type
      case object Upcoming                 extends Type
      case class Unrecognized(str: String) extends Type
    }

    implicit final class _OptionTagType_(val self: Option[String]) {
      val repr: Repr = self.map(Repr(_)).getOrElse(Repr())
      val html: HTML = {
        val htmlCls = HTML.`class` := "tag_details"
        Seq(
          self
            .map(HTML.div(htmlCls)(_))
            .getOrElse("".html)
        )
      }
    }
  }

}

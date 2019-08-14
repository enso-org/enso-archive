package org.enso.syntax.text.ast

import org.enso.data.List1
import scalatags.Text.TypedTag
import scalatags.Text.{all => HTML}
import HTML._
import org.enso.syntax.text.ast.Doc.Tags.Tag.Unrecognized
import Repr.R

////////////////
////// Doc /////
////////////////

final case class Doc(
  tags: Option[Doc.Tags],
  synopsis: Option[Doc.Synopsis],
  body: Option[Doc.Body]
) extends Doc.AST {
  val repr: Repr = R + tags + synopsis + body

  val html: Doc.HTML = {
    val className    = this.getClass.getSimpleName
    val htmlCls      = HTML.`class` := className
    val tagsHtml     = tags.getOrElse(Doc.AST.Text("")).html
    val synopsisHtml = synopsis.getOrElse(Doc.AST.Text("")).html
    val bodyHtml     = body.getOrElse(Doc.AST.Text("")).html
    Seq(HTML.div(htmlCls)(tagsHtml)(synopsisHtml)(bodyHtml))
  }
}

object Doc {
  def apply(tags: Tags):         Doc = Doc(Some(tags), None, None)
  def apply(synopsis: Synopsis): Doc = Doc(None, Some(synopsis), None)
  def apply(synopsis: Synopsis, body: Body): Doc =
    Doc(None, Some(synopsis), Some(body))
  def apply(tags: Tags, synopsis: Synopsis): Doc =
    Doc(Some(tags), Some(synopsis), None)
  def apply(tags: Tags, synopsis: Synopsis, body: Body): Doc =
    Doc(Some(tags), Some(synopsis), Some(body))

  type HTML    = Seq[Modifier]
  type HTMLTag = TypedTag[String]

  def makeIndent(size: Int): String = " " * size

  //////////////
  /// Symbol ///
  //////////////

  trait Symbol extends org.enso.syntax.text.AST.Symbol {
    val html: HTML
    def renderHTML(): HTMLTag = {
      val docMeta: HTMLTag =
        HTML.meta(HTML.httpEquiv := "Content-Type")(
          HTML.content := "text/html"
        )(
          HTML.charset := "UTF-8"
        )
      val cssLink: HTMLTag =
        HTML.link(HTML.rel := "stylesheet")(HTML.href := "style.css")
      HTML.html(HTML.head(docMeta, cssLink), HTML.body(html))
    }
  }

  implicit final class _ListAST_(val self: List[AST]) extends Symbol {
    val repr: Repr =
      R + self.map(_.repr)
    val html: HTML =
      Seq(self.map(_.html))
  }

  ///////////
  /// AST ///
  ///////////

  sealed trait AST extends Symbol
  object AST {
    trait Invalid extends AST

    /////////////////////////////
    /// Normal text & Newline ///
    /////////////////////////////

    final case class Text(text: String) extends AST {
      val repr: Repr = text
      val html: HTML = Seq(text.replaceAll("\n", " "))
    }

    case object Newline extends AST {
      val repr: Repr = R + "\n"
      val html: HTML = Seq()
    }

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

    final case class Formatter(
      tp: Formatter.Type,
      elems: scala.List[AST]
    ) extends AST {
      val repr: Repr = R + tp.marker + elems + tp.marker
      val html: HTML = Seq(tp.htmlMarker(elems.html))
    }

    object Formatter {
      def apply(tp: Type): Formatter = Formatter(tp, Nil)
      def apply(tp: Type, elem: AST): Formatter =
        Formatter(tp, elem :: Nil)
      def apply(tp: Type, elems: AST*): Formatter =
        Formatter(tp, elems.toList)

      abstract class Type(
        val marker: Char,
        val htmlMarker: HTMLTag
      )
      case object Bold          extends Type('*', HTML.b)
      case object Italic        extends Type('_', HTML.i)
      case object Strikethrough extends Type('~', HTML.s)

      final case class Unclosed(tp: Type, elems: scala.List[AST])
          extends AST.Invalid {
        val repr: Repr = R + tp.marker + elems
        val html: HTML = Seq {
          val htmlCls = HTML.`class` := s"unclosed_${tp.htmlMarker.tag}"
          HTML.div(htmlCls)(elems.html)
        }
      }

      object Unclosed {
        def apply(tp: Type): Unclosed = Unclosed(tp, Nil)
        def apply(tp: Type, elem: AST): Unclosed =
          Unclosed(tp, elem :: Nil)
        def apply(tp: Type, elems: AST*): Unclosed =
          Unclosed(tp, elems.toList)
      }
    }

    //////////////////
    ////// Code //////
    //////////////////

    object Code {
      final case class Inline(str: String) extends AST {
        val marker     = '`'
        val repr: Repr = R + marker + str + marker
        val html: HTML = Seq(HTML.code(str))
      }
      final case class Multiline(indent: Int, elems: scala.List[String])
          extends AST {
        val repr: Repr =
          elems.map(makeIndent(indent) + _).mkString(AST.Newline.show())
        val html: HTML = {
          val htmlCls   = HTML.`class` := this.getClass.getName.split('$').last
          val htmlId    = HTML.`id` := "code-1"
          val elemsHTML = elems.map(elem => Seq(HTML.code(elem), HTML.br))
          val stl       = HTML.`style` := "display: none"
          val btnAction = onclick :=
            """var code = document.getElementById("code-1");
              |var btn = document.getElementById("btn-1").firstChild;
              |btn.data = btn.data == "Show" ? "Hide" : "Show";
              |code.style.display = code.style.display == "none" ? "inline-block" : "none";""".stripMargin
          val btn = HTML.button(btnAction)(HTML.`id` := "btn-1")("Show")
          Seq(HTML.div(btn, HTML.div(htmlCls)(htmlId)(stl)(elemsHTML)))
        }
      }
      object Multiline {
        def apply(indent: Int, elem: String): Multiline =
          Multiline(indent, elem :: Nil)
        def apply(indent: Int, elems: String*): Multiline =
          Multiline(indent, elems.toList)
      }
    }

    ///////////////////
    ////// Links //////
    ///////////////////

    abstract class Link(name: String, url: String, val marker: String)
        extends AST {
      val repr: Repr = Repr() + marker + "[" + name + "](" + url + ")"
      val html: HTML = this match {
        case _: Link.URL   => Seq(HTML.a(HTML.href := url)(name))
        case _: Link.Image => Seq(HTML.img(HTML.src := url), name)
      }
    }

    object Link {
      final case class URL(name: String, url: String)
          extends Link(name, url, "")
      object URL {
        def apply(): URL = new URL("", "")
      }

      final case class Image(name: String, url: String)
          extends Link(name, url, "!")
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
            R + Newline + elem
          case elem @ (_: List) =>
            R + Newline + elem
          case elem =>
            if (elems.head != elem) {
              R + Newline + makeIndent(indent) + tp.marker + elem
            } else {
              Repr(makeIndent(indent)) + tp.marker + elem
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
          tp: Type,
          elem: AST
        ) extends AST.Invalid {
          val repr: Repr = Repr(makeIndent(indent)) + tp.marker + elem
          val html: HTML = {
            val htmlCls = HTML.`class` := "InvalidIndent"
            Seq(HTML.div(htmlCls)(elem.html))
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
    val indent: Int
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
        Header(elems.toList)
    }

    final case class Marked(
      indent: Int,
      tp: Marked.Type,
      elems: List[AST]
    ) extends Section {
      val marker: String = tp.marker.toString
      val firstIndentRepr = Repr(indent match {
        case 1 => marker
        case _ =>
          val indentBeforeMarker: String = makeIndent(1)
          indentBeforeMarker + marker + makeIndent(
            indent - (marker.length + indentBeforeMarker.length)
          )
      })

      val elemsRepr: List[Repr] = elems.zipWithIndex.map {
        case (elem @ (_: AST.List), _)           => R + elem
        case (elem @ (_: AST.Code.Multiline), _) => R + elem
        case (elem, index) =>
          if (index > 0) {
            val previousElem = elems(index - 1)
            if (previousElem == AST.Newline) {
              Repr(makeIndent(indent)) + elem.repr
            } else elem.repr
          } else R + elem
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
        Marked(indent, st, elems.toList)
      def apply(st: Type): Marked =
        Marked(1, st, Nil)
      def apply(st: Type, elem: AST): Marked =
        Marked(1, st, elem :: Nil)
      def apply(st: Type, elems: AST*): Marked =
        Marked(1, st, elems.toList)

      abstract class Type(val marker: Char)
      case object Important extends Type('!')
      case object Info      extends Type('?')
      case object Example   extends Type('>')
    }

    final case class Raw(
      indent: Int,
      elems: List[AST]
    ) extends Section {

      val elemsRepr: List[Repr] = elems.zipWithIndex.map {
        case (elem @ (_: Section.Header), _) =>
          R + AST.Newline + makeIndent(indent) + elem
        case (elem @ (_: AST.List), _)           => R + elem
        case (elem @ (_: AST.Code.Multiline), _) => R + elem
        case (elem, index) =>
          if (index > 0) {
            val previousElem = elems(index - 1)
            if (previousElem == AST.Newline) {
              Repr(makeIndent(indent)) + elem.repr
            } else elem.repr
          } else R + elem
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
        Raw(indent, elems.toList)
      def apply(): Raw =
        Raw(0, Nil)
      def apply(elem: AST): Raw =
        Raw(0, elem :: Nil)
      def apply(elems: AST*): Raw =
        Raw(0, elems.toList)
    }
  }

  //////////////////
  ////// Body //////
  //////////////////

  final case class Body(elems: List[Section]) extends Symbol {
    val head: Repr = if (elems == Nil) R else R + AST.Newline
    val repr: Repr = head + elems
        .map(_.repr.show())
        .mkString(AST.Newline.show())
    val html: HTML = {
      val htmlCls = HTML.`class` := this.getClass.getSimpleName
      Seq(HTML.div(htmlCls)(elems.map(_.html)))
    }
  }

  object Body {
    def apply():                Body = Body(Nil)
    def apply(elem: Section):   Body = Body(elem :: Nil)
    def apply(elems: Section*): Body = Body(elems.toList)
  }

  //////////////////////
  ////// Synopsis //////
  //////////////////////

  final case class Synopsis(elems: List[Section]) extends Symbol {
    val repr: Repr =
      R + elems.map(_.repr.show()).mkString(AST.Newline.show())
    val html: HTML = {
      val htmlCls = HTML.`class` := this.getClass.getSimpleName
      Seq(HTML.div(htmlCls)(elems.map(_.html)))
    }
  }
  object Synopsis {
    def apply():                Synopsis = Synopsis(Nil)
    def apply(elem: Section):   Synopsis = Synopsis(elem :: Nil)
    def apply(elems: Section*): Synopsis = Synopsis(elems.toList)
  }

  ////////////
  /// Tags ///
  ////////////

  final case class Tags(elems: List[Tags.Tag]) extends Symbol {
    val tail: String = if (elems == Nil) "" else AST.Newline.show()
    val repr: Repr   = elems.map(_.repr.show()).mkString("\n") + tail
    val html: HTML =
      if (elems == Nil) "".html
      else {
        val htmlCls = HTML.`class` := this.getClass.getSimpleName
        Seq(HTML.div(htmlCls)(elems.map(_.html)))
      }
  }
  object Tags {
    def apply():            Tags = Tags(Nil)
    def apply(elem: Tag):   Tags = Tags(elem :: Nil)
    def apply(elems: Tag*): Tags = Tags(elems.toList)

    final case class Tag(indent: Int, tp: Tag.Type, details: Option[String]) {
      val name: String = tp.toString.toUpperCase
      val repr: Repr = tp match {
        case Unrecognized => R + makeIndent(indent) + details
        case _            => R + makeIndent(indent) + name + details
      }
      val html: HTML = tp match {
        case Unrecognized => Seq(HTML.div(HTML.`class` := name)(details.html))
        case _            => Seq(HTML.div(HTML.`class` := name)(name)(details.html))
      }
    }
    object Tag {
      def apply(tp: Type): Tag = Tag(0, tp, None)
      def apply(tp: Type, details: String): Tag =
        Tag(0, tp, Option(details))
      def apply(indent: Int, tp: Type): Tag = Tag(indent, tp, None)
      def apply(indent: Int, tp: Type, details: String): Tag =
        Tag(indent, tp, Option(details))

      sealed trait Type
      case object Deprecated   extends Type
      case object Added        extends Type
      case object Removed      extends Type
      case object Modified     extends Type
      case object Upcoming     extends Type
      case object Unrecognized extends Type
    }

    implicit final class _OptionTagType_(val self: Option[String]) {
      val repr: Repr = self.map(Repr(_)).getOrElse(Repr())
      val html: HTML = {
        val htmlCls = HTML.`class` := "tag_details"
        Seq(self.map(HTML.div(htmlCls)(_)).getOrElse("".html))
      }
    }
  }
}

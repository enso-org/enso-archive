package org.enso.syntax.text.ast

import org.enso.data.List1
import Repr.R
import scalatags.Text.TypedTag
import scalatags.Text.{all => HTML}
import HTML._
import scala.util.Random

////////////////////////////////////////////////////////////////////////////////
////// Doc /////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////

final case class Doc(
  tags: Option[Doc.Tags],
  synopsis: Option[Doc.Synopsis],
  body: Option[Doc.Body]
) extends Doc.Symbol {
  implicit class _OptionT_[T <: Doc.Symbol](val self: Option[T]) {
    val html: Doc.HTML = self.getOrElse(Doc.AST.Text("")).html
  }

  val repr: Repr = R + tags + synopsis + body

  val html: Doc.HTML = {
    val htmlCls = HTML.`class` := this.productPrefix
    Seq(HTML.div(htmlCls)(tags.html)(synopsis.html)(body.html))
  }
}

object Doc {
  def apply():                   Doc = Doc(None, None, None)
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

  //////////////////////////////////////////////////////////////////////////////
  ////// Symbol ////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  trait Symbol extends Repr.Provider {
    def span:   Int    = repr.span
    def show(): String = repr.show()

    val html: HTML
    def renderHTML(): HTMLTag = {
      val metaEquiv     = HTML.httpEquiv := "Content-Type"
      val metaCont      = HTML.content := "text/html"
      val metaChar      = HTML.charset := "UTF-8"
      val meta: HTMLTag = HTML.meta(metaEquiv)(metaCont)(metaChar)
      val cssRel        = HTML.rel := "stylesheet"
      val cssHref       = HTML.href := "style.css"
      val css: HTMLTag  = HTML.link(cssRel)(cssHref)
      HTML.html(HTML.head(meta, css), HTML.body(html))
    }
  }

  implicit final class _ListAST_(val self: List[AST]) extends Symbol {
    val repr: Repr = R + self.map(_.repr)
    val html: HTML = Seq(self.map(_.html))
  }

  //////////////////////////////////////////////////////////////////////////////
  ////// AST ///////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  sealed trait AST extends Symbol
  object AST {
    trait Invalid extends AST

    ////////////////////////////////////////////////////////////////////////////
    ////// Normal text & Newline ///////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    final case class Text(text: String) extends AST {
      val repr: Repr = text
      val html: HTML = Seq(text)
    }

    case object Newline extends AST {
      val repr: Repr = R + "\n"
      val html: HTML = Seq(" ")
    }

    ////////////////////////////////////////////////////////////////////////////
    ////// Text Formatter - Bold, Italic, Strikethrough ////////////////////////
    ////////////////////////////////////////////////////////////////////////////

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
          val htmlCls = HTML.`class` := this.productPrefix
          HTML.div(htmlCls)(tp.htmlMarker(elems.html))
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

    ////////////////////////////////////////////////////////////////////////////
    ////// Code ////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    final case class Code(elems: scala.List[Code.Line]) extends AST {
      val repr: Repr = R + elems.map(_.repr.show()).mkString(AST.Newline.show())
      val html: HTML = {
        val uniqueIDCode = Random.alphanumeric.take(8).mkString("")
        val uniqueIDBtn  = Random.alphanumeric.take(8).mkString("")
        val htmlClsCode  = HTML.`class` := this.productPrefix
        val htmlIdCode   = HTML.`id` := uniqueIDCode
        val htmlIdBtn    = HTML.`id` := uniqueIDBtn
        val elemsHTML    = elems.map(elem => elem.html)
        val style        = HTML.`style` := "display: none"
        val btnAction = onclick :=
          s"""var code = document.getElementById("$uniqueIDCode");
             |var btn = document.getElementById("$uniqueIDBtn").firstChild;
             |btn.data = btn.data == "Show" ? "Hide" : "Show";
             |code.style.display = code.style.display == "none" ? "inline-block" : "none";""".stripMargin
        val btn = HTML.button(btnAction)(htmlIdBtn)("Show")
        Seq(HTML.div(btn, HTML.div(htmlClsCode)(htmlIdCode)(style)(elemsHTML)))
      }
    }
    object Code {
      def apply(elem: Code.Line):   Code = Code(elem :: Nil)
      def apply(elems: Code.Line*): Code = Code(elems.toList)

      final case class Inline(str: String) extends AST {
        val marker     = '`'
        val repr: Repr = R + marker + str + marker
        val html: HTML = Seq(HTML.code(str))
      }
      final case class Line(indent: Int, elem: String) extends AST {
        val repr: Repr = R + makeIndent(indent) + elem
        val html: HTML = Seq(HTML.code(elem), HTML.br)
      }
    }

    ////////////////////////////////////////////////////////////////////////////
    ////// Link - URL & Image //////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

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
        def apply(): URL = URL("", "")
      }

      final case class Image(name: String, url: String)
          extends Link(name, url, "!")
      object Image {
        def apply(): Image = Image("", "")
      }
    }

    ////////////////////////////////////////////////////////////////////////////
    ////// List - Ordered & Unordered, Invalid Indent //////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    final case class List(indent: Int, tp: List.Type, elems: List1[AST])
        extends AST {

      val repr: Repr = Repr() + elems.toList.map {
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
            val objectName = getClass.getEnclosingClass.toString.split('$').last
            val className  = this.productPrefix
            val htmlCls    = HTML.`class` := className + objectName
            Seq(HTML.div(htmlCls)(elem.html))
          }
        }
      }
    }
  }
  implicit def stringToText(str: String): AST.Text = AST.Text(str)

  //////////////////////////////////////////////////////////////////////////////
  ////// Sections - Raw & Marked ///////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  trait Section extends Symbol {
    val indent: Int
    val elems: List[AST]
  }

  object Section {
    final case class Header(elems: List[AST]) extends AST {
      val repr: Repr = Repr() + elems.map(_.repr)
      val html: HTML = {
        val htmlCls = HTML.`class` := this.productPrefix
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
        case (elem @ (_: AST.List), _) => R + elem
        case (elem @ (_: AST.Code), _) => R + elem
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
        case (elem @ (_: AST.List), _) => R + elem
        case (elem @ (_: AST.Code), _) => R + elem
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
        val htmlCls = HTML.`class` := this.productPrefix
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

  //////////////////////////////////////////////////////////////////////////////
  ////// Body //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  final case class Body(elems: List1[Section]) extends Symbol {
    val newLn: String = AST.Newline.show()
    val head: Repr    = R + newLn
    val repr: Repr    = head + elems.toList.map(_.repr.show()).mkString(newLn)
    val html: HTML = {
      val htmlCls = HTML.`class` := this.productPrefix
      Seq(HTML.div(htmlCls)(elems.toList.map(_.html)))
    }
  }

  object Body {
    def apply(elem: Section): Body =
      Body(List1(elem))
    def apply(elems: Section*): Body =
      Body(List1(elems.head, elems.tail.toList))
  }

  //////////////////////////////////////////////////////////////////////////////
  ////// Synopsis //////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  final case class Synopsis(elems: List1[Section]) extends Symbol {
    val newLn: String = AST.Newline.show()
    val repr: Repr =
      R + elems.toList.map(_.repr.show()).mkString(newLn)
    val html: HTML = {
      val htmlCls = HTML.`class` := this.productPrefix
      Seq(HTML.div(htmlCls)(elems.toList.map(_.html)))
    }
  }
  object Synopsis {
    def apply(elem: Section): Synopsis =
      Synopsis(List1(elem))
    def apply(elems: Section*): Synopsis =
      Synopsis(List1(elems.head, elems.tail.toList))
  }

  //////////////////////////////////////////////////////////////////////////////
  ////// Tags //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  final case class Tags(elems: List1[Tags.Tag]) extends Symbol {
    val newLn: String = AST.Newline.show()
    val repr: Repr    = elems.toList.map(_.repr.show()).mkString(newLn) + newLn
    val html: HTML = {
      val htmlCls = HTML.`class` := this.productPrefix
      Seq(HTML.div(htmlCls)(elems.toList.map(_.html)))
    }
  }
  object Tags {
    def apply(elem: Tag): Tags =
      Tags(List1(elem))
    def apply(elems: Tag*): Tags =
      Tags(List1(elems.head, elems.tail.toList))

    final case class Tag(indent: Int, tp: Tag.Type, details: Option[String]) {
      val name: String = tp.toString.toUpperCase
      val repr: Repr = tp match {
        case Tag.Unrecognized => R + makeIndent(indent) + details
        case _                => R + makeIndent(indent) + name + details
      }
      val html: HTML = tp match {
        case Tag.Unrecognized =>
          Seq(HTML.div(HTML.`class` := name)(details.html))
        case _ => Seq(HTML.div(HTML.`class` := name)(name)(details.html))
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

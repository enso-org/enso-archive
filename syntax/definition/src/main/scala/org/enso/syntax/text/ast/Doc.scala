package org.enso.syntax.text.ast

import org.enso.data.List1
import org.enso.syntax.text.ast.Repr.R
import scala.util.Random
import scalatags.Text.TypedTag
import scalatags.Text.{all => HTML}
import HTML._

////////////////////////////////////////////////////////////////////////////////
////// Doc /////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////

final case class Doc(
  tags: Option[Doc.Tags],
  synopsis: Option[Doc.Synopsis],
  body: Option[Doc.Body]
) extends Doc.Symbol {
  val repr: Repr = R + tags + synopsis + body
  val html: Doc.HTML = {
    val htmlCls = HTML.`class` := productPrefix
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

  //////////////////////////////////////////////////////////////////////////////
  ////// Symbol ////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  trait Symbol extends Repr.Provider {
    def span:   Int    = repr.span
    def show(): String = repr.show()

    def html: HTML
    def renderHTML(cssLink: String): HTMLTag = {
      val metaEquiv = HTML.httpEquiv := "Content-Type"
      val metaCont  = HTML.content := "text/html"
      val metaChar  = HTML.charset := "UTF-8"
      val meta      = HTML.meta(metaEquiv)(metaCont)(metaChar)
      val cssRel    = HTML.rel := "stylesheet"
      val cssHref   = HTML.href := cssLink
      val css       = HTML.link(cssRel)(cssHref)
      HTML.html(HTML.head(meta, css), HTML.body(html))
    }
  }

  implicit final class _OptionT_[T <: Symbol](val self: Option[T]) {
    val dummyText  = Elem.Text("")
    val html: HTML = self.getOrElse(dummyText).html
  }

  //////////////////////////////////////////////////////////////////////////////
  ////// AST ///////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  sealed trait Elem extends Symbol
  object Elem {
    trait Invalid extends Elem

    ////////////////////////////////////////////////////////////////////////////
    ////// Normal text & Newline ///////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    final case class Text(text: String) extends Elem {
      val repr: Repr = text
      val html: HTML = Seq(text)
    }

    implicit def stringToText(str: String): Elem.Text = Elem.Text(str)

    case object Newline extends Elem {
      val repr: Repr = R + "\n"
      val html: HTML = Seq(" ")
    }

    ////////////////////////////////////////////////////////////////////////////
    ////// Text Formatter - Bold, Italic, Strikeout ////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    final case class Formatter(tp: Formatter.Type, elems: scala.List[Elem])
        extends Elem {
      val repr: Repr = R + tp.marker + elems + tp.marker
      val html: HTML = Seq(tp.htmlMarker(elems.html))
    }

    object Formatter {
      def apply(tp: Type): Formatter = Formatter(tp, Nil)
      def apply(tp: Type, elem: Elem): Formatter =
        Formatter(tp, elem :: Nil)
      def apply(tp: Type, elems: Elem*): Formatter =
        Formatter(tp, elems.toList)

      abstract class Type(val marker: Char, val htmlMarker: HTMLTag)
      case object Bold      extends Type('*', HTML.b)
      case object Italic    extends Type('_', HTML.i)
      case object Strikeout extends Type('~', HTML.s)

      final case class Unclosed(tp: Type, elems: scala.List[Elem])
          extends Elem.Invalid {
        val repr: Repr = R + tp.marker + elems
        val html: HTML = Seq {
          val htmlCls = HTML.`class` := this.productPrefix
          HTML.div(htmlCls)(tp.htmlMarker(elems.html))
        }
      }

      object Unclosed {
        def apply(tp: Type):               Unclosed = Unclosed(tp, Nil)
        def apply(tp: Type, elem: Elem):   Unclosed = Unclosed(tp, elem :: Nil)
        def apply(tp: Type, elems: Elem*): Unclosed = Unclosed(tp, elems.toList)
      }
    }

    implicit final class _ListOfFormattedElems_(val self: scala.List[Elem])
        extends Symbol {
      val repr: Repr = R + self.map(_.repr)
      val html: HTML = Seq(self.map(_.html))
    }

    ////////////////////////////////////////////////////////////////////////////
    ////// Code ////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /*TODO [MM]: Next PR
         Code showing button - we need other design here.
         Basically we don't want to display always button
         we want to be able to display it maybe as a button on website
         and completely differently in gui, it should be configurable*/
    final case class Code(elems: List1[Code.Line]) extends Elem {
      val newLn: Elem = Elem.Newline
      val repr: Repr  = R + elems.head + elems.tail.map(R + newLn + _)
      val html: HTML = {
        val uniqueIDCode = Random.alphanumeric.take(8).mkString("")
        val uniqueIDBtn  = Random.alphanumeric.take(8).mkString("")
        val htmlClsCode  = HTML.`class` := this.productPrefix
        val htmlIdCode   = HTML.`id` := uniqueIDCode
        val htmlIdBtn    = HTML.`id` := uniqueIDBtn
        val elemsHTML    = elems.toList.map(elem => elem.html)
        val btnAction = onclick :=
          s"""var code = document.getElementById("$uniqueIDCode");
             |var btn = document.getElementById("$uniqueIDBtn").firstChild;
             |btn.data = btn.data == "Show" ? "Hide" : "Show";
             |code.style.display = code.style.display == 
             |"inline-block" ? "none" : "inline-block";""".stripMargin
            .replaceAll("\n", "")
        val btn = HTML.button(btnAction)(htmlIdBtn)("Show")
        Seq(HTML.div(btn, HTML.div(htmlClsCode)(htmlIdCode)(elemsHTML)))
      }
    }
    object Code {
      def apply(elem: Code.Line): Code = Code(List1(elem))
      def apply(elems: Code.Line*): Code =
        Code(List1(elems.head, elems.tail.toList))

      final case class Inline(str: String) extends Elem {
        val marker     = '`'
        val repr: Repr = R + marker + str + marker
        val html: HTML = Seq(HTML.code(str))
      }
      final case class Line(indent: Int, elem: String) extends Elem {
        val repr: Repr = R + indent + elem
        val html: HTML = Seq(HTML.code(elem), HTML.br)
      }
    }

    ////////////////////////////////////////////////////////////////////////////
    ////// Link - URL & Image //////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    abstract class Link(name: String, url: String, val marker: String)
        extends Elem {
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

    final case class List(indent: Int, tp: List.Type, elems: List1[Elem])
        extends Elem {
      val repr: Repr = R + elems.toList.map {
          case elem @ (_: Elem.Invalid)   => R + Newline + elem
          case elem @ (_: List)           => R + Newline + elem
          case elem if elems.head == elem => R + indent + tp.marker + elem
          case elem =>
            R + Newline + indent + tp.marker + elem
        }

      val html: HTML = {
        val elemsHTML = elems.toList.map {
          case elem @ (_: List) => elem.html
          case elem             => Seq(HTML.li(elem.html))
        }
        Seq(tp.HTMLMarker(elemsHTML))
      }
    }

    object List {
      def apply(indent: Int, listType: Type, elem: Elem): List =
        List(indent, listType, List1(elem))
      def apply(indent: Int, listType: Type, elems: Elem*): List =
        List(indent, listType, List1(elems.head, elems.tail.toList))

      abstract class Type(val marker: Char, val HTMLMarker: HTMLTag)
      final case object Unordered extends Type('-', HTML.ul)
      final case object Ordered   extends Type('*', HTML.ol)

      object Indent {
        final case class Invalid(indent: Int, tp: Type, elem: Elem)
            extends Elem.Invalid {
          val repr: Repr = R + indent + tp.marker + elem
          val html: HTML = {
            val className = this.productPrefix
            val htmlCls   = HTML.`class` := className + getObjectName
            Seq(HTML.div(htmlCls)(elem.html))
          }
        }

        def getObjectName: String = {
          getClass.getEnclosingClass.toString.split('$').last
        }
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  ////// Sections - Raw & Marked ///////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  trait Section extends Symbol {
    def indent: Int
    def elems: List[Elem]

    def reprOfNormalText(elem: Elem, prevElem: Elem): Repr = {
      prevElem match {
        case Elem.Newline => R + indent + elem
        case _            => R + elem
      }
    }
  }

  object Section {
    final case class Header(elems: List[Elem]) extends Elem {
      val repr: Repr = Repr() + elems.map(_.repr)
      val html: HTML = {
        val htmlCls = HTML.`class` := this.productPrefix
        Seq(HTML.div(htmlCls)(elems.map(_.html)))
      }
    }
    object Header {
      def apply(elem: Elem):   Header = Header(elem :: Nil)
      def apply(elems: Elem*): Header = Header(elems.toList)
    }

    final case class Marked(indent: Int, tp: Marked.Type, elems: List[Elem])
        extends Section {
      val marker: String         = tp.marker.toString
      val markerLength: Int      = 1
      val indentAfterMarker: Int = 1
      val firstIndentRepr: Repr = indent match {
        case 1 => R + marker
        case _ =>
          val indentAfterMarker: Int = 1
          val indentBeforeMarker
            : Int = indent - (markerLength + indentAfterMarker)
          R + indentBeforeMarker + marker + indentAfterMarker
      }

      val dummyElem = Elem.Text("")
      val elemsRepr: List[Repr] = elems.zip(dummyElem :: elems).map {
        case (elem @ (_: Elem.List), _) => R + elem
        case (elem @ (_: Elem.Code), _) => R + elem
        case (elem, prevElem)           => reprOfNormalText(elem, prevElem)
      }

      val repr: Repr = firstIndentRepr + elemsRepr
      val html: HTML = {
        val htmlCls = HTML.`class` := tp.toString
        Seq(HTML.div(htmlCls)(elems.map(_.html)))
      }
    }

    object Marked {
      def apply(indent: Int, st: Type): Marked = Marked(indent, st, Nil)
      def apply(indent: Int, st: Type, elem: Elem): Marked =
        Marked(indent, st, elem :: Nil)
      def apply(indent: Int, st: Type, elems: Elem*): Marked =
        Marked(indent, st, elems.toList)
      val defaultIndent = 1
      def apply(st: Type): Marked = Marked(defaultIndent, st, Nil)
      def apply(st: Type, elem: Elem): Marked =
        Marked(defaultIndent, st, elem :: Nil)
      def apply(st: Type, elems: Elem*): Marked =
        Marked(defaultIndent, st, elems.toList)

      abstract class Type(val marker: Char)
      case object Important extends Type('!')
      case object Info      extends Type('?')
      case object Example   extends Type('>')
    }

    final case class Raw(indent: Int, elems: List[Elem]) extends Section {
      val dummyElem   = Elem.Text("")
      val newLn: Elem = Elem.Newline
      val elemsRepr: List[Repr] = elems.zip(dummyElem :: elems).map {
        case (elem @ (_: Section.Header), _) => R + newLn + indent + elem
        case (elem @ (_: Elem.List), _)      => R + elem
        case (elem @ (_: Elem.Code), _)      => R + elem
        case (elem, prevElem)                => reprOfNormalText(elem, prevElem)
      }

      val repr: Repr = R + indent + elemsRepr
      val html: HTML = {
        val htmlCls = HTML.`class` := this.productPrefix
        Seq(HTML.div(htmlCls)(elems.map(_.html)))
      }
    }

    object Raw {
      def apply(indent: Int):               Raw = Raw(indent, Nil)
      def apply(indent: Int, elem: Elem):   Raw = Raw(indent, elem :: Nil)
      def apply(indent: Int, elems: Elem*): Raw = Raw(indent, elems.toList)
      val defaultIndent = 0
      def apply():             Raw = Raw(defaultIndent, Nil)
      def apply(elem: Elem):   Raw = Raw(defaultIndent, elem :: Nil)
      def apply(elems: Elem*): Raw = Raw(defaultIndent, elems.toList)
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  ////// Body //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  final case class Body(elems: List1[Section]) extends Symbol {
    val newLn: Elem = Elem.Newline
    val repr: Repr  = R + newLn + elems.head + elems.tail.map(R + newLn + _)
    val html: HTML = {
      val htmlCls = HTML.`class` := this.productPrefix
      Seq(HTML.div(htmlCls)(HTML.h2("Overview"))(elems.toList.map(_.html)))
    }
  }

  object Body {
    def apply(elem: Section): Body = Body(List1(elem))
    def apply(elems: Section*): Body =
      Body(List1(elems.head, elems.tail.toList))
  }

  //////////////////////////////////////////////////////////////////////////////
  ////// Synopsis //////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  final case class Synopsis(elems: List1[Section]) extends Symbol {
    val newLn: Elem = Elem.Newline
    val repr: Repr  = R + elems.head + elems.tail.map(R + newLn + _)
    val html: HTML = {
      val htmlCls = HTML.`class` := this.productPrefix
      Seq(HTML.div(htmlCls)(elems.toList.map(_.html)))
    }
  }
  object Synopsis {
    def apply(elem: Section): Synopsis = Synopsis(List1(elem))
    def apply(elems: Section*): Synopsis =
      Synopsis(List1(elems.head, elems.tail.toList))
  }

  //////////////////////////////////////////////////////////////////////////////
  ////// Tags //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  final case class Tags(elems: List1[Tags.Tag]) extends Symbol {
    val newLn: Elem = Elem.Newline
    val repr: Repr  = R + elems.head + elems.tail.map(R + newLn + _) + newLn
    val html: HTML = {
      val htmlCls = HTML.`class` := this.productPrefix
      Seq(HTML.div(htmlCls)(elems.toList.map(_.html)))
    }
  }
  object Tags {
    def apply(elem: Tag):   Tags = Tags(List1(elem))
    def apply(elems: Tag*): Tags = Tags(List1(elems.head, elems.tail.toList))

    final case class Tag(indent: Int, tp: Tag.Type, details: Option[String])
        extends Elem {
      val name: String = tp.toString.toUpperCase
      val repr: Repr = tp match {
        case Tag.Unrecognized => R + indent + details
        case _                => R + indent + name + details
      }
      val html: HTML = tp match {
        case Tag.Unrecognized =>
          Seq(HTML.div(HTML.`class` := name)(details.html))
        case _ => Seq(HTML.div(HTML.`class` := name)(name)(details.html))
      }
    }
    object Tag {
      val defaultIndent = 0
      def apply(tp: Type): Tag = Tag(defaultIndent, tp, None)
      def apply(tp: Type, details: String): Tag =
        Tag(defaultIndent, tp, Some(details))
      def apply(indent: Int, tp: Type): Tag = Tag(indent, tp, None)
      def apply(indent: Int, tp: Type, details: String): Tag =
        Tag(indent, tp, Some(details))

      sealed trait Type
      case object Deprecated   extends Type
      case object Added        extends Type
      case object Removed      extends Type
      case object Modified     extends Type
      case object Upcoming     extends Type
      case object Unrecognized extends Type
    }

    implicit final class tagDetails(val self: Option[String]) {
      val html: HTML = {
        val htmlCls = HTML.`class` := this.getClass.toString.split('$').last
        Seq(self.map(HTML.div(htmlCls)(_)))
      }
    }
  }
}

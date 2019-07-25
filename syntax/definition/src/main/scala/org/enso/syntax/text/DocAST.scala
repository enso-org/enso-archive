package org.enso.syntax.text

import cats.data.NonEmptyList
import org.enso.syntax.text.AST.Repr

object DocAST {

  ///////////////////
  /// Abstraction ///
  ///////////////////

  trait Showable {
    def show(): String
  }

  trait HTMLGenerable {
    def generateHTML(): String
  }

  trait Spanned {
    def span: Int
  }

  //////////////////////
  //// Repr Helpers ////
  //////////////////////

  trait HasRepr {
    val textRepr: Repr
    val htmlRepr: Repr
  }

  trait ReprOf[-T] {
    def reprOf(a: T): Repr
  }
  def reprOf[T](t: T)(implicit ev: ReprOf[T]) = ev.reprOf(t)

  implicit val HasRepr_0: ReprOf[Unit]    = _ => Repr.Empty()
  implicit val HasRepr_1: ReprOf[String]  = Repr.Text(_)
  implicit val HasRepr_2: ReprOf[Int]     = i => Repr.Text(" " * i)
  implicit val HasRepr_3: ReprOf[Char]    = Repr.Letter(_)
  implicit val HasRepr_4: ReprOf[Repr]    = identity(_)
  implicit val HasRepr_5: ReprOf[HasRepr] = _.textRepr
  implicit def HasRepr_6[T: ReprOf]: ReprOf[List[T]] =
    _.foldLeft(Repr.Empty(): Repr)((a, b) => Repr.Seq(a, reprOf(b)))
  implicit def HasRepr_7[T: ReprOf]: ReprOf[NonEmptyList[T]] =
    _.foldLeft(Repr.Empty(): Repr)((a, b) => Repr.Seq(a, reprOf(b)))
  //////////////
  /// Symbol ///
  //////////////

  trait Symbol extends HasRepr with Spanned with Showable with HTMLGenerable {
    def span: Int = textRepr.span
    def show(): String = {
      val showBuilder = new StringBuilder()
      textRepr.show(showBuilder)
      showBuilder.result()
    }
    def generateHTML(): String = {
      val htmlBuilder = new StringBuilder()
      htmlRepr.show(htmlBuilder)
      htmlBuilder.result()
    }
  }

  implicit final class _OptionAST_(val self: Option[AST]) extends Symbol {
    val textRepr: Repr = self.map(_.textRepr).getOrElse(Repr())
    val htmlRepr: Repr = self.map(_.htmlRepr).getOrElse(Repr())
  }

  ///////////
  /// AST ///
  ///////////

  trait AST        extends Symbol
  trait InvalidAST extends AST

  final case class Text(text: String) extends AST {
    val textRepr: Repr = text
    val htmlRepr: Repr = text.replaceAll("\n", "<br />")
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
    val textRepr
      : Repr = Repr(formatterType.showableMarker) + elem.textRepr + formatterType.showableMarker
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
    val textRepr: Repr = Repr(formatterType.showableMarker) + elem.textRepr
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
    val textRepr
      : Repr = Repr(" " * indent) + listType.readableMarker + elem.textRepr
    val htmlRepr
      : Repr = Repr("<div class=\"invalidIndent\">") + elem.htmlRepr + "</div>"
  }

  ///////////////////////
  ////// Code Line //////
  ///////////////////////

  final case class CodeLine(code: String, inMultilineCode: Boolean)
      extends AST {
    val textRepr: Repr = {
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
    val textRepr: Repr = elem.textRepr
    val htmlRepr: Repr = Repr("<h1>") + elem.htmlRepr + "</h1>"
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
    val textRepr
      : Repr = Repr() + linkType.readableMarker + name + "](" + url + ")"
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
    val textRepr: Repr = {
      var _repr = Repr()
      elems.toList.foreach(elem => {
        if (elem.show().contains(" " * (indent + 2))) {
          _repr += '\n'
          _repr += elem.textRepr
        } else if (elem.isInstanceOf[InvalidIndent]) {
          _repr += '\n'
          _repr += elem.textRepr
        } else {
          if (elems.head != elem) {
            _repr += '\n'
          }
          _repr += " " * indent
          _repr += listType.readableMarker
          _repr += elem.textRepr
        }
      })
      _repr
    }
    val htmlRepr: Repr = {
      var _repr = Repr("<") + listType.HTMLMarker + ">"
      elems.toList.foreach(elem => {
        _repr += "<li>"
        _repr += elem.htmlRepr
        _repr += "</li>"
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

    val textRepr: Repr = {
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
        _repr += elems(i).textRepr
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
    val textRepr: Repr = {
      var _repr = Repr()
      elems.foreach(elem => {
        _repr += elem.textRepr
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
    val textRepr: Repr = {
      var _repr = Repr()
      elems.foreach(elem => {
        _repr += elem.textRepr
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
    val textRepr: Repr = {
      if (version.textRepr == Repr()) {
        Repr(name)
      } else {
        Repr(name) + ' ' + version.textRepr
      }
    }
    val htmlRepr
      : Repr = Repr("<div class=\"") + name + "\">" + name + ' ' + version.htmlRepr + "</div>"
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
    val textRepr: Repr = {
      var _repr = Repr()
      elems.foreach(elem => {
        _repr += " " * indent
        _repr += elem.textRepr
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
    val textRepr: Repr = self.map(Repr(_)).getOrElse(Repr())
    val htmlRepr: Repr =
      self
        .map(Repr() + "<div class=\"version\">" + Repr(_) + "</div>")
        .getOrElse(Repr())
  }

  ///////////////////////////
  ////// Documentation //////
  ///////////////////////////

  final case class Documentation(tags: Tags, synopsis: Synopsis, body: Body)
      extends AST {
    val textRepr: Repr = {
      var _repr = Repr()
      if (tags != Tags()) {
        _repr += tags.textRepr
        if (synopsis != Synopsis()) {
          _repr += "\n"
        }
      }

      if (synopsis != Synopsis()) {
        _repr += synopsis.textRepr
      }

      if (body != Body()) {
        _repr += "\n"
        _repr += body.textRepr
      }
      _repr
    }
    val htmlRepr: Repr = {
      var _repr = Repr(
        "<!DOCTYPE html><html><head><link rel=\"stylesheet\" href=\"style.css\"></head><body>"
      )
      if (tags != Tags()) {
        _repr += tags.htmlRepr
      }

      if (synopsis != Synopsis()) {
        _repr += synopsis.htmlRepr
      }

      if (body != Body()) {
        _repr += body.htmlRepr
      }
      _repr += "</body></html>"
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

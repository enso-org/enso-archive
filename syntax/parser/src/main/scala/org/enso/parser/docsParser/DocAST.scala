package org.enso.parser.docsParser

import org.enso.parser.AST.Repr

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

  implicit val HasRepr_1: ReprOf[String] = Repr.Text(_)
  implicit val HasRepr_2: ReprOf[Int]    = i => Repr.Text(" " * i)
  implicit val HasRepr_3: ReprOf[Char]   = Repr.Letter(_)
  implicit val HasRepr_4: ReprOf[Repr]   = identity(_)
  implicit def HasRepr_5[T](implicit ev: ReprOf[T]): ReprOf[List[T]] =
    _.foldLeft(Repr.Empty(): Repr)((a, b) => Repr.Seq(a, ev.reprOf(b)))

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
    val textRepr
      : Repr = Repr(formatterType.showableMarker) + elem.textRepr + formatterType.showableMarker

    val htmlRepr
      : Repr = Repr("<") + formatterType.HTMLMarker + ">" + elem.htmlRepr + "</" + formatterType.HTMLMarker + ">"
  }
  object Formatter {
    def apply(formatterType: FormatterType): Formatter =
      Formatter(formatterType, None)
    def apply(formatterType: FormatterType, elem: AST): Formatter =
      Formatter(formatterType, Option(elem))
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

  final case class InvalidIndent(indent: Int, elem: AST) extends InvalidAST {
    val textRepr
      : Repr = Repr(" (INVALID INDENT ") + indent + ") -" + elem.textRepr

    val htmlRepr
      : Repr = Repr("<div class=\"invalidIndent\">") + elem.htmlRepr + "</div>"
  }

  ///////////////////////
  ////// Code Line //////
  ///////////////////////

  final case class CodeLine(code: String) extends AST {
    val textRepr: Repr = Repr('`') + code + '`'

    val htmlRepr: Repr = Repr("<code>") + code + "</code>"
  }

  ////////////////////
  ////// Header //////
  ////////////////////

  final case class Header(elem: AST) extends AST {
    val textRepr: Repr = Repr("\n\n") + elem.textRepr

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
    val textRepr: Repr = Repr(linkType.readableMarker) + name + "](" + url + ")"

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
    elems: List[AST]
  ) extends AST {
    val textRepr: Repr = {
      var _repr = Repr()
      elems.foreach(elem => {
        if (elem.show().contains(" " * (indent + 2))) {
          _repr += elem.textRepr
        } else {
          _repr += " " * indent
          _repr += listType.readableMarker
          _repr += elem.textRepr
          _repr += '\n'
        }
      })
      _repr
    }

    val htmlRepr: Repr = {
      var _repr = Repr("<") + listType.HTMLMarker + ">"
      elems.foreach(elem => {
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
    def apply(indent: Int, listType: ListType): ListBlock =
      ListBlock(indent, listType, Nil)
    def apply(indent: Int, listType: ListType, elem: AST): ListBlock =
      ListBlock(indent, listType, elem :: Nil)
    def apply(indent: Int, listType: ListType, elems: AST*): ListBlock =
      ListBlock(indent, listType, elems.to[List])
  }

  //////////////////////
  ////// Sections //////
  //////////////////////

  trait Section extends Symbol {
    val readableMarker: Char
    val elems: List[AST]

    val textRepr: Repr = {
      var _repr = Repr("\n") + readableMarker
      elems.foreach(elem => {
        _repr += elem.textRepr
        _repr += "\n"
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

  ///// Important /////
  final case class Important(elems: List[AST]) extends Section {
    val readableMarker: Char = '!'
  }

  object Important {
    def apply():            Important = Important(Nil)
    def apply(elem: AST):   Important = Important(elem :: Nil)
    def apply(elems: AST*): Important = Important(elems.to[List])
  }

  ///// Info /////
  final case class Info(elems: List[AST]) extends Section {
    val readableMarker: Char = '?'
  }

  object Info {
    def apply():            Info = Info(Nil)
    def apply(elem: AST):   Info = Info(elem :: Nil)
    def apply(elems: AST*): Info = Info(elems.to[List])
  }

  ///// Example /////
  final case class Example(elems: List[AST]) extends Section {
    val readableMarker: Char = '>'
  }

  object Example {
    def apply():            Example = Example(Nil)
    def apply(elem: AST):   Example = Example(elem :: Nil)
    def apply(elems: AST*): Example = Example(elems.to[List])
  }

  ///// Code /////
  final case class Code(elems: List[AST]) extends Section {
    val readableMarker: Char = ' '

    override val textRepr: Repr = {
      var _repr = Repr("\n")
      elems.foreach(elem => {
        _repr += readableMarker * 4
        _repr += elem.textRepr
        _repr += '\n'
      })
      _repr
    }
  }

  object Code {
    def apply():            Code = Code(Nil)
    def apply(elem: AST):   Code = Code(elem :: Nil)
    def apply(elems: AST*): Code = Code(elems.to[List])
  }

  ///// Text Block /////
  final case class TextBlock(elems: List[AST]) extends Section { // types
    val readableMarker: Char = '\0'
  }

  object TextBlock {
    def apply():            TextBlock = TextBlock(Nil)
    def apply(elem: AST):   TextBlock = TextBlock(elem :: Nil)
    def apply(elems: AST*): TextBlock = TextBlock(elems.to[List])
  }

  ///////////////////
  ////// Chunk //////
  ///////////////////

  trait Chunk extends AST {
    val elems: List[Section]

    val textRepr: Repr = {
      var _repr = Repr()
      elems.foreach(elem => _repr += elem.textRepr)
      _repr
    }

    val htmlRepr: Repr = {
      var _repr = Repr("<div class=\"") + this.getClass.getSimpleName + "\">"
      elems.foreach(elem => _repr += elem.htmlRepr)
      _repr += "</div>"
      _repr
    }
  }

  //////////////////
  ////// Body //////
  //////////////////

  final case class Body(elems: List[Section]) extends Chunk {}
  object Body {
    def apply():                Body = Body(Nil)
    def apply(elem: Section):   Body = Body(elem :: Nil)
    def apply(elems: Section*): Body = Body(elems.to[List])
  }

  //////////////////////
  ////// Synopsis //////
  //////////////////////

  final case class Synopsis(elems: List[Section]) extends Chunk {}
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
    val name: String   = this.getClass.getSimpleName.toUpperCase
    val textRepr: Repr = Repr(name) + ' ' + version.textRepr
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

  final case class Tags(elems: List[TagType]) extends AST {
    val textRepr: Repr = {
      var _repr = Repr()
      elems.foreach(elem => {
        _repr += elem.textRepr
        _repr += "\n"
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
    def apply():                Tags = Tags(Nil)
    def apply(elem: TagType):   Tags = Tags(elem :: Nil)
    def apply(elems: TagType*): Tags = Tags(elems.to[List])
  }

  implicit final class _OptionTagType_(val self: Option[String]) extends AST {
    val textRepr: Repr = self.map(Repr(_)).getOrElse(Repr())
    val htmlRepr: Repr = "<div class=\"version\">" + self
        .map(Repr(_))
        .getOrElse(Repr()) + "</div>"
  }

  ///////////////////////////
  ////// Documentation //////
  ///////////////////////////

  final case class Documentation(tags: Tags, synopsis: Synopsis, body: Body)
      extends AST {
    val textRepr
      : Repr = Repr() + tags.textRepr + synopsis.textRepr + body.textRepr
    val htmlRepr
      : Repr = Repr("<!DOCTYPE html><html><body>") + tags.htmlRepr + synopsis.htmlRepr + body.htmlRepr + "</body></html>"
  }

  object Documentation {
    def apply(tags: Tags, synopsis: Synopsis, body: Body): Documentation =
      new Documentation(tags, synopsis, body)
    def apply(synopsis: Synopsis, body: Body): Documentation =
      new Documentation(Tags(Nil), synopsis, body)
    def apply(synopsis: Synopsis): Documentation =
      new Documentation(Tags(Nil), synopsis, Body(Nil))
    def apply(tags: Tags): Documentation =
      new Documentation(tags, Synopsis(Nil), Body(Nil))
    def apply(tags: Tags, synopsis: Synopsis): Documentation =
      new Documentation(tags, synopsis, Body(Nil))
  }
}

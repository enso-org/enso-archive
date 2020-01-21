package org.enso.gateway.protocol.request.clientcapabilities.textdocument.completion

import io.circe.Decoder

sealed abstract class CompletionItemKind(value: Int)

object CompletionItemKind {

  case object Text extends CompletionItemKind(1)

  case object Method extends CompletionItemKind(2)

  case object Function extends CompletionItemKind(3)

  case object Constructor extends CompletionItemKind(4)

  case object Field extends CompletionItemKind(5)

  case object Variable extends CompletionItemKind(6)

  case object Class extends CompletionItemKind(7)

  case object Interface extends CompletionItemKind(8)

  case object Module extends CompletionItemKind(9)

  case object Property extends CompletionItemKind(10)

  case object Unit extends CompletionItemKind(11)

  case object Value extends CompletionItemKind(12)

  case object Enum extends CompletionItemKind(13)

  case object Keyword extends CompletionItemKind(14)

  case object Snippet extends CompletionItemKind(15)

  case object Color extends CompletionItemKind(16)

  case object File extends CompletionItemKind(17)

  case object Reference extends CompletionItemKind(18)

  case object Folder extends CompletionItemKind(19)

  case object EnumMember extends CompletionItemKind(20)

  case object Constant extends CompletionItemKind(21)

  case object Struct extends CompletionItemKind(22)

  case object Event extends CompletionItemKind(23)

  case object Operator extends CompletionItemKind(24)

  case object TypeParameter extends CompletionItemKind(25)

  implicit val textDocumentSyncKindDecoder: Decoder[CompletionItemKind] =
    Decoder.decodeInt.emap {
      case 1  => Right(Text)
      case 2  => Right(Method)
      case 3  => Right(Function)
      case 4  => Right(Constructor)
      case 5  => Right(Field)
      case 6  => Right(Variable)
      case 7  => Right(Class)
      case 8  => Right(Interface)
      case 9  => Right(Module)
      case 10 => Right(Property)
      case 11 => Right(Unit)
      case 12 => Right(Value)
      case 13 => Right(Enum)
      case 14 => Right(Keyword)
      case 15 => Right(Snippet)
      case 16 => Right(Color)
      case 17 => Right(File)
      case 18 => Right(Reference)
      case 19 => Right(Folder)
      case 20 => Right(EnumMember)
      case 21 => Right(Constant)
      case 22 => Right(Struct)
      case 23 => Right(Event)
      case 24 => Right(Operator)
      case 25 => Right(TypeParameter)
      case _  => Left("Invalid CompletionItemKind")
    }
}

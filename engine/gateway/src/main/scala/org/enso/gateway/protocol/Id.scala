package org.enso.gateway.protocol

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
import cats.syntax.functor._
import io.circe.syntax._
import org.enso.{languageserver => ls}

/** Id of [[RequestOrNotification]] or [[Response]]. */
sealed trait Id {
  def toLsModel: ls.Id = this match {
    case Id.Number(value) => ls.Id.Number(value)
    case Id.Text(value)   => ls.Id.Text(value)
  }
}
object Id {
  def fromLsModel(id: ls.Id): Id = id match {
    case ls.Id.Number(value) => Id.Number(value)
    case ls.Id.Text(value)   => Id.Text(value)
  }

  implicit val idEncoder: Encoder[Id] = Encoder.instance {
    case number: Number => number.asJson
    case text: Text     => text.asJson
  }

  implicit val idDecoder: Decoder[Id] = List[Decoder[Id]](
    Decoder[Number].widen,
    Decoder[Text].widen
  ).reduceLeft(_ or _)

  /** A number id. */
  case class Number(value: Int) extends Id
  object Number {
    implicit val idNumberEncoder: Encoder[Number] = deriveUnwrappedEncoder
    implicit val idNumberDecoder: Decoder[Number] = deriveUnwrappedDecoder
  }

  /** A string id. */
  case class Text(value: String) extends Id
  object Text {
    implicit val idStringEncoder: Encoder[Text] = deriveUnwrappedEncoder
    implicit val idStringDecoder: Decoder[Text] = deriveUnwrappedDecoder
  }
}

package org.enso.languageserver.data
import java.io.File
import java.util.UUID

import io.circe.{ACursor, Decoder, DecodingFailure, Encoder, Json}

sealed abstract class Capability(val method: String)

//TODO[MK]: Migrate to actual Path, once it is implemented.
case class CanEdit(path: String) extends Capability(CanEdit.methodName)
object CanEdit {
  val methodName = "canEdit"
}

object Capability {
  import cats.syntax.functor._
  import io.circe.generic.auto._
  import io.circe.syntax._

  implicit val encoder: Encoder[Capability] = {
    case cap: CanEdit => cap.asJson
  }

  implicit val decoder: Decoder[Capability] = Decoder[CanEdit].widen
}

case class CapabilityRegistration(id: UUID, capability: Capability)

object CapabilityRegistration {
  import io.circe.syntax._
  import io.circe.generic.auto._

  private val idField      = "id"
  private val methodField  = "method"
  private val optionsField = "registerOptions"

  implicit val encoder: Encoder[CapabilityRegistration] = registration =>
    Json.obj(
      idField      -> registration.id.asJson,
      methodField  -> registration.capability.method.asJson,
      optionsField -> registration.capability.asJson
    )

  implicit val decoder: Decoder[CapabilityRegistration] = json => {
    def resolveOptions(
      method: String,
      cursor: ACursor
    ): Decoder.Result[Capability] = method match {
      case CanEdit.methodName => cursor.as[CanEdit]
      case _ =>
        Left(DecodingFailure("Unrecognized method.", List()))
    }

    for {
      id         <- json.downField(idField).as[UUID]
      method     <- json.downField(methodField).as[String]
      capability <- resolveOptions(method, json.downField(optionsField))
    } yield CapabilityRegistration(id, capability)
  }
}

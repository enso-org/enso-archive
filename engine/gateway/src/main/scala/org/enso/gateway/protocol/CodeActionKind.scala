package org.enso.gateway.protocol

import io.circe.{Decoder, Encoder}

sealed abstract class CodeActionKind(val value: String)
object CodeActionKind {
  private val empty                 = ""
  private val quickfix              = "quickfix"
  private val refactor              = "refactor"
  private val refactorExtract       = "refactor.extract"
  private val refactorInline        = "refactor.inline"
  private val refactorRewrite       = "refactor.rewrite"
  private val source                = "source"
  private val sourceOrganizeImports = "source.organizeImports"
  private val invalidCodeActionKind = "Invalid CodeActionKind"

  case object Empty extends CodeActionKind(empty)

  case object QuickFix extends CodeActionKind(quickfix)

  case object Refactor extends CodeActionKind(refactor)

  case object RefactorExtract extends CodeActionKind(refactorExtract)

  case object RefactorInline extends CodeActionKind(refactorInline)

  case object RefactorRewrite extends CodeActionKind(refactorRewrite)

  case object CodeActionKind extends CodeActionKind(source)

  case object SourceOrganizeImports
      extends CodeActionKind(sourceOrganizeImports)

  implicit val codeActionKindDecoder: Decoder[CodeActionKind] =
    Decoder.decodeString.emap {
      case `empty`                 => Right(Empty)
      case `quickfix`              => Right(QuickFix)
      case `refactor`              => Right(Refactor)
      case `refactorExtract`       => Right(RefactorExtract)
      case `refactorInline`        => Right(RefactorInline)
      case `refactorRewrite`       => Right(RefactorRewrite)
      case `source`                => Right(CodeActionKind)
      case `sourceOrganizeImports` => Right(SourceOrganizeImports)
      case _                       => Left(invalidCodeActionKind)
    }

  implicit val codeActionKindEncoder: Encoder[CodeActionKind] =
    Encoder.encodeString.contramap(_.value)
}

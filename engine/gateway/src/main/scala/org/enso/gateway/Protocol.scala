package org.enso.gateway

import io.circe.{Decoder, Encoder, Printer}
import io.circe.parser._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder, deriveEnumerationDecoder, deriveEnumerationEncoder, deriveUnwrappedDecoder, deriveUnwrappedEncoder}
import io.circe.generic.extras.Configuration
import io.circe.syntax._
import cats.syntax.functor._

/** Types implementing language server protocol.
  *
  * The protocol always is single request -> single or no response.
  */
object Protocol {
  val jsonRPCVersion = "2.0"

  object DerivationConfig {
    implicit val configuration: Configuration = Configuration.default.withDiscriminator("method")
  }

  object ShapesDerivation {
    import shapeless.{Coproduct, Generic}

    implicit def sealedTraitEncoder[A, Repr <: Coproduct](implicit
                                                          generic: Generic.Aux[A, Repr],
                                                          coproductEncoder: Encoder[Repr]
                                                         ): Encoder[A] = coproductEncoder.contramap(generic.to)
  }

  import io.circe.shapes._
  import ShapesDerivation._

  sealed trait RequestOrNotification {
    def jsonrpc: String
    require(jsonrpc == jsonRPCVersion, s"jsonrpc must be $jsonRPCVersion")
  }
  object RequestOrNotification {
    import DerivationConfig._
    implicit val requestOrNotificationDecoder: Decoder[RequestOrNotification] = deriveConfiguredDecoder
    implicit val requestOrNotificationEncoder: Encoder[RequestOrNotification] = deriveConfiguredEncoder
  }

  sealed trait Request extends RequestOrNotification {
    def jsonrpc: String
    def id: Id
    def method: String
    def params: Option[Params]
  }

  sealed trait Notification extends RequestOrNotification {
    def jsonrpc: String
    def method: String
    def params: Option[Params]
  }

  case class Response private(
                       jsonrpc: String,
                       id: Option[Id],
                       result: Option[Result],
                       error: Option[Error]
                     )
  object Response {
    def result(id: Option[Id] = None, result: Result): Response = Response(jsonRPCVersion, id, Some(result), None)
    def error(id: Option[Id] = None, error: Error): Response = Response(jsonRPCVersion, id, None, Some(error))

    implicit val responseEncoder: Encoder[Response] = deriveEncoder
    implicit val responseDecoder: Decoder[Response] = deriveDecoder
  }

  sealed trait Id
  object Id {
    implicit val idDecoder: Decoder[Id] = List[Decoder[Id]](
      Decoder[Number].widen,
      Decoder[String].widen
    ).reduceLeft(_ or _)

    case class Number(value: Int) extends Id
    object Number {
      implicit val idNumberEncoder: Encoder[Number] = deriveUnwrappedEncoder
      implicit val idNumberDecoder: Decoder[Number] = deriveUnwrappedDecoder
    }

    case class String(value: Predef.String) extends Id
    object String {
      implicit val idStringEncoder: Encoder[String] = deriveUnwrappedEncoder
      implicit val idStringDecoder: Decoder[String] = deriveUnwrappedDecoder
    }
  }

  sealed trait Params
  object Params {
    implicit val paramsDecoder: Decoder[Params] = List[Decoder[Params]](
//      Decoder[Array].widen,
      Decoder[InitializeParams].widen,
      Decoder[InitializedParams].widen,
    ).reduceLeft(_ or _)

    case class Array(value: Seq[Option[Param]]) extends Params
    object Array {
//      implicit val paramsArrayEncoder: Encoder[Array] = deriveUnwrappedEncoder
//      implicit val paramsArrayDecoder: Decoder[Array] = deriveUnwrappedDecoder
    }

    //object
  }

  sealed trait Param
  object Param {
    implicit val paramDecoder: Decoder[Param] = List[Decoder[Param]](
      Decoder.apply[Number].widen,
      Decoder[Boolean].widen,
      Decoder[String].widen,
//      Decoder[Array].widen,
      Decoder[ClientInfo].widen,
      Decoder[ClientCapabilities].widen,
      Decoder[InitializationOptions].widen,
      Decoder[Trace].widen,
      Decoder[WorkspaceFolder].widen,
    ).reduceLeft(_ or _)

    case class String(value: Predef.String) extends Param
    object String {
      implicit val paramStringEncoder: Encoder[String] = deriveUnwrappedEncoder
      implicit val paramStringDecoder: Decoder[String] = deriveUnwrappedDecoder
    }

    case class Number(value: Int) extends Param
    object Number {
      implicit val paramNumberEncoder: Encoder[Number] = deriveUnwrappedEncoder
      implicit val paramNumberDecoder: Decoder[Number] = deriveUnwrappedDecoder
    }

    case class Boolean(value: scala.Boolean) extends Param
    object Boolean {
      implicit val paramBooleanEncoder: Encoder[Boolean] = deriveUnwrappedEncoder
      implicit val paramBooleanDecoder: Decoder[Boolean] = deriveUnwrappedDecoder
    }

    case class Array(value: Seq[Param]) extends Param
    object Array {
//      implicit val paramArrayEncoder: Encoder[Array] = deriveUnwrappedEncoder
//      implicit val paramArrayDecoder: Decoder[Array] = deriveUnwrappedDecoder
    }

    //object
  }

  sealed trait Result
  object Result {
    implicit val resultDecoder: Decoder[Result] = List[Decoder[Result]](
      Decoder[Number].widen,
      Decoder[Boolean].widen,
      Decoder[String].widen,
      Decoder[InitializeResult].widen
    ).reduceLeft(_ or _)

    case class String(value: Predef.String) extends Result
    object String {
      implicit val resultStringEncoder: Encoder[String] = deriveUnwrappedEncoder
      implicit val resultStringDecoder: Decoder[String] = deriveUnwrappedDecoder
    }

    case class Number(value: Int) extends Result
    object Number {
      implicit val resultNumberEncoder: Encoder[Number] = deriveUnwrappedEncoder
      implicit val resultNumberDecoder: Decoder[Number] = deriveUnwrappedDecoder
    }

    case class Boolean(value: scala.Boolean) extends Result
    object Boolean {
      implicit val resultBooleanEncoder: Encoder[Boolean] = deriveUnwrappedEncoder
      implicit val resultBooleanDecoder: Decoder[Boolean] = deriveUnwrappedDecoder
    }

    //object
  }

  sealed trait Error {
    def code: Int
    def message: String
    def data: Option[Data]
  }
  object Error {
    implicit val errorDecoder: Decoder[Error] = List[Decoder[Error]](
      Decoder[InitializeError].widen
    ).reduceLeft(_ or _)
  }

  sealed trait Data
  object Data {
    implicit val dataDecoder: Decoder[Data] = List[Decoder[Data]](
      Decoder[Number].widen,
      Decoder[Boolean].widen,
      Decoder[Array].widen,
      Decoder[String].widen,
      Decoder[InitializeData].widen,
    ).reduceLeft(_ or _)

    case class String(value: Predef.String) extends Data
    object String {
      implicit val dataStringEncoder: Encoder[String] = deriveUnwrappedEncoder
      implicit val dataStringDecoder: Decoder[String] = deriveUnwrappedDecoder
    }

    case class Number(value: Int) extends Data
    object Number {
      implicit val dataNumberEncoder: Encoder[Number] = deriveUnwrappedEncoder
      implicit val dataNumberDecoder: Decoder[Number] = deriveUnwrappedDecoder
    }

    case class Boolean(value: scala.Boolean) extends Data
    object Boolean {
      implicit val dataBooleanEncoder: Encoder[Boolean] = deriveUnwrappedEncoder
      implicit val dataBooleanDecoder: Decoder[Boolean] = deriveUnwrappedDecoder
    }

    case class Array(value: Seq[Data]) extends Data
    object Array {
      implicit val dataArrayEncoder: Encoder[Array] = deriveUnwrappedEncoder
      implicit val dataArrayDecoder: Decoder[Array] = deriveUnwrappedDecoder
    }

    //object
  }

  case class initialize(
                         jsonrpc: String,
                         id: Id,
                         method: String,
                         params: Option[InitializeParams] = None
                       ) extends Request
  object initialize {
    import DerivationConfig._
    implicit val initializeDecoder: Decoder[initialize] = deriveConfiguredDecoder
    implicit val initializeEncoder: Encoder[initialize] = deriveConfiguredEncoder
  }

  case class InitializeParams(
                               processId: Option[Int] = None,
                               clientInfo: Option[ClientInfo] = None,
                               rootPath: Option[String] = None, //deprecated: use rootUri, LSP Spec
                               rootUri: Option[DocumentUri] = None,
                               initializationOptions: Option[InitializationOptions] = None,
                               capabilities: ClientCapabilities,
                               trace: Option[Trace] = None,
                               workspaceFolders: Option[Seq[WorkspaceFolder]] = None
                             ) extends Params
  object InitializeParams {
    implicit val initializeParamsEncoder: Encoder[InitializeParams] = deriveEncoder
    implicit val initializeParamsDecoder: Decoder[InitializeParams] = deriveDecoder
  }

  case class InitializationOptions(value: String) extends Param
  object InitializationOptions {
    implicit val initializationOptionsEncoder: Encoder[InitializationOptions] = deriveUnwrappedEncoder
    implicit val initializationOptionsDecoder: Decoder[InitializationOptions] = deriveUnwrappedDecoder
  }

  case class InitializeResult(
                               capabilities: ServerCapabilities,
                               serverInfo: Option[ServerInfo] = None
                             ) extends Result
  object InitializeResult {
    implicit val initializeResultEncoder: Encoder[InitializeResult] = deriveEncoder
    implicit val initializeResultDecoder: Decoder[InitializeResult] = deriveDecoder
  }

  case class InitializeError(
                              code: Int, //ErrorCodes.unknownProtocolVersion,
                              message: String,
                              data: Option[InitializeData] = None
                            ) extends Error

  object InitializeError {
    implicit val initializeErrorEncoder: Encoder[InitializeError] = deriveEncoder
    implicit val initializeErrorDecoder: Decoder[InitializeError] = deriveDecoder
  }

  case class InitializeData(
                             retry: Boolean
                           ) extends Data
  object InitializeData {
    implicit val initializeDataEncoder: Encoder[InitializeData] = deriveEncoder
    implicit val initializeDataDecoder: Decoder[InitializeData] = deriveDecoder
  }

  case class initialized(
                         jsonrpc: String,
                         method: String,
                         params: Option[InitializedParams] = None
                       ) extends Notification
  object initialized {
    import DerivationConfig._
    implicit val initializedDecoder: Decoder[initialized] = deriveConfiguredDecoder
    implicit val initializedEncoder: Encoder[initialized] = deriveConfiguredEncoder
  }

  case class InitializedParams() extends Params
  object InitializedParams {
    implicit val initializedParamsEncoder: Encoder[InitializedParams] = deriveEncoder
    implicit val initializedParamsDecoder: Decoder[InitializedParams] = deriveDecoder
  }

  case class ServerInfo(
                         name: String,
                         version: Option[String] = None
                       )
  object ServerInfo {
    implicit val serverInfoEncoder: Encoder[ServerInfo] = deriveEncoder
    implicit val serverInfoDecoder: Decoder[ServerInfo] = deriveDecoder
  }

  case class ServerCapabilities(
                                 //textDocumentSync: Option[TextDocumentSync] = None,
                                 //completionProvider: Option[CompletionOptions] = None,
                                 //hoverProvider: Option[HoverProvider] = None,
                                 //signatureHelpProvider: Option[SignatureHelpOptions] = None,
                                 //declarationProvider: Option[DeclarationProvider] = None,
                                 //definitionProvider: Option[DefinitionProvider] = None,
                                 //typeDefinitionProvider: Option[TypeDefinitionProvider] = None,
                                 //implementationProvider: Option[ImplementationProvider] = None,
                                 //referencesProvider: Option[ReferencesProvider] = None,
                                 //documentHighlightProvider: Option[DocumentHighlightProvider] = None,
                                 //documentSymbolProvider: Option[DocumentSymbolProvider] = None,
                                 //codeActionProvider: Option[CodeActionProvider] = None,
                                 //codeLensProvider: Option[CodeLensProvider] = None,
                                 //documentLinkProvider: Option[DocumentLinkProvider] = None,
                                 //colorProvider: Option[ColorProvider] = None,
                                 //documentFormattingProvider: Option[DocumentFormattingProvider] = None,
                                 //documentRangeFormattingProvider: Option[DocumentRangeFormattingProvider] = None,
                                 //documentOnTypeFormattingProvider: Option[DocumentOnTypeFormattingProvider] = None,
                                 //renameProvider: Option[RenameProvider] = None,
                                 //foldingRangeProvider: Option[FoldingRangeProvider] = None,
                                 //executeCommandProvider: Option[ExecuteCommandOptions] = None,
                                 workspaceSymbolProvider: Option[Boolean] = None,
                                 workspace: Option[ServerCapabilitiesWorkspace] = None,
                                 experimental: Option[ServerCapabilitiesExperimental] = None
                               )
  object ServerCapabilities {
    implicit val serverCapabilitiesEncoder: Encoder[ServerCapabilities] = deriveEncoder
    implicit val serverCapabilitiesDecoder: Decoder[ServerCapabilities] = deriveDecoder
  }

  case class ServerCapabilitiesExperimental(value: String) extends AnyVal
  object ServerCapabilitiesExperimental {
    implicit val serverCapabilitiesExperimentalEncoder: Encoder[ServerCapabilitiesExperimental] = deriveUnwrappedEncoder
    implicit val serverCapabilitiesExperimentalDecoder: Decoder[ServerCapabilitiesExperimental] = deriveUnwrappedDecoder
  }

  case class ServerCapabilitiesWorkspace(
                                          //workspaceFolders: Option[WorkspaceFoldersServerCapabilities]
                                        )
  object ServerCapabilitiesWorkspace {
    implicit val serverCapabilitiesWorkspaceEncoder: Encoder[ServerCapabilitiesWorkspace] = deriveEncoder
    implicit val serverCapabilitiesWorkspaceDecoder: Decoder[ServerCapabilitiesWorkspace] = deriveDecoder
  }

  type DocumentUri = String

  case class ClientInfo(
                         name: String,
                         version: Option[String]
                       ) extends Param
  object ClientInfo {
    implicit val clientInfoEncoder: Encoder[ClientInfo] = deriveEncoder
    implicit val clientInfoDecoder: Decoder[ClientInfo] = deriveDecoder
  }

  sealed trait Trace extends Param
  object Trace {
    implicit val traceOffEncoder: Encoder[Trace] = deriveEnumerationEncoder
    implicit val traceOffDecoder: Decoder[Trace] = deriveEnumerationDecoder

    case object Off extends Trace
    case object Messages extends Trace
    case object Verbose extends Trace
  }

  sealed trait WorkspaceFolder extends Param
  object WorkspaceFolder {
    implicit val workspaceFolderDecoder: Decoder[WorkspaceFolder] = List[Decoder[WorkspaceFolder]](
      Decoder[WorkspaceFolderImpl].widen,
    ).reduceLeft(_ or _)

    //???
    case class WorkspaceFolderImpl() extends WorkspaceFolder
    object WorkspaceFolderImpl {
      implicit val workspaceFolderImplEncoder: Encoder[WorkspaceFolderImpl] = deriveEncoder
      implicit val workspaceFolderImplDecoder: Decoder[WorkspaceFolderImpl] = deriveDecoder
    }
  }

  case class ClientCapabilities(
                                 workspace: Option[ClientCapabilitiesWorkspace] = None,
                                 textDocument: Option[TextDocumentClientCapabilities] = None,
                                 experimental: Option[ClientCapabilitiesExperimental] = None
                               ) extends Param
  object ClientCapabilities {
    implicit val clientCapabilitiesEncoder: Encoder[ClientCapabilities] = deriveEncoder
    implicit val clientCapabilitiesDecoder: Decoder[ClientCapabilities] = deriveDecoder
  }

  case class ClientCapabilitiesExperimental(value: String) extends AnyVal
  object ClientCapabilitiesExperimental {
    implicit val clientCapabilitiesExperimentalEncoder: Encoder[ClientCapabilitiesExperimental] = deriveUnwrappedEncoder
    implicit val clientCapabilitiesExperimentalDecoder: Decoder[ClientCapabilitiesExperimental] = deriveUnwrappedDecoder
  }

  case class ClientCapabilitiesWorkspace(
                                          applyEdit: Option[Boolean] = None,
                                          //workspaceEdit: Option[WorkspaceEditClientCapabilities] = None,
                                          //didChangeConfiguration: Option[DidChangeConfigurationClientCapabilities] = None,
                                          //didChangeWatchedFiles: Option[DidChangeWatchedFilesClientCapabilities] = None,
                                          //symbol: Option[WorkspaceSymbolClientCapabilities] = None,
                                          //executeCommand: Option[ExecuteCommandClientCapabilities] = None
                                        )
  object ClientCapabilitiesWorkspace {
    implicit val clientCapabilitiesWorkspaceEncoder: Encoder[ClientCapabilitiesWorkspace] = deriveEncoder
    implicit val clientCapabilitiesWorkspaceDecoder: Decoder[ClientCapabilitiesWorkspace] = deriveDecoder
  }

  case class TextDocumentClientCapabilities(
                                             //synchronization: Option[TextDocumentSyncClientCapabilities] = None,
                                             //completion: Option[CompletionClientCapabilities] = None,
                                             //hover: Option[HoverClientCapabilities] = None,
                                             //signatureHelp: Option[SignatureHelpClientCapabilities] = None,
                                             //declaration: Option[DeclarationClientCapabilities] = None,
                                             //definition: Option[DefinitionClientCapabilities] = None,
                                             //typeDefinition: Option[TypeDefinitionClientCapabilities] = None,
                                             //implementation: Option[ImplementationClientCapabilities] = None,
                                             //references: Option[ReferenceClientCapabilities] = None,
                                             //documentHighlight: Option[DocumentHighlightClientCapabilities] = None,
                                             //documentSymbol: Option[DocumentSymbolClientCapabilities] = None,
                                             //codeAction: Option[CodeActionClientCapabilities] = None,
                                             //codeLens: Option[CodeLensClientCapabilities] = None,
                                             //documentLink: Option[DocumentLinkClientCapabilities] = None,
                                             //colorProvider: Option[DocumentColorClientCapabilities] = None,
                                             //formatting: Option[DocumentFormattingClientCapabilities] = None,
                                             //rangeFormatting: Option[DocumentRangeFormattingClientCapabilities] = None,
                                             //onTypeFormatting: Option[DocumentOnTypeFormattingClientCapabilities] = None,
                                             //rename: Option[RenameClientCapabilities] = None,
                                             //publishDiagnostics: Option[PublishDiagnosticsClientCapabilities] = None,
                                             //foldingRange: Option[FoldingRangeClientCapabilities] = None
                                           )
  object TextDocumentClientCapabilities {
    implicit val textDocumentClientCapabilitiesEncoder: Encoder[TextDocumentClientCapabilities] = deriveEncoder
    implicit val textDocumentClientCapabilitiesDecoder: Decoder[TextDocumentClientCapabilities] = deriveDecoder
  }


  object ErrorCodes {
    // Defined by JSON RPC
    val parseError: Int = -32700
    val invalidRequest: Int = -32600
    val methodNotFound: Int = -32601
    val invalidParams: Int = -32602
    val internalError: Int = -32603
    val serverErrorStart: Int = -32099
    val serverErrorEnd: Int = -32000
    val serverNotInitialized: Int = -32002
    val unknownErrorCode: Int = -32001

    // Defined by the protocol
    val requestCancelled: Int = -32800
    val contentModified: Int = -32801

    // initialize
    val unknownProtocolVersion: Int = 1
  }
}

/** Helper for implementing protocol over text-based transport.
  *
  * Requests and responses are marshaled as text using JSON
  * (and default circe serialzation schema).
  */
trait Protocol {
  import Protocol._

  /** Generate [[Response]] for a given [[RequestOrNotification]].
    *
    * Any [[Throwable]] thrown out of implementation will be translated into
    * [[Response]] message.
    */
  def handleRequestOrNotification(requestOrNotification: RequestOrNotification): Option[Response]

  def handleInput(input: String): Option[String] = {
    try {
      import ShapesDerivation._
      decode[RequestOrNotification](input) match {
        case Left(err) => throw err
        case Right(requestOrNotification: RequestOrNotification) =>
          for {
            response <- handleRequestOrNotification(requestOrNotification)
          } yield response.asJson.printWith(Printer.noSpaces.copy(dropNullValues = true))
      }
    } catch {
      case e: Throwable => throw e // TODO
    }
  }
}

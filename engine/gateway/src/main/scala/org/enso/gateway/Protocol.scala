package org.enso.gateway

import io.circe.{Decoder, Encoder}
import io.circe.parser._
import io.circe.generic.semiauto._
import io.circe.syntax._

/** Types implementing language server protocol.
  *
  * The protocol always is single request -> single response.
  */
object Protocol {

  object ShapesDerivation {
    import shapeless.{ Coproduct, Generic }

    implicit def sealedTraitEncoder[A, Repr <: Coproduct](implicit
                                                          generic: Generic.Aux[A, Repr],
                                                          reprEncoder: Encoder[Repr]
                                                       ): Encoder[A] = reprEncoder.contramap(generic.to)

    implicit def sealedTraitDecoder[A, Repr <: Coproduct](implicit
                                                          generic: Generic.Aux[A, Repr],
                                                          reprDecoder: Decoder[Repr]
                                                       ): Decoder[A] = reprDecoder.map(generic.from)

  }

  import io.circe.shapes._
  import ShapesDerivation._

  sealed trait Message {
    val jsonrpc: String = "2.0"
  }
  object Message {
    implicit def messageEncoder: Encoder[Message] = deriveEncoder
    implicit def messageDecoder: Decoder[Message] = deriveDecoder
  }

  sealed trait RequestOrNotification
  object RequestOrNotification {
    implicit def requestOrNotificationEncoder: Encoder[RequestOrNotification] = deriveEncoder
    implicit def requestOrNotificationDecoder: Decoder[RequestOrNotification] = deriveDecoder
  }

  sealed trait RequestMessage extends Message with RequestOrNotification {
    def id: Id
    def method: String
    def params: Option[Params] = None
  }
  object RequestMessage {
    implicit def requestEncoder: Encoder[RequestMessage] = deriveEncoder
    implicit def requestDecoder: Decoder[RequestMessage] = deriveDecoder
  }

  sealed trait NotificationMessage extends Message with RequestOrNotification {
    def method: String
    def params: Option[Params] = None
  }
  object NotificationMessage {
    implicit def notificationEncoder: Encoder[NotificationMessage] = deriveEncoder
    implicit def notificationDecoder: Decoder[NotificationMessage] = deriveDecoder
  }

  case class NotificationMessageImpl(method: String) extends NotificationMessage
  object NotificationMessageImpl {
    implicit def notificationImplEncoder: Encoder[NotificationMessageImpl] = deriveEncoder
    implicit def notificationImplDecoder: Decoder[NotificationMessageImpl] = deriveDecoder
  }

  case class ResponseMessage(
    id: Option[Id] = None,
    result: Option[Result] = None,
    error: Option[ResponseError] = None
  ) extends Message
  object ResponseMessage {
    //def result
    //def error

    implicit def responseEncoder: Encoder[ResponseMessage] = deriveEncoder
    implicit def responseDecoder: Decoder[ResponseMessage] = deriveDecoder
  }

  sealed trait Id
  object Id {
    implicit def idEncoder: Encoder[Id] = deriveEncoder
    implicit def idDecoder: Decoder[Id] = deriveDecoder

    case class Number(value: Int) extends /*AnyVal with*/ Id
    object Number {
      implicit def requestEncoder: Encoder[Number] = deriveEncoder
      implicit def requestDecoder: Decoder[Number] = deriveDecoder
    }
    case class String(value: Predef.String) extends /*AnyVal with*/ Id
    object String {
      implicit def requestEncoder: Encoder[String] = deriveEncoder
      implicit def requestDecoder: Decoder[String] = deriveDecoder
    }
  }

  sealed trait Params
  object Params {
    implicit def paramsEncoder: Encoder[Params] = deriveEncoder
    implicit def paramsDecoder: Decoder[Params] = deriveDecoder

    case class Array(value: Seq[Params]) extends /*AnyVal with*/ Params
    //object
  }

  sealed trait Result
  object Result {
    implicit def resultEncoder: Encoder[Result] = deriveEncoder
    implicit def resultDecoder: Decoder[Result] = deriveDecoder

    case class String(value: Predef.String) extends /*AnyVal with*/ Result
    object String {
      implicit def stringEncoder: Encoder[String] = deriveEncoder
      implicit def stringDecoder: Decoder[String] = deriveDecoder
    }

    case class Number(value: Int) extends /*AnyVal with*/ Result
    object Number {
      implicit def numberEncoder: Encoder[Number] = deriveEncoder
      implicit def numberDecoder: Decoder[Number] = deriveDecoder
    }

    case class Boolean(value: scala.Boolean) extends /*AnyVal with*/ Result
    object Boolean {
      implicit def booleanEncoder: Encoder[Boolean] = deriveEncoder
      implicit def booleanDecoder: Decoder[Boolean] = deriveDecoder
    }

    //object
  }

  sealed trait ResponseError {
    def code: Int
    def message: String
    def data: Option[Data] = None
  }
  object ResponseError {
    implicit def responseErrorEncoder: Encoder[ResponseError] = deriveEncoder
    implicit def responseErrorDecoder: Decoder[ResponseError] = deriveDecoder
  }

  sealed trait Data
  object Data {
    implicit def dataEncoder: Encoder[Data] = deriveEncoder
    implicit def dataDecoder: Decoder[Data] = deriveDecoder

    case class String(value: Predef.String) extends /*AnyVal with*/ Data
    object String {
      implicit def stringEncoder: Encoder[String] = deriveEncoder
      implicit def stringDecoder: Decoder[String] = deriveDecoder
    }

    case class Number(value: Int) extends /*AnyVal with*/ Data
    object Number {
      implicit def numberEncoder: Encoder[Number] = deriveEncoder
      implicit def numberDecoder: Decoder[Number] = deriveDecoder
    }

    case class Boolean(value: scala.Boolean) extends /*AnyVal with*/ Data
    object Boolean {
      implicit def booleanEncoder: Encoder[Boolean] = deriveEncoder
      implicit def booleanDecoder: Decoder[Boolean] = deriveDecoder
    }

    case class Array(value: Seq[Data]) extends /*AnyVal with*/ Data
    object Array {
      implicit def arrayEncoder: Encoder[ResponseError] = deriveEncoder
      implicit def arrayDecoder: Decoder[ResponseError] = deriveDecoder
    }

    //object
  }

  case class Initialize(id: Id, override val params: Option[InitializeParams] = None) extends RequestMessage {
    override def method: String = "initialize"
  }
  object Initialize {
    implicit def initializeEncoder: Encoder[Initialize] = deriveEncoder
    implicit def initializeDecoder: Decoder[Initialize] = deriveDecoder
  }

  case class InitializeParams(
//                               processId: Option[Int] = None,
//                               clientInfo: Option[ClientInfo] = None,
//                               @deprecated("use rootUri", "LSP Spec") rootPath: Option[String] = None,
//                               rootUri: Option[DocumentUri] = None,
////                               initializationOptions: Option[Any] = None,
//                               capabilities: ClientCapabilities,
//                               trace: Option[Trace] = None,
//                               workspaceFolders: Option[Seq[WorkspaceFolder]] = None
                             ) extends Params
  object InitializeParams {
    implicit def requestEncoder: Encoder[InitializeParams] = deriveEncoder
    implicit def requestDecoder: Decoder[InitializeParams] = deriveDecoder
  }

  case class InitializeResult(
    capabilities: ServerCapabilities,
    serverInfo: Option[ServerInfo] = None
  ) extends Result
  object InitializeResult {
    implicit def initializeResultEncoder: Encoder[InitializeResult] = deriveEncoder
    implicit def initializeResultDecoder: Decoder[InitializeResult] = deriveDecoder
  }

  case class InitializeError(
                              message: String,
                              override val data: Option[InitializeData] = None
                            ) extends ResponseError {
    override def code: Int = ErrorCodes.unknownProtocolVersion
  }

  object InitializeError {
    implicit def initializeErrorEncoder: Encoder[InitializeError] = deriveEncoder
    implicit def initializeErrorDecoder: Decoder[InitializeError] = deriveDecoder
  }

  case class InitializeData(
                             retry: Boolean
                           ) extends Data
  object InitializeData {
    implicit def initializeDataEncoder: Encoder[InitializeData] = deriveEncoder
    implicit def initializeDataDecoder: Decoder[InitializeData] = deriveDecoder
  }

  case class ServerInfo(
    name: String,
    version: Option[String] = None
  )
  object ServerInfo {
    implicit def serverInfoEncoder: Encoder[ServerInfo] = deriveEncoder
    implicit def serverInfoDecoder: Decoder[ServerInfo] = deriveDecoder
  }

  case class ServerCapabilities(
//    textDocumentSync: Option[TextDocumentSync] = None,
//    completionProvider: Option[CompletionOptions] = None,
//    hoverProvider: Option[HoverProvider] = None,
//    signatureHelpProvider: Option[SignatureHelpOptions] = None,
//    declarationProvider: Option[DeclarationProvider] = None,
//    definitionProvider: Option[DefinitionProvider] = None,
//    typeDefinitionProvider: Option[TypeDefinitionProvider] = None,
//    implementationProvider: Option[ImplementationProvider] = None,
//    referencesProvider: Option[ReferencesProvider] = None,
//    documentHighlightProvider: Option[DocumentHighlightProvider] = None,
//    documentSymbolProvider: Option[DocumentSymbolProvider] = None,
//    codeActionProvider: Option[CodeActionProvider] = None,
//    codeLensProvider: Option[CodeLensProvider] = None,
//    documentLinkProvider: Option[DocumentLinkProvider] = None,
//    colorProvider: Option[ColorProvider] = None,
//    documentFormattingProvider: Option[DocumentFormattingProvider] = None,
//    documentRangeFormattingProvider: Option[DocumentRangeFormattingProvider] = None,
//    documentOnTypeFormattingProvider: Option[DocumentOnTypeFormattingProvider] = None,
//    renameProvider: Option[RenameProvider] = None,
//    foldingRangeProvider: Option[FoldingRangeProvider] = None,
//    executeCommandProvider: Option[ExecuteCommandOptions] = None,
    workspaceSymbolProvider: Option[Boolean] = None,
    workspace: Option[ServerCapabilitiesWorkspace] = None,
//    experimental: Option[Any] = None
  )
  object ServerCapabilities {
    implicit def serverCapabilitiesEncoder: Encoder[ServerCapabilities] = deriveEncoder
    implicit def serverCapabilitiesDecoder: Decoder[ServerCapabilities] = deriveDecoder
  }

  case class ServerCapabilitiesWorkspace(
//    workspaceFolders: Option[WorkspaceFoldersServerCapabilities]
  )
  object ServerCapabilitiesWorkspace {
    implicit def serverCapabilitiesWorkspaceEncoder: Encoder[ServerCapabilitiesWorkspace] = deriveEncoder
    implicit def serverCapabilitiesWorkspaceDecoder: Decoder[ServerCapabilitiesWorkspace] = deriveDecoder
  }


  type DocumentUri = String

  case class ClientInfo(
                         name: String,
                         version: Option[String]
                       )

  sealed trait Trace
  object Trace {
    case object Off extends Trace
    case object Messages extends Trace
    case object Verbose extends Trace
  }

  sealed trait WorkspaceFolder
  object WorkspaceFolder {
    //???
  }

  case class ClientCapabilities(
    workspace: Option[ClientCapabilitiesWorkspace] = None,
    textDocument: Option[TextDocumentClientCapabilities] = None,
//    experimental: Option[Any] = None
  )

  case class ClientCapabilitiesWorkspace(
    applyEdit: Option[Boolean] = None,
//    workspaceEdit: Option[WorkspaceEditClientCapabilities] = None,
//    didChangeConfiguration: Option[DidChangeConfigurationClientCapabilities] = None,
//    didChangeWatchedFiles: Option[DidChangeWatchedFilesClientCapabilities] = None,
//    symbol: Option[WorkspaceSymbolClientCapabilities] = None,
//    executeCommand: Option[ExecuteCommandClientCapabilities] = None
  )

  case class TextDocumentClientCapabilities(
//    synchronization: Option[TextDocumentSyncClientCapabilities] = None,
//    completion: Option[CompletionClientCapabilities] = None,
//    hover: Option[HoverClientCapabilities] = None,
//    signatureHelp: Option[SignatureHelpClientCapabilities] = None,
//    declaration: Option[DeclarationClientCapabilities] = None,
//    definition: Option[DefinitionClientCapabilities] = None,
//    typeDefinition: Option[TypeDefinitionClientCapabilities] = None,
//    implementation: Option[ImplementationClientCapabilities] = None,
//    references: Option[ReferenceClientCapabilities] = None,
//    documentHighlight: Option[DocumentHighlightClientCapabilities] = None,
//    documentSymbol: Option[DocumentSymbolClientCapabilities] = None,
//    codeAction: Option[CodeActionClientCapabilities] = None,
//    codeLens: Option[CodeLensClientCapabilities] = None,
//    documentLink: Option[DocumentLinkClientCapabilities] = None,
//    colorProvider: Option[DocumentColorClientCapabilities] = None,
//    formatting: Option[DocumentFormattingClientCapabilities] = None,
//    rangeFormatting: Option[DocumentRangeFormattingClientCapabilities] = None,
//    onTypeFormatting: Option[DocumentOnTypeFormattingClientCapabilities] = None,
//    rename: Option[RenameClientCapabilities] = None,
//    publishDiagnostics: Option[PublishDiagnosticsClientCapabilities] = None,
//    foldingRange: Option[FoldingRangeClientCapabilities] = None
  )


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

  /** Generate [[ResponseMessage]] for a given [[RequestOrNotification]].
    *
    * Any [[Throwable]] thrown out of implementation will be translated into
    * [[ResponseMessage]] message.
    */
  def handleRequest(request: RequestOrNotification): ResponseMessage

  def handleMessage(input: String): String = {
    try {
      import io.circe.shapes._
      import ShapesDerivation._
      decode[RequestOrNotification](input) match {
        case Left(err)      => throw err
        case Right(request) => handleRequest(request).asJson.toString()
      }
    } catch {
      case e: Throwable => throw e //TODO
//        Response.error(error = Error(-1, e.toString)).asJson.noSpaces
    }
  }
}

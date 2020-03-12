package org.enso.languageserver.text

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props, Stash}
import org.enso.languageserver.capability.CapabilityProtocol._
import org.enso.languageserver.data.Client.Id
import org.enso.languageserver.data.buffer.Rope
import org.enso.languageserver.data.{
  CanEdit,
  CapabilityRegistration,
  Client,
  ContentBasedVersioning
}
import org.enso.languageserver.event.{
  BufferClosed,
  BufferOpened,
  ClientDisconnected
}
import org.enso.languageserver.filemanager.FileManagerProtocol.{
  ReadFileResult,
  WriteFileResult
}
import org.enso.languageserver.filemanager.{
  FileManagerProtocol,
  OperationTimeout,
  Path
}
import org.enso.languageserver.text.CollaborativeBuffer.IOTimeout
import org.enso.languageserver.text.TextProtocol._
import org.enso.languageserver.text.editing.{
  EditorOps,
  EndPositionBeforeStartPosition,
  InvalidPosition,
  NegativeCoordinateInPosition,
  TextEditValidationFailure
}
import org.enso.languageserver.text.editing.model.{FileEdit, Position, TextEdit}
import cats.implicits._
import org.enso.languageserver.text.Buffer.Version

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * An actor enabling multiple users edit collaboratively a file.
  *
  * @param bufferPath a path to a file
  * @param fileManager a file manger actor
  * @param timeout a request timeout
  * @param versionCalculator a content based version calculator
  */
class CollaborativeBuffer(
  bufferPath: Path,
  fileManager: ActorRef,
  timeout: FiniteDuration
)(
  implicit versionCalculator: ContentBasedVersioning
) extends Actor
    with Stash
    with ActorLogging {

  import context.dispatcher

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[ClientDisconnected])
  }

  override def receive: Receive = uninitialized

  override def unhandled(message: Any): Unit =
    log.warning("Received unknown message: {}", message)

  private def uninitialized: Receive = {
    case OpenFile(client, path) =>
      context.system.eventStream.publish(BufferOpened(path))
      log.info(s"Buffer opened for $path [client:${client.id}]")
      readFile(client, path)
  }

  private def waitingForFileContent(
    client: Client,
    replyTo: ActorRef,
    timeoutCancellable: Cancellable
  ): Receive = {
    case ReadFileResult(Right(content)) =>
      handleFileContent(client, replyTo, content)
      unstashAll()
      timeoutCancellable.cancel()

    case ReadFileResult(Left(failure)) =>
      replyTo ! OpenFileResponse(Left(failure))
      timeoutCancellable.cancel()
      stop()

    case IOTimeout =>
      replyTo ! OpenFileResponse(Left(OperationTimeout))
      stop()

    case _ => stash()
  }

  private def collaborativeEditing(
    buffer: Buffer,
    clients: Map[Client.Id, Client],
    lockHolder: Option[Client]
  ): Receive = {
    case OpenFile(client, _) =>
      openFile(buffer, clients, lockHolder, client)

    case AcquireCapability(clientId, CapabilityRegistration(CanEdit(path))) =>
      acquireWriteLock(buffer, clients, lockHolder, clientId, path)

    case ReleaseCapability(clientId, CapabilityRegistration(CanEdit(_))) =>
      releaseWriteLock(buffer, clients, lockHolder, clientId)

    case ClientDisconnected(clientId) =>
      if (clients.contains(clientId)) {
        removeClient(buffer, clients, lockHolder, clientId)
      }

    case CloseFile(clientId, _) =>
      if (clients.contains(clientId)) {
        removeClient(buffer, clients, lockHolder, clientId)
        sender() ! FileClosed
      } else {
        sender() ! FileNotOpened
      }

    case ApplyEdit(clientId, change) =>
      applyEdits(buffer, lockHolder, clientId, change) match {
        case Left(failure) =>
          sender() ! failure

        case Right(modifiedBuffer) =>
          sender() ! ApplyEditSuccess
          val subscribers = clients.filterNot(_._1 == clientId).values
          subscribers foreach { _.actor ! TextDidChange(List(change)) }
          context.become(
            collaborativeEditing(modifiedBuffer, clients, lockHolder)
          )
      }

    case SaveFile(clientId, _, clientVersion) =>
      saveFile(buffer, clients, lockHolder, clientId, clientVersion)
  }

  private def saving(
    buffer: Buffer,
    clients: Map[Client.Id, Client],
    lockHolder: Option[Client],
    replyTo: ActorRef,
    timeoutCancellable: Cancellable
  ): Receive = {
    case IOTimeout =>
      replyTo ! SaveFailed(OperationTimeout)
      unstashAll()
      context.become(collaborativeEditing(buffer, clients, lockHolder))

    case WriteFileResult(Left(failure)) =>
      replyTo ! SaveFailed(failure)
      unstashAll()
      timeoutCancellable.cancel()
      context.become(collaborativeEditing(buffer, clients, lockHolder))

    case WriteFileResult(Right(())) =>
      replyTo ! FileSaved
      unstashAll()
      timeoutCancellable.cancel()
      context.become(collaborativeEditing(buffer, clients, lockHolder))

    case _ => stash()
  }

  private def saveFile(
    buffer: Buffer,
    clients: Map[Id, Client],
    lockHolder: Option[Client],
    clientId: Id,
    clientVersion: Version
  ): Unit = {
    val hasLock = lockHolder.exists(_.id == clientId)
    if (hasLock) {
      if (clientVersion == buffer.version) {
        fileManager ! FileManagerProtocol.WriteFile(
          bufferPath,
          buffer.contents.toString
        )

        val timeoutCancellable = context.system.scheduler
          .scheduleOnce(timeout, self, IOTimeout)
        context.become(
          saving(buffer, clients, lockHolder, sender(), timeoutCancellable)
        )
      } else {
        sender() ! SaveFileInvalidVersion(clientVersion, buffer.version)
      }
    } else {
      sender() ! SaveDenied
    }
  }

  private def applyEdits(
    buffer: Buffer,
    lockHolder: Option[Client],
    clientId: Id,
    change: FileEdit
  ): Either[ApplyEditFailure, Buffer] =
    for {
      _              <- validateAccess(lockHolder, clientId)
      _              <- validateVersions(change.oldVersion, buffer.version)
      modifiedBuffer <- doEdit(buffer, change.edits)
      _              <- validateVersions(change.newVersion, modifiedBuffer.version)
    } yield modifiedBuffer

  private def validateVersions(
    clientVersion: Buffer.Version,
    serverVersion: Buffer.Version
  ): Either[ApplyEditFailure, Unit] = {
    if (clientVersion == serverVersion) {
      Right(())
    } else {
      Left(TextEditInvalidVersion(clientVersion, serverVersion))
    }
  }

  private def validateAccess(
    lockHolder: Option[Client],
    clientId: Id
  ): Either[ApplyEditFailure, Unit] = {
    val hasLock = lockHolder.exists(_.id == clientId)
    if (hasLock) {
      Right(())
    } else {
      Left(WriteDenied)
    }
  }

  private def doEdit(
    buffer: Buffer,
    edits: List[TextEdit]
  ): Either[ApplyEditFailure, Buffer] = {
    EditorOps
      .applyEdits(buffer.contents, edits)
      .leftMap(toEditFailure)
      .map(rope => Buffer(rope, versionCalculator.evalVersion(rope.toString)))
  }

  private val toEditFailure: TextEditValidationFailure => ApplyEditFailure = {
    case EndPositionBeforeStartPosition =>
      TextEditValidationFailed("The start position is after the end position")
    case NegativeCoordinateInPosition =>
      TextEditValidationFailed("Negative coordinate in a position object")
    case InvalidPosition(position) =>
      TextEditValidationFailed(s"Invalid position: $position")
  }

  private def readFile(client: Client, path: Path): Unit = {
    fileManager ! FileManagerProtocol.ReadFile(path)
    val timeoutCancellable = context.system.scheduler
      .scheduleOnce(timeout, self, IOTimeout)
    context.become(waitingForFileContent(client, sender(), timeoutCancellable))
  }

  private def handleFileContent(
    client: Client,
    originalSender: ActorRef,
    content: String
  ): Unit = {
    val buffer = Buffer(content)
    val cap    = CapabilityRegistration(CanEdit(bufferPath))
    originalSender ! OpenFileResponse(
      Right(OpenFileResult(buffer, Some(cap)))
    )
    context.become(
      collaborativeEditing(buffer, Map(client.id -> client), Some(client))
    )
  }

  private def openFile(
    buffer: Buffer,
    clients: Map[Id, Client],
    lockHolder: Option[Client],
    client: Client
  ): Unit = {
    val writeCapability =
      if (lockHolder.isEmpty)
        Some(CapabilityRegistration(CanEdit(bufferPath)))
      else
        None
    sender ! OpenFileResponse(Right(OpenFileResult(buffer, writeCapability)))
    context.become(
      collaborativeEditing(buffer, clients + (client.id -> client), lockHolder)
    )
  }

  private def removeClient(
    buffer: Buffer,
    clients: Map[Id, Client],
    lockHolder: Option[Client],
    clientId: Id
  ): Unit = {
    val newLock =
      lockHolder.flatMap {
        case holder if (holder.id == clientId) => None
        case holder                            => Some(holder)
      }
    val newClientMap = clients - clientId
    if (newClientMap.isEmpty) {
      stop()
    } else {
      context.become(collaborativeEditing(buffer, newClientMap, newLock))
    }
  }

  private def releaseWriteLock(
    buffer: Buffer,
    clients: Map[Client.Id, Client],
    lockHolder: Option[Client],
    clientId: Id
  ): Unit = {
    lockHolder match {
      case None =>
        sender() ! CapabilityReleaseBadRequest
        context.become(collaborativeEditing(buffer, clients, lockHolder))

      case Some(holder) if holder.id != clientId =>
        sender() ! CapabilityReleaseBadRequest
        context.become(collaborativeEditing(buffer, clients, lockHolder))

      case Some(holder) if holder.id == clientId =>
        sender() ! CapabilityReleased
        context.become(collaborativeEditing(buffer, clients, None))
    }
  }

  private def acquireWriteLock(
    buffer: Buffer,
    clients: Map[Client.Id, Client],
    lockHolder: Option[Client],
    clientId: Client,
    path: Path
  ): Unit = {
    lockHolder match {
      case None =>
        sender() ! CapabilityAcquired
        context.become(collaborativeEditing(buffer, clients, Some(clientId)))

      case Some(holder) if holder == clientId =>
        sender() ! CapabilityAcquisitionBadRequest
        context.become(collaborativeEditing(buffer, clients, lockHolder))

      case Some(holder) if holder != clientId =>
        sender() ! CapabilityAcquired
        holder.actor ! CapabilityForceReleased(
          CapabilityRegistration(CanEdit(path))
        )
        context.become(collaborativeEditing(buffer, clients, Some(clientId)))
    }
  }

  def stop(): Unit = {
    context.system.eventStream.publish(BufferClosed(bufferPath))
    context.stop(self)
  }

}

object CollaborativeBuffer {

  case object IOTimeout

  /**
    * Creates a configuration object used to create a [[CollaborativeBuffer]]
    *
    * @param bufferPath a path to a file
    * @param fileManager a file manager actor
    * @param timeout a request timeout
    * @param versionCalculator a content based version calculator
    * @return a configuration object
    */
  def props(
    bufferPath: Path,
    fileManager: ActorRef,
    timeout: FiniteDuration = 10 seconds
  )(implicit versionCalculator: ContentBasedVersioning): Props =
    Props(new CollaborativeBuffer(bufferPath, fileManager, timeout))

}

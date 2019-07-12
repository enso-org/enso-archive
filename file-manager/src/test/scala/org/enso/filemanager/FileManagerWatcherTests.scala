package org.enso.filemanager

import java.nio.file.{Files, Path}
import java.util.UUID

import akka.actor.Scheduler
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.util.Timeout
import io.methvin.watcher.DirectoryChangeEvent
import org.apache.commons.io.FileUtils
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers, Outcome}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{Success, Try}


// needs to be separate because watcher message are asynchronous
class FileManagerWatcherTests
    extends FunSuite
    with BeforeAndAfterAll
    with Matchers
    with FileSystemHelpers {
  import API._

  var testKit: ActorTestKit         = ActorTestKit()
  implicit val timeout: Timeout     = 3.seconds
  implicit val scheduler: Scheduler = testKit.scheduler

  var fileManager: ActorRef[InputMessage]   = _
  var testProbe: TestProbe[FileSystemEvent] = _
  var watcherID: UUID                       = _

  override def withFixture(test: NoArgTest): Outcome = {
    withTemporaryDirectory(_ => {
      fileManager = testKit.spawn(FileManager.fileManager(tempDir))
      testProbe   = testKit.createTestProbe[FileSystemEvent]("file-observer")
      watcherID   = observe(tempDir)
      super.withFixture(test)
    })
  }

  override def afterAll() {
    testKit.shutdownTestKit()
  }

  def matchesEvent(
    path: Path,
    eventType: DirectoryChangeEvent.EventType
  ): FileSystemEvent => Boolean = { message: FileSystemEvent =>
    message.event.path() == path && message.event.eventType() == eventType
  }

  def expectEventPresentIn(
    path: Path,
    eventType: DirectoryChangeEvent.EventType,
    events: Seq[FileSystemEvent]
  ): Unit = {
    assert(
      events.exists(matchesEvent(path, eventType)),
      s"not received message about $path"
    )
  }

  def expectNextEvent(
    path: Path,
    eventType: DirectoryChangeEvent.EventType
  ): Unit = {
    val message = testProbe.receiveMessage()
    assert(
      matchesEvent(path, eventType)(message),
      s"expected of type $eventType for $path, got $message"
    )
  }

  def ask[response <: SuccessResponse: ClassTag](
    requestPayload: RequestPayload[response]
  ): Future[Try[response]] = {
    FileManager.ask(fileManager, requestPayload)
  }

  def observe(path: Path): UUID = {
    val futureResponse = ask(CreateWatcherRequest(path, testProbe.ref))
    Await.result(futureResponse, 50.millis).get.id
  }

  test("Watcher: observe subtree creation and deletion") {
    val subtree = createSubtree()
    val events  = testProbe.receiveMessages(subtree.elements.size)
    subtree.elements.foreach(
      expectEventPresentIn(_, DirectoryChangeEvent.EventType.CREATE, events)
    )

    FileUtils.deleteDirectory(subtree.root.toFile)

    val deletionEvents = testProbe.receiveMessages(subtree.elements.size)
    subtree.elements.foreach(
      expectEventPresentIn(
        _,
        DirectoryChangeEvent.EventType.DELETE,
        deletionEvents
      )
    )

    testProbe.expectNoMessage(50.millis)
  }

  test("Watcher: observe file modification") {
    // should generate two events
    val dir10 = tempDir.resolve("dir10")
    val dir20 = dir10.resolve("dir20")
    // create two directories at once - we should get two notifications
    Files.createDirectories(dir20)
    expectNextEvent(dir10, DirectoryChangeEvent.EventType.CREATE)
    expectNextEvent(dir20, DirectoryChangeEvent.EventType.CREATE)

    val someFile = dir20.resolve("file.dat")
    Files.createFile(someFile)
    expectNextEvent(someFile, DirectoryChangeEvent.EventType.CREATE)
    Files.write(someFile, "blahblah".getBytes)
    expectNextEvent(someFile, DirectoryChangeEvent.EventType.MODIFY)

    // deleting dir removes the file first
    FileUtils.deleteDirectory(dir20.toFile)
    expectNextEvent(someFile, DirectoryChangeEvent.EventType.DELETE)
    expectNextEvent(dir20, DirectoryChangeEvent.EventType.DELETE)
    testProbe.expectNoMessage(50.millis)
  }

  test("Watcher: disabling watch") {
    val subtree = createSubtree()
    testProbe.receiveMessages(subtree.elements.size)
    val stopResponse =
      Await.result(ask(WatcherRemoveRequest(watcherID)), 1.second)
    stopResponse should be(Success(WatcherRemoveResponse()))

    FileUtils.deleteDirectory(subtree.root.toFile)
    testProbe.expectNoMessage(50.millis)
  }
}

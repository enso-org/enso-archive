package org.enso.filemanager

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths

import org.apache.commons.io.FileUtils
import akka.actor.testkit.typed.CapturedLogEvent
import akka.actor.testkit.typed.Effect._
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import akka.actor.testkit.typed.scaladsl.TestInbox
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.event.Logging
import org.scalatest.FunSuite
import org.scalatest.Outcome

import scala.reflect.ClassTag
import org.scalatest.Matchers

trait TempDirFixture {}

class FileManagerTests extends FunSuite with Matchers {
  import API2._
  
  var tempDir: Path                                         = _
  var testKit: BehaviorTestKit[Request[SuccessResponse]] = _
  var inbox: TestInbox[Either[ErrorResponse, SuccessResponse]]                       = _

  override def withFixture(test: NoArgTest): Outcome = {
    tempDir = Files.createTempDirectory("file-manager-test")
    testKit = BehaviorTestKit(FileManager.fileManager(tempDir))
    inbox   = TestInbox[Either[ErrorResponse, SuccessResponse]]()
    //println("Fixture prepared " + tempDir.toString)
    try test()
    finally FileUtils.deleteDirectory(tempDir.toFile)
  }

  def createSubFile(): Path = {
    Files.createTempFile(tempDir, "foo", "")
  }

  def createSubDir(): Path = {
    Files.createTempDirectory(tempDir, "foo")
  }

  def homeDirectory(): Path = Paths.get(System.getProperty("user.home"))

  def expect[T: ClassTag](sth: Any): T = {
    sth shouldBe a[T]
    sth.asInstanceOf[T]
  }

  def expectResponse[T: ClassTag](): T = {
    expect[T](inbox.receiveMessage())
  }

  def expectExceptionInResponse[T: ClassTag](): T = {
    val e = expectResponse[ErrorResponse]().exception
    e shouldBe a[T]
    e.asInstanceOf[T]
  }

  def runRequest(contents: RequestPayload): Unit =
    testKit.run(Request(inbox.ref, contents))

  def ask(contents: RequestPayload): contents.ResponseType = {
    runRequest(contents)
    inbox.receiveMessage() match {
      case Left(msg) =>
        msg shouldBe a[contents.ResponseType]
      case Right(msg) =>
        fail("Unexpected error message " + msg.toString)
    }
    expectResponse[contents.ResponseType]()
  }

  // ask for something that is not allowed and is expected to cause exception
  def abet[exception: ClassTag](contents: RequestPayload): exception = {
    runRequest(contents)
    expectExceptionInResponse[exception]()
  }

  test("Exists: outside project by relative path") {
    val path = tempDir.resolve("../foo")
    // make sure that our path seemingly may look like something under the project
    assert(path.startsWith(tempDir))
    abet[PathOutsideProject](ExistsRequest(path))
  }

  test("Exists: outside project by absolute path") {
    abet[PathOutsideProject](ExistsRequest(homeDirectory()))
  }

  test("Exists: existing file") {
    val filePath = createSubFile()
    val response = ask(ExistsRequest(filePath))
    response.exists should be(true)
  }

  test("Exists: existing directory") {
    val dirPath  = createSubDir()
    val response = ask(ExistsRequest(dirPath))
    response.exists should be(true)
  }

  test("Exists: missing file") {
    val filePath = tempDir.resolve("bar")
    val response = ask(ExistsRequest(filePath))
    response.exists should be(false)
  }

//  test("Stat: ask is file a dir") {
//    val filePath = createSubFile()
//
//    var inbox: TestInbox[AnyResponse] = TestInbox()
//    testKit.ref.ask[StatResponse](ref =>
//      Request(ref, StatRequest(filePath)))
//  }
}

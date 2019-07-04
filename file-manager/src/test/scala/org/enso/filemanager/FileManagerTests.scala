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
import org.enso.filemanager.API2._
import org.scalatest.FunSuite
import org.scalatest.Outcome

import scala.reflect.ClassTag
//#imports
import org.scalatest.Matchers
import org.scalatest.WordSpec

trait TempDirFixture {}

class FileManagerTests extends FunSuite with Matchers {
  var tempDir: Path                                         = _
  var testKit: BehaviorTestKit[API2.Request[API2.Response]] = _
  var inbox: TestInbox[API2.Response]                       = _

  override def withFixture(test: NoArgTest): Outcome = {
    tempDir = Files.createTempDirectory("file-manager-test")
    testKit = BehaviorTestKit(FileManager.fileManager(tempDir))
    inbox   = TestInbox[API2.Response]()
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
    val e = expectResponse[API2.ErrorResponse]().exception
    e shouldBe a[T]
    e.asInstanceOf[T]
  }

  def runRequest(contents: API2.RequestPayload): Unit =
    testKit.run(API2.Request(inbox.ref, contents))

  def ask(contents: API2.RequestPayload): contents.ResponseType = {
    runRequest(contents)
    expectResponse[contents.ResponseType]()
  }

  // ask for something that is not allowed and is expected to cause exception
  def abet[exception: ClassTag](contents: API2.RequestPayload): exception = {
    runRequest(contents)
    expectExceptionInResponse[exception]()
  }

  test("Exists: outside project by relative path") {
    val path = tempDir.resolve("../foo")
    // make sure that our path seemingly may look like something under the project
    assert(path.startsWith(tempDir))
    abet[API2.PathOutsideProject](API2.ExistsRequest(path))
  }

  test("Exists: outside project by absolute path") {
    abet[API2.PathOutsideProject](API2.ExistsRequest(homeDirectory()))
  }

  test("Exists: existing file") {
    val filePath = createSubFile()
    val response = ask(API2.ExistsRequest(filePath))
    response.exists should be(true)
  }

  test("Exists: existing directory") {
    val dirPath  = createSubDir()
    val response = ask(API2.ExistsRequest(dirPath))
    response.exists should be(true)
  }

  test("Exists: missing file") {
    val filePath = tempDir.resolve("bar")
    val response = ask(API2.ExistsRequest(filePath))
    response.exists should be(false)
  }

//  test("Stat: ask is file a dir") {
//    val filePath = createSubFile()
//
//    var inbox: TestInbox[API2.Response] = TestInbox()
//    testKit.ref.ask[API2.StatResponse](ref =>
//      API2.Request(ref, API2.StatRequest(filePath)))
//  }
}

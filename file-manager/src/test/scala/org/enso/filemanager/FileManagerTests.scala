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
import org.enso.filemanager.API._
import org.scalatest.FunSuite
import org.scalatest.Outcome

import scala.reflect.ClassTag
//#imports
import org.scalatest.Matchers
import org.scalatest.WordSpec

trait TempDirFixture {}

class FileManagerTests extends FunSuite with Matchers {
  var tempDir: Path                         = _
  var testKit: BehaviorTestKit[API.Request] = _
  var inbox: TestInbox[API.Response]        = _

  override def withFixture(test: NoArgTest): Outcome = {
    tempDir = Files.createTempDirectory("file-manager-test")
    testKit = BehaviorTestKit(FileManager.fileManager(tempDir))
    inbox   = TestInbox[API.Response]()
    println("Fixture prepared " + tempDir.toString)
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
    val e = expectResponse[API.ErrorResponse]().exception
    e shouldBe a[T]
    e.asInstanceOf[T]
  }

  def runRequest(contents: API.RequestPayload): Unit =
    testKit.run(API.Request(inbox.ref, contents))

  def ask[response: ClassTag](contents: API.RequestPayload): response = {
    runRequest(contents)
    expectResponse[response]()
  }

  // ask for something that is not allowed and is expected to cause exception
  def abet[exception: ClassTag](contents: API.RequestPayload): exception = {
    runRequest(contents)
    expectExceptionInResponse[exception]()
  }

  test("List: empty directory") {
    val requestContents = API.ListRequest(tempDir)
    val response        = ask[API.ListResponse](requestContents)
    response.entries should have length 0
    response shouldBe a[API.ListResponse]
  }

  test("List: missing directory") {
    val path = tempDir.resolve("bar")
    ask[NoSuchFileException](API.ListRequest(path))
  }

  test("List: non-empty directory") {
    val filePath   = createSubFile()
    val subdirPath = createSubDir()

    val response = ask[API.ListResponse](API.ListRequest(tempDir))

    def expectPath(path: Path): Path = {
      response.entries.find(_.toString == path.toString) match {
        case Some(entry) => entry
        case _           => fail("cannot find entry for path " + path.toString)
      }
    }

    response.entries should have length 2
    expectPath(filePath)
    expectPath(subdirPath)
  }

  test("List: outside project") {
    runRequest(API.ListRequest(homeDirectory()))
    expectExceptionInResponse[API.PathOutsideProject]()
  }

  test("Exists: outside project by relative path") {
    val path = tempDir.resolve("../foo")
    // make sure that our path seemingly may look like something under the project
    assert(path.startsWith(tempDir))
    abet[API.PathOutsideProject](API.ExistsRequest(path))
  }

  test("Exists: outside project by absolute path") {
    abet[API.PathOutsideProject](API.ExistsRequest(homeDirectory()))
  }

  test("Exists: existing file") {
    val filePath = createSubFile()
    val response = ask[API.ExistsResponse](API.ExistsRequest(filePath))
    response.exists should be(true)
  }

  test("Exists: existing directory") {
    val dirPath = createSubDir()
    val response = ask[API.ExistsResponse](API.ExistsRequest(dirPath))
    response.exists should be(true)
  }

  test("Exists: missing file") {
    val filePath = tempDir.resolve("bar")
    val response = ask[API.ExistsResponse](API.ExistsRequest(filePath))
    response.exists should be(false)
  }

  test("Stat: missing file") {
    val filePath = tempDir.resolve("bar")
    abet[NoSuchFileException](API.StatRequest(filePath))
  }

  test("Read: file") {
    val filePath = tempDir.resolve("bar")
    val contents = "gfhniugdzhbuiobf".getBytes
    Files.write(filePath, contents)

    val response = ask[API.ReadResponse](API.ReadRequest(filePath))
    response.contents should be(contents)
  }

  test("Write: file") {
    val filePath = tempDir.resolve("bar")
    val contents = "gfhniugdzhbuiobf".getBytes

    ask[API.WriteResponse](API.WriteRequest(filePath, contents))

    val actualFileContents = Files.readAllBytes(filePath)
    actualFileContents should be(contents)
  }

  test("Stat: normal file") {
    val filePath = createSubFile()
    val contents = "aaa"
    Files.write(filePath, contents.getBytes())

    val response = ask[API.StatResponse](API.StatRequest(filePath))
    response.isDirectory should be(false)
    response.path.toString should be(filePath.toString)
    response.size should be(contents.length)
  }

//  test("Stat: ask is file a dir") {
//    val filePath = createSubFile()
//
//    var inbox: TestInbox[API.Response] = TestInbox()
//    testKit.ref.ask[API.StatResponse](ref =>
//      API.Request(ref, API.StatRequest(filePath)))
//  }
}

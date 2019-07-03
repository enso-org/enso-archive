package org.enso.filemanager

import java.io.File
import java.nio.charset.Charset
import java.nio.file.{Files, NoSuchFileException, Path, Paths}

import org.apache.commons.io.FileUtils
import akka.actor.testkit.typed.CapturedLogEvent
import akka.actor.testkit.typed.Effect._
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import akka.actor.testkit.typed.scaladsl.TestInbox
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.event.Logging
import org.enso.filemanager.FileManager.RequestPayload
import org.scalatest.{FunSuite, Outcome}

import scala.reflect.ClassTag
//#imports
import org.scalatest.Matchers
import org.scalatest.WordSpec

//object HelperIO {
//  def withTempDir[T](prefix: String = "file-manager-test", f: Path => T): T = {
//    val tempDir = Files.createTempDirectory("file-manager-test")
//    try {
//      f(tempDir)
//    }
//    finally {
//      FileUtils.deleteDirectory(tempDir.toFile)
//    }
//  }
//}

trait TempDirFixture {

}

class FileManagerTests extends FunSuite with Matchers {
  var tempDir: Path = _
  var testKit: BehaviorTestKit[FileManager.Request] = _
  var inbox: TestInbox[FileManager.Response] = _

  override def withFixture(test: NoArgTest): Outcome = {
    tempDir = Files.createTempDirectory("file-manager-test")
    testKit = BehaviorTestKit(FileManager.fileManager)
    inbox = TestInbox[FileManager.Response]()
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

  def expect[T: ClassTag](sth: Any): T = {
    sth shouldBe a [T]
    sth.asInstanceOf[T]
  }

  def responseShouldCome[T: ClassTag](): T = {
    expect[T](inbox.receiveMessage())
  }

  def expectExceptionInResponse[T: ClassTag](): T = {
    val e = responseShouldCome[FileManager.ErrorResponse]().exception
    e shouldBe a [T]
    e.asInstanceOf[T]
  }

  def runRequest(contents: FileManager.RequestPayload): Unit =
    testKit.run(FileManager.Request(inbox.ref, contents))

  test("List: empty directory") {
    val requestContents = FileManager.ListRequest(tempDir)
    runRequest(requestContents)
    val response = responseShouldCome[FileManager.ListResponse]()
    response shouldBe a [FileManager.ListResponse]
    response.entries should have length 0
  }
  test("List: missing directory") {
    val path = tempDir.resolve("bar")
    val requestContents = FileManager.ListRequest(path)
    runRequest(requestContents)
    expectExceptionInResponse[NoSuchFileException]()
  }

  test("List: non-empty directory") {
    val filePath = createSubFile()
    val subdirPath = createSubDir()

    val requestContents = FileManager.ListRequest(tempDir)
    runRequest(requestContents)
    val response = responseShouldCome[FileManager.ListResponse]()

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

  test("Exists: existing file") {
    val filePath = createSubFile()
    runRequest(FileManager.ExistsRequest(filePath))
    val response = responseShouldCome[FileManager.ExistsResponse]()
    response.exists should be (true)
  }
  test("Exists: existing directory") {
    val filePath = createSubDir()
    runRequest(FileManager.ExistsRequest(filePath))
    val response = responseShouldCome[FileManager.ExistsResponse]()
    response.exists should be (true)
  }

  test("Exists: missing file") {
    val filePath = tempDir.resolve("bar")
    runRequest(FileManager.ExistsRequest(filePath))
    val response = responseShouldCome[FileManager.ExistsResponse]()
    response.exists should be (false)
  }

  test("Stat: missing file") {
    val filePath = tempDir.resolve("bar")
    runRequest(FileManager.StatRequest(filePath))
    expectExceptionInResponse[NoSuchFileException]()
  }

  test("Read: file") {
    val filePath = tempDir.resolve("bar")
    val contents = "gfhniugdzhbuiobf".getBytes
    Files.write(filePath, contents)

    runRequest(FileManager.ReadRequest(filePath))
    val response = responseShouldCome[FileManager.ReadResponse]()
    response.contents should be (contents)
  }

  test("Write: file") {
    val filePath = tempDir.resolve("bar")
    val contents = "gfhniugdzhbuiobf".getBytes

    runRequest(FileManager.WriteRequest(filePath, contents))
    responseShouldCome[FileManager.WriteResponse]()

    val actualFileContents = Files.readAllBytes(filePath)
    actualFileContents should be (contents)
  }

  test("Stat: normal file") {
    val filePath = createSubFile()
    val contents = "aaa"
    Files.write(filePath, contents.getBytes())

    runRequest(FileManager.StatRequest(filePath))
    val response = responseShouldCome[FileManager.StatResponse]()
    response.isDirectory should be (false)
    response.path.toString should be (filePath.toString)
    response.size should be (contents.length)
  }

//  test("Stat: ask is file a dir") {
//    val filePath = createSubFile()
//
//    var inbox: TestInbox[FileManager.Response] = TestInbox()
//    testKit.ref.ask[FileManager.StatResponse](ref =>
//      FileManager.Request(ref, FileManager.StatRequest(filePath)))
//  }
}
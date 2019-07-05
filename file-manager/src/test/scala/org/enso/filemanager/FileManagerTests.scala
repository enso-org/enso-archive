package org.enso.filemanager

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.TimeUnit

import org.apache.commons.io.FileUtils
import akka.actor.testkit.typed.CapturedLogEvent
import akka.actor.testkit.typed.Effect._
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import akka.actor.testkit.typed.scaladsl.TestInbox
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import org.scalatest.FunSuite
import org.scalatest.Outcome

import scala.reflect.ClassTag
import org.scalatest.Matchers

import scala.concurrent.duration.FiniteDuration

trait TempDirFixture {}

class FileManagerTests extends FunSuite with Matchers {
  import API._

  var tempDir: Path                                            = _
  var testKit: BehaviorTestKit[Request[SuccessResponse]]       = _
  var inbox: TestInbox[Either[ErrorResponse, SuccessResponse]] = _

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

  def expectSuccess[T <: SuccessResponse: ClassTag](): T = {
    inbox.receiveMessage() match {
      case Left(err) =>
        fail(s"Unexpected error message: $err")
      case Right(msg) =>
        msg shouldBe a[T]
        msg.asInstanceOf[T]
    }
  }

  def expectError[T <: Throwable: ClassTag](): T = {
    inbox.receiveMessage() match {
      case Left(err) =>
        err.exception shouldBe a[T]
        err.exception.asInstanceOf[T]
      case Right(msg) =>
        fail(s"Unexpected non-error message: $msg")
    }
  }

  def runRequest(contents: RequestPayload[SuccessResponse]): Unit =
    testKit.run(Request(inbox.ref, contents))

  def ask[res <: SuccessResponse: ClassTag](
    contents: RequestPayload[res]
  ): res = {
    runRequest(contents)
    expectSuccess[res]
  }

  // ask for something that is not allowed and is expected to cause exception
  def abet[exception <: Throwable: ClassTag](
    contents: RequestPayload[SuccessResponse]
  ): exception = {
    runRequest(contents)
    expectError[exception]
  }

  test("Exists: outside project by relative path") {
    val path = tempDir.resolve("../foo")
    // make sure that our path seemingly may look like something under the project
    assert(path.startsWith(tempDir))
    abet[PathOutsideProjectException](ExistsRequest(path))
  }

  test("Exists: outside project by absolute path") {
    abet[PathOutsideProjectException](ExistsRequest(homeDirectory()))
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
//    val system = ActorSystem[API.Request[API.SuccessResponse]](
//      FileManager.fileManager(Paths.get("")),
//      "hello"
//    )
//
//    implicit val askTimeout: Timeout = new Timeout(50, TimeUnit.MILLISECONDS)
//    system.ask(
//      (ref: ActorRef[Either[ErrorResponse, TouchFileResponse]]) =>
//        Request(ref, TouchFileRequest(filePath))
//    )
//  }
}

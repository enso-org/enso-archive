package org.enso.filemanager

import java.io.File
import java.nio.charset.Charset
import java.nio.file.{FileAlreadyExistsException, Files, NoSuchFileException, Path, Paths}
import java.time.Duration
import java.util.concurrent.TimeUnit

import org.apache.commons.io.FileUtils
import akka.actor.testkit.typed.CapturedLogEvent
import akka.actor.testkit.typed.Effect._
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import akka.actor.testkit.typed.scaladsl.TestInbox
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import org.scalatest.FunSuite
import org.scalatest.Outcome

import scala.reflect.ClassTag
import org.scalatest.Matchers

import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success

trait FilesystemHelpers {
  var tempDir: Path = _
  val contents      = "葦垣の中の和草にこやかに我れと笑まして人に知らゆな\nzażółć gęślą jaźń".getBytes

  def createSubFile(): Path = {
    val path = Files.createTempFile(tempDir, "foo", "")
    Files.write(path, contents)
  }

  def createSubDir(): Path = {
    Files.createTempDirectory(tempDir, "foo")
  }

  def homeDirectory(): Path = Paths.get(System.getProperty("user.home"))

  def withTemporaryDirectory[ret](f: Path => ret): ret = {
    tempDir = Files.createTempDirectory("file-manager-test")
    try f(tempDir)
    finally {
      FileUtils.deleteDirectory(tempDir.toFile)
      tempDir = null
    }
  }

  case class Subtree(root: Path, children: Seq[Path])

  def createSubtree(): Subtree = {
    val root      = createSubDir()
    val rootFile1 = root.resolve("file1")
    val rootDir   = root.resolve("dir")
    val rootFile2 = root.resolve("dir/file2")

    Files.write(rootFile1, contents)
    Files.createDirectory(rootDir)
    Files.write(rootFile2, contents)
    Subtree(root, Seq(rootFile1, rootFile2))
  }
}

class FileManagerTests extends FunSuite with Matchers with FilesystemHelpers {
  import API._

  var testKit: BehaviorTestKit[Request[SuccessResponse]] = _
  var inbox: TestInbox[OutputMessage]                    = _

  override def withFixture(test: NoArgTest): Outcome = {
    withTemporaryDirectory(_ => {
      testKit = BehaviorTestKit(FileManager.fileManager(tempDir))
      inbox   = TestInbox[OutputMessage]()
      test()
    })
  }

  def expectSuccess[T <: SuccessResponse: ClassTag](): T = {
    inbox.receiveMessage() match {
      case Failure(err) =>
        fail(s"Unexpected error message: $err")
      case Success(msg) =>
        msg shouldBe a[T]
        msg.asInstanceOf[T]
    }
  }

  def expectError[T <: Throwable: ClassTag](): T = {
    inbox.receiveMessage() match {
      case Failure(exception) =>
        exception shouldBe a[T]
        exception.asInstanceOf[T]
      case Success(msg) =>
        fail(s"Unexpected non-error message: $msg")
    }
  }

  def expectSubtree(subtree: Subtree): Unit = {
    assert(Files.exists(subtree.root))
    subtree.children.foreach(
      elem => assert(Files.exists(subtree.root.resolve(elem)))
    )

    val listStream = Files.list(subtree.root)
    try listStream.count() should be(2)
    finally listStream.close()
  }

  def runRequest(contents: RequestPayload[SuccessResponse]): Unit =
    testKit.run(Request(inbox.ref, contents))

  def ask[res <: SuccessResponse: ClassTag](
    contents: RequestPayload[res]
  ): res = {
    runRequest(contents)
    expectSuccess[res]()
  }

  // ask for something that is not allowed and is expected to cause exception
  def abet[exception <: Throwable: ClassTag](
    contents: RequestPayload[SuccessResponse]
  ): exception = {
    runRequest(contents)
    expectError[exception]()
  }

  test("Copy directory: empty directory") {
    val subdir      = createSubDir()
    val destination = tempDir.resolve("target")
    ask(CopyDirectoryRequest(subdir, destination))

    assert(Files.exists(destination))
  }

  test("Copy directory: non-empty directory") {
    val subtree     = createSubtree()
    val destination = tempDir.resolve("target")
    ask(CopyDirectoryRequest(subtree.root, destination))
    val subtreeExpected = Subtree(destination, subtree.children)
    expectSubtree(subtreeExpected)
  }

  test("Copy directory: target already exists") {
    val subtree     = createSubtree()
    val destination = tempDir.resolve("target")
    Files.createDirectory(destination)
    // no exception should happen, but merge
    ask(CopyDirectoryRequest(subtree.root, destination))
    val subtreeExpected = Subtree(destination, subtree.children)
    expectSubtree(subtreeExpected)
  }

  test("Copy file: plain") {
    val srcFile = createSubFile()
    val dstFile = tempDir.resolve("file2")

    ask(CopyFileRequest(srcFile, dstFile))

    assert(Files.exists(dstFile))
    assert(Files.readAllBytes(dstFile).sameElements(contents))
  }

  test("Copy file: target already exists") {
    val srcFile = createSubFile()
    val dstFile = createSubFile()
    abet[FileAlreadyExistsException](CopyFileRequest(srcFile, dstFile))
  }

  test("Delete directory: empty directory") {
    val dir = createSubDir()
    ask(DeleteDirectoryRequest(dir))
    assert(!Files.exists(dir))
  }

  test("Delete directory: non-empty directory") {
    val subtree = createSubtree()
    ask(DeleteDirectoryRequest(subtree.root))
    assert(!Files.exists(subtree.root))
  }

  test("Delete directory: missing directory") {
    val missingPath = tempDir.resolve("foo")
    abet[NoSuchFileException](DeleteDirectoryRequest(missingPath))
  }

  test("Delete file: simple") {
    val file = createSubFile()
    ask(DeleteFileRequest(file))
    assert(!Files.exists(file))
  }

  test("Delete file: missing file") {
    val missingPath = tempDir.resolve("foo")
    abet[NoSuchFileException](DeleteFileRequest(missingPath))
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

  test("List: empty directory") {
    val requestContents = ListRequest(tempDir)
    val response        = ask(requestContents)
    response.entries should have length 0
  }

  test("List: missing directory") {
    val path = tempDir.resolve("bar")
    abet[NoSuchFileException](ListRequest(path))
  }

  test("List: non-empty directory") {
    val filePath   = createSubFile()
    val subdirPath = createSubDir()
    val response   = ask(ListRequest(tempDir))

    def expectPath(path: Path): Path = {
      response.entries.find(_.toString == path.toString) match {
        case Some(entry) => entry
        case _           => fail(s"cannot find entry for path $path")
      }
    }

    response.entries should have length 2
    expectPath(filePath)
    expectPath(subdirPath)
  }

  test("List: outside project") {
    abet[PathOutsideProjectException](ListRequest(homeDirectory()))
  }

  test("Stat: missing file") {
    val filePath = tempDir.resolve("bar")
    abet[NoSuchFileException](StatRequest(filePath))
  }

  test("Read: file") {
    val filePath = tempDir.resolve("bar")
    Files.write(filePath, contents)

    val response = ask(ReadRequest(filePath))
    response.contents should be(contents)
  }

  test("Write: file") {
    val filePath = tempDir.resolve("bar")

    ask(WriteRequest(filePath, contents))

    val actualFileContents = Files.readAllBytes(filePath)
    actualFileContents should be(contents)
  }

  test("Stat: normal file") {
    val filePath = createSubFile()
    val contents = "aaa"
    Files.write(filePath, contents.getBytes())

    val response = ask(StatRequest(filePath))

    response.isDirectory should be(false)
    response.path.toString should be(filePath.toString)
    response.size should be(contents.length)
  }
}

package org.enso.filemanager

import java.io.File
import java.nio.charset.Charset
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

import org.apache.commons.io.FileExistsException
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
    val root       = createSubDir()
    val rootFile1  = Paths.get("file1")
    val rootSubDir = Paths.get("dir")
    val rootFile2  = Paths.get("dir/file2")

    Files.write(root.resolve(rootFile1), contents)
    Files.createDirectory(root.resolve(rootSubDir))
    Files.write(root.resolve(rootFile2), contents)
    Subtree(root, Seq(rootFile1, rootSubDir, rootFile2))
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
      elem => expectExist(subtree.root.resolve(elem))
    )

    val listStream = Files.list(subtree.root)
    try listStream.count() should be(2)
    finally listStream.close()
  }

  def expectExist(path: Path): Unit = {
    assert(Files.exists(path), s"$path is expected to exist")
  }

  def expectNotExist(path: Path): Unit = {
    assert(!Files.exists(path), s"$path is expected to not exist")
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

    expectExist(subdir)
    expectExist(destination)
  }

  test("Copy directory: non-empty directory") {
    val subtree     = createSubtree()
    val destination = tempDir.resolve("target")
    ask(CopyDirectoryRequest(subtree.root, destination))
    val subtreeExpected = Subtree(destination, subtree.children)
    expectSubtree(subtree)
    expectSubtree(subtreeExpected)
  }

  test("Copy directory: target already exists") {
    val subtree     = createSubtree()
    val destination = tempDir.resolve("target")
    Files.createDirectory(destination)
    // no exception should happen, but merge
    ask(CopyDirectoryRequest(subtree.root, destination))
    val subtreeExpected = Subtree(destination, subtree.children)
    expectSubtree(subtree)
    expectSubtree(subtreeExpected)
  }

  test("Copy file: plain") {
    val srcFile = createSubFile()
    val dstFile = tempDir.resolve("file2")

    ask(CopyFileRequest(srcFile, dstFile))

    expectExist(srcFile)
    expectExist(dstFile)
    assert(Files.readAllBytes(dstFile).sameElements(contents))
  }

  test("Copy file: target already exists") {
    val srcFile = createSubFile()
    val dstFile = createSubFile()
    abet[FileAlreadyExistsException](CopyFileRequest(srcFile, dstFile))
    expectExist(srcFile)
  }

  test("Delete directory: empty directory") {
    val dir = createSubDir()
    ask(DeleteDirectoryRequest(dir))
    expectNotExist(dir)
  }

  test("Delete directory: non-empty directory") {
    val subtree = createSubtree()
    ask(DeleteDirectoryRequest(subtree.root))
    expectNotExist(subtree.root)
  }

  test("Delete directory: missing directory") {
    val missingPath = tempDir.resolve("foo")
    abet[NoSuchFileException](DeleteDirectoryRequest(missingPath))
  }

  test("Delete file: simple") {
    val file = createSubFile()
    expectExist(file)
    ask(DeleteFileRequest(file))
    expectNotExist(file)
  }

  test("Delete file: missing file") {
    val missingPath = tempDir.resolve("foo")
    expectNotExist(missingPath)
    abet[NoSuchFileException](DeleteFileRequest(missingPath))
    expectNotExist(missingPath)
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

  test("Move directory: empty directory") {
    val subdir      = createSubDir()
    val destination = tempDir.resolve("target")
    ask(MoveDirectoryRequest(subdir, destination))

    assert(!Files.exists(subdir))
    assert(Files.exists(destination))
  }

  test("Move directory: non-empty directory") {
    val subtree     = createSubtree()
    val destination = tempDir.resolve("target")
    ask(MoveDirectoryRequest(subtree.root, destination))
    val subtreeExpected = Subtree(destination, subtree.children)
    assert(!Files.exists(subtree.root))
    expectSubtree(subtreeExpected)
  }

  test("Move directory: target already exists") {
    val subtree     = createSubtree()
    val destination = tempDir.resolve("target")
    Files.createDirectory(destination)
    abet[FileExistsException](MoveDirectoryRequest(subtree.root, destination))
    // source was not destroyed by failed move
    expectSubtree(subtree)
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

  test("Touch: new file") {
    val filePath = tempDir.resolve("bar")
    ask(TouchFileRequest(filePath))
    expectExist(filePath)
    Files.size(filePath) should be(0)
  }

  test("Touch: update file") {
    val filePath         = createSubFile()
    val initialTimestamp = Files.getLastModifiedTime(filePath).toInstant

    val beforeRequest = Instant.now()
    assert(initialTimestamp.isBefore(beforeRequest))
    // [mwu] I'm not really happy about these sleeps but without them all 3
    // times (before, after, timestamp) usually got the same read and the
    // point of this test is to make sure that touch sensibly updates the
    // last modified timestamp
    Thread.sleep(25)
    ask(TouchFileRequest(filePath))
    Thread.sleep(25)
    val afterRequest   = Instant.now()
    val finalTimestamp = Files.getLastModifiedTime(filePath).toInstant
    assert(finalTimestamp.isAfter(beforeRequest))
    assert(finalTimestamp.isBefore(afterRequest))
    expectExist(filePath)
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

  test("Watch: WIP") {
    val eventsInbox = TestInbox[FileSystemEvent]()
    ask(WatchPathRequest(tempDir, eventsInbox.ref))

    createSubFile()
    createSubDir()
    val msg = eventsInbox.receiveMessage()
    assert(eventsInbox.hasMessages)
    eventsInbox.receiveAll().foreach(msg => println(s"$msg"))
  }
}

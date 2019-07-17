package org.enso.filemanager

import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import akka.actor.testkit.typed.scaladsl.TestInbox

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

import org.apache.commons.io.FileExistsException
import org.scalatest.FunSuite
import org.scalatest.Matchers
import org.scalatest.Outcome

import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success

class FileManagerBehaviorTests
    extends FunSuite
    with Matchers
    with FileSystemHelpers {
  import API._

  var testKit: BehaviorTestKit[InputMessage] = _
  var inbox: TestInbox[OutputMessage]        = _

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
    val subtreeExpected = subtree.rebase(destination)
    expectSubtree(subtree)
    expectSubtree(subtreeExpected)
  }

  test("Copy directory: target already exists") {
    val subtree     = createSubtree()
    val destination = tempDir.resolve("target")
    Files.createDirectory(destination)
    // no exception should happen, but merge
    ask(CopyDirectoryRequest(subtree.root, destination))
    val subtreeExpected = subtree.rebase(destination)
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
    val subtreeExpected = subtree.rebase(destination)
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

    Thread.sleep(1000)
    ask(TouchFileRequest(filePath))
    val finalTimestamp = Files.getLastModifiedTime(filePath).toInstant
    assert(initialTimestamp.isBefore(finalTimestamp))
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
    response.size should be(contents.length)
  }
}

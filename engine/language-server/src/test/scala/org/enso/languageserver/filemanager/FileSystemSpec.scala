package org.enso.languageserver.filemanager

import java.nio.file.{Files, Paths}

import cats.effect.IO
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.io.Source

class FileSystemSpec extends AnyFlatSpec with Matchers {

  "A file system interpreter" should "write textual content to file" in new TestCtx {
    //given
    val path    = Paths.get(testDirPath.toString, "foo.txt")
    val content = "123456789"
    //when
    val result =
      objectUnderTest.write(path.toString, content).unsafeRunSync()
    //then
    result shouldBe Right(())
    Source.fromFile(path.toFile).getLines().mkString shouldBe content
  }

  it should "overwrite existing files" in new TestCtx {
    //given
    val path            = Paths.get(testDirPath.toString, "foo.txt")
    val existingContent = "123456789"
    val newContent      = "abcdef"
    //when
    objectUnderTest.write(path.toString, existingContent).unsafeRunSync()
    objectUnderTest.write(path.toString, newContent).unsafeRunSync()
    //then
    Source.fromFile(path.toFile).getLines().mkString shouldBe newContent
  }

  it should "create the parent directory if it doesn't exist" in new TestCtx {
    //given
    val path    = Paths.get(testDirPath.toString, "foo.txt")
    val content = "123456789"
    testDir.delete()
    //when
    val result =
      objectUnderTest.write(path.toString, content).unsafeRunSync()
    //then
    result shouldBe Right(())
    Source.fromFile(path.toFile).getLines().mkString shouldBe content
  }

  trait TestCtx {

    val testDirPath = Files.createTempDirectory(null)

    val testDir = testDirPath.toFile
    testDir.deleteOnExit()

    val objectUnderTest = new FileSystem[IO]

  }

}

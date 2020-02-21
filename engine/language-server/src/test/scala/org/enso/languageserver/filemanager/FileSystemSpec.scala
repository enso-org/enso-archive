package org.enso.languageserver.filemanager

import java.nio.file.{Files, Paths}

import cats.effect.IO
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.io.Source

class FileSystemSpec extends AnyFlatSpec with Matchers {

  "A file system interpreter" should "write textual content to file" in new TestCtx {
    //given
    val path    = Paths.get(TestDirPath.toString, "foo.txt")
    val content = "123456789"
    //when
    println(path)
    val result =
      objectUnderTest.write(path.toString, content).unsafeRunSync()
    //then
    result shouldBe Right(())
    Source.fromFile(path.toFile).getLines().mkString shouldBe content
  }

  trait TestCtx {

    val TestDirPath = Files.createTempDirectory(null)

    val TestDir = TestDirPath.toFile
//    TestDir.deleteOnExit()

    val objectUnderTest = new FileSystem[IO]

  }

}

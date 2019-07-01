package org.enso.filemanager

import akka.actor.ActorSystem
import org.scalatest.Matchers
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import java.io.File
import java.nio.file.Files

import org.enso.filemanager.FileManager.{ExistsRequest, ListRequest}

import scala.language.postfixOps
import scala.concurrent.duration._

class FileManagerTests(_system: ActorSystem)
  extends TestKit(_system)
    with Matchers
    with WordSpecLike
    with BeforeAndAfterAll {

  def this() = this(ActorSystem("FileManagerTests"))

  override def afterAll: Unit = {
    shutdown(system)
  }

  "A Greeter Actor" should {
    "pass on a greeting message when instructed to" in {
      val temp = Files.createTempDirectory("file-manager-test")

      val testProbe = TestProbe()
      val fileManager = system.actorOf(FileManager.props)

      fileManager ! FileManager.Request(testProbe.ref, ListRequest(temp.toFile))
      testProbe.expectMsg(500 millis, FileManager.ListResponse(Array()))

      fileManager ! FileManager.Request(testProbe.ref, ExistsRequest(new File(temp.toFile, "foo.txt")))
      testProbe.expectMsg(500 millis, FileManager.ExistsResponse(false))
    }
  }
  //#first-test
}
//#full-example

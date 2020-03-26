package org.enso.languageserver.filemanager

import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.{Executors, LinkedBlockingQueue}

import org.apache.commons.io.FileUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.util.Try

class FileEventWatcherSpec extends AnyFlatSpec with Matchers {

  import FileEventWatcherApi._

  final val Timeout: FiniteDuration = 5.seconds

  it should "get create events" in withWatcher { (path, events) =>
    val fileA = Paths.get(path.toString, "1.txt")

    Files.createFile(fileA)
    // val event = events.poll(Timeout.length, Timeout.unit)
    // event shouldBe WatcherEvent(fileA, EventTypeCreate)
    consume(events)
  }

  it should "get delete events" in withWatcher { (path, events) =>
    val fileA = Paths.get(path.toString, "2.txt")

    Files.createFile(fileA)
    // val event1 = events.poll(Timeout.length, Timeout.unit)
    // event1 shouldBe WatcherEvent(fileA, EventTypeCreate)

    Files.delete(fileA)
    // val event2 = events.poll(Timeout.length, Timeout.unit)
    // event2 shouldBe WatcherEvent(fileA, EventTypeDelete)
    consume(events)
  }

  it should "get modify events" in withWatcher { (path, events) =>
    val fileA = Paths.get(path.toString, "3.txt")

    Files.createFile(fileA)
    // val event1 = events.poll(Timeout.length, Timeout.unit)
    // event1 shouldBe WatcherEvent(fileA, EventTypeCreate)

    Files.write(fileA, "hello".getBytes())
    // val event2 = events.poll(Timeout.length, Timeout.unit)
    // event2 shouldBe WatcherEvent(fileA, EventTypeModify)
    consume(events)
  }

  it should "get events from subdirectories" in withWatcher { (path, events) =>
    val subdir = Paths.get(path.toString, "subdir")
    val fileA  = Paths.get(path.toString, "subdir", "4.txt")

    Files.createDirectories(subdir)
    // val event1 = events.poll(Timeout.length, Timeout.unit)
    // event1 shouldBe WatcherEvent(subdir, EventTypeCreate)

    Files.createFile(fileA)
    // val event2 = events.poll(Timeout.length, Timeout.unit)
    // event2 shouldBe WatcherEvent(fileA, EventTypeCreate)
    consume(events)
  }

  it should "test macOS file events" in withWatcherMacOs { (path, events) =>
    val file = Paths.get(path.toString, "5.txt")
    Files.createFile(file)

    val event = events.poll(Timeout.length, Timeout.unit)
    event shouldBe WatcherEvent(file, EventTypeCreate)
    // consume(events)
  }

  def withWatcher(
    test: (Path, LinkedBlockingQueue[WatcherEvent]) => Any
  ): Any = {
    val executor = Executors.newSingleThreadExecutor()
    val tmp      = Files.createTempDirectory(null).toRealPath()
    val queue    = new LinkedBlockingQueue[WatcherEvent]()
    val watcher  = new FileEventWatcher(tmp, queue.put(_))

    executor.submit(new Runnable {
      def run() = watcher.start()
    })

    try test(tmp, queue)
    finally {
      watcher.stop()
      executor.shutdown()
      Try(executor.awaitTermination(Timeout.length, Timeout.unit))
      Try(FileUtils.deleteDirectory(tmp.toFile)): Unit
    }
  }

  def withWatcherMacOs(
    test: (Path, LinkedBlockingQueue[WatcherEvent]) => Any
  ): Any = {
    val executor = Executors.newSingleThreadExecutor()
    val tmp      = Files.createTempDirectory(null).toRealPath()
    val keep     = Paths.get(tmp.toString, ".keep")
    Files.createFile(keep)
    val queue    = new LinkedBlockingQueue[WatcherEvent]()
    val watcher  = new FileEventWatcher(tmp, queue.put(_))

    executor.submit(new Runnable {
      def run() = watcher.start()
    })

    try test(tmp, queue)
    finally {
      watcher.stop()
      executor.shutdown()
      Try(executor.awaitTermination(Timeout.length, Timeout.unit))
      Try(FileUtils.deleteDirectory(tmp.toFile)): Unit
    }
  }

  // poll until the timeout exception is thrown
  def consume(queue: LinkedBlockingQueue[WatcherEvent]): Unit = {
    var event = queue.poll(Timeout.length, Timeout.unit)
    while (event ne null) {
      println(event)
      event = queue.poll(Timeout.length, Timeout.unit)
    }
  }
}

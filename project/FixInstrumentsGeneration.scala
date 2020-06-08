import sbt.Keys._
import sbt._

object FixInstrumentsGeneration {
  private val instrumentsSubPath = "org/enso/interpreter/instrument"

  /**
    * This task detects any changes in source files of Instruments and forces
    * recompilation of all instruments on any change.
    * This is to ensure that the Annotation Processor registers all of the
    * instruments.
    * (Without that fix, incremental compilation would not register unchanged
    * instruments, leading to runtime errors.)
    */
  lazy val task = Def.task {
    val root = baseDirectory.value
    val sources =
      (file(s"$root/src/main/java/$instrumentsSubPath") ** "*Instrument.java").get
    val baseClassDirectory = (Compile / classDirectory).value
    val classFilesDirectory =
      file(s"$baseClassDirectory/$instrumentsSubPath")

    val schemaSourcesStore =
      streams.value.cacheStoreFactory.make("instruments_fixer")

    Tracked.diffInputs(schemaSourcesStore, FileInfo.hash)(sources.toSet) {
      sourcesDiff: ChangeReport[File] =>
        if (sourcesDiff.modified.nonEmpty) {
          val others =
            if (sourcesDiff.modified.size >= 2)
              s" and ${sourcesDiff.modified.size - 1} others"
            else ""
          val firstInstrument = sourcesDiff.modified.head
          println(
            s"Instruments sources ($firstInstrument$others) have been changed.\n" +
            s"Forcing recompilation of all instruments to maintain consistency of generated services files."
          )

          val instrumentRelatedClassFiles =
            (classFilesDirectory ** "*Instrument.class").get
          instrumentRelatedClassFiles.foreach(_.delete())
        }
    }
  }
}

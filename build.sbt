import sys.process._
// Global Configuration
organization := "org.enso"
scalaVersion := "2.12.8"

// Compiler Options
scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint"
)

// Benchmark Configuration
lazy val Benchmark = config("bench") extend Test
lazy val Stage0    = config("stage0") extend Compile
lazy val Stage1    = config("stage1") extend Compile
lazy val bench     = taskKey[Unit]("Run Benchmarks")

// Global Project
lazy val enso = (project in file("."))
  .settings(version := "0.1")
  .aggregate(
    syntax,
    pkg,
    interpreter
  )

// Sub-Projects
lazy val syntax = (project in file("syntax"))
  .settings(
    mainClass in (Compile, run) := Some("org.enso.syntax.Main"),
    version := "0.1"
  )
  .settings(
    libraryDependencies ++= Seq(
      "com.storm-enroute" %% "scalameter" % "0.17" % "bench",
      "org.typelevel"     %% "cats-core"  % "1.6.0",
      "org.scalatest"     %% "scalatest"  % "3.0.5" % Test,
      "com.lihaoyi"       %% "pprint"     % "0.5.3"
    ),
    resolvers ++= Seq(
      "Sonatype OSS Snapshots" at
      "https://oss.sonatype.org/content/repositories/snapshots",
      "Sonatype OSS Releases" at
      "https://oss.sonatype.org/content/repositories/releases"
    )
  )
  .settings(SbtJFlexPlugin.jflexSettings)
  .configs(Test)
  .settings(
    testFrameworks += new TestFramework("org.scalameter.ScalaMeterFramework"),
    logBuffered := false
  )
  .configs(Benchmark)
  .settings(
    inConfig(Benchmark)(Defaults.testSettings),
    bench := (test in Benchmark).value,
    parallelExecution in Benchmark := false
  )

lazy val pkg = (project in file("pkg"))
  .settings(
    mainClass in (Compile, run) := Some("org.enso.pkg.Main"),
    version := "0.1"
  )
  .settings(
    libraryDependencies ++= Seq("circe-core", "circe-generic", "circe-yaml")
      .map("io.circe" %% _ % "0.10.0"),
    libraryDependencies += "commons-io" % "commons-io" % "2.6"
  )

lazy val interpreter = (project in file("interpreter"))
  .settings(
    mainClass in (Compile, run) := Some("org.enso.interpreter.Main"),
    version := "0.1"
  )
  .settings(
    libraryDependencies ++= Seq(
      "com.chuusai"         %% "shapeless"            % "2.3.3",
      "com.storm-enroute"   %% "scalameter"           % "0.17" % "bench",
      "org.graalvm.sdk"     % "graal-sdk"             % "19.0.0",
      "org.graalvm.sdk"     % "polyglot-tck"          % "19.0.0",
      "org.graalvm.truffle" % "truffle-api"           % "19.0.0",
      "org.graalvm.truffle" % "truffle-dsl-processor" % "19.0.0",
      "org.graalvm.truffle" % "truffle-nfi"           % "19.0.0",
      "org.graalvm.truffle" % "truffle-tck"           % "19.0.0",
      "org.graalvm.truffle" % "truffle-tck-common"    % "19.0.0",
      "org.scalacheck"      %% "scalacheck"           % "1.14.0" % Test,
      "org.scalatest"       %% "scalatest"            % "3.2.0-SNAP10" % Test,
      "org.typelevel"       %% "cats-core"            % "2.0.0-M4"
    )
  )
  .settings(javaOptions ++= graalOptions)
  .dependsOn(syntax)
  .configs(Test)
  .configs(Benchmark)
  .settings(
    inConfig(Benchmark)(Defaults.testSettings),
    bench := (test in Benchmark).value,
    parallelExecution in Benchmark := false
  )
  .settings(inConfig(Stage0)(Defaults.compileSettings))
  .settings(inConfig(Stage1)(Defaults.compileSettings))
  .settings(Stage0 / classDirectory := (Compile / classDirectory).value)
  .settings(Stage1 / classDirectory := (Compile / classDirectory).value)
  .settings(
    Stage1 / sourceDirectory := new File(thisProject.value.base, "src/main")
  )
  .settings(
    Stage0 / sourceDirectory := new File(thisProject.value.base, "src/stage0")
  )
  .settings(Stage0 / genTruffle := {
    val classPath = (Stage0 / fullClasspath).value.files.mkString(":")
    val destinationDir =
      (Stage0 / classDirectory).value.getAbsolutePath
    def recursiveGetClasses(root: File, pkgName: String = ""): Seq[String] = {
      val subdirs = root.listFiles(_.isDirectory).toSeq
      val classes = root.listFiles(_.getName.endsWith(".class")).toSeq
      val recur =
        subdirs.flatMap { dir =>
          val dirName = dir.getName
          val newPkgName =
            if (pkgName.isEmpty) dirName else s"$pkgName.$dirName"
          recursiveGetClasses(dir, newPkgName)
        }
      val classNames = classes.map { classFile =>
        val name      = classFile.getName
        val className = name.split("\\.")
        s"$pkgName.${className.head}"
      }
      classNames ++ recur
    }
    val classesToProcess =
      recursiveGetClasses((Stage0 / classDirectory).value, "").mkString(" ")
    val processor = "com.oracle.truffle.dsl.processor.TruffleProcessor"

    val managedSourceDir = (Stage0 / sourceManaged).value
    managedSourceDir.mkdirs()

    val command = Seq(
      "javac",
      s"-cp $classPath",
      s"-processor $processor",
      "-XprintRounds",
      s"-d $destinationDir",
      s"-s $managedSourceDir",
      classesToProcess
    ).mkString(" ")

    command !
  })
  .settings(
    Compile / compile := (Stage1 / compile)
      .dependsOn(Stage0 / genTruffle)
      .dependsOn(Stage0 / compile)
      .value
  )

val genTruffle =
  taskKey[Unit]("Run annotations processor on .class files")

// Configuration Options
lazy val graalOptions = Seq(
  "-XX:UseJVMCIClassLoader"
)

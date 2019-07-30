import sbt.Keys.scalacOptions
// Global Configuration
organization := "org.enso"
scalaVersion in ThisBuild := "2.12.8"

// Compiler Options
scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint"
)

// Benchmark Configuration
lazy val Benchmark = config("bench") extend Test
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
lazy val logger = (project in file("lib/logger"))
  .settings(
    version := "0.1",
    scalacOptions += "-language:experimental.macros"
  )
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect"  % "2.12.8",
      "org.scala-lang" % "scala-compiler" % "2.12.8"
    )
  )

lazy val flexer = (project in file("lib/flexer"))
  .settings(
    version := "0.1",
    scalacOptions += "-language:experimental.macros",
    scalacOptions += "-Xmacro-settings:-logging@org.enso.flexer"
  )
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect"  % "2.12.8",
      "org.scala-lang" % "scala-compiler" % "2.12.8",
      "org.feijoas"    %% "mango"         % "0.14"
    ),
    resolvers += Resolver.sonatypeRepo("releases"),
    addCompilerPlugin(
      "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
    )
  )
  .dependsOn(logger) //depends logger macro

lazy val syntax_definition = (project in file("syntax/definition"))
  .settings(
    scalacOptions += "-Xmacro-settings:-logging@org.enso",
    libraryDependencies ++= Seq(
      "org.typelevel"      %% "cats-core"     % "1.6.0",
      "com.lihaoyi"        %% "pprint"        % "0.5.3",
      "org.scala-lang"     % "scala-reflect"  % "2.12.8",
      "org.scala-lang"     % "scala-compiler" % "2.12.8",
      "org.feijoas"        %% "mango"         % "0.14",
      "org.apache.commons" % "commons-text"   % "1.6",
      "org.scalameta"      %% "scalameta"     % "4.2.0",
      "com.lihaoyi"        %% "scalatags"     % "0.7.0"
    ),
    resolvers ++= Seq(
      "Sonatype OSS Snapshots" at
      "https://oss.sonatype.org/content/repositories/snapshots",
      "Sonatype OSS Releases" at
      "https://oss.sonatype.org/content/repositories/releases"
    )
  )
  .dependsOn(logger)
  .dependsOn(flexer)

lazy val syntax = (project in file("syntax/specialization"))
  .settings(
    mainClass in (Compile, run) := Some("org.enso.syntax.text.Main"),
    version := "0.1",
    scalacOptions += "-Ypartial-unification"
  )
  .settings(
    libraryDependencies ++= Seq(
      "com.storm-enroute"  %% "scalameter"    % "0.17" % "bench",
      "org.typelevel"      %% "cats-core"     % "1.6.0",
      "org.scalatest"      %% "scalatest"     % "3.0.5" % Test,
      "com.lihaoyi"        %% "pprint"        % "0.5.3",
      "org.scala-lang"     % "scala-reflect"  % "2.12.8",
      "org.scala-lang"     % "scala-compiler" % "2.12.8",
      "org.feijoas"        %% "mango"         % "0.14",
      "org.apache.commons" % "commons-text"   % "1.6",
      "org.scalameta"      %% "scalameta"     % "4.2.0",
      "org.typelevel"      %% "cats-core"     % "1.6.1",
      "com.lihaoyi"        %% "scalatags"     % "0.7.0"
    ),
    resolvers ++= Seq(
      "Sonatype OSS Snapshots" at
      "https://oss.sonatype.org/content/repositories/snapshots",
      "Sonatype OSS Releases" at
      "https://oss.sonatype.org/content/repositories/releases"
    ),
    resolvers += Resolver.sonatypeRepo("releases"),
    addCompilerPlugin(
      "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
    ),
    (Compile / compile) := (Compile / compile)
      .dependsOn(Def.taskDyn {
        val parserCompile =
          (syntax_definition / Compile / compileIncremental).value
        if (parserCompile.hasModified) {
          Def.task {
            streams.value.log.info("Parser changed, forcing recompilation.")
            clean.value
          }
        } else Def.task {}
      })
      .value
  )
  .dependsOn(syntax_definition)
  .dependsOn(logger)
  .dependsOn(flexer)
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
      "com.chuusai"       %% "shapeless"  % "2.3.3",
      "com.storm-enroute" %% "scalameter" % "0.17" % "bench",
      "org.graalvm.sdk"   % "graal-sdk"   % "19.0.0",
      "org.scalacheck"    %% "scalacheck" % "1.14.0" % Test,
      "org.scalatest"     %% "scalatest"  % "3.2.0-SNAP10" % Test,
      "org.typelevel"     %% "cats-core"  % "2.0.0-M4"
    )
  )
  .dependsOn(syntax)
  .configs(Test)
  .configs(Benchmark)
  .settings(
    inConfig(Benchmark)(Defaults.testSettings),
    bench := (test in Benchmark).value,
    parallelExecution in Benchmark := false
  )

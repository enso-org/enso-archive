import sbt.Keys.scalacOptions
// Global Configuration
organization := "org.enso"
scalaVersion in ThisBuild := "2.12.8"

val monocleVersion = "1.6.0"

// Compiler Options
scalacOptions in ThisBuild ++= Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-encoding",
  "utf-8",                         // Specify character encoding used by source files.
  "-explaintypes",                 // Explain type errors in more detail.
  "-feature",                      // Emit warning and location for usages of features that should be imported explicitly.
  "-language:existentials",        // Existential types (besides wildcard types) can be written and inferred
  "-language:experimental.macros", // Allow macro definition (besides implementation and application)
  "-language:higherKinds",         // Allow higher-kinded types
  "-language:implicitConversions", // Allow definition of implicit functions called views
  "-unchecked",                    // Enable additional warnings where generated code depends on assumptions.
//  "-Xcheckinit",                   // Wrap field accessors to throw an exception on uninitialized access.
//  "-Xfatal-warnings",                 // Fail the compilation if there are any warnings.
//  "-Xfuture",                         // Turn on future language features.
  "-Xlint:adapted-args",              // Warn if an argument list is modified to match the receiver.
  "-Xlint:by-name-right-associative", // By-name parameter of right associative operator.
  "-Xlint:constant",                  // Evaluation of a constant arithmetic expression results in an error.
  "-Xlint:delayedinit-select",        // Selecting member of DelayedInit.
  "-Xlint:doc-detached",              // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible",              // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any",                 // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator",      // A string literal appears to be missing an interpolator id.
  "-Xlint:nullary-override",          // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Xlint:nullary-unit",              // Warn when nullary methods return Unit.
  "-Xlint:option-implicit",           // Option.apply used implicit view.
  "-Xlint:package-object-classes",    // Class or object defined in package object.
  "-Xlint:poly-implicit-overload",    // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:private-shadow",            // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align",               // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow",     // A local type parameter shadows a type already in scope.
  "-Xlint:unsound-match",             // Pattern match may not be typesafe.
  "-Yno-adapted-args",                // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
  "-Ypartial-unification",            // Enable partial unification in type constructor inference
  "-Ywarn-dead-code",                 // Warn when dead code is identified.
  "-Ywarn-extra-implicit",            // Warn when more than one implicit parameter section is defined.
  "-Ywarn-inaccessible",              // Warn about inaccessible types in method signatures.
  "-Ywarn-infer-any",                 // Warn when a type argument is inferred to be `Any`.
  "-Ywarn-nullary-override",          // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Ywarn-nullary-unit",              // Warn when nullary methods return Unit.
  "-Ywarn-numeric-widen",             // Warn when numerics are widened.
  "-Ywarn-unused:implicits",          // Warn if an implicit parameter is unused.
  "-Ywarn-unused:imports",            // Warn if an import selector is not referenced.
  "-Ywarn-unused:locals",             // Warn if a local definition is unused.
  "-Ywarn-unused:params",             // Warn if a value parameter is unused.
  "-Ywarn-unused:patvars",            // Warn if a variable bound in a pattern is unused.
  "-Ywarn-unused:privates",           // Warn if a private member is unused.
  "-Ywarn-value-discard",             // Warn when non-Unit expression results are unused.
//  "-language:implicitConversions",
  "-Xmacro-settings:-logging@org.enso"
//  "-Xmacro-settings:-logging@org.enso.flexer.automata"
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
  .dependsOn(unused)

lazy val flexer = (project in file("lib/flexer"))
  .settings(
    version := "0.1",
    scalacOptions += "-language:experimental.macros",
    scalacOptions -= "-deprecation",    // FIXME
    scalacOptions -= "-Xfatal-warnings" // FIXME
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

lazy val unused = (project in file("lib/unused"))
  .settings(
    version := "0.1",
    scalacOptions += "-nowarn"
  )

lazy val syntax_definition = (project in file("syntax/definition"))
  .settings(
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
    libraryDependencies ++= Seq(
      "com.github.julien-truffaut" %% "monocle-core"  % monocleVersion,
      "com.github.julien-truffaut" %% "monocle-macro" % monocleVersion,
      "com.github.julien-truffaut" %% "monocle-law"   % monocleVersion % "test"
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
      "org.typelevel"      %% "cats-core"     % "1.6.1"
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

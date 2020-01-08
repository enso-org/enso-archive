package org.enso.interpreter.test

import java.io.File

import org.enso.interpreter.Constants
import org.enso.interpreter.runtime.RuntimeOptions
import org.enso.pkg.Package
import org.enso.polyglot.{LanguageInfo, TopScope}
import org.graalvm.polyglot.{Context, Source, Value}
import org.scalatest.{FlatSpec, Matchers}

trait PackageTest extends FlatSpec with Matchers with ValueEquality {

  def evalTestProject(name: String): Value = {
    val pkgPath =
      new File(getClass.getClassLoader.getResource(name).getPath)
    val pkg        = Package.fromDirectory(pkgPath).get
    val mainFile   = pkg.mainFile
    val mainModule = pkg.moduleNameForFile(mainFile)
    val context = Context
      .newBuilder(LanguageInfo.ID)
      .allowExperimentalOptions(true)
      .allowAllAccess(true)
      .option(RuntimeOptions.getPackagesPathOption, pkgPath.getAbsolutePath)
      .out(System.out)
      .in(System.in)
      .build()
    context.initialize(LanguageInfo.ID)
    val topScope = TopScope.get(context)
    val mainModuleScope = topScope.getModule(mainModule.toString)
    val assocCons = mainModuleScope.getAssociatedConstructor
    val mainFun = mainModuleScope.getMethod(assocCons, "main")
    InterpreterException.rethrowPolyglot(mainFun.execute(assocCons))
  }
}

package org.enso.runner

import org.apache.commons.cli._
import org.enso.interpreter.Constants
import org.enso.pkg.Package
import org.graalvm.polyglot.Source
import java.io.File

import org.enso.gateway
import org.enso.{Gateway, LanguageServer}

import scala.io.StdIn
import scala.util.Try

/** The main CLI entry point class. */
object Main {

  private val RUN_OPTION     = "run"
  private val HELP_OPTION    = "help"
  private val NEW_OPTION     = "new"
  private val REPL_OPTION    = "repl"
  private val JUPYTER_OPTION = "jupyter-kernel"
  private val LSP_OPTION     = "lsp"

  /**
    * Builds the [[Options]] object representing the CLI syntax.
    *
    * @return an [[Options]] object representing the CLI syntax
    */
  private def buildOptions = {
    val help = Option
      .builder("h")
      .longOpt(HELP_OPTION)
      .desc("Displays this message.")
      .build
    val repl = Option.builder
      .longOpt(REPL_OPTION)
      .desc("Runs the Enso REPL.")
      .build
    val run = Option.builder
      .hasArg(true)
      .numberOfArgs(1)
      .argName("file")
      .longOpt(RUN_OPTION)
      .desc("Runs a specified Enso file.")
      .build
    val newOpt = Option.builder
      .hasArg(true)
      .numberOfArgs(1)
      .argName("path")
      .longOpt(NEW_OPTION)
      .desc("Creates a new Enso project.")
      .build
    val jupyterOption = Option.builder
      .hasArg(true)
      .numberOfArgs(1)
      .argName("connection file")
      .longOpt(JUPYTER_OPTION)
      .desc("Runs Enso Jupyter Kernel.")
      .build
    val lsp = Option.builder
      .longOpt(LSP_OPTION)
      .desc("Talks Language Server Protocol.")
      .build
    val options = new Options
    options
      .addOption(help)
      .addOption(repl)
      .addOption(run)
      .addOption(newOpt)
      .addOption(jupyterOption)
      .addOption(lsp)
    options
  }

  /**
    * Prints the help message to the standard output.
    *
    * @param options object representing the CLI syntax
    */
  private def printHelp(options: Options): Unit =
    new HelpFormatter().printHelp(Constants.LANGUAGE_ID, options)

  /** Terminates the process with a failure exit code. */
  private def exitFail(): Unit = System.exit(1)

  /** Terminates the process with a success exit code. */
  private def exitSuccess(): Unit = System.exit(0)

  /**
    * Handles the `--new` CLI option.
    *
    * @param path root path of the newly created project
    */
  private def createNew(path: String) {
    Package.getOrCreate(new File(path))
    exitSuccess()
  }

  /**
    * Handles the `--run` CLI option.
    *
    * @param path path of the project or file to execute
    */
  private def run(path: String): Unit = {
    val file = new File(path)
    if (!file.exists) {
      System.out.println("File " + file + " does not exist.")
      exitFail()
    }
    val projectMode = file.isDirectory
    val packagePath =
      if (projectMode) file.getAbsolutePath
      else ""
    var mainLocation = file
    if (projectMode) {
      val pkg  = Package.fromDirectory(file)
      val main = pkg.map(_.mainFile)
      if (!main.exists(_.exists)) {
        println("Main file does not exist.")
        exitFail()
      }
      mainLocation = main.get
    }
    val context = new ContextFactory().create(
      packagePath,
      System.in,
      System.out,
      Repl(TerminalIO())
    )
    val source = Source.newBuilder(Constants.LANGUAGE_ID, mainLocation).build
    context.eval(source)
    exitSuccess()
  }

  /**
    * Handles the `--repl` CLI option
    */
  private def runRepl(): Unit = {
    val dummySourceToTriggerRepl = "@{ @breakpoint[@Debug] }"
    val context =
      new ContextFactory().create("", System.in, System.out, Repl(TerminalIO()))
    context.eval(Constants.LANGUAGE_ID, dummySourceToTriggerRepl)
    exitSuccess()
  }

  /**
    * Handles the `--lsp` CLI option
    */
  private def talkLSP(): Unit = {
    val context = new ContextFactory().create(
      "",
      System.in,
      System.out,
      Repl(TerminalIO())
    )
    val languageServer = new LanguageServer(context)
    languageServer.run(/*LanguageServer.Config("http", "localhost", 8080)*/)
    Gateway(languageServer).run(gateway.Server.Config("ws", "localhost", 30000))
    println("Press ENTER to shut down")
    StdIn.readLine()
    exitSuccess()
  }

  /**
    * Main entry point for the CLI program.
    *
    * @param args the command line arguments
    */
  def main(args: Array[String]): Unit = {
    val options = buildOptions
    val parser  = new DefaultParser
    val line: CommandLine = Try(parser.parse(options, args)).getOrElse {
      printHelp(options)
      exitFail()
      return
    }
    if (line.hasOption(HELP_OPTION)) {
      printHelp(options)
      exitSuccess()
    }
    if (line.hasOption(NEW_OPTION)) {
      createNew(line.getOptionValue(NEW_OPTION))
    }
    if (line.hasOption(RUN_OPTION)) {
      run(line.getOptionValue(RUN_OPTION))
    }
    if (line.hasOption(REPL_OPTION)) {
      runRepl()
    }
    if (line.hasOption(JUPYTER_OPTION)) {
      new JupyterKernel().run(line.getOptionValue(JUPYTER_OPTION))
    }
    if (line.hasOption(LSP_OPTION)) {
      talkLSP()
    }
    printHelp(options)
    exitFail()
  }
}

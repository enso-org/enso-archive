package org.enso.interpreter.runtime;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import org.enso.compiler.Compiler;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.source.Source;
import org.enso.interpreter.AstGlobalScope;
import org.enso.interpreter.Constants;
import org.enso.interpreter.EnsoParser;
import org.enso.interpreter.Language;
import org.enso.interpreter.builder.ModuleScopeExpressionFactory;
import org.enso.interpreter.node.EnsoRootNode;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.callable.atom.AtomConstructor;
import org.enso.interpreter.runtime.error.ModuleDoesNotExistException;
import org.enso.interpreter.runtime.scope.ModuleScope;
import org.enso.interpreter.util.ScalaConversions;
import org.enso.pkg.Package;
import org.enso.pkg.SourceFile;

import java.io.File;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The language context is the internal state of the language that is associated with each thread in
 * a running Enso program.
 */
public class Context {

  private final Language language;
  private final Env environment;
  private final Compiler compiler;
  private final PrintStream out;
  private final Builtins builtins;

  /**
   * Creates a new Enso context.
   *
   * @param language the language identifier
   * @param environment the execution environment of the {@link TruffleLanguage}
   */
  public Context(Language language, Env environment) {
    this.language = language;
    this.environment = environment;
    this.out = new PrintStream(environment.out());
    this.builtins = new Builtins(language);

    List<File> packagePaths = RuntimeOptions.getPackagesPaths(environment);
    // TODO [MK] Replace getTruffleFile with getInternalTruffleFile when Graal 19.3.0 comes out.
    Map<String, Module> knownFiles =
        packagePaths.stream()
            .map(Package::fromDirectory)
            .map(ScalaConversions::asJava)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .flatMap(p -> ScalaConversions.asJava(p.listSources()).stream())
            .collect(
                Collectors.toMap(
                    SourceFile::qualifiedName,
                    srcFile ->
                        new Module(
                            getEnvironment().getTruffleFile(srcFile.file().getAbsolutePath()))));

      this.compiler = new Compiler(this.language, knownFiles);
  }

  /**
   * Parses language sources, registering bindings in the given scope.
   *
   * @param source the source to be parsed
   * @param scope the scope in which to register any new bindings
   * @return a call target which execution corresponds to the toplevel executable bits in the module
   */
  public CallTarget parse(Source source, ModuleScope scope) {
    AstModuleScope parsed = new EnsoParser().parseEnso(source.getCharacters().toString());
    ExpressionNode result = new ModuleScopeExpressionFactory(language, scope).run(parsed);
    EnsoRootNode root =
        new EnsoRootNode(
            language,
            new FrameDescriptor(),
            result,
            null,
            "root",
            EnsoRootNode.ResultStateHandlingMode.IGNORE);
    return Truffle.getRuntime().createCallTarget(root);
  }

  /**
   * Gets the compiler instance.
   *
   * <p>The compiler is the portion of the interpreter that performs static analysis and
   * transformation passes on the input program. A handle to the compiler lets you execute various
   * portions of the compilation pipeline, including parsing, analysis, and final code generation.
   *
   * <p>Having this access available means that Enso programs can metaprogram Enso itself.
   *
   * @return a handle to the compiler
   */
  public final Compiler compiler() {
    return compiler;
  }

  /**
   * Returns the {@link Env} instance used by this context.
   *
   * @return the {@link Env} instance used by this context
   */
  public Env getEnvironment() {
    return environment;
  }

  /**
   * Gets the language to which this context belongs.
   *
   * @return the language to which this context belongs
   */
  public Language getLanguage() {
    return language;
  }

  /**
   * Returns the standard output stream for this context.
   *
   * @return the standard output stream for this context.
   */
  public PrintStream getOut() {
    return out;
  }

  /**
   * Creates a new module scope that automatically imports all the builtin types and methods.
   *
   * @return a new module scope with automatic builtins dependency.
   */
  public ModuleScope createScope() {
    ModuleScope moduleScope = new ModuleScope();
    moduleScope.addImport(getBuiltins().getScope());
    return moduleScope;
  }

  private Builtins getBuiltins() {
    return builtins;
  }

  /**
   * Returns the atom constructor corresponding to the {@code Unit} type, for builtin constructs
   * that need to return an atom of this type.
   *
   * @return the builtin {@code Unit} atom constructor
   */
  public AtomConstructor getUnit() {
    return getBuiltins().unit();
  }
}

package org.enso.interpreter.runtime;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.source.Source;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.enso.interpreter.AstModuleScope;
import org.enso.interpreter.Constants;
import org.enso.interpreter.EnsoParser;
import org.enso.interpreter.Language;
import org.enso.interpreter.builder.ModuleScopeExpressionFactory;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.error.ModuleDoesNotExistException;
import org.enso.interpreter.runtime.scope.ModuleScope;
import org.enso.interpreter.util.ScalaConversions;
import org.enso.pkg.Package;
import org.enso.pkg.SourceFile;

/**
 * The language context is the internal state of the language that is associated with each thread in
 * a running Enso program.
 *
 * <p>Please note that a given context instance may be accessed from multiple threads at once as
 * long as Enso is executing in multi-threaded mode.
 */
public class Context {
  private final Language language;
  private final Env environment;
  private final Map<String, Module> knownFiles;

  /**
   * Creates a new Enso context.
   *
   * @param language the language identifier
   * @param environment the execution environment of the {@link TruffleLanguage}
   */
  public Context(Language language, Env environment) {
    this.language = language;
    this.environment = environment;

    List<File> packagePaths = RuntimeOptions.getPackagesPaths(environment);
    // TODO [MK] Replace getTruffleFile with getInternalTruffleFile when Graal 19.3.0 comes out.
    this.knownFiles =
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
  }

  /**
   * Parses language sources, registering bindings in the given scope.
   *
   * @param source the source to be parsed
   * @param scope the scope in which to register any new bindings
   * @return a call target which execution corresponds to the toplevel executable bits in the module
   */
  public ExpressionNode parse(Source source, ModuleScope scope) {
    AstModuleScope parsed = new EnsoParser().parseEnso(source.getCharacters().toString());

    return new ModuleScopeExpressionFactory(language, scope).run(parsed);
  }

  /**
   * Parses language sources.
   *
   * @param source the source to be parsed
   * @return a call target which execution corresponds to the toplevel executable bits in the module
   */
  public ExpressionNode parse(Source source) {
    return parse(source, new ModuleScope());
  }

  /**
   * Parses language sources from file, registering bindings in the given scope.
   *
   * @param file file containing the source to be parsed
   * @param scope the scope in which to register any new bindings
   * @return a call target which execution corresponds to the toplevel executable bits in the module
   * @throws IOException when the file could not be read
   */
  public ExpressionNode parse(TruffleFile file, ModuleScope scope) throws IOException {
    return parse(Source.newBuilder(Constants.LANGUAGE_ID, file).build(), scope);
  }

  /**
   * Finds and parses a language source by its qualified name. Results of this operation are cached.
   *
   * @param qualifiedName the qualified name of module to parse
   * @return the scope of the requested module
   * @throws IOException when the source file could not be read
   */
  public ModuleScope requestParse(String qualifiedName) throws IOException {
    Module module = knownFiles.get(qualifiedName);
    if (module == null) throw new ModuleDoesNotExistException(qualifiedName);
    return module.requestParse(this);
  }

  /**
   * Returns the {@link Env} instance used by this context.
   *
   * @return the {@link Env} instance used by this context
   */
  public Env getEnvironment() {
    return environment;
  }
}

package org.enso.interpreter.runtime;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.source.Source;
import org.enso.interpreter.AstGlobalScope;
import org.enso.interpreter.Constants;
import org.enso.interpreter.EnsoParser;
import org.enso.interpreter.Language;
import org.enso.interpreter.builder.GlobalScopeExpressionFactory;
import org.enso.interpreter.node.EnsoRootNode;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.error.ModuleDoesNotExistException;
import org.enso.interpreter.runtime.scope.ModuleScope;
import org.enso.interpreter.util.ScalaConversions;
import org.enso.pkg.Package;
import org.enso.pkg.SourceFile;

import java.io.*;
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

  public CallTarget parse(Source source, ModuleScope scope) {
    AstGlobalScope parsed = new EnsoParser().parseEnso(source.getCharacters().toString());
    ExpressionNode result = new GlobalScopeExpressionFactory(language, scope).run(parsed);
    EnsoRootNode root = new EnsoRootNode(language, new FrameDescriptor(), result, null, "root");
    return Truffle.getRuntime().createCallTarget(root);
  }

  public CallTarget parse(Source source) {
    return parse(source, new ModuleScope());
  }

  public CallTarget parse(TruffleFile file, ModuleScope scope) throws IOException {
    return parse(Source.newBuilder(Constants.LANGUAGE_ID, file).build(), scope);
  }

  public ModuleScope requestParse(String qualifiedName) throws IOException {
    Module module = knownFiles.get(qualifiedName);
    if (module == null) throw new ModuleDoesNotExistException(qualifiedName);
    return module.requestParse(this);
  }

  public Env getEnvironment() {
    return environment;
  }
}

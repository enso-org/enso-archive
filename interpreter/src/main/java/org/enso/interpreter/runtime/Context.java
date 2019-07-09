package org.enso.interpreter.runtime;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.object.Layout;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.enso.interpreter.Language;

public class Context {
  private final Layout objectLayout = Layout.createLayout();

  private final Language language;
  private final Env environment;
  private final BufferedReader input;
  private final PrintWriter output;
  private final Map<String, GlobalCallTarget> globalNames = new HashMap<>();

  public Context() {
    language = null;
    environment = null;
    input = new BufferedReader(new InputStreamReader(System.in));
    output = new PrintWriter(System.out, true);
  }

  public Context(Language language, Env environment) {
    this.language = language;
    this.environment = environment;

    this.input = new BufferedReader(new InputStreamReader(environment.in()));
    this.output = new PrintWriter(environment.out(), true);
  }

  public void registerName(String name) {
    this.globalNames.put(name, null);
  }

  public void updateCallTarget(String name, RootCallTarget rootBinding) {
    GlobalCallTarget globalTarget = this.globalNames.get(name);

    if (globalTarget == null) {
      this.globalNames.put(name, new GlobalCallTarget(rootBinding));
    } else {
      globalTarget.setTarget(rootBinding);
    }
  }

  public Optional<GlobalCallTarget> getGlobalCallTarget(String name) {
    return Optional.ofNullable(this.globalNames.get(name));
  }
}

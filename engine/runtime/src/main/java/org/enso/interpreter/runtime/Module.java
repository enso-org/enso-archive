package org.enso.interpreter.runtime;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.interop.TruffleObject;
import org.enso.interpreter.runtime.scope.ModuleScope;
import org.enso.pkg.Package;

import java.io.IOException;

/** Represents a source module with a known location. */
public class Module {
  private ModuleScope scope = null;
  private final TruffleFile file;
  private final Package.QualifiedName name;

  /**
   * Creates a new module.
   *
   * @param file file in which this module sources are located
   */
  public Module(Package.QualifiedName name, TruffleFile file) {
    this.name = name;
    this.file = file;
  }

  /**
   * Parses the module sources. The results of this operation are cached.
   *
   * @param context context in which the parsing should take place
   * @return the scope defined by this module
   * @throws IOException when the source file could not be read
   */
  public ModuleScope requestParse(Context context) {
    // TODO [AA] This needs to evolve to support scope execution
    if (scope == null) {
      scope = context.createScope(name.module());
      context.compiler().run(file, scope);
    }
    return scope;
  }

  public ModuleScope getScope() {
    return scope;
  }

  public boolean hasComputedScope() {
    return getScope() != null;
  }
}

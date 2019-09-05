package org.enso.interpreter.runtime;

import com.oracle.truffle.api.TruffleFile;
import org.enso.interpreter.runtime.scope.ModuleScope;

import java.io.IOException;

public class Module {
  private ModuleScope cachedScope = null;
  private final TruffleFile file;

  public Module(TruffleFile file) {
    this.file = file;
  }

  public ModuleScope requestParse(Context context) throws IOException {
    if (cachedScope == null) {
      cachedScope = new ModuleScope();
      context.parse(file, cachedScope);
    }
    return cachedScope;
  }
}

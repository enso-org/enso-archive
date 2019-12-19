package org.enso.interpreter.runtime.scope;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import org.enso.interpreter.Language;
import org.enso.interpreter.runtime.Builtins;
import org.enso.interpreter.runtime.Context;
import org.enso.interpreter.runtime.Module;
import org.enso.interpreter.runtime.data.Vector;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@ExportLibrary(InteropLibrary.class)
public class TopScope implements TruffleObject {
  private final Builtins builtins;
  private final Map<String, Module> modules;
  private final Scope scope = Scope.newBuilder("top_scope", this).build();

  public TopScope(Builtins builtins, Map<String, Module> modules) {
    this.builtins = builtins;
    this.modules = modules;
  }

  public Map<String, Module> getModules() {
    return modules;
  }

  public Scope getScope() {
    return scope;
  }

  public Optional<Module> getModule(String name) {
    return Optional.ofNullable(getModules().get(name));
  }

  public Builtins getBuiltins() {
    return builtins;
  }

  private static final String GET_MODULE_KEY = "get_module";
  private static final String CREATE_MODULE_KEY = "create_module";

  @ExportMessage
  public boolean hasMembers() {
    return true;
  }

  @ExportMessage
  public Vector getMembers(boolean includeInternal) {

    Set<String> keys = modules.keySet();
    keys.add(Builtins.MODULE_NAME);
    return new Vector(GET_MODULE_KEY, CREATE_MODULE_KEY);
  }

  @ExportMessage
  public abstract static class InvokeMember {
    @Specialization
    public static ModuleScope doInvoke(
        TopScope scope,
        String member,
        Object[] arguments,
        @CachedContext(Language.class) TruffleLanguage.ContextReference<Context> contextRef)
        throws UnknownIdentifierException {
      String moduleName = (String) arguments[0];
      switch (member) {
        case GET_MODULE_KEY:
          if (moduleName.equals(Builtins.MODULE_NAME)) {
            return scope.builtins.getScope();
          }
          Module module = scope.modules.get(moduleName);
          if (module == null) {
            throw UnknownIdentifierException.create(moduleName);
          }
          if (module.hasComputedScope()) {
            return module.getScope();
          } else {
            return module.requestParse(contextRef.get());
          }
        case CREATE_MODULE_KEY:
          return contextRef.get().createScope(moduleName);
        default:
          throw UnknownIdentifierException.create(member);
      }
    }
  }

  @ExportMessage
  public boolean isMemberInvocable(String member) {
    return member.equals(GET_MODULE_KEY) || member.equals(CREATE_MODULE_KEY);
  }
}

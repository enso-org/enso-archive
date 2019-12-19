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

  @ExportMessage
  public boolean hasMembers() {
    return true;
  }

  @ExportMessage
  public Vector getMembers(boolean includeInternal) {
    Set<String> keys = modules.keySet();
    keys.add(Builtins.MODULE_NAME);
    return new Vector(keys.toArray(new Object[0]));
  }

  @ExportMessage
  public abstract static class ReadMember {
    @Specialization
    public static ModuleScope doRead(
        TopScope scope,
        String member,
        @CachedContext(Language.class) TruffleLanguage.ContextReference<Context> contextRef)
        throws UnknownIdentifierException {
      if (member.equals(Builtins.MODULE_NAME)) {
        return scope.builtins.getScope();
      }
      Module module = scope.modules.get(member);
      if (module == null) {
        throw UnknownIdentifierException.create(member);
      }
      if (module.hasComputedScope()) {
        return module.getScope();
      } else {
        return module.requestParse(contextRef.get());
      }
    }
  }

  @ExportMessage
  public boolean hasMemberReadSideEffects(String member) {
    return !member.equals(Builtins.MODULE_NAME) && !getModules().get(member).hasComputedScope();
  }

  @ExportMessage
  public boolean isMemberReadable(String member) {
    return member.equals(Builtins.MODULE_NAME) || getModules().containsKey(member);
  }
}

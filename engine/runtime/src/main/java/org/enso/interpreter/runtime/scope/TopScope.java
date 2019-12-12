package org.enso.interpreter.runtime.scope;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import org.enso.interpreter.Language;
import org.enso.interpreter.runtime.Context;
import org.enso.interpreter.runtime.Module;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@ExportLibrary(InteropLibrary.class)
public class TopScope implements TruffleObject {
  private Map<String, Module> modules;
  private final Scope scope = Scope.newBuilder("top_scope", this).build();

  public TopScope(Map<String, Module> modules) {
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

  @ExportMessage
  public boolean hasMembers() {
    return true;
  }

  @ExportMessage
  public ModuleNamesArray getMembers(boolean includeInternal) {
    return new ModuleNamesArray(getModules().keySet().toArray(new String[0]));
  }

  @ExportMessage
  public abstract static class ReadMember {
    @Specialization
    public static ModuleScope doRead(
        TopScope scope,
        String member,
        @CachedContext(Language.class) TruffleLanguage.ContextReference<Context> contextRef) {
      Module module = scope.modules.get(member);
      if (module.hasComputedScope()) {
        return module.getScope();
      } else {
        return module.requestParse(contextRef.get());
      }
    }
  }

  @ExportMessage
  public boolean hasMemberReadSideEffects(String member) {
    return !getModules().get(member).hasComputedScope();
  }

  @ExportMessage
  public boolean isMemberReadable(String member) {
    return getModules().containsKey(member);
  }

  @ExportLibrary(InteropLibrary.class)
  public static class ModuleNamesArray implements TruffleObject {
    private final String[] names;

    public ModuleNamesArray(String[] names) {
      this.names = names;
    }

    @ExportMessage
    public boolean hasArrayElements() {
      return true;
    }

    @ExportMessage
    public String readArrayElement(long index) {
      return names[(int) index];
    }

    @ExportMessage
    public long getArraySize() {
      return names.length;
    }

    @ExportMessage
    public boolean isArrayElementReadable(long index) {
      return index < getArraySize();
    }
  }
}

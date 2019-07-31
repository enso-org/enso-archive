package org.enso.interpreter.runtime.errors;

public class DuplicateArgumentNameException extends RuntimeException {
  public DuplicateArgumentNameException(String name) {
    super("A function cannot have two argumebnts called " + name);
  }
}

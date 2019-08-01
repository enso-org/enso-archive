package org.enso.interpreter.runtime.error;

public class DuplicateArgumentNameException extends RuntimeException {
  public DuplicateArgumentNameException(String name) {
    super("A function cannot have two argumebnts called " + name);
  }
}

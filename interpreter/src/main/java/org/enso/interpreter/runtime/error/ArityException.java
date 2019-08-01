package org.enso.interpreter.runtime.error;

public class ArityException extends RuntimeException {
  public ArityException(int expected, int actual) {
    super("Wrong number of arguments. Expected: " + expected + " but got: " + actual + ".");
  }
}

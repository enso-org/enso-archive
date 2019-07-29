package org.enso.interpreter.runtime.errors;

import java.util.Set;

public class ArgumentNameException extends RuntimeException {
  public ArgumentNameException(String name, Set<String> availableNames) {
    super(
        name
            + " is not a valid argument name for a function with arguments: "
            + availableNames.stream().reduce("", (l, r) -> l + ", " + r));
  }
}

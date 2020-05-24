package org.enso.interpreter.instrument;

import java.util.List;
import java.util.UUID;

public class ExecutionMode {

  /** Execution mode utilizing cache. */
  public static final class Default extends ExecutionMode {
  }

  /** Execution mode invalidating all encountered expressions, and forcing their execution. */
  public static final class InvalidateAll extends ExecutionMode {
  }

  /** Execution mode invalidating expressions from list, and forcing their execution. */
  public static final class InvalidateExpressions extends ExecutionMode {

    private final List<UUID> expressionIds;

    /**
     * Create {@link InvalidateExpressions} execution mode.
     * @param expressionIds a list of expressions to invalidate.
     */
    public InvalidateExpressions(List<UUID> expressionIds) {
      this.expressionIds = expressionIds;
    }

    /** @return a list of expressions to invalidate. */
    public List<UUID> getExpressionIds() {
      return expressionIds;
    }
  }
}

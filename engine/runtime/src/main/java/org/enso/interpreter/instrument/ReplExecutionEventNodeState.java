package org.enso.interpreter.instrument;

import org.enso.interpreter.runtime.callable.CallerInfo;

/**
     * State of the execution node.
     *
     * As the execution nodes are reused by Truffle, the nested nodes share
     * state. If execution of a nested node fails, to ensure consistent state of
     * the parent node, its state has to be restored.
     */
class ReplExecutionEventNodeState {
      private final Object lastReturn;
      private final Object lastState;
      private final CallerInfo lastScope;

      ReplExecutionEventNodeState(Object lastReturn, Object lastState, CallerInfo lastScope) {
        this.lastReturn = lastReturn;
        this.lastState = lastState;
        this.lastScope = lastScope;
      }

      Object getLastReturn() {
        return lastReturn;
      }

      Object getLastState() {
        return lastState;
      }

      CallerInfo getLastScope() {
        return lastScope;
      }
    }

package org.enso.interpreter.instrument;

import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.*;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.expression.debug.CaptureResultScope;
import org.enso.interpreter.node.expression.debug.EvalNode;
import org.enso.interpreter.runtime.callable.CallerInfo;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.scope.FramePointer;
import org.enso.interpreter.runtime.state.Stateful;

import java.util.HashMap;
import java.util.Map;

@TruffleInstrument.Registration(
    id = ReplDebuggerInstrument.INSTRUMENT_ID,
    services = ReplDebuggerInstrument.class)
public class ReplDebuggerInstrument extends TruffleInstrument {
  public static final String INSTRUMENT_ID = "enso-repl";

  private static class SessionManagerReference {
    private SessionManager sessionManager;

    private SessionManagerReference(SessionManager sessionManager) {
      this.sessionManager = sessionManager;
    }

    public SessionManager get() {
      return sessionManager;
    }

    private void set(SessionManager sessionManager) {
      this.sessionManager = sessionManager;
    }
  }

  public interface SessionManager {
    void startSession(MyExecutionEventListener executionNode);
  }

  private SessionManagerReference sessionManagerReference =
      new SessionManagerReference(MyExecutionEventListener::exit);

  @Override
  protected void onCreate(Env env) {
    SourceSectionFilter filter =
        SourceSectionFilter.newBuilder().tagIs(DebuggerTags.AlwaysHalt.class).build();
    Instrumenter instrumenter = env.getInstrumenter();
    env.registerService(this);
    instrumenter.attachExecutionEventFactory(
        filter, ctx -> new MyExecutionEventListener(ctx, sessionManagerReference));
  }

  public void setSessionManager(SessionManager sessionManager) {
    this.sessionManagerReference.set(sessionManager);
  }

  public static class MyExecutionEventListener extends ExecutionEventNode {
    private @Child EvalNode evalNode = EvalNode.buildWithResultScopeCapture();

    private Object lastReturn;
    private Object lastState;
    private CallerInfo lastScope;

    private EventContext eventContext;
    private SessionManagerReference sessionManagerReference;

    private MyExecutionEventListener(
        EventContext eventContext, SessionManagerReference sessionManagerReference) {
      this.eventContext = eventContext;
      this.sessionManagerReference = sessionManagerReference;
    }

    private Object getValue(MaterializedFrame frame, FramePointer ptr) {
      return getProperFrame(frame, ptr).getValue(ptr.getFrameSlot());
    }

    private MaterializedFrame getProperFrame(MaterializedFrame frame, FramePointer ptr) {
      MaterializedFrame currentFrame = frame;
      for (int i = 0; i < ptr.getParentLevel(); i++) {
        currentFrame = getParentFrame(currentFrame);
      }
      return currentFrame;
    }

    private MaterializedFrame getParentFrame(MaterializedFrame frame) {
      return Function.ArgumentsHelper.getLocalScope(frame.getArguments());
    }

    public Map<String, Object> listBindings() {
      Map<String, FramePointer> flatScope = lastScope.getLocalScope().flatten();
      Map<String, Object> result = new HashMap<>();
      for (Map.Entry<String, FramePointer> entry : flatScope.entrySet()) {
        result.put(entry.getKey(), getValue(lastScope.getFrame(), entry.getValue()));
      }
      return result;
    }

    public Object evaluate(String expression) {
      try {
        Stateful result = evalNode.execute(lastScope, lastState, expression);
        lastState = result.getState();
        CaptureResultScope.WithCallerInfo payload =
            (CaptureResultScope.WithCallerInfo) result.getValue();
        lastScope = payload.getCallerInfo();
        lastReturn = payload.getResult();
        return lastReturn;
      } catch (Exception e) {
        return e;
      }
    }

    public void exit() {
      throw eventContext.createUnwind(lastReturn);
    }

    @Override
    protected void onEnter(VirtualFrame frame) {
      lastScope = Function.ArgumentsHelper.getCallerInfo(frame.getArguments());
      lastReturn = lookupContextReference(Language.class).get().getUnit().newInstance();
      lastState = lastScope.getFrame().getValue(lastScope.getLocalScope().getStateFrameSlot());
      sessionManagerReference.get().startSession(this);
    }

    @Override
    protected Object onUnwind(VirtualFrame frame, Object info) {
      return new Stateful(lastState, lastReturn);
    }
  }
}

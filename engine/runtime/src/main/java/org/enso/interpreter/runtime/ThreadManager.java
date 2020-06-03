package org.enso.interpreter.runtime;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import org.enso.interpreter.runtime.control.ThreadInterruptedException;

import java.util.concurrent.Phaser;
import java.util.concurrent.locks.ReentrantLock;

public class ThreadManager {
  private final Phaser safepointPhaser =
      new Phaser() {
        @Override
        protected boolean onAdvance(int phase, int registeredParties) {
          // Ensure the phaser never terminates, even if the number of parties drops to zero at some
          // point.
          return false;
        }
      };
  private final ReentrantLock lock = new ReentrantLock();

  private @CompilerDirectives.CompilationFinal Assumption safepointAssumption =
      Truffle.getRuntime().createAssumption("Safepoint");

  public void enter() {
    safepointPhaser.register();
  }

  public void leave() {
    safepointPhaser.arriveAndDeregister();
  }

  public void poll() {
    try {
      safepointAssumption.check();
    } catch (InvalidAssumptionException e) {
      safepointPhaser.arriveAndAwaitAdvance();
      if (Thread.interrupted()) {
        throw new ThreadInterruptedException();
      }
    }
  }

  public void checkInterrupts() {
    lock.lock();
    try {
      enter();
      try {
        safepointAssumption.invalidate();
        safepointPhaser.arriveAndAwaitAdvance();
        safepointAssumption = Truffle.getRuntime().createAssumption("Safepoint");
      } finally {
        leave();
      }
    } finally {
      lock.unlock();
    }
  }
}

package fr.sukikui.mineverify.remote;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

/**
 * Non-blocking gate for scheduled remote operations.
 */
final class RemoteOperationGate {

  private final ReentrantLock lock = new ReentrantLock();

  boolean tryBegin(BooleanSupplier closed) {
    if (closed.getAsBoolean() || !lock.tryLock()) {
      return false;
    }
    if (closed.getAsBoolean()) {
      lock.unlock();
      return false;
    }
    return true;
  }

  void end() {
    lock.unlock();
  }

  void waitUntilIdle() {
    lock.lock();
    lock.unlock();
  }
}

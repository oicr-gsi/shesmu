package ca.on.oicr.gsi.shesmu.util;

import java.util.concurrent.Semaphore;

/** A lock that can be used via the try-with-resources syntax */
public final class AutoLock {
  private final Semaphore lock = new Semaphore(1);

  public AutoCloseable acquire() {
    lock.acquireUninterruptibly();
    return lock::release;
  }
}

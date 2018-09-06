package ca.on.oicr.gsi.shesmu;

import java.util.concurrent.Semaphore;

public final class AutoLock {
	private final Semaphore lock = new Semaphore(1);

	public AutoCloseable acquire() {
		lock.acquireUninterruptibly();
		return lock::release;
	}
}

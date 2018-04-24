package ca.on.oicr.gsi.shesmu.actions.util;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

import ca.on.oicr.gsi.shesmu.Action;
import ca.on.oicr.gsi.shesmu.ActionState;

/**
 * Create a maximum-in-flight action lock
 */
public final class MaxInFlight {
	private int inflight;
	private final int max;

	public MaxInFlight(int max) {
		this.max = max;
	}

	/**
	 * Create a new action
	 * 
	 * This should be called exactly once per action. When the
	 * {@link Action#perform()} method is called, it should return the value of
	 * {@link Supplier#get()}. If there are already too many in-flight actions,
	 * {@link ActionState#WAITING} will be returned. Once there is enough capacity,
	 * an action will receive in an in-flight lock which it will hold until the
	 * action returns either {@link ActionState#SUCCEEDED} or
	 * {@link ActionState#FAILED}.
	 * 
	 * @param protectedCode
	 *            the block of code to run while holding a lock
	 */
	public Supplier<ActionState> create(Callable<ActionState> protectedCode) {
		return new Supplier<ActionState>() {
			boolean hasLock;

			@Override
			public ActionState get() {
				if (!hasLock) {
					if (inflight >= max) {
						return ActionState.WAITING;
					}
					inflight++;
				}
				try {
					ActionState result = protectedCode.call();
					if (result == ActionState.SUCCEEDED || result == ActionState.FAILED) {
						hasLock = false;
						inflight--;
					}
					return result;
				} catch (Exception e) {
					e.printStackTrace();
					hasLock = false;
					inflight--;
					return ActionState.FAILED;
				}
			}
		};
	}

}

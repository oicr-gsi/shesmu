package ca.on.oicr.gsi.shesmu;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * User-supplied code base
 *
 * This is the bridge interface between the Java code in the server and the
 * dynamically compiled user code.
 */
public abstract class ActionGenerator {

	/**
	 * An action generator which ignores the input.
	 */
	public static final ActionGenerator NULL = new ActionGenerator() {

		@Override
		public void populateLookups(NameLoader<Lookup> loader) {
			// Do nothing.
		}

		@Override
		public void run(Consumer<Action> consumer, Supplier<Stream<Variables>> input) {
			// Do nothing.
		}

	};

	/**
	 * Gather the instances of any lookups required by this action generator.
	 *
	 * The lookup instances provided must match those defined when compiled.
	 *
	 * @param loader
	 *            source of lookup instances
	 */
	public abstract void populateLookups(NameLoader<Lookup> loader);

	/**
	 * Call the action generator to process the input.
	 *
	 * @param consumer
	 *            an output handler to collect and process actions as they are
	 *            created; this may be called multiple times
	 * @param input
	 *            a generator of a stream of input from the outside world; this
	 *            maybe called multiple times; the contents of the stream should
	 *            remain the value-equivalent for each call
	 */
	public abstract void run(Consumer<Action> consumer, Supplier<Stream<Variables>> input);
}

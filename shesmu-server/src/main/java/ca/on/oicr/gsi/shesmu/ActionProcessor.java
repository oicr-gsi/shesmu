package ca.on.oicr.gsi.shesmu;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.prometheus.client.Gauge;

/**
 * Background process for launching actions and reporting the results
 *
 * This class collects actions and tries to {@link Action#perform()} until
 * successful.
 */
public final class ActionProcessor {
	/**
	 * A filter all the actions based on some criteria
	 */
	public static abstract class Filter {
		protected abstract boolean check(Information info);

		/**
		 * Produce a filter that selects the opposite output of this filter.
		 */
		public Filter negate() {
			final Filter owner = this;
			return new Filter() {

				@Override
				protected boolean check(Information info) {
					return !owner.check(info);
				}

			};

		}
	}

	private static class Information {
		Instant lastChecked = Instant.EPOCH;
		ActionState lastState = ActionState.UNKNOWN;
		boolean thrown;
	}

	private static final Gauge actionThrows = Gauge.build("shesmu_action_perform_throw",
			"The number of actions that threw an exception in their last attempt.").register();

	private static final Gauge lastRun = Gauge
			.build("shesmu_action_perform_last_time", "The last time the actions were processed.").register();

	private static final Gauge stateCount = Gauge
			.build("shesmu_action_state_count", "The number of actions in a particular state.").labelNames("state")
			.register();

	/**
	 * Checks that a filter has one of the specified actions states
	 *
	 * @param states
	 *            the permitted states
	 */
	public static Filter isState(ActionState... states) {
		final EnumSet<ActionState> set = EnumSet.noneOf(ActionState.class);
		set.addAll(Arrays.asList(states));
		return new Filter() {

			@Override
			protected boolean check(Information info) {
				return set.contains(info.lastState);
			}

		};
	}

	/**
	 * Check that a filter has been updated in after the timestamp specified.
	 *
	 * @param instant
	 *            the exclusive cut-off timestamp
	 */
	public static Filter updatedAfter(Instant instant) {
		return new Filter() {

			@Override
			protected boolean check(Information info) {
				return info.lastChecked.isAfter(instant);
			}

		};
	}

	private final Map<Action, Information> actions = new ConcurrentHashMap<>();

	private final Thread processing = new Thread(this::update, "action-processor");

	private volatile boolean running = true;

	public ActionProcessor() {
		super();
		Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
	}

	/**
	 * Add an action to the execution pool
	 *
	 * If this action is a duplicate of an existing action, the existing state is
	 * kept.
	 */
	public synchronized boolean accept(Action action) {
		if (!actions.containsKey(action)) {
			actions.put(action, new Information());
			stateCount.labels(ActionState.UNKNOWN.name()).inc();
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Begin the action processor's thread
	 */
	public void start() {
		processing.start();
	}

	private Stream<Entry<Action, Information>> startStream(Filter... filters) {
		return actions.entrySet().stream()
				.filter(entry -> Arrays.stream(filters).allMatch(filter -> filter.check(entry.getValue())));
	}

	/**
	 * Stop the action processors thread
	 *
	 * There maybe some delay as the currently processed action must finish
	 */
	public void stop() {
		running = false;
		processing.interrupt();
	}

	/**
	 * Stream all the actions in the processor matching a filter set
	 *
	 * @param filters
	 *            the filters to match
	 */
	public Stream<Action> stream(Filter... filters) {
		return startStream(filters).map(Entry::getKey);
	}

	/**
	 * Stream all the actions, converted to JSON objects, in the processor matching
	 * a filter set
	 *
	 * @param filters
	 *            the filters to match
	 */
	public Stream<ObjectNode> stream(ObjectMapper mapper, Filter... filters) {
		return startStream(filters).map(entry -> {
			final ObjectNode node = entry.getKey().toJson(mapper);
			node.put("state", entry.getValue().lastState.name());
			node.put("lastChecked", entry.getValue().lastChecked.getEpochSecond());
			return node;
		});
	}

	private void update() {
		while (running) {
			final Instant now = Instant.now();
			actions.entrySet().stream()//
					.sorted(Comparator.comparingInt(e -> e.getKey().priority()))//
					.filter(entry -> entry.getValue().lastState != ActionState.SUCCEEDED
							&& Duration.between(entry.getValue().lastChecked, now).toMinutes() >= Math.max(5,
									entry.getKey().retryMinutes()))//
					.forEach(entry -> {
						entry.getValue().lastChecked = Instant.now();
						final ActionState oldState = entry.getValue().lastState;
						final boolean oldThrown = entry.getValue().thrown;
						try {
							entry.getValue().lastState = entry.getKey().perform();
						} catch (final Exception e) {
							entry.getValue().lastState = ActionState.UNKNOWN;
							entry.getValue().thrown = true;
							e.printStackTrace();
						}
						if (oldState != entry.getValue().lastState) {
							stateCount.labels(oldState.name()).dec();
							stateCount.labels(entry.getValue().lastState.name()).inc();
						}
						actionThrows.inc((entry.getValue().thrown ? 0 : 1) - (oldThrown ? 0 : 1));
					});
			lastRun.setToCurrentTime();
			try {
				Thread.sleep(60_000);
			} catch (final InterruptedException e) {
			}
		}
	}
}

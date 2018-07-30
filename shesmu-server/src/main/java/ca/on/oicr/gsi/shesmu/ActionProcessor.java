package ca.on.oicr.gsi.shesmu;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.prometheus.client.Gauge;

/**
 * Background process for launching actions and reporting the results
 *
 * This class collects actions and tries to {@link Action#perform()} until
 * successful.
 */
public final class ActionProcessor {
	private interface Bin<T> extends Comparator<T> {
		long bucket(T min, long width, T value);

		T extract(Entry<Action, Information> input);

		String name();

		JsonNode name(T min, long offset);

		long span(T min, T max);
	}

	/**
	 * A filter all the actions based on some criteria
	 */
	public static abstract class Filter {
		protected abstract boolean check(Action action, Information info);

		/**
		 * Produce a filter that selects the opposite output of this filter.
		 */
		public Filter negate() {
			final Filter owner = this;
			return new Filter() {

				@Override
				protected boolean check(Action action, Information info) {
					return !owner.check(action, info);
				}

			};

		}
	}

	private static class Information {
		Instant lastAdded = Instant.now();
		Instant lastChecked = Instant.EPOCH;
		ActionState lastState = ActionState.UNKNOWN;
		boolean thrown;
	}

	private static abstract class InstantBin implements Bin<Instant> {

		@Override
		public final long bucket(Instant min, long width, Instant value) {
			return (value.getEpochSecond() - min.getEpochSecond()) / width;
		}

		@Override
		public final int compare(Instant o1, Instant o2) {
			return o1.compareTo(o2);
		}

		@Override
		public JsonNode name(Instant min, long offset) {
			return JSON_FACTORY.numberNode(min.getEpochSecond() + offset);
		}

		@Override
		public final long span(Instant min, Instant max) {
			return max.getEpochSecond() - min.getEpochSecond();
		}

	}

	private static abstract class InstantFilter extends Filter {
		private final Optional<Instant> end;
		private final Optional<Instant> start;

		private InstantFilter(Optional<Instant> start, Optional<Instant> end) {
			super();
			this.start = start;
			this.end = end;
		}

		@Override
		protected final boolean check(Action action, Information info) {
			return start.map(s -> s.compareTo(get(info)) < 1).orElse(true)
					&& end.map(e -> e.isAfter(get(info))).orElse(true);
		}

		protected abstract Instant get(Information info);

	}

	private interface Property<T> {
		T extract(Entry<Action, Information> input);

		String name();

		String name(T input);
	}

	private static final Property<ActionState> ACTION_STATE = new Property<ActionState>() {

		@Override
		public ActionState extract(Entry<Action, Information> input) {
			return input.getValue().lastState;
		}

		@Override
		public String name() {
			return "status";
		}

		@Override
		public String name(ActionState input) {
			return input.name();
		}

	};

	private static final Gauge actionThrows = Gauge.build("shesmu_action_perform_throw",
			"The number of actions that threw an exception in their last attempt.").register();

	private static final Bin<Instant> ADDED = new InstantBin() {

		@Override
		public Instant extract(Entry<Action, Information> input) {
			return input.getValue().lastAdded;
		}

		@Override
		public String name() {
			return "added";
		}

	};

	private static final Bin<Instant> CHECKED = new InstantBin() {

		@Override
		public Instant extract(Entry<Action, Information> input) {
			return input.getValue().lastChecked;
		}

		@Override
		public String name() {
			return "checked";
		}

	};

	private static final JsonNodeFactory JSON_FACTORY = JsonNodeFactory.withExactBigDecimals(false);

	private static final Gauge lastRun = Gauge
			.build("shesmu_action_perform_last_time", "The last time the actions were processed.").register();

	private static final Gauge stateCount = Gauge
			.build("shesmu_action_state_count", "The number of actions in a particular state.").labelNames("state")
			.register();

	private static final Property<String> TYPE = new Property<String>() {

		@Override
		public String extract(Entry<Action, Information> input) {
			return input.getKey().type();
		}

		@Override
		public String name() {
			return "type";
		}

		@Override
		public String name(String input) {
			return input;
		}

	};

	/**
	 * Check that an action was last added in the time range provided
	 *
	 * @param start
	 *            the exclusive cut-off timestamp
	 * @param end
	 *            the exclusive cut-off timestamp
	 */
	public static Filter added(Optional<Instant> start, Optional<Instant> end) {
		return new InstantFilter(start, end) {

			@Override
			protected Instant get(Information info) {
				return info.lastAdded;
			}

		};
	}

	/**
	 * Check that an action was last checked in the time range provided
	 *
	 * @param start
	 *            the exclusive cut-off timestamp
	 * @param end
	 *            the exclusive cut-off timestamp
	 */
	public static Filter checked(Optional<Instant> start, Optional<Instant> end) {
		return new InstantFilter(start, end) {

			@Override
			protected Instant get(Information info) {
				return info.lastChecked;
			}

		};
	}

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
			protected boolean check(Action action, Information info) {
				return set.contains(info.lastState);
			}

		};
	}

	/**
	 * Check that an action has on eof the types specified
	 *
	 * @param instant
	 *            the exclusive cut-off timestamp
	 */
	public static Filter type(String... types) {
		final Set<String> set = Stream.of(types).collect(Collectors.toSet());
		return new Filter() {

			@Override
			protected boolean check(Action action, Information info) {
				return set.contains(action.type());
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
			actions.get(action).lastAdded = Instant.now();
			return true;
		}
	}

	private <T, U> void crosstab(ArrayNode output, List<Entry<Action, Information>> input, Property<T> row,
			Property<U> column) {
		Set<T> rows = input.stream().map(row::extract).collect(Collectors.toSet());
		Set<U> columns = input.stream().map(column::extract).collect(Collectors.toSet());
		if (rows.size() < 2 && columns.size() < 2) {
			return;
		}
		ObjectNode node = output.addObject();
		node.put("type", "crosstab");
		node.put("column", column.name());
		node.put("row", row.name());
		columns.stream().map(column::name).sorted().forEach(node.putArray("columns")::add);
		Map<T, List<Entry<Action, Information>>> map = input.stream().collect(Collectors.groupingBy(row::extract));
		ObjectNode data = node.putObject("data");
		for (Entry<T, List<Entry<Action, Information>>> entry : map.entrySet()) {
			ObjectNode inner = data.putObject(row.name(entry.getKey()));
			for (Entry<U, Long> i : entry.getValue().stream()
					.collect(Collectors.groupingBy(column::extract, Collectors.counting())).entrySet()) {
				inner.put(column.name(i.getKey()), i.getValue());
			}
		}
	}

	private <T> void histogram(ArrayNode output, int count, List<Entry<Action, Information>> input, Bin<T> bin) {
		Optional<T> min = input.stream().map(bin::extract).min(bin);
		Optional<T> max = input.stream().map(bin::extract).max(bin);
		if (!min.isPresent() || !max.isPresent() || min.get().equals(max.get())) {
			return;
		}
		long width = bin.span(min.get(), max.get()) / count;
		if (width == 0) {
			return;
		}
		int[] buckets = new int[count];
		for (Entry<Action, Information> value : input) {
			int index = (int) bin.bucket(min.get(), width, bin.extract(value));
			buckets[index >= buckets.length ? buckets.length - 1 : index]++;
		}
		ObjectNode node = output.addObject();
		node.put("type", "histogram");
		node.put("bin", bin.name());
		ArrayNode boundaries = node.putArray("boundaries");
		ArrayNode counts = node.putArray("counts");
		for (int i = 0; i < buckets.length; i++) {
			boundaries.add(bin.name(min.get(), i * width));
			counts.add(buckets[i]);
		}
		boundaries.add(bin.name(max.get(), 0));
	}

	/**
	 * Begin the action processor's thread
	 */
	public void start() {
		processing.start();
	};

	private Stream<Entry<Action, Information>> startStream(Filter... filters) {
		return actions.entrySet().stream().filter(
				entry -> Arrays.stream(filters).allMatch(filter -> filter.check(entry.getKey(), entry.getValue())));
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
			node.put("lastAdded", entry.getValue().lastAdded.getEpochSecond());
			node.put("lastChecked", entry.getValue().lastChecked.getEpochSecond());
			node.put("type", entry.getKey().type());
			return node;
		});
	}

	public ArrayNode summary(ObjectMapper mapper, Filter... filters) {
		List<Entry<Action, Information>> actions = startStream(filters).collect(Collectors.toList());
		ArrayNode array = mapper.createArrayNode();
		final ObjectNode message = array.addObject();
		message.put("type", "text");
		message.put("value",
				actions.isEmpty() ? "No actions match." : String.format("%d actions found.", actions.size()));
		crosstab(array, actions, TYPE, ACTION_STATE);
		histogram(array, 10, actions, ADDED);
		histogram(array, 10, actions, CHECKED);
		return array;
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

package ca.on.oicr.gsi.shesmu;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

/**
 * Periodically run an {@link ActionGenerator} on all available variables from
 * {@link VariablesSource}
 */
public class MasterRunner {
	private static final Counter errors = Counter
			.build("shesmu_run_errors", "The number of times processing the input has thrown.").register();
	private static final Gauge lastRun = Gauge.build("shesmu_run_last_run", "The last time the input was run.")
			.register();
	private static final Gauge duplicates = Gauge.build("shesmu_action_duplicate_count", "The number of repeated actions from the last run.")
			.register();
	private static final LatencyHistogram runTime = new LatencyHistogram("shesmu_run_time",
			"The time the script takes to run on all the input.");
	private final Supplier<ActionGenerator> actionGeneratorSource;
	private final ActionProcessor actionSink;

	private final Supplier<Stream<Lookup>> lookupSource;

	private volatile boolean running;
	private final Thread thread = new Thread(this::run, "master_runner");

	public MasterRunner(Supplier<ActionGenerator> actionGeneratorSource, Supplier<Stream<Lookup>> lookupSource,
			ActionProcessor actionSink) {
		this.actionGeneratorSource = actionGeneratorSource;
		this.lookupSource = lookupSource;
		this.actionSink = actionSink;
	}

	private void run() {
		while (running) {
			lastRun.setToCurrentTime();
			try (AutoCloseable timer = runTime.start()) {
				final AtomicInteger currentDuplicates = new AtomicInteger();
				final ActionGenerator generator = actionGeneratorSource.get();
				generator.populateLookups(new NameLoader<>(lookupSource.get(), Lookup::name));
				generator.run(action -> {
					if (actionSink.accept(action)) {
						currentDuplicates.incrementAndGet();
					}
				}, VariablesSource::all);
				duplicates.set(currentDuplicates.get());
			} catch (final Exception e) {
				e.printStackTrace();
				errors.inc();
			}
			try {
				Thread.sleep(5 * 60_000);
			} catch (final InterruptedException e) {
			}
		}

	}

	public void start() {
		running = true;
		thread.start();
	}

	public void stop() {
		running = false;
		thread.interrupt();
	}

}

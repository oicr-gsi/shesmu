package ca.on.oicr.gsi.shesmu;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.prometheus.client.Counter;

public class MasterRunner {
	private static final Counter errors = Counter
			.build("shesmu_run_errors", "The number of times processing the input has thrown.").register();
	private static final LatencyHistogram runTime = new LatencyHistogram("shesmu_run_time",
			"The time the script takes to run on all the input.");
	private final Supplier<ActionGenerator> actionGeneratorSource;
	private final Consumer<Action> actionSink;

	private final Supplier<Stream<Lookup>> lookupSource;

	private volatile boolean running;
	private final Thread thread = new Thread(this::run);

	public MasterRunner(Supplier<ActionGenerator> actionGeneratorSource, Supplier<Stream<Lookup>> lookupSource,
			Consumer<Action> actionSink) {
		this.actionGeneratorSource = actionGeneratorSource;
		this.lookupSource = lookupSource;
		this.actionSink = actionSink;
	}

	private void run() {
		while (running) {
			try (AutoCloseable timer = runTime.start()) {
				final ActionGenerator generator = actionGeneratorSource.get();
				generator.populateLookups(new NameLoader<>(lookupSource.get(), Lookup::name));
				generator.run(actionSink, VariablesSource::all);
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
		thread.start();
	}

	public void stop() {
		running = false;
		thread.interrupt();
	}

}

package ca.on.oicr.gsi.shesmu.util.server;

import java.util.concurrent.atomic.AtomicInteger;

import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.Action;
import ca.on.oicr.gsi.shesmu.ActionConsumer;
import ca.on.oicr.gsi.shesmu.ActionGenerator;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.InputRepository;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

/**
 * Periodically run an {@link ActionGenerator} on all available variables from
 * {@link InputRepository}
 */
public class MasterRunner {
	private static final Gauge actionDuplicates = Gauge
			.build("shesmu_action_duplicate_count", "The number of repeated actions from the last run.").register();
	private static final Gauge alertDuplicates = Gauge
			.build("shesmu_alert_duplicate_count", "The number of repeated alerts from the last run.").register();
	private static final Counter errors = Counter
			.build("shesmu_run_errors", "The number of times processing the input has thrown.").register();
	private static final Gauge lastRun = Gauge.build("shesmu_run_last_run", "The last time the input was run.")
			.register();
	private static final LatencyHistogram runTime = new LatencyHistogram("shesmu_run_time",
			"The time the script takes to run on all the input.");
	private final ActionGenerator generator;
	private final ActionConsumer consumer;

	private volatile boolean running;
	private final Thread thread = new Thread(this::run, "master_runner");

	public MasterRunner(ActionGenerator generator, ActionConsumer consumer) {
		this.generator = generator;
		this.consumer = consumer;
	}

	private void run() {
		while (running) {
			lastRun.setToCurrentTime();
			try (AutoCloseable timer = runTime.start()) {
				final AtomicInteger currentActionDuplicates = new AtomicInteger();
				final AtomicInteger currentAlertDuplicates = new AtomicInteger();
				generator.run(new ActionConsumer() {

					@Override
					public boolean accept(Action action, String filename, int line, int column, long time) {

						final boolean isDuplicated = consumer.accept(action, filename, line, column, time);
						if (isDuplicated) {
							currentActionDuplicates.incrementAndGet();
						}
						return isDuplicated;
					}

					@Override
					public boolean accept(String[] labels, String[] annotation, long ttl) throws Exception {
						final boolean isDuplicated = consumer.accept(labels, annotation, ttl);
						if (isDuplicated) {
							currentAlertDuplicates.incrementAndGet();
						}
						return isDuplicated;
					}
				}, InputFormatDefinition::all);
				actionDuplicates.set(currentActionDuplicates.get());
				alertDuplicates.set(currentAlertDuplicates.get());
			} catch (final Exception e) {
				e.printStackTrace();
				errors.inc();
			}
			try {
				Thread.sleep(5 * 60_000);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
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

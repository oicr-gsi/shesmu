package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.dumper.Dumper;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.ActionGenerator;
import ca.on.oicr.gsi.shesmu.runtime.CompiledGenerator;
import ca.on.oicr.gsi.shesmu.runtime.InputProvider;
import ca.on.oicr.gsi.shesmu.runtime.OliveServices;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/** Periodically run an {@link ActionGenerator} on all available variables */
public class MasterRunner {
  private static final Gauge actionDuplicates =
      Gauge.build(
              "shesmu_action_duplicate_count", "The number of repeated actions from the last run.")
          .register();
  private static final Gauge alertDuplicates =
      Gauge.build(
              "shesmu_alert_duplicate_count", "The number of repeated alerts from the last run.")
          .register();
  private static final Counter errors =
      Counter.build("shesmu_run_errors", "The number of times processing the input has thrown.")
          .register();
  private static final Gauge lastRun =
      Gauge.build("shesmu_run_last_run", "The last time the input was run.").register();
  private static final LatencyHistogram runTime =
      new LatencyHistogram("shesmu_run_time", "The time the script takes to run on all the input.");
  private final CompiledGenerator generator;
  private final InputProvider inputProvider;
  private ScheduledFuture<?> scheduled;
  private final OliveServices services;

  public MasterRunner(
      CompiledGenerator generator, OliveServices services, InputProvider inputProvider) {
    this.generator = generator;
    this.services = services;
    this.inputProvider = inputProvider;
  }

  private void run() {
    lastRun.setToCurrentTime();
    try (AutoCloseable timer = runTime.start()) {
      final AtomicInteger currentActionDuplicates = new AtomicInteger();
      final AtomicInteger currentAlertDuplicates = new AtomicInteger();
      generator.run(
          new OliveServices() {

            @Override
            public boolean accept(
                Action action, String filename, int line, int column, long time, String[] tags) {

              final boolean isDuplicated =
                  services.accept(action, filename, line, column, time, tags);
              if (isDuplicated) {
                currentActionDuplicates.incrementAndGet();
              }
              return isDuplicated;
            }

            @Override
            public boolean accept(String[] labels, String[] annotation, long ttl) throws Exception {
              final boolean isDuplicated = services.accept(labels, annotation, ttl);
              if (isDuplicated) {
                currentAlertDuplicates.incrementAndGet();
              }
              return isDuplicated;
            }

            @Override
            public Dumper findDumper(String name, Imyhat... types) {
              return services.findDumper(name, types);
            }

            @Override
            public boolean isOverloaded(String... throttledServices) {
              return services.isOverloaded(throttledServices);
            }

            @Override
            public <T> Stream<T> measureFlow(
                Stream<T> input,
                String filename,
                int line,
                int column,
                int oliveLine,
                int oliveColumn) {
              return services.measureFlow(input, filename, line, column, oliveLine, oliveColumn);
            }

            @Override
            public void oliveRuntime(String filename, int line, int column, long timeInNs) {
              services.oliveRuntime(filename, line, column, timeInNs);
            }
          },
          inputProvider);
      actionDuplicates.set(currentActionDuplicates.get());
      alertDuplicates.set(currentAlertDuplicates.get());
    } catch (final Exception e) {
      e.printStackTrace();
      errors.inc();
    }
  }

  public void start(ScheduledExecutorService executor) {
    if (scheduled != null) {
      scheduled.cancel(true);
    }
    scheduled = executor.scheduleWithFixedDelay(this::run, 5, 5, TimeUnit.MINUTES);
  }

  public void stop() {
    scheduled.cancel(true);
  }
}

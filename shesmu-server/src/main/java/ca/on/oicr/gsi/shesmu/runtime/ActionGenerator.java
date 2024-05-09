package ca.on.oicr.gsi.shesmu.runtime;

import ca.on.oicr.gsi.shesmu.plugin.RequiredServices;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Superclass for user-defined
 *
 * <p>This is the bridge interface between the Java code in the server and the compiled Shesmu
 * script. The compiler will convert a Shesmu script into a subclass and can expect to call it using
 * only what is defined in this class.
 */
public abstract class ActionGenerator implements RequiredServices {

  /** An action generator which ignores the input. */
  public static final ActionGenerator NULL =
      new ActionGenerator() {

        @Override
        public Stream<String> inputs() {
          return Stream.empty();
        }

        @Override
        public Lookup lookup() {
          // Because this is a dummy instance, we're going to get the public lookup instance since
          // there will be no exports that require using it.
          return MethodHandles.publicLookup();
        }

        @Override
        public void run(OliveServices consumer, InputProvider input) {
          // Do nothing.
        }

        @Override
        public int timeout() {
          return 1;
        }
      };

  private final List<Collector> collectors = new ArrayList<>();

  /**
   * Create a new Prometheus monitoring gauge for this action.
   *
   * @param metricName The metric name, as specified in the “Monitor” clause
   * @param help The help text that is associated with the metric
   * @param labelNames The label names for the metric. Maybe empty, but should not be null.
   * @return the newly generated Prometheus gauge
   */
  @RuntimeInterop
  protected final Gauge buildGauge(String metricName, String help, String[] labelNames) {
    final var g = Gauge.build("shesmu_user_" + metricName, help).labelNames(labelNames).create();
    collectors.add(g);
    return g;
  }

  /**
   * All of the input formats that are used by this generator.
   *
   * @return the names of the input formats
   */
  public abstract Stream<String> inputs();

  /**
   * Gets a private lookup for this class
   *
   * <p>This is kind of dangerous and weird, but since this is generated code, we need a way into
   * the unnamed module it was compiled in, so we rummage.
   *
   * @return the private lookup instance
   */
  public abstract Lookup lookup();

  /**
   * Add all Prometheus monitoring for this program.
   *
   * <p>Only one Shesmu script may be active at any time, but multiple may be loaded in memory as
   * part of compilation. Since duplicate Prometheus metrics are not permitted, Shesmu will {@link
   * #unregister()} the old {@link ActionGenerator} to remove its monitoring output, then connect
   * the new one using {@link #register()}.
   */
  public final void register() {
    register(CollectorRegistry.defaultRegistry);
  }

  /**
   * Add all Prometheus monitoring for this program.
   *
   * @param registry the Prometheus registry to attach the metrics to
   * @see #register()
   */
  public final void register(CollectorRegistry registry) {
    collectors.forEach(registry::register);
  }

  /**
   * Call the action generator to process the input.
   *
   * @param consumer an output handler to collect and process actions as they are created; this may
   *     be called multiple times with duplicate input
   * @param input a generator of a stream of input from the outside world; this maybe called
   *     multiple times; the contents of the stream should remain the value-equivalent for each
   *     call. The order of the input and reference-equivalence are not required. That is, multiple
   *     calls do not have return the same objects, but should return ones with the same content.
   *     Duplicate items are permitted, but might be a problem for the Shesmu script.
   */
  @RuntimeInterop
  public abstract void run(OliveServices consumer, InputProvider input);

  /**
   * The maximum runtime of this script, in seconds.
   *
   * @return the timeout
   */
  public abstract int timeout();

  /**
   * Remove all Prometheus monitoring for this program.
   *
   * @see #register()
   */
  public final void unregister() {
    unregister(CollectorRegistry.defaultRegistry);
  }

  /**
   * Remove all Prometheus monitoring for this program.
   *
   * @param registry the Prometheus registry to remove the metrics from
   * @see #register()
   */
  public final void unregister(CollectorRegistry registry) {
    collectors.forEach(registry::unregister);
  }
}

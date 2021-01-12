package ca.on.oicr.gsi.shesmu.plugin;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.dumper.Dumper;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilterBuilder;
import ca.on.oicr.gsi.shesmu.plugin.filter.ExportSearch;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.status.SectionRenderer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;

/**
 * Every configuration file read by Shesmu has a matching {@link PluginFile} to export services
 * defined by that configuration to the server and olives
 */
public abstract class PluginFile implements RequiredServices {
  private final Path fileName;
  private final String instanceName;

  public PluginFile(Path fileName, String instanceName) {
    super();
    this.fileName = fileName;
    this.instanceName = instanceName;
  }

  /** Generate a configuration block to be shown on the status page */
  public abstract void configuration(SectionRenderer renderer) throws XMLStreamException;

  /** Generate a list of export buttons to provide to the UI */
  public <T> Stream<T> exportSearches(ExportSearch<T> builder) {
    return Stream.empty();
  }

  /** The configuration file that was read */
  public final Path fileName() {
    return fileName;
  }

  /**
   * Find a dumper
   *
   * @param name the dumper to find
   * @param columns
   * @return the dumper if found, or an empty stream if none is available
   */
  public Stream<Dumper> findDumper(String name, String[] columns, Imyhat... types) {
    return Stream.empty();
  }

  /**
   * Check throttling should be applied
   *
   * @param services a list of service names to check; this set must not be modified; these names
   *     are arbitrary and must be coordinated by {@link Action} and the throttler
   * @return the names of any services that are overloaded
   */
  public Stream<String> isOverloaded(Set<String> services) {
    return Stream.empty();
  }

  /**
   * The instance name for this plugin
   *
   * <p>This is the file name less the extension
   */
  public String name() {
    return instanceName;
  }

  /** Receive JSON data describing Prometheus alerts that are firing for the olives */
  public void pushAlerts(String alertJson) {}

  /** Create a list of searches */
  public <F> Stream<Pair<String, F>> searches(
      ActionFilterBuilder<F, ActionState, String, Instant, Long> builder) {
    return Stream.empty();
  }

  /**
   * Create a URL for a source file
   *
   * @return the URL to the source file or null if not possible
   */
  public Stream<String> sourceUrl(String localFilePath, int line, int column, String hash) {
    return Stream.empty();
  }

  /**
   * Called when a configuration file is first read.
   *
   * <p>{@link #update()} will be called immediately after
   */
  public void start() {}

  /** Called when a configuration file is deleted from disk. */
  public void stop() {}

  /**
   * Processed a changed file.
   *
   * @return if empty, the file is assumed to be updated correctly; if a number is returned, this
   *     method will be called again in the specified number of minutes. This is useful if
   *     configuration talks to an external service which is failed.
   */
  public abstract Optional<Integer> update();
  /**
   * Write out a logging message to this service
   *
   * <p>Do not use this message for logging! Shesmu will invoke this message when it wants to send
   * logging information to this service and this service should write those logs out to an external
   * service. To write logs, use {@link Definer#log(String, Map)}
   *
   * @param message the log message to write
   * @param attributes the labels associated with this message
   */
  public void writeLog(String message, Map<String, String> attributes) {
    // Do nothing
  }
}

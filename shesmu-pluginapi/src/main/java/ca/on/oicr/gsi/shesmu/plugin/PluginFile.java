package ca.on.oicr.gsi.shesmu.plugin;

import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.dumper.Dumper;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.status.SectionRenderer;
import java.nio.file.Path;
import java.time.Instant;
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

  /** The configuration file that was read */
  public final Path fileName() {
    return fileName;
  }
  /** Receive JSON data describing Prometheus alerts that are firing for the olives */
  public void pushAlerts(String alertJson) {}

  /**
   * Check throttling should be applied
   *
   * @param services a list of service names to check; this set must not be modified; these names
   *     are arbitrary and must be coordinated by {@link Action} and the throttler
   * @return true if the action should be blocked; false if it may proceed
   */
  public boolean isOverloaded(Set<String> services) {
    return false;
  }

  /** Called when a configuration file is first read. */
  public void start() {
    update();
  }

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
   * Create a URL for a source file
   *
   * @return the URL to the source file or null if not possible
   */
  public Stream<String> sourceUrl(String localFilePath, int line, int column, Instant time) {
    return Stream.empty();
  }

  /**
   * Find a dumper
   *
   * @param name the dumper to find
   * @return the dumper if found, or an empty stream if none is available
   */
  public Stream<Dumper> findDumper(String name, Imyhat... types) {
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
}

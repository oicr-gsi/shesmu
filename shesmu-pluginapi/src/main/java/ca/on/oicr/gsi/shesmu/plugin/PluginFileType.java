package ca.on.oicr.gsi.shesmu.plugin;

import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.dumper.Dumper;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The format of a set of configuration files
 *
 * <p>This corresponds to a known format of configuration (think file extension) that defines how to
 * process each individual configuration file with a match {@link PluginFile}.
 *
 * <p>Concrete subclasses should provide a no arguments constructor
 *
 * @param <T> a monitor for each individual configuration file discovered
 */
public abstract class PluginFileType<T extends PluginFile> {
  private final String extension;
  private final Class<T> fileClass;
  private final Lookup lookup;

  /**
   * Create a new configuration format
   *
   * @param lookup a lookup to access annotated methods in this class and the plugin file class; if
   *     private or package private methods are to be exported, this must be {@link
   *     MethodHandles#lookup()}. It must be run in the caller context to capture correct access
   *     permissions.
   * @param pluginFileClass the class associated with each instance of a configuration file
   * @param extension the extension to use from directories; no two plugins may use the same
   *     extension
   */
  public PluginFileType(Lookup lookup, Class<T> pluginFileClass, String extension) {
    super();
    this.lookup = lookup;
    this.fileClass = pluginFileClass;
    this.extension = extension;
  }

  /**
   * Register a new configuration file
   *
   * <p>This is called when a new configuration file is discovered on disk
   *
   * <p>Anything callback defined using this {@link Definer} or any action created from an {@link
   * ca.on.oicr.gsi.shesmu.plugin.action.ShesmuAction} annotated method must <b>not</b> hold a
   * reference to {@link T}; it <b>must</b> use the {@link Definer#get()} method
   *
   * @param filePath the path to the configuration file
   * @param instanceName the name of the instance, based on the configuration file name
   * @param definer an interface to create actions, constants, and functions rather than using
   *     annotations
   */
  public abstract T create(Path filePath, String instanceName, Definer<T> definer);

  /** The file extension to monitor for */
  public final String extension() {
    return extension;
  }

  /** Get the type of the files */
  public final Class<T> fileClass() {
    return fileClass;
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
   * Check throttling should be applied
   *
   * @param services a list of service names to check; this set must not be modified; these names
   *     are arbitrary and must be coordinated by {@link Action} and the throttler
   * @return true if the action should be blocked; false if it may proceed
   */
  public boolean isOverloaded(Set<String> services) {
    return false;
  }
  /** Receive JSON data describing Prometheus alerts that are firing for the olives */
  public void pushAlerts(String alertJson) {}

  /** Get the method lookup for this class. */
  public final Lookup lookup() {
    return lookup;
  }

  /**
   * Create a URL for a source file
   *
   * @return the URL to the source file or an empty stream if not possible
   */
  public Stream<String> sourceUrl(String localFilePath, int line, int column, Instant time) {
    return Stream.empty();
  }

  /** Create some JavaScript code to render this item in dashboards */
  public void writeJavaScriptRenderer(PrintStream writer) {}
}

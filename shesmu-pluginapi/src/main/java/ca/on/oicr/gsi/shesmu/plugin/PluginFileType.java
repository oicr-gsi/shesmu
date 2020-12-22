package ca.on.oicr.gsi.shesmu.plugin;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.dumper.Dumper;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilterBuilder;
import ca.on.oicr.gsi.shesmu.plugin.filter.ExportSearch;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
  private final String namespace;
  private final Class<T> fileClass;
  private final Lookup lookup;
  private static final Set<String> RESERVED_NAMES =
      new TreeSet<>(
          Arrays.asList("core", "java", "olive", "plugin", "script", "shesmu", "std", "sys"));

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
   * @param namespaces the namespace that will be used to identify this all plugins are group into
   *     namespaces; different plugins may share a namespace though this is discouraged. It also
   *     cannot be any of the reserved namespace include <tt>core</tt>, <tt>java</tt>,
   *     <tt>olive</tt>, <tt>plugin</tt>, <tt>script</tt>, <tt>shesmu</tt>, <tt>std</tt>, and
   *     <tt>sys</tt>.
   */
  public PluginFileType(
      Lookup lookup, Class<T> pluginFileClass, String extension, String... namespaces) {
    super();
    this.lookup = lookup;
    this.fileClass = pluginFileClass;
    this.extension = extension;
    if (namespaces.length == 0) {
      throw new IllegalArgumentException(
          String.format("Plugin %s has no namespaces.", getClass().getName()));
    }
    for (final String namespace : namespaces) {
      if (!Parser.IDENTIFIER.matcher(namespace).matches()) {
        throw new IllegalArgumentException(
            String.format(
                "Plugin %s has an invalid namespace identifier “%s”",
                getClass().getName(), namespace));
      }
    }
    if (RESERVED_NAMES.contains(namespaces[0])) {
      throw new IllegalArgumentException(
          String.format(
              "Namespace identifier “%s” for plugin %s is reserved",
              namespaces[0], getClass().getName()));
    }
    this.namespace = String.join(Parser.NAMESPACE_SEPARATOR, namespaces);
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

  /** Generate a list of export buttons to provide to the UI */
  public <T> Stream<T> exportSearches(ExportSearch<T> builder) {
    return Stream.empty();
  }

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
   * @param columns
   * @return the dumper if found, or an empty stream if none is available
   */
  public Stream<Dumper> findDumper(String name, String[] columns, Imyhat... types) {
    return Stream.empty();
  }

  /** The namespace that olives will see when they use exports from this plugin */
  public String namespace() {
    return namespace;
  }

  /**
   * Check throttling should be applied
   *
   * @param services a list of service names to check; this set must not be modified; these names
   *     are arbitrary and must be coordinated by {@link Action} and the throttler
   * @return the names of any services that are in overload
   */
  public Stream<String> isOverloaded(Set<String> services) {
    return Stream.empty();
  }

  /** Get the method lookup for this class. */
  public final Lookup lookup() {
    return lookup;
  }

  /** Receive JSON data describing Prometheus alerts that are firing for the olives */
  public void pushAlerts(String alertJson) {}

  /** Create a list of searches */
  public <F> Stream<Pair<String, F>> searches(ActionFilterBuilder<F> builder) {
    return Stream.empty();
  }

  /**
   * Create a URL for a source file
   *
   * @return the URL to the source file or an empty stream if not possible
   */
  public Stream<String> sourceUrl(String localFilePath, int line, int column, String hash) {
    return Stream.empty();
  }

  /** Create some JavaScript code to render this item in dashboards */
  public void writeJavaScriptRenderer(PrintStream writer) {}
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

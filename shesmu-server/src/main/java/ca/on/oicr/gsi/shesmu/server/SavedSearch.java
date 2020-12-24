package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.plugin.files.WatchedFileListener;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilter;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

public class SavedSearch implements WatchedFileListener {
  private final Path filename;
  private ActionFilter[] filters;

  public SavedSearch(Path filename) {
    this.filename = filename;
  }

  public Stream<ActionFilter> filters() {
    return Stream.of(filters);
  }

  public String name() {
    return RuntimeSupport.removeExtension(filename, ".search");
  }

  public void start() {}

  public void stop() {}

  public Optional<Integer> update() {
    try {
      filters = RuntimeSupport.MAPPER.readValue(filename.toFile(), ActionFilter[].class);
    } catch (final Exception e) {
      e.printStackTrace();
    }

    return Optional.empty();
  }

  public void write(ObjectNode searches) {
    if (filters != null && filters.length > 0) {
      searches.putPOJO(name(), filters);
    }
  }
}

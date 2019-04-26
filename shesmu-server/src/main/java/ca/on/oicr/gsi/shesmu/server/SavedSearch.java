package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.server.Query.FilterJson;
import ca.on.oicr.gsi.shesmu.util.WatchedFileListener;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.Optional;

public class SavedSearch implements WatchedFileListener {
  private final Path filename;
  private FilterJson[] filters;

  public SavedSearch(Path filename) {
    this.filename = filename;
  }

  public void start() {
    update();
  }

  public void stop() {}

  public Optional<Integer> update() {
    try {
      filters = RuntimeSupport.MAPPER.readValue(filename.toFile(), FilterJson[].class);
    } catch (final Exception e) {
      e.printStackTrace();
    }

    return Optional.empty();
  }

  public void write(ObjectNode searches) {
    if (filters != null && filters.length > 0) {
      searches.putPOJO(RuntimeSupport.removeExtension(filename, ".search"), filters);
    }
  }
}

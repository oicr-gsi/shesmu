package ca.on.oicr.gsi.shesmu.runtime;

import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.dumper.Dumper;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;

public interface OliveServices {
  boolean isOverloaded(String... services);

  Dumper findDumper(String name, Imyhat... types);

  boolean accept(Action action, String filename, int line, int column, long time);

  boolean accept(String[] labels, String[] annotation, long ttl) throws Exception;
}

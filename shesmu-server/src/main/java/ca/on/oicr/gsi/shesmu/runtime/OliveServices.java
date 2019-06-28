package ca.on.oicr.gsi.shesmu.runtime;

import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.dumper.Dumper;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.stream.Stream;

public interface OliveServices {
  boolean accept(Action action, String filename, int line, int column, long time, String[] tags);

  boolean accept(String[] labels, String[] annotation, long ttl) throws Exception;

  Dumper findDumper(String name, Imyhat... types);

  boolean isOverloaded(String... services);

  <T> Stream<T> measureFlow(
      Stream<T> input, String filename, int line, int column, int oliveLine, int oliveColumn);

  void oliveRuntime(String filename, int line, int column, long timeInNs);
}

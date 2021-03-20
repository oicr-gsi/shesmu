package ca.on.oicr.gsi.shesmu.intervals;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class IntervalFileType extends PluginFileType<IntervalFile> {
  private static final Imyhat GROUP_TYPE = Imyhat.STRING.asList();

  @ShesmuMethod(
      name = "split_greedily",
      description =
          "Splits a weighted input set into buckets where each bucket will not exceed a certain cost (unless an input unit exceeds that cost).")
  public static Set<Set<String>> greedySplitting(
      @ShesmuParameter(description = "The items to split and their costs") Map<String, Long> inputs,
      @ShesmuParameter(description = "The maximum cost in a bucket") long limit) {
    @SuppressWarnings("unchecked")
    final var output = (Set<Set<String>>) ((Object) GROUP_TYPE.newSet());
    inputs.entrySet().stream()
        .filter(e -> e.getValue() >= limit)
        .forEach(e -> output.add(Set.of(e.getKey())));
    var remainder =
        inputs.entrySet().stream()
            .filter(e -> e.getValue() < limit)
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .collect(Collectors.toList());
    while (!remainder.isEmpty()) {
      final var group = new TreeSet<String>();
      var capacity = limit;
      for (final var entry : remainder) {
        if (entry.getValue() <= capacity) {
          group.add(entry.getKey());
          capacity -= entry.getValue();
          if (capacity == 0) {
            break;
          }
        }
      }
      output.add(group);
      remainder.removeIf(e -> group.contains(e.getKey()));
    }
    return output;
  }

  public IntervalFileType() {
    super(MethodHandles.lookup(), IntervalFile.class, ".intervalbed", "intervals");
  }

  @Override
  public IntervalFile create(Path filePath, String instanceName, Definer<IntervalFile> definer) {
    return new IntervalFile(filePath, instanceName);
  }
}

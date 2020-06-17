package ca.on.oicr.gsi.shesmu.pinery;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class PineryPluginType extends PluginFileType<PinerySource> {

  private enum Mask {
    I,
    Y,
    N
  }

  @ShesmuMethod(
      description =
          "Convert mask object produced by parse_bases_mask into the format for pack_bases_mask.",
      type = "o4group$ilength$iposition$itype$s")
  public static Tuple convert_mask(
      @ShesmuParameter(
              description = "The mask object to convert",
              type = "o7cycle_end$icycle_start$igroup$ilength$iordinal$iposition$itype$s")
          Tuple input) {
    return new Tuple(input.get(2), input.get(3), input.get(5), input.get(6));
  }

  @ShesmuMethod(description = "Writes a bases mask string from a collection of objects")
  public static String pack_bases_mask(
      @ShesmuParameter(
              description = "Bases mask collection",
              type = "ao4group$ilength$iposition$itype$s")
          Set<Tuple> basesMask) {
    return basesMask
        .stream()
        .collect(Collectors.groupingBy(t -> (Long) t.get(0)))
        .entrySet()
        .stream()
        .sorted(Comparator.comparing(Map.Entry::getKey))
        .map(
            e ->
                e.getValue()
                    .stream()
                    .sorted(Comparator.comparing(t -> (Long) t.get(2)))
                    .map(
                        t -> {
                          final String type = (String) t.get(3);
                          final long length = (Long) t.get(1);
                          return (type.isEmpty() ? "N" : type.substring(0, 1).toUpperCase())
                              + (length > 0 ? length : "*");
                        })
                    .collect(Collectors.joining()))
        .collect(Collectors.joining(","));
  }

  @ShesmuMethod(
      description =
          "Parse a bases mask string into a collection of objects. Each object has the comma-separated group it belongs to, position within that group, the type of that mask, the length of that mask (or negative for *), and ordinal, the number of times that type has been seen previously in any group.",
      type = "ao7cycle_end$icycle_start$igroup$ilength$iordinal$iposition$itype$s")
  public static Set<Tuple> parse_bases_mask(
      @ShesmuParameter(description = "Bases mask string") String basesMask) {
    final Set<Tuple> result = BASES_MASK_TYPE.newSet();
    final Map<Mask, AtomicLong> instances = new EnumMap<>(Mask.class);
    long cycle = 0;
    for (Mask mask : Mask.values()) {
      instances.put(mask, new AtomicLong());
    }
    String[] groups = basesMask.split(",");
    for (int i = 0; i < groups.length; i++) {
      Matcher match = BASE_MASK.matcher(groups[i].trim());
      long position = 0;
      while (match.find()) {
        final long length = match.group(2).equals("*") ? -1L : Long.parseLong(match.group(2));
        final String type = match.group(1).toUpperCase();
        final long endCycle = length < 0 || cycle < 0 ? -1L : (cycle + length);
        result.add(
            new Tuple(
                endCycle,
                cycle,
                (long) i,
                length,
                instances.get(Mask.valueOf(type)).getAndIncrement(),
                position++,
                type));
        cycle = endCycle;
      }
    }
    return result;
  }

  private static final Imyhat BASES_MASK_TYPE =
      new Imyhat.ObjectImyhat(
          Stream.of(
              new Pair<>("group", Imyhat.INTEGER),
              new Pair<>("length", Imyhat.INTEGER),
              new Pair<>("ordinal", Imyhat.INTEGER),
              new Pair<>("position", Imyhat.INTEGER),
              new Pair<>("type", Imyhat.STRING)));
  private static final Pattern BASE_MASK = Pattern.compile("([iIyYnN])\\s*(\\*|[0-9]*)");

  public PineryPluginType() {
    super(MethodHandles.lookup(), PinerySource.class, ".pinery", "pinery");
  }

  @Override
  public PinerySource create(Path filePath, String instanceName, Definer<PinerySource> definer) {
    return new PinerySource(filePath, instanceName);
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    // No actions.
  }
}

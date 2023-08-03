package ca.on.oicr.gsi.shesmu.pinery.barcodes;

import ca.on.oicr.gsi.shesmu.plugin.grouper.Grouper;
import ca.on.oicr.gsi.shesmu.plugin.grouper.Subgroup;
import ca.on.oicr.gsi.shesmu.plugin.grouper.TriGrouper;
import io.prometheus.client.Gauge;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BarcodeGrouper<I, O> implements Grouper<I, O> {
  private static final Gauge badGroups =
      Gauge.build("shesmu_barcode_grouper_rejected", "Whether this particular run was rejected.")
          .labelNames("run")
          .register();
  private final Function<I, List<String>> barcodeReader;
  private final Function<I, String> runNameReader;
  private final Function<I, String> basesMaskReader;
  private final TriGrouper<String, Function<I, Set<String>>, Function<I, String>, O, I>
      collectorFactory;
  private final int minAllowedEditDistance;

  public <T, C> BarcodeGrouper(
      long minAllowedEditDistance,
      Function<I, String> runNameReader,
      Function<I, String> basesMaskReader,
      Function<I, List<String>> barcodeReader,
      TriGrouper<String, Function<I, Set<String>>, Function<I, String>, O, I> collectorFactory) {
    this.minAllowedEditDistance = (int) minAllowedEditDistance;
    this.runNameReader = runNameReader;
    this.basesMaskReader = basesMaskReader;
    this.barcodeReader = barcodeReader;
    this.collectorFactory = collectorFactory;
    badGroups.clear();
  }

  @Override
  public Stream<Subgroup<I, O>> group(List<I> inputs) {
    final Gauge.Child gauge = badGroups.labels(runNameReader.apply(inputs.get(0)));
    final List<BasesMask> basesMasks =
        inputs.stream()
            .map(basesMaskReader)
            .distinct()
            .map(BasesMask::fromString)
            .collect(Collectors.toList());
    if (basesMasks.size() != 1 || basesMasks.get(0) == null) {
      // If we've got multiple bases masks, (╯°□°)╯︵ ┻━┻
      gauge.set(1);
      return Stream.empty();
    }
    final List<Barcode> barcodes = new ArrayList<>();
    // Barcode length → input row → trimmed barcodes
    final Map<String, Map<I, Set<String>>> groups = new HashMap<>();
    final Map<I, String> newMasks = new HashMap<>();
    for (I input : inputs) {
      for (String barcodeString : barcodeReader.apply(input)) {
        final Barcode barcode =
            BarcodeAndBasesMask.applyBasesMask(
                Barcode.fromString(barcodeString), basesMasks.get(0));
        if (barcode == null) {
          // If we've got an unparseable barcode, (╯°□°)╯︵ ┻━┻
          gauge.set(1);
          return Stream.empty();
        }
        barcodes.add(barcode);
        groups
            .computeIfAbsent(barcode.getLengthString(), k -> new HashMap<>())
            .computeIfAbsent(input, k -> new TreeSet<>())
            .add(barcode.toString());
        final BasesMask newMask =
            BarcodeAndBasesMask.calculateBasesMask(barcode, basesMasks.get(0));
        if (newMask == null) {
          // If we've got an mess of a mask, (╯°□°)╯︵ ┻━┻
          gauge.set(1);
          return Stream.empty();
        }
        newMasks.put(input, newMask.toString());
      }
    }
    List<BarcodeCollision> collisions =
        BarcodeComparison.getTruncatedHammingDistanceCollisions(barcodes, minAllowedEditDistance);
    if (!collisions.isEmpty()) {
      // You know the drill, (╯°□°)╯︵ ┻━┻
      gauge.set(1);
      return Stream.empty();
    }

    gauge.set(0);
    return groups.entrySet().stream()
        .map(
            entry ->
                new Subgroup<>(
                    collectorFactory.apply(entry.getKey(), entry.getValue()::get, newMasks::get),
                    entry.getValue().keySet().stream()));
  }
}

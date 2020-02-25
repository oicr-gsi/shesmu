package ca.on.oicr.gsi.shesmu.pinery.barcodes;

import ca.on.oicr.gsi.shesmu.plugin.grouper.Grouper;
import ca.on.oicr.gsi.shesmu.plugin.grouper.Subgroup;
import ca.on.oicr.gsi.shesmu.plugin.grouper.TriGrouper;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BarcodeGrouper<I, O> implements Grouper<I, O> {
  private final Function<I, List<String>> barcodeReader;
  private final Function<I, String> basesMaskReader;
  private final TriGrouper<String, Function<I, Set<String>>, Function<I, String>, O, I>
      collectorFactory;
  private final int minAllowedEditDistance;

  public <T, C> BarcodeGrouper(
      long minAllowedEditDistance,
      Function<I, String> basesMaskReader,
      Function<I, List<String>> barcodeReader,
      TriGrouper<String, Function<I, Set<String>>, Function<I, String>, O, I> collectorFactory) {
    this.minAllowedEditDistance = (int) minAllowedEditDistance;
    this.basesMaskReader = basesMaskReader;
    this.barcodeReader = barcodeReader;
    this.collectorFactory = collectorFactory;
  }

  @Override
  public Stream<Subgroup<I, O>> group(List<I> inputs) {
    final List<BasesMask> basesMasks =
        inputs
            .stream()
            .map(basesMaskReader)
            .distinct()
            .map(BasesMask::fromString)
            .collect(Collectors.toList());
    if (basesMasks.size() != 1 || basesMasks.get(0) == null) {
      // If we've got multiple bases masks, (╯°□°)╯︵ ┻━┻
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
          return Stream.empty();
        }
        newMasks.put(input, newMask.toString());
      }
    }
    List<BarcodeCollision> collisions =
        BarcodeComparison.getTruncatedHammingDistanceCollisions(barcodes, minAllowedEditDistance);
    if (!collisions.isEmpty()) {
      // You know the drill, (╯°□°)╯︵ ┻━┻
      return Stream.empty();
    }

    return groups
        .entrySet()
        .stream()
        .map(
            entry ->
                new Subgroup<>(
                    collectorFactory.apply(entry.getKey(), entry.getValue()::get, newMasks::get),
                    entry.getValue().keySet().stream()));
  }
}

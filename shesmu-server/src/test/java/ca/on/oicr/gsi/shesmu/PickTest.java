package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PickTest {

  @Test
  public void pickTest() {
    final Map<Boolean, Integer> results =
        RuntimeSupport.pick(
                Stream.of(
                    new Pair<>(false, 7),
                    new Pair<>(true, 4),
                    new Pair<>(true, 3),
                    new Pair<>(false, 5),
                    new Pair<>(true, 6)),
                p -> p.first().hashCode(),
                (a, b) -> a.first().equals(b.first()),
                Comparator.comparing(Pair::second))
            .collect(Collectors.toMap(Pair::first, Pair::second));
    Assertions.assertEquals(2, results.size());
    Assertions.assertEquals(3, results.get(true).intValue());
    Assertions.assertEquals(5, results.get(false).intValue());
  }
}

package ca.on.oicr.gsi.shesmu.configmaster;

import ca.on.oicr.gsi.shesmu.configmaster.Files.File;
import java.util.HashSet;
import java.util.Set;

/**
 * This mapping currently is being developed for autoverification and lives in qc-gate-etl. I hope
 * we could develop that further out into something queryable! For now, mock the response, wherever
 * it may come from. "metrics" is an abstraction for this demo.
 */
public class QcMapping {
  public static Set<File> getRequirements(Set<String> requirements) {
    Set<File> ret = new HashSet<>();
    for (String requirement : requirements) {
      switch (requirement) {
        case "Mean Insert Size":
          ret.add(Files.getByName("metrics"));
          break;
        case "Median Insert Size":
          ret.add(Files.getByName("metrics"));
      }
    }
    return ret;
  }
}

package ca.on.oicr.gsi.shesmu.configmaster;

import ca.on.oicr.gsi.shesmu.configmaster.Files.File;
import java.util.HashSet;
import java.util.Set;

public class Djerba {
  /**
   * This is a mock method! In actual implementation, this would be a call out to Djerba itself.
   *
   * @param assayTest
   * @return
   */
  public static Set<File> getRequirements(String assayTest) {
    Set<File> ret = new HashSet<>();
    switch (assayTest) {
      case "WGTS":
        ret.add(Files.getByName("seg"));
    }
    return ret;
  }
}

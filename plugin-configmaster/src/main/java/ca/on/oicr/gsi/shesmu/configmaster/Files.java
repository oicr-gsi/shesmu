package ca.on.oicr.gsi.shesmu.configmaster;

import java.util.HashSet;
import java.util.Set;

public class Files {
  public record File(String name) {}

  public static Set<File> files = new HashSet<>();

  public static File getByName(String name) {
    return files.stream().filter(file -> file.name().equals(name)).findFirst().orElse(null);
  }
}

package ca.on.oicr.gsi.shesmu.niassa;

import java.util.Set;

public enum FileMatchingPolicy {
  EXACT {
    @Override
    public boolean matches(Set<Integer> desired, Set<Integer> found) {
      return found.equals(desired);
    }
  },
  SUPERSET {
    @Override
    public boolean matches(Set<Integer> desired, Set<Integer> found) {
      return found.containsAll(desired);
    }
  };

  public abstract boolean matches(Set<Integer> desired, Set<Integer> found);
}

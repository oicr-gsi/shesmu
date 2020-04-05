package ca.on.oicr.gsi.shesmu.compiler;

public enum WildcardCheck {
  NONE {
    @Override
    public WildcardCheck combine(WildcardCheck other) {
      return other;
    }
  },
  HAS_WILDCARD {
    @Override
    public WildcardCheck combine(WildcardCheck other) {
      return other == NONE ? HAS_WILDCARD : BAD;
    }
  },
  BAD {
    @Override
    public WildcardCheck combine(WildcardCheck other) {
      return BAD;
    }
  };

  public abstract WildcardCheck combine(WildcardCheck other);
}

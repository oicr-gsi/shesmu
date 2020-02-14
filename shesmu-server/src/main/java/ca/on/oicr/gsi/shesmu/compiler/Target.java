package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;

/** A defined variable in a program */
public interface Target {
  /** The category of variable; this defines the capture and redefinition semantics */
  public enum Flavour {
    /**
     * A variable from outside the olive
     *
     * <p>May be captured and survive stream alteration
     */
    CONSTANT(false),
    /**
     * A variable in a <tt>For</tt> operation
     *
     * <p>May be be captured and should not be in scope to even worry about stream alteration
     */
    LAMBDA(false),
    /**
     * A variable from a <tt>Define</tt> olive's parameters
     *
     * <p>Equivalent to {@link #CONSTANT}
     */
    PARAMETER(false),
    /**
     * A variable from the stream which is not to be included in a signature
     *
     * <p>Not captured and should be erased during stream alteration
     */
    STREAM(true),
    /**
     * A variable from the stream which must be included in a signature
     *
     * <p>Not captured and should be erased during stream alteration
     */
    STREAM_SIGNABLE(true),
    /**
     * A variable which is really a signing function on the current stream value
     *
     * <p>May be capture and should be erased during stream alteration
     */
    STREAM_SIGNATURE(true);
    private final boolean isStream;

    private Flavour(boolean isStream) {
      this.isStream = isStream;
    }

    public boolean isStream() {
      return isStream;
    }

    public boolean needsCapture() {
      return !isStream;
    }
  }

  static Target softWrap(Target target) {
    if (!target.flavour().isStream) {
      throw new IllegalArgumentException(
          String.format("Cannot wrap %s variable %s", target.flavour().name(), target.name()));
    }
    return new Target() {

      @Override
      public Flavour flavour() {
        return target.flavour();
      }

      @Override
      public String name() {
        return target.name();
      }

      @Override
      public Imyhat type() {
        return target.type();
      }
    };
  }

  static Target wrap(Target target) {
    if (!target.flavour().isStream) {
      throw new IllegalArgumentException(
          String.format("Cannot wrap %s variable %s", target.flavour().name(), target.name()));
    }
    return new Target() {

      @Override
      public Flavour flavour() {
        return Flavour.STREAM;
      }

      @Override
      public String name() {
        return target.name();
      }

      @Override
      public Imyhat type() {
        return target.type();
      }
    };
  }
  public static final TargetWithContext BAD =
      new TargetWithContext() {

        @Override
        public Flavour flavour() {
          return Flavour.CONSTANT;
        }

        @Override
        public String name() {
          return "<BAD>";
        }

        @Override
        public void setContext(NameDefinitions defs) {
          // Ignore it
        }

        @Override
        public Imyhat type() {
          return Imyhat.BAD;
        }
      };

  /** What category of variables this one belongs to */
  Flavour flavour();

  /** The Shemsu name for this variable */
  String name();

  /** The Shesmu type for this variable */
  Imyhat type();
}

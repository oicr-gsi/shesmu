package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;

public enum OptionalFlattenOutput {
  /** Follow the inner collector's behaviour (<em>i.e.</em>, if it would reject, we reject) */
  PRESERVE {
    @Override
    public Imyhat type(Imyhat type) {
      return type;
    }
  },
  /** If the inner collector saw no input, return an optional output */
  WRAP_ON_NO_INPUT {
    @Override
    public Imyhat type(Imyhat type) {
      return type.asOptional();
    }
  },
  ;

  public abstract Imyhat type(Imyhat type);
}

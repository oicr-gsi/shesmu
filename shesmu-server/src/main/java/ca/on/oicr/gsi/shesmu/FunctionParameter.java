package ca.on.oicr.gsi.shesmu;

public final class FunctionParameter {

  private final String description;

  private final Imyhat type;

  public FunctionParameter(String description, Imyhat type) {
    super();
    this.description = description;
    this.type = type;
  }

  public String description() {
    return description;
  }

  public Imyhat type() {
    return type;
  }
}

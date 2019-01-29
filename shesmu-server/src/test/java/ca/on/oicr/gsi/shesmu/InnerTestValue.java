package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;

public class InnerTestValue {

  private final long l;
  private final String s;

  public InnerTestValue(long l, String s) {
    super();
    this.l = l;
    this.s = s;
  }

  @ShesmuVariable
  public long l() {
    return l;
  }

  @ShesmuVariable(type = "s")
  public String s() {
    return s;
  }
}

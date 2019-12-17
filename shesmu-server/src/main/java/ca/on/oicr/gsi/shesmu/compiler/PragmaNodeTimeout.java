package ca.on.oicr.gsi.shesmu.compiler;

import java.util.concurrent.atomic.AtomicInteger;

public class PragmaNodeTimeout extends PragmaNode {

  private final int timeout;

  public PragmaNodeTimeout(int timeout) {
    super();
    this.timeout = timeout;
  }

  @Override
  public void renderGuard(RootBuilder root) {
    // Do nothing.

  }

  @Override
  public void renderAtExit(RootBuilder root) {
    // Do nothing.

  }

  @Override
  public void timeout(AtomicInteger timeout) {
    timeout.set(this.timeout);
  }
}

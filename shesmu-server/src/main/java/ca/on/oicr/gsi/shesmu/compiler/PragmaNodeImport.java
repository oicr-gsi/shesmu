package ca.on.oicr.gsi.shesmu.compiler;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class PragmaNodeImport extends PragmaNode {
  private final String prefix;
  private final List<ImportNode> imports;

  public PragmaNodeImport(String prefix, List<ImportNode> imports) {
    this.prefix = prefix;
    this.imports = imports;
  }

  @Override
  public Stream<ImportRewriter> imports() {
    return imports.stream().map(import_ -> import_.prepare(prefix));
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
    // Do nothing.

  }
}

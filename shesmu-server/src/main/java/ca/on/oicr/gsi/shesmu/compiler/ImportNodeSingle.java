package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.Parser;

public final class ImportNodeSingle extends ImportNode {
  private final String name;

  public ImportNodeSingle(String name) {
    this.name = name;
  }

  @Override
  public ImportRewriter prepare(String prefix) {
    return candidate ->
        candidate.equals(name) ? String.join(Parser.NAMESPACE_SEPARATOR, prefix, name) : null;
  }
}

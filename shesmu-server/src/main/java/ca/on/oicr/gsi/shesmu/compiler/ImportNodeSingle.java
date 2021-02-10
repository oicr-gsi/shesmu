package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.Parser;

public final class ImportNodeSingle extends ImportNode {
  private final String name;

  public ImportNodeSingle(String name) {
    this.name = name;
  }

  @Override
  public ImportRewriter prepare(String prefix) {
    return new ImportRewriter() {
      @Override
      public String rewrite(String candidate) {
        return candidate.equals(name)
            ? String.join(Parser.NAMESPACE_SEPARATOR, prefix, name)
            : null;
      }

      @Override
      public String strip(String candidate) {
        return candidate.equals(String.join(Parser.NAMESPACE_SEPARATOR, prefix, name))
            ? name
            : null;
      }
    };
  }
}

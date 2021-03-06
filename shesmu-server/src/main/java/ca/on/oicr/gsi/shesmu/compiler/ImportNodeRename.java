package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.Parser;

public final class ImportNodeRename extends ImportNode {
  private final String original;
  private final String alias;

  public ImportNodeRename(String original, String alias) {
    this.original = original;
    this.alias = alias;
  }

  @Override
  public ImportRewriter prepare(String prefix) {
    return new ImportRewriter() {
      @Override
      public String rewrite(String candidate) {
        return candidate.equals(alias)
            ? String.join(Parser.NAMESPACE_SEPARATOR, prefix, original)
            : null;
      }

      @Override
      public String strip(String candidate) {
        return candidate.equals(String.join(Parser.NAMESPACE_SEPARATOR, prefix, original))
            ? alias
            : null;
      }
    };
  }
}

package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.ParseDispatch;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class ImportNode {
  private static final Parser.ParseDispatch<List<ImportNode>> DISPATCH = new ParseDispatch<>();

  static {
    DISPATCH.addSymbol(
        "*",
        Parser.justWhiteSpace(
            List.of(
                new ImportNode() {
                  @Override
                  public ImportRewriter prepare(String prefix) {
                    return new ImportRewriter() {
                      @Override
                      public String rewrite(String name) {
                        return String.join(Parser.NAMESPACE_SEPARATOR, prefix, name);
                      }

                      @Override
                      public String strip(String name) {
                        return name.startsWith(prefix + Parser.NAMESPACE_SEPARATOR)
                            ? name.substring(prefix.length() + Parser.NAMESPACE_SEPARATOR.length())
                            : null;
                      }
                    };
                  }
                })));
    DISPATCH.addSymbol(
        "{",
        (parser, output) ->
            parser
                .whitespace()
                .list(
                    lists ->
                        output.accept(
                            lists.stream().flatMap(List::stream).collect(Collectors.toList())),
                    ImportNode::tail,
                    ',')
                .whitespace()
                .symbol("}")
                .whitespace());
    DISPATCH.addRaw("name", ImportNode::tail);
  }

  public static Parser parse(Parser parse, Consumer<List<ImportNode>> output) {
    return parse.whitespace().dispatch(DISPATCH, output);
  }

  private static Parser tail(Parser parse, Consumer<List<ImportNode>> output) {
    final var name = new AtomicReference<String>();
    var result = parse.identifier(name::set).whitespace();
    if (result.isGood()) {
      final var alias = new AtomicReference<String>();
      final var aliasResult = result.keyword("As").whitespace().identifier(alias::set).whitespace();
      if (aliasResult.isGood()) {
        output.accept(List.of(new ImportNodeRename(name.get(), alias.get())));
        result = aliasResult;
      } else {
        output.accept(List.of(new ImportNodeSingle(name.get())));
      }
    }
    return result;
  }

  public abstract ImportRewriter prepare(String prefix);
}

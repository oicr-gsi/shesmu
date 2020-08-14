package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class AlgebraicImyhatNode {

  public static Parser parse(Parser input, Consumer<AlgebraicImyhatNode> output) {
    final AtomicReference<String> name = new AtomicReference<>();
    final Parser result = input.algebraicIdentifier(name::set).whitespace();
    if (!result.isGood()) {
      return result;
    }
    final Parser tupleParser = result.symbol("{");
    if (tupleParser.isGood()) {
      final AtomicReference<List<Pair<String, ImyhatNode>>> fields = new AtomicReference<>();
      final Parser objectParser =
          tupleParser
              .whitespace()
              .<Pair<String, ImyhatNode>>list(
                  fields::set,
                  (fp, fo) -> {
                    final AtomicReference<String> fieldName = new AtomicReference<>();
                    final AtomicReference<ImyhatNode> value = new AtomicReference<>();
                    final Parser fresult =
                        fp.whitespace()
                            .identifier(fieldName::set)
                            .whitespace()
                            .symbol("=")
                            .whitespace()
                            .then(ImyhatNode::parse, value::set);
                    if (fresult.isGood()) {
                      fo.accept(new Pair<>(fieldName.get(), value.get()));
                    }
                    return fresult;
                  },
                  ',')
              .whitespace()
              .symbol("}")
              .whitespace();
      if (objectParser.isGood()) {
        output.accept(new AlgebraicImyhatNodeObject(name.get(), fields.get()));
        return objectParser;
      }

      final AtomicReference<List<ImyhatNode>> inner =
          new AtomicReference<>(Collections.emptyList());
      final Parser tupleResult =
          tupleParser
              .whitespace()
              .list(inner::set, (p, o) -> ImyhatNode.parse(p.whitespace(), o).whitespace(), ',')
              .symbol("}")
              .whitespace();
      output.accept(new AlgebraicImyhatNodeTuple(name.get(), inner.get()));
      return tupleResult;
    }
    output.accept(new AlgebraicImyhatNodeEmpty(name.get()));
    return result;
  }

  public abstract Imyhat render(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler);
}

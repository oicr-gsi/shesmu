package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class RefillerDefinitionNode implements RefillerDefinition {
  private final int column;
  private final List<Pair<String, ImyhatNode>> fields;
  private final int line;
  private final String name;
  private String outputType = "null";
  private final List<RefillerParameterDefinition> parameters = new ArrayList<>();

  public RefillerDefinitionNode(
      int line, int column, String name, List<Pair<String, ImyhatNode>> fields) {
    this.line = line;
    this.column = column;
    this.name = name;
    this.fields = fields;
  }

  @Override
  public String description() {
    return null;
  }

  @Override
  public Path filename() {
    return null;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Stream<RefillerParameterDefinition> parameters() {
    return parameters.stream();
  }

  public static Parser parse(Parser parser, Consumer<RefillerDefinitionNode> output) {
    final var name = new AtomicReference<String>();
    final var parameters = new AtomicReference<List<Pair<String, ImyhatNode>>>();
    final var result =
        parser
            .keyword("Refiller")
            .whitespace()
            .list(
                parameters::set,
                (p, o) -> {
                  final var parameter = new AtomicReference<String>();
                  final var type = new AtomicReference<ImyhatNode>();
                  final var parameterResult =
                      p.whitespace()
                          .identifier(parameter::set)
                          .whitespace()
                          .symbol("=")
                          .whitespace()
                          .then(ImyhatNode::parse, type::set);
                  if (parameterResult.isGood()) {
                    o.accept(new Pair<>(parameter.get(), type.get()));
                  }
                  return parameterResult;
                },
                ',')
            .whitespace()
            .keyword("As")
            .whitespace()
            .identifier(name::set)
            .whitespace();
    if (result.isGood()) {
      output.accept(
          new RefillerDefinitionNode(parser.line(), parser.column(), name.get(), parameters.get()));
    }
    return result;
  }

  @Override
  public void render(Renderer renderer) {
    throw new UnsupportedOperationException();
  }

  public String render() {
    return name + ": " + outputType;
  }

  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    final Map<String, Imyhat> arguments = new TreeMap<>();
    var ok = true;
    for (final var field : fields) {
      if (arguments.containsKey(field.first())) {
        ok = false;
        errorHandler.accept(
            String.format("%d:%d: Duplicate field %s in %s.", line, column, field.first(), name));
      } else {
        final var result = field.second().render(expressionCompilerServices, errorHandler);
        if (result.isBad()) {
          ok = false;
        } else {
          arguments.put(field.first(), result);
          parameters.add(
              new RefillerParameterDefinition() {
                @Override
                public String name() {
                  return field.first();
                }

                @Override
                public void render(Renderer renderer, int refillerLocal, int functionLocal) {
                  throw new UnsupportedOperationException();
                }

                @Override
                public Imyhat type() {
                  return result;
                }
              });
        }
      }
    }
    if (ok) {
      outputType =
          arguments.entrySet().stream()
              .map(e -> e.getKey() + ": \"" + e.getValue().descriptor() + "\"")
              .collect(Collectors.joining(", ", "{", "}"));
    }
    return ok;
  }
}

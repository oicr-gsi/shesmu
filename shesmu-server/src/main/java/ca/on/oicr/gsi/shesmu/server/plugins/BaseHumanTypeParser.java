package ca.on.oicr.gsi.shesmu.server.plugins;

import ca.on.oicr.gsi.shesmu.compiler.ExpressionCompilerServices;
import ca.on.oicr.gsi.shesmu.compiler.ImyhatNode;
import ca.on.oicr.gsi.shesmu.compiler.definitions.*;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.TypeParser;
import ca.on.oicr.gsi.shesmu.runtime.CompiledGenerator;
import ca.on.oicr.gsi.shesmu.util.NameLoader;
import java.util.concurrent.atomic.AtomicReference;

public abstract class BaseHumanTypeParser implements TypeParser {
  private final DefinitionRepository definitionRepository;
  private final InputFormatDefinition inputFormat;

  public BaseHumanTypeParser(
      DefinitionRepository definitionRepository, InputFormatDefinition inputFormat) {
    this.inputFormat = inputFormat;
    this.definitionRepository = definitionRepository;
  }

  @Override
  public final Imyhat parse(String input) {

    final AtomicReference<ImyhatNode> node = new AtomicReference<>();
    final Parser parser =
        Parser.start(input, (l, c, m) -> {})
            .whitespace()
            .then(ImyhatNode::parse, node::set)
            .whitespace();
    if (parser.isGood()) {
      return node.get()
          .render(
              new ExpressionCompilerServices() {
                private final NameLoader<FunctionDefinition> functions =
                    new NameLoader<>(definitionRepository.functions(), FunctionDefinition::name);

                @Override
                public FunctionDefinition function(String name) {
                  return functions.get(name);
                }

                @Override
                public Imyhat imyhat(String name) {
                  return typeForName(name);
                }

                @Override
                public InputFormatDefinition inputFormat(String format) {
                  return CompiledGenerator.SOURCES.get(format);
                }

                @Override
                public InputFormatDefinition inputFormat() {
                  return inputFormat;
                }
              },
              m -> {});
    }
    return Imyhat.BAD;
  }

  protected abstract Imyhat typeForName(String name);
}

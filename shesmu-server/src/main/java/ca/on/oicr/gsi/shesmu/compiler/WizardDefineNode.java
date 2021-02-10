package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.core.StandardDefinitions;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WizardDefineNode {
  public static final Parser parse(Parser parser, Consumer<WizardDefineNode> output) {
    final AtomicReference<String> name = new AtomicReference<>();
    final AtomicReference<List<OliveParameter>> parameters = new AtomicReference<>();
    final AtomicReference<WizardNode> step = new AtomicReference<>();
    final Parser result =
        parser
            .whitespace()
            .keyword("Define")
            .whitespace()
            .identifier(name::set)
            .whitespace()
            .symbol("(")
            .whitespace()
            .list(parameters::set, OliveParameter::parse, ',')
            .whitespace()
            .symbol(")")
            .whitespace()
            .then(WizardNode::parse, step::set)
            .whitespace()
            .symbol(";")
            .whitespace();
    if (result.isGood()) {
      output.accept(
          new WizardDefineNode(
              parser.line(), parser.column(), name.get(), parameters.get(), step.get()));
    }
    return result;
  }

  private final int column;
  private final int line;
  private final String name;
  private final List<OliveParameter> parameters;
  private String renderedFunction;
  private final WizardNode step;

  public WizardDefineNode(
      int line, int column, String name, List<OliveParameter> parameters, WizardNode step) {
    this.line = line;
    this.column = column;
    this.name = name;
    this.parameters = parameters;
    this.step = step;
  }

  public boolean check(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    boolean ok = true;
    for (final Map.Entry<String, Long> duplicate :
        parameters.stream()
            .collect(Collectors.groupingBy(OliveParameter::name, Collectors.counting()))
            .entrySet()) {
      if (duplicate.getValue() > 1) {
        errorHandler.accept(
            String.format(
                "%d:%d: Parameter %s is defined %d times.",
                line, column, duplicate.getKey(), duplicate.getValue()));
        ok = false;
      }
    }
    return ok
        && parameters.stream()
                .filter(p -> p.resolveTypes(expressionCompilerServices, errorHandler))
                .count()
            == parameters.size()
        && step.resolveDefinitions(expressionCompilerServices, nativeDefinitions, errorHandler)
        && step.resolve(
            NameDefinitions.root(
                InputFormatDefinition.DUMMY,
                Stream.concat(new StandardDefinitions().constants(), parameters.stream()),
                Stream.empty()),
            errorHandler)
        && step.typeCheck(errorHandler);
  }

  public boolean checkParameters(
      int line, int column, List<Imyhat> arguments, Consumer<String> errorHandler) {
    if (arguments.size() != parameters.size()) {
      errorHandler.accept(
          String.format(
              "%d:%d: Call to step %s requires %d arguments but %d given.",
              line, column, name, parameters.size(), arguments.size()));
      return false;
    }
    boolean ok = true;
    for (int i = 0; i < parameters.size(); i++) {
      if (!parameters.get(i).type().isAssignableFrom(arguments.get(i))) {
        errorHandler.accept(
            String.format(
                "%d:%d: Argument %d to step %s requires %s arguments but %s given.",
                line, column, i, name, parameters.get(i).type().name(), arguments.get(i).name()));
        ok = false;
      }
    }
    return ok;
  }

  public String function() {
    return renderedFunction;
  }

  public void render(EcmaScriptRenderer renderer) {
    renderedFunction =
        renderer.newConst(
            renderer.lambda(
                parameters.size(),
                (r, a) -> {
                  final String child =
                      step.renderEcma(
                          r,
                          rr ->
                              parameters.stream()
                                  .map(
                                      new Function<OliveParameter, EcmaLoadableValue>() {
                                        private int counter;

                                        @Override
                                        public EcmaLoadableValue apply(OliveParameter parameter) {
                                          return new EcmaLoadableValue() {
                                            private final int index = counter++;

                                            @Override
                                            public String apply(EcmaScriptRenderer renderer) {
                                              return a.apply(index);
                                            }

                                            @Override
                                            public String name() {
                                              return parameter.name();
                                            }
                                          };
                                        }
                                      }));
                  return String.format(
                      "%s(%s)",
                      child,
                      parameters.stream()
                          .map(
                              new Function<OliveParameter, String>() {
                                private int index;

                                @Override
                                public String apply(OliveParameter parameter) {
                                  try {
                                    return RuntimeSupport.MAPPER.writeValueAsString(
                                            parameter.name())
                                        + ": "
                                        + a.apply(index++);
                                  } catch (JsonProcessingException e) {
                                    throw new RuntimeException(e);
                                  }
                                }
                              })
                          .collect(Collectors.joining(", ", "{", "}")));
                }));
  }

  public boolean resolveCrossReferences(
      Map<String, WizardDefineNode> references, Consumer<String> errorHandler) {
    boolean ok = step.resolveCrossReferences(references, errorHandler);
    if (references.containsKey(name)) {
      errorHandler.accept(
          String.format("%d:%d: Meditation step %s has already been defined.", line, column, name));
      return false;
    } else {
      references.put(name, this);
    }
    return ok;
  }
}

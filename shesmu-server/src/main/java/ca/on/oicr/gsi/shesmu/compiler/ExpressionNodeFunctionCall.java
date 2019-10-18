package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.objectweb.asm.commons.GeneratorAdapter;

public class ExpressionNodeFunctionCall extends ExpressionNode {
  private static final FunctionDefinition BROKEN_FUCNTION =
      new FunctionDefinition() {

        @Override
        public String description() {
          return "Undefined function";
        }

        @Override
        public String name() {
          return "💔";
        }

        @Override
        public Path filename() {
          return null;
        }

        @Override
        public Stream<FunctionParameter> parameters() {
          return Stream.empty();
        }

        @Override
        public void render(GeneratorAdapter methodGen) {
          throw new UnsupportedOperationException();
        }

        @Override
        public final void renderStart(GeneratorAdapter methodGen) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Imyhat returnType() {
          return Imyhat.BAD;
        }
      };

  private final List<ExpressionNode> arguments;

  private FunctionDefinition function;

  private final String name;

  public ExpressionNodeFunctionCall(
      int line, int column, String name, List<ExpressionNode> arguments) {
    super(line, column);
    this.name = name;
    this.arguments = arguments;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    arguments.forEach(item -> item.collectFreeVariables(names, predicate));
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    if (function.filename() != null) {
      pluginFileNames.add(function.filename());
    }
    arguments.forEach(arg -> arg.collectPlugins(pluginFileNames));
  }

  @Override
  public void render(Renderer renderer) {
    function.renderStart(renderer.methodGen());
    arguments.forEach(argument -> argument.render(renderer));
    function.render(renderer.methodGen());
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return arguments.stream().filter(argument -> argument.resolve(defs, errorHandler)).count()
        == arguments.size();
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    boolean ok = true;
    function = expressionCompilerServices.function(name);
    if (function == null) {
      function = BROKEN_FUCNTION;
      errorHandler.accept(String.format("%d:%d: Undefined function “%s”.", line(), column(), name));
      ok = false;
    }
    return ok
        & arguments
                .stream()
                .filter(
                    argument ->
                        argument.resolveDefinitions(expressionCompilerServices, errorHandler))
                .count()
            == arguments.size();
  }

  @Override
  public Imyhat type() {
    return function.returnType();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok =
        arguments.stream().filter(argument -> argument.typeCheck(errorHandler)).count()
            == arguments.size();
    if (ok) {
      final List<Imyhat> argumentTypes =
          function.parameters().map(FunctionParameter::type).collect(Collectors.toList());
      if (arguments.size() != argumentTypes.size()) {
        errorHandler.accept(
            String.format(
                "%d:%d: Wrong number of arguments to function “%s”. Expected %d, got %d.",
                line(), column(), function.name(), argumentTypes.size(), arguments.size()));
      }
      ok =
          IntStream.range(0, argumentTypes.size())
                  .filter(
                      index -> {
                        final boolean isSame =
                            argumentTypes.get(index).isSame(arguments.get(index).type());
                        if (!isSame) {
                          arguments
                              .get(index)
                              .typeError(
                                  argumentTypes.get(index).name(),
                                  arguments.get(index).type(),
                                  errorHandler);
                        }
                        return isSame;
                      })
                  .count()
              == argumentTypes.size();
    }
    return ok;
  }
}

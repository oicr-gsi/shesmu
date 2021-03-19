package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class FetchNodeFunction extends FetchNode {
  private final int line, column;
  private final String funcName;
  private final List<ExpressionNode> args;
  private FunctionDefinition funcDef = null;

  public FetchNodeFunction(
      String name, int line, int column, String funcName, List<ExpressionNode> args) {
    super(name);
    this.line = line;
    this.column = column;
    this.funcName = funcName;
    this.args = args;
  }

  @Override
  public String renderEcma(EcmaScriptRenderer r) {
    try {
      return String.format(
          "{type:\"function\", name:%s, args:%s}",
          RuntimeSupport.MAPPER.writeValueAsString(funcDef.name()),
          args.stream().map(a -> a.renderEcma(r)).collect(Collectors.joining(",", "[", "]")));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return args.stream().filter(a -> a.resolve(defs, errorHandler)).count() == args.size();
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    final var funcDef =
        nativeDefinitions.functions().filter(f -> f.name().equals(funcName)).findAny();
    if (funcDef.isPresent()) {
      this.funcDef = funcDef.get();
      final var argsCount = this.funcDef.parameters().count();
      final long argsSize = args.size();
      if (argsCount != argsSize) {
        errorHandler.accept(
            String.format(
                "%d:%d: Function %s takes %d arguments but %d arguments supplied.",
                line, column, funcName, argsCount, argsSize));
        return false;
      }
      return args.stream()
              .filter(a -> a.resolveDefinitions(expressionCompilerServices, errorHandler))
              .count()
          == args.size();
    } else {
      errorHandler.accept(String.format("%d:%d: Unknown function %s.", line, column, funcName));
      return false;
    }
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (args.stream().filter(a -> a.typeCheck(errorHandler)).count() == args.size()) {
      return funcDef
              .parameters()
              .filter(
                  new Predicate<>() {
                    private int index = 0;

                    @Override
                    public boolean test(FunctionParameter p) {
                      var argAtIndex = args.get(index++);
                      if (p.type().isAssignableFrom(argAtIndex.type())) {
                        return true;
                      } else {
                        argAtIndex.typeError(p.type(), argAtIndex.type(), errorHandler);
                        return false;
                      }
                    }
                  })
              .count()
          == args.size();
    }
    return false;
  }

  @Override
  public Flavour flavour() {
    return Flavour.LAMBDA;
  }

  @Override
  public void read() {
    // no
  }

  @Override
  public Imyhat type() {
    return funcDef.returnType();
  }
}

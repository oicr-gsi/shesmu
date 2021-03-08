package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.function.Consumer;

public class FetchNodeConstant extends FetchNode {

  private final String constantName;
  private final int line;
  private final int column;
  private Target target = Target.BAD;

  public FetchNodeConstant(int line, int column, String constantName) {
    this.line = line;
    this.column = column;
    this.constantName = constantName;
  }

  @Override
  public String renderEcma(EcmaScriptRenderer r) {
    try {
      return String.format(
          "{type:\"constant\",name:%s}",
          RuntimeSupport.MAPPER.writeValueAsString(target.unaliasedName()));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    var def = nativeDefinitions.constants().filter(c -> c.name().equals(constantName)).findAny();
    if (def.isPresent()) {
      target = def.get();
      return true;
    } else {
      errorHandler.accept(String.format("%d:%d: Unknown constant %s.", line, column, constantName));
      return false;
    }
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public Imyhat type() {
    return target.type();
  }
}

package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public class TypeAliasNode {

  public static Parser parse(Parser input, Consumer<TypeAliasNode> output) {
    final AtomicReference<String> name = new AtomicReference<>();
    final AtomicReference<ImyhatNode> type = new AtomicReference<>();
    final Parser result =
        input
            .whitespace()
            .keyword("TypeAlias")
            .whitespace()
            .identifier(name::set)
            .whitespace()
            .then(ImyhatNode::parse, type::set)
            .whitespace()
            .symbol(";")
            .whitespace();
    if (result.isGood()) {
      output.accept(new TypeAliasNode(input.line(), input.column(), name.get(), type.get()));
    }
    return result;
  }

  private final int column;
  private final int line;
  private final String name;
  private final ImyhatNode type;

  public TypeAliasNode(int line, int column, String name, ImyhatNode type) {
    super();
    this.line = line;
    this.column = column;
    this.name = name;
    this.type = type;
  }

  public String name() {
    return name;
  }

  public Imyhat resolve(
      Function<String, Imyhat> definedTypes,
      Function<String, FunctionDefinition> definedFunctions,
      Consumer<String> errorHandler) {
    if (ImyhatNode.isBaseType(name)) {
      errorHandler.accept(
          String.format("%d:%d: Attempt to redefine base type “%s”.", line, column, name));
      return Imyhat.BAD;
    }
    if (definedTypes.apply(name) != null) {
      errorHandler.accept(
          String.format(
              "%d:%d: Attempt to redefine type “%s” already defined as %s.",
              line, column, name, definedTypes.apply(name).name()));
      return Imyhat.BAD;
    }
    return type.render(definedTypes, definedFunctions, errorHandler);
  }
}

package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** A parameter to a “Define” olive */
public class OliveParameter implements Target {

  public static Parser parse(Parser parser, Consumer<OliveParameter> output) {
    final var type = new AtomicReference<ImyhatNode>();
    final var name = new AtomicReference<String>();
    final var result =
        parser
            .whitespace()
            .then(ImyhatNode::parse, type::set)
            .whitespace()
            .identifier(name::set)
            .whitespace();
    if (result.isGood()) {
      output.accept(new OliveParameter(name.get(), type.get()));
    }
    return result;
  }

  private final String name;
  private boolean read;
  private Imyhat realType;
  private final ImyhatNode type;

  public OliveParameter(String name, ImyhatNode type) {
    this.name = name;
    this.type = type;
  }

  @Override
  public Flavour flavour() {
    return Flavour.PARAMETER;
  }

  public boolean isRead() {
    return read;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public void read() {
    read = true;
  }

  public boolean resolveTypes(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    realType = type.render(expressionCompilerServices, errorHandler);
    return !realType.isBad();
  }

  @Override
  public Imyhat type() {
    return realType;
  }
}

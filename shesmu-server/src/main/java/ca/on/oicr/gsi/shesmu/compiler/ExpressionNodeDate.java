package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeDate extends ExpressionNode {

  private static final Type A_INSTANT_TYPE = Type.getType(Instant.class);

  private static final Method METHOD_OF_EPOCH_MILLI =
      new Method("ofEpochMilli", A_INSTANT_TYPE, new Type[] {Type.LONG_TYPE});

  private final Instant value;

  public ExpressionNodeDate(int line, int column, Instant value) {
    super(line, column);
    this.value = value;
  }

  public ExpressionNodeDate(int line, int column, ZonedDateTime value) {
    this(line, column, value.toInstant());
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    // Do nothing.
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    // Do nothing.
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    return String.format("new Date(%d)", value.toEpochMilli());
  }

  @Override
  public void render(Renderer renderer) {
    renderer.mark(line());

    renderer.methodGen().push(value.toEpochMilli());
    renderer.methodGen().invokeStatic(A_INSTANT_TYPE, METHOD_OF_EPOCH_MILLI);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public Imyhat type() {
    return Imyhat.DATE;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return true;
  }
}

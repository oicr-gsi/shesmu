package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ListImyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeListToTuple extends ExpressionNode {

  private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
  private static final Method METHOD_RUNTIME_SUPPORT__LIST_TO_TUPLE =
      new Method(
          "listToTuple",
          Type.getType(Optional.class),
          new Type[] {Type.INT_TYPE, Type.BOOLEAN_TYPE, Type.getType(Set.class)});
  private final boolean allowExtra;
  private final ExpressionNode inner;
  private final int size;
  private Imyhat type = Imyhat.BAD;

  public ExpressionNodeListToTuple(
      int line, int column, int size, boolean allowExtra, ExpressionNode inner) {
    super(line, column);
    this.size = size;
    this.allowExtra = allowExtra;
    this.inner = inner;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    inner.collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    inner.collectPlugins(pluginFileNames);
  }

  @Override
  public void render(Renderer renderer) {
    renderer.methodGen().push(size);
    renderer.methodGen().push(allowExtra);
    inner.render(renderer);
    renderer
        .methodGen()
        .invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_RUNTIME_SUPPORT__LIST_TO_TUPLE);
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    return String.format(
        "$runtime.listToTuple(%d, %s, %s)", size, allowExtra, inner.renderEcma(renderer));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return inner.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return inner.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return type;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (inner.typeCheck(errorHandler)) {
      if (inner.type() instanceof ListImyhat) {
        final var innerType = ((ListImyhat) inner.type()).inner();
        type =
            Imyhat.tuple(Collections.nCopies(size, innerType).toArray(Imyhat[]::new)).asOptional();
        return true;
      } else {
        inner.typeError("list", inner.type(), errorHandler);
      }
    }
    return false;
  }
}

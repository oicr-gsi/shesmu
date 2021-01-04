package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class SourceNodeRange extends SourceNode {

  private static final Type A_LONG_STREAM_TYPE = Type.getType(LongStream.class);

  private static final Method METHOD_LONG_STREAM__BOXED =
      new Method("boxed", Type.getType(Stream.class), new Type[] {});
  private static final Method METHOD_LONG_STREAM__RANGE =
      new Method("range", A_LONG_STREAM_TYPE, new Type[] {Type.LONG_TYPE, Type.LONG_TYPE});
  private final ExpressionNode end;
  private final ExpressionNode start;

  public SourceNodeRange(int line, int column, ExpressionNode start, ExpressionNode end) {
    super(line, column);
    this.start = start;
    this.end = end;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    start.collectFreeVariables(names, predicate);
    end.collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    start.collectPlugins(pluginFileNames);
    end.collectPlugins(pluginFileNames);
  }

  @Override
  public Ordering ordering() {
    return Ordering.REQESTED;
  }

  @Override
  public JavaStreamBuilder render(Renderer renderer) {
    start.render(renderer);
    end.render(renderer);
    renderer.invokeInterfaceStatic(A_LONG_STREAM_TYPE, METHOD_LONG_STREAM__RANGE);
    renderer.methodGen().invokeInterface(A_LONG_STREAM_TYPE, METHOD_LONG_STREAM__BOXED);
    return renderer.buildStream(Imyhat.INTEGER);
  }

  @Override
  public EcmaStreamBuilder render(EcmaScriptRenderer renderer) {
    return renderer.buildStream(Imyhat.INTEGER, String.format("$runtime.range(%s, %s)", start.renderEcma(renderer), end.renderEcma(renderer)));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return start.resolve(defs, errorHandler) & end.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return start.resolveDefinitions(expressionCompilerServices, errorHandler)
        & end.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat streamType() {
    return Imyhat.INTEGER;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = start.typeCheck(errorHandler) & end.typeCheck(errorHandler);
    if (ok) {
      if (!start.type().isSame(Imyhat.INTEGER)) {
        start.typeError(Imyhat.INTEGER, start.type(), errorHandler);
        ok = false;
      }
      if (!end.type().isSame(Imyhat.INTEGER)) {
        end.typeError(Imyhat.INTEGER, end.type(), errorHandler);
        ok = false;
      }
    }
    return ok;
  }
}

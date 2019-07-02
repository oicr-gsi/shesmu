package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class SourceNodeSet extends SourceNode {

  private static final Type A_SET_TYPE = Type.getType(Set.class);
  private static final Type A_STREAM_TYPE = Type.getType(Stream.class);

  private static final Method METHOD_SET__STREAM =
      new Method("stream", A_STREAM_TYPE, new Type[] {});
  private final ExpressionNode expression;
  private Imyhat initialType;

  public SourceNodeSet(int line, int column, ExpressionNode expression) {
    super(line, column);
    this.expression = expression;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    expression.collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  @Override
  public Ordering ordering() {
    return Ordering.RANDOM;
  }

  @Override
  public JavaStreamBuilder render(Renderer renderer) {
    expression.render(renderer);
    renderer.methodGen().invokeInterface(A_SET_TYPE, METHOD_SET__STREAM);
    final JavaStreamBuilder builder = renderer.buildStream(initialType);
    return builder;
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return expression.resolveFunctions(definedFunctions, errorHandler);
  }

  @Override
  public Imyhat streamType() {
    return initialType;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (!expression.typeCheck(errorHandler)) {
      return false;
    }
    final Imyhat type = expression.type();
    if (type instanceof Imyhat.ListImyhat) {
      initialType = ((Imyhat.ListImyhat) type).inner();
      return true;
    } else {
      expression.typeError("list", type, errorHandler);
      return false;
    }
  }
}

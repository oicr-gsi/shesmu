package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class SourceNodeSplit extends SourceNode {

  private static final Type A_PATTERN_TYPE = Type.getType(Pattern.class);
  private static final Type A_STREAM_TYPE = Type.getType(Stream.class);
  private static final Method METHOD_PATTERN__SPLIT_AS_STREAM =
      new Method("splitAsStream", A_STREAM_TYPE, new Type[] {Type.getType(CharSequence.class)});
  private final ExpressionNode expression;

  private final String regex;

  public SourceNodeSplit(int line, int column, String regex, ExpressionNode expression) {
    super(line, column);
    this.regex = regex;
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
    return Ordering.REQESTED;
  }

  @Override
  public JavaStreamBuilder render(Renderer renderer) {
    renderer.regex(regex);
    expression.render(renderer);
    renderer.methodGen().invokeVirtual(A_PATTERN_TYPE, METHOD_PATTERN__SPLIT_AS_STREAM);
    return renderer.buildStream(Imyhat.STRING);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat streamType() {
    return Imyhat.STRING;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (!expression.typeCheck(errorHandler)) {
      return false;
    }
    if (expression.type().isSame(Imyhat.STRING)) {
      return true;
    }
    expression.typeError(Imyhat.STRING.name(), expression.type(), errorHandler);
    return false;
  }
}

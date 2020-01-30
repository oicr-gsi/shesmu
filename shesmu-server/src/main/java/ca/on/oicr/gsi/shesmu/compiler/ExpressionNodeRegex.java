package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeRegex extends ExpressionNode {

  private static final Type A_MATCHER_TYPE = Type.getType(Matcher.class);
  private static final Type A_PATTERN_TYPE = Type.getType(Pattern.class);

  private static final Method METHOD_MATCHER__MATCHES =
      new Method("matches", Type.BOOLEAN_TYPE, new Type[] {});

  private static final Method METHOD_PATTERN__MATCHER =
      new Method("matcher", A_MATCHER_TYPE, new Type[] {Type.getType(CharSequence.class)});

  private final ExpressionNode expression;

  private final String regex;

  public ExpressionNodeRegex(int line, int column, ExpressionNode expression, String regex) {
    super(line, column);
    this.expression = expression;
    this.regex = regex;
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
  public void render(Renderer renderer) {
    renderer.regex(regex);
    expression.render(renderer);
    renderer.methodGen().invokeVirtual(A_PATTERN_TYPE, METHOD_PATTERN__MATCHER);
    renderer.methodGen().invokeVirtual(A_MATCHER_TYPE, METHOD_MATCHER__MATCHES);
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
  public Imyhat type() {
    return Imyhat.BOOLEAN;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean patternOk = true;
    try {
      Pattern.compile(regex);
    } catch (PatternSyntaxException e) {
      errorHandler.accept(
          String.format("%d:%d: %s", line(), column(), e.getMessage().split("\n")[0]));
      patternOk = false;
    }
    final boolean ok = expression.typeCheck(errorHandler);
    if (ok) {
      if (expression.type().isSame(Imyhat.STRING)) {
        return patternOk;
      }
      typeError(Imyhat.STRING, expression.type(), errorHandler);
      return false;
    }
    return ok;
  }
}

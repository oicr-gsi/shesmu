package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeRegexBinding extends ExpressionNode {

  private static final Type A_MATCHER_TYPE = Type.getType(Matcher.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  private static final Type A_PATTERN_TYPE = Type.getType(Pattern.class);
  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
  private static final Method CTOR_TUPLE =
      new Method("<init>", Type.VOID_TYPE, new Type[] {Type.getType(Object[].class)});
  private static final Method METHOD_MATCHER__GROUP =
      new Method("group", Type.getType(String.class), new Type[] {Type.INT_TYPE});
  private static final Method METHOD_MATCHER__MATCHES =
      new Method("matches", Type.BOOLEAN_TYPE, new Type[] {});
  private static final Method METHOD_OPTIONAL__EMPTY =
      new Method("empty", A_OPTIONAL_TYPE, new Type[] {});
  private static final Method METHOD_OPTIONAL__OF =
      new Method("of", A_OPTIONAL_TYPE, new Type[] {A_OBJECT_TYPE});
  private static final Method METHOD_OPTIONAL__OF_NULLABLE =
      new Method("ofNullable", A_OPTIONAL_TYPE, new Type[] {A_OBJECT_TYPE});
  private static final Method METHOD_PATTERN__MATCHER =
      new Method("matcher", A_MATCHER_TYPE, new Type[] {Type.getType(CharSequence.class)});
  int captureCount = 0;
  private final ExpressionNode expression;
  private final int flags;
  private final String regex;

  public ExpressionNodeRegexBinding(
      int line, int column, ExpressionNode expression, String regex, int flags) {
    super(line, column);
    this.expression = expression;
    this.regex = regex;
    this.flags = flags;
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
  public String renderEcma(EcmaScriptRenderer renderer) {
    return String.format(
        "$runtime.regexBind(/%s/%s, %s, %d)",
        regex,
        EcmaScriptRenderer.regexFlagsToString(flags),
        expression.renderEcma(renderer),
        captureCount);
  }

  @Override
  public void render(Renderer renderer) {
    renderer.mark(line());

    // Match the regex
    renderer.regex(regex, flags);
    expression.render(renderer);
    renderer.methodGen().invokeVirtual(A_PATTERN_TYPE, METHOD_PATTERN__MATCHER);
    renderer.methodGen().dup();

    // Check if it matches
    renderer.methodGen().invokeVirtual(A_MATCHER_TYPE, METHOD_MATCHER__MATCHES);

    final var empty = renderer.methodGen().newLabel();
    final var end = renderer.methodGen().newLabel();
    renderer.methodGen().ifZCmp(GeneratorAdapter.EQ, empty);

    // If it matches, convert the capture groups
    final var matcher = renderer.methodGen().newLocal(A_MATCHER_TYPE);
    renderer.methodGen().storeLocal(matcher);

    renderer.methodGen().newInstance(A_TUPLE_TYPE);
    renderer.methodGen().dup();
    renderer.methodGen().push(captureCount);
    renderer.methodGen().newArray(A_OBJECT_TYPE);
    for (var i = 0; i < captureCount; i++) {
      renderer.methodGen().dup();
      renderer.methodGen().push(i);
      renderer.methodGen().loadLocal(matcher);
      renderer.methodGen().push(i + 1);
      renderer.methodGen().invokeVirtual(A_MATCHER_TYPE, METHOD_MATCHER__GROUP);
      renderer.methodGen().invokeStatic(A_OPTIONAL_TYPE, METHOD_OPTIONAL__OF_NULLABLE);
      renderer.methodGen().arrayStore(A_OBJECT_TYPE);
    }
    renderer.methodGen().invokeConstructor(A_TUPLE_TYPE, CTOR_TUPLE);
    renderer.methodGen().invokeStatic(A_OPTIONAL_TYPE, METHOD_OPTIONAL__OF);
    renderer.methodGen().goTo(end);

    // Otherwise, empty an empty optional
    renderer.methodGen().mark(empty);
    renderer.methodGen().pop();
    renderer.methodGen().invokeStatic(A_OPTIONAL_TYPE, METHOD_OPTIONAL__EMPTY);
    renderer.methodGen().mark(end);
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
    return captureCount > 0
        ? Imyhat.tuple(
                Collections.nCopies(captureCount, Imyhat.STRING.asOptional())
                    .toArray(Imyhat[]::new))
            .asOptional()
        : Imyhat.BAD;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    var patternOk = true;
    try {
      captureCount = Pattern.compile(regex).matcher("").groupCount();
      if (captureCount == 0) {
        errorHandler.accept(String.format("%d:%d: No capture groups found.", line(), column()));
        patternOk = false;
      }
    } catch (PatternSyntaxException e) {
      errorHandler.accept(
          String.format("%d:%d: %s", line(), column(), e.getMessage().split("\n")[0]));
      patternOk = false;
    }

    final var ok = expression.typeCheck(errorHandler);
    if (ok) {
      if (expression.type().isSame(Imyhat.STRING)) {
        return patternOk;
      }
      typeError(Imyhat.STRING, expression.type(), errorHandler);
    }
    return false;
  }
}

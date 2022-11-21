package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.TupleImyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeOrder extends ExpressionNode {
  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
  private static final Method METHOD_TUPLE__ORDER =
      new Method("order", A_TUPLE_TYPE, new Type[] {Type.getType(Imyhat.class)});
  private final ExpressionNode expression;
  private Imyhat type = Imyhat.BAD;

  public ExpressionNodeOrder(int line, int column, ExpressionNode expression) {
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
  public void render(Renderer renderer) {
    expression.render(renderer);
    renderer.loadImyhat(type.descriptor());
    renderer.methodGen().invokeVirtual(A_TUPLE_TYPE, METHOD_TUPLE__ORDER);
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    final var value = renderer.newConst(String.format("[...%s]", expression.renderEcma(renderer)));
    renderer.statement(
        String.format(
            "%s.sort(%s)",
            value, type.isSame(Imyhat.STRING) ? "comparatorString" : "comparatorNumeric"));
    return value;
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
    return expression.type();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (expression.typeCheck(errorHandler)) {
      if (expression.type() instanceof TupleImyhat) {
        final var tupleType = (TupleImyhat) expression.type();
        type = tupleType.get(0);
        for (var i = 1; i < tupleType.count(); i++) {
          if (type.isSame(tupleType.get(i))) {
            type = type.unify(tupleType.get(i));
          } else {
            this.typeError(type, tupleType.get(i), errorHandler);
            return false;
          }
        }
        if (type.isOrderable()) {
          return true;
        } else {
          this.typeError("orderable type", type, errorHandler);
          return false;
        }
      } else {
        this.typeError("tuple", expression.type(), errorHandler);
        return false;
      }
    } else {
      return false;
    }
  }
}

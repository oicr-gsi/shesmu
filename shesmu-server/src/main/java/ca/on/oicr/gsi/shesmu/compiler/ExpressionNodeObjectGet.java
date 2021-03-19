package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeObjectGet extends ExpressionNode {

  private static final Type A_FUNCTION_TYPE = Type.getType(Function.class);
  private static final Type A_JSON_NODE_TYPE = Type.getType(JsonNode.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
  private static final Method METHOD_OPTIONAL__FLAT_MAP =
      new Method("flatMap", A_OPTIONAL_TYPE, new Type[] {A_FUNCTION_TYPE});
  private static final Method METHOD_OPTIONAL__MAP =
      new Method("map", A_OPTIONAL_TYPE, new Type[] {A_FUNCTION_TYPE});
  private static final Method METHOD_RUNTIME_SUPPORT_GET_JSON =
      new Method(
          "getJson", A_JSON_NODE_TYPE, new Type[] {A_JSON_NODE_TYPE, Type.getType(String.class)});
  private static final Method METHOD_TUPLE__GET =
      new Method("get", A_OBJECT_TYPE, new Type[] {Type.INT_TYPE});
  private final ExpressionNode expression;

  private final String field;

  private int index = -1;
  private boolean lifted;
  private Imyhat type = Imyhat.BAD;

  public ExpressionNodeObjectGet(int line, int column, ExpressionNode expression, String field) {
    super(line, column);
    this.expression = expression;
    this.field = field;
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
    if (lifted) {
      return String.format(
          "$runtime.mapNull(%s, v => v.%s)", expression.renderEcma(renderer), field);
    } else {
      return expression.renderEcma(renderer) + "." + field;
    }
  }

  @Override
  public Optional<String> dumpColumnName() {
    return expression.dumpColumnName().map(s -> s + "." + field);
  }

  @Override
  public void render(Renderer renderer) {
    expression.render(renderer);
    renderer.mark(line());
    if (lifted) {
      final LambdaBuilder.LambdaType lambdaType;
      if (index == -1) {
        lambdaType = LambdaBuilder.function(A_JSON_NODE_TYPE, A_JSON_NODE_TYPE);
      } else {
        lambdaType = LambdaBuilder.function(A_OBJECT_TYPE, A_TUPLE_TYPE);
      }
      final var lambda =
          new LambdaBuilder(
              renderer.root(),
              String.format("Optional Getter %d:%d %s", line(), column(), field),
              lambdaType);
      final var method = lambda.methodGen();
      method.visitCode();
      method.loadArg(0);
      renderLoad(method);
      method.returnValue();
      method.endMethod();
      lambda.push(renderer);
      if (type instanceof Imyhat.OptionalImyhat || type == Imyhat.NOTHING) {
        renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__FLAT_MAP);
      } else {
        renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__MAP);
      }

    } else {
      renderLoad(renderer.methodGen());
      renderer.methodGen().unbox(type.apply(TypeUtils.TO_ASM));
    }
  }

  private void renderLoad(GeneratorAdapter method) {
    if (index == -1) {
      method.push(field);
      method.invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_RUNTIME_SUPPORT_GET_JSON);
    } else {
      method.push(index);
      method.invokeVirtual(A_TUPLE_TYPE, METHOD_TUPLE__GET);
    }
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
    return lifted ? type.asOptional() : type;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    var ok = expression.typeCheck(errorHandler);
    if (ok) {
      var expressionType = expression.type();
      if (expressionType instanceof Imyhat.OptionalImyhat) {
        lifted = true;
        expressionType = ((Imyhat.OptionalImyhat) expressionType).inner();
      }
      if (expressionType instanceof Imyhat.ObjectImyhat) {
        final var objectType = (Imyhat.ObjectImyhat) expressionType;
        type = objectType.get(field);
        if (type.isBad()) {
          errorHandler.accept(
              String.format("%d:%d: Cannot access field at %s.", line(), column(), field));
          ok = false;
        } else {
          index = objectType.index(field);
        }
      } else if (expressionType.isSame(Imyhat.JSON)) {
        type = Imyhat.JSON;
      } else {
        ok = false;
        typeError("object or json", expressionType, errorHandler);
      }
    }
    return ok;
  }
}

package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class SourceNodeContainer extends SourceNode {
  private enum Mode {
    LIST {
      @Override
      public void render(Renderer renderer) {
        renderer.methodGen().invokeInterface(A_SET_TYPE, METHOD_SET__STREAM);
      }
    },
    OPTIONAL {
      @Override
      public void render(Renderer renderer) {
        renderer.methodGen().invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_RUNTIME_SUPPORT__STREAM);
      }
    },
    JSON {
      @Override
      public void render(Renderer renderer) {
        renderer
            .methodGen()
            .invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_RUNTIME_SUPPORT__JSON_ELEMENTS);
      }
    };

    public abstract void render(Renderer renderer);
  }

  private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
  private static final Type A_SET_TYPE = Type.getType(Set.class);
  private static final Type A_STREAM_TYPE = Type.getType(Stream.class);
  private static final Method METHOD_RUNTIME_SUPPORT__STREAM =
      new Method("stream", A_STREAM_TYPE, new Type[] {Type.getType(Optional.class)});
  private static final Method METHOD_RUNTIME_SUPPORT__JSON_ELEMENTS =
      new Method("jsonElements", A_STREAM_TYPE, new Type[] {Type.getType(JsonNode.class)});

  private static final Method METHOD_SET__STREAM =
      new Method("stream", A_STREAM_TYPE, new Type[] {});
  private final ExpressionNode expression;
  private Imyhat initialType;
  private Mode mode;

  public SourceNodeContainer(int line, int column, ExpressionNode expression) {
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
    mode.render(renderer);
    final JavaStreamBuilder builder = renderer.buildStream(initialType);
    return builder;
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
    return initialType;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (!expression.typeCheck(errorHandler)) {
      return false;
    }
    final Imyhat type = expression.type();
    if (type == Imyhat.EMPTY) {
      errorHandler.accept(
          String.format(
              "%d:%d: Cannot iterate over empty list. No type to check subsequent operations.",
              line(), column()));
      return false;
    }
    if (type instanceof Imyhat.ListImyhat) {
      initialType = ((Imyhat.ListImyhat) type).inner();
      mode = Mode.LIST;
      return true;
    } else if (type instanceof Imyhat.OptionalImyhat) {
      initialType = ((Imyhat.OptionalImyhat) type).inner();
      mode = Mode.OPTIONAL;
      return true;
    } else if (type.isSame(Imyhat.JSON)) {
      initialType = Imyhat.JSON;
      mode = Mode.JSON;
      return true;
    } else {
      expression.typeError("list or json or optional", type, errorHandler);
      return false;
    }
  }
}

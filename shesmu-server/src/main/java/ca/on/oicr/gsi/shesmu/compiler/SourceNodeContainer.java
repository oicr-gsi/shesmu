package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
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

      @Override
      public String render(EcmaScriptRenderer renderer, ExpressionNode expression) {
        return expression.renderEcma(renderer);
      }
    },
    LIFTED_LIST {
      @Override
      public void render(Renderer renderer) {
        renderer.methodGen().invokeStatic(A_COLLECTIONS_TYPE, METHOD_COLLECTIONS__EMPTY_SET);
        renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__OR_ELSE);
        renderer.methodGen().unbox(A_SET_TYPE);
        renderer.methodGen().invokeInterface(A_SET_TYPE, METHOD_SET__STREAM);
      }

      @Override
      public String render(EcmaScriptRenderer renderer, ExpressionNode expression) {
        return String.format(
            "$runtime.mapNullOrDefault(%s, $v => $v, [])", expression.renderEcma(renderer));
      }
    },
    MAP {
      @Override
      public void render(Renderer renderer) {
        renderer
            .methodGen()
            .invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_RUNTIME_SUPPORT__STREAM_MAP);
      }

      @Override
      public String render(EcmaScriptRenderer renderer, ExpressionNode expression) {
        return String.format("$runtime.dictIterator(%s)", expression.renderEcma(renderer));
      }
    },
    LIFTED_MAP {
      @Override
      public void render(Renderer renderer) {
        renderer
            .methodGen()
            .invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_RUNTIME_SUPPORT__STREAM_MAP_OPTIONAL);
      }

      @Override
      public String render(EcmaScriptRenderer renderer, ExpressionNode expression) {
        return String.format(
            "$runtime.mapNullOrDefault(%s, $v => $runtime.dictIterator($v), [])",
            expression.renderEcma(renderer));
      }
    },
    OPTIONAL {
      @Override
      public void render(Renderer renderer) {
        renderer
            .methodGen()
            .invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_RUNTIME_SUPPORT__STREAM_OPTIONAL);
      }

      @Override
      public String render(EcmaScriptRenderer renderer, ExpressionNode expression) {
        return String.format(
            "$runtime.mapNullOrDefault(%s, $v => [$v], [])", expression.renderEcma(renderer));
      }
    },
    JSON {
      @Override
      public void render(Renderer renderer) {
        renderer
            .methodGen()
            .invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_RUNTIME_SUPPORT__JSON_ELEMENTS);
      }

      @Override
      public String render(EcmaScriptRenderer renderer, ExpressionNode expression) {
        return String.format("$runtime.arrayFromJson(%s)", expression.renderEcma(renderer));
      }
    },
    LIFTED_JSON {
      @Override
      public void render(Renderer renderer) {
        renderer
            .methodGen()
            .invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_RUNTIME_SUPPORT__JSON_ELEMENTS_OPTIONAL);
      }

      @Override
      public String render(EcmaScriptRenderer renderer, ExpressionNode expression) {
        return String.format(
            "$runtime.mapNullOrDefault(%s, $v => $runtime.arrayFromJson($v), [])",
            expression.renderEcma(renderer));
      }
    };

    public abstract void render(Renderer renderer);

    public abstract String render(EcmaScriptRenderer renderer, ExpressionNode expression);
  }

  private static final Type A_COLLECTIONS_TYPE = Type.getType(Collections.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
  private static final Type A_SET_TYPE = Type.getType(Set.class);
  private static final Type A_STREAM_TYPE = Type.getType(Stream.class);
  private static final Method METHOD_COLLECTIONS__EMPTY_SET =
      new Method("emptySet", A_SET_TYPE, new Type[] {});
  private static final Method METHOD_OPTIONAL__OR_ELSE =
      new Method("orElse", A_OBJECT_TYPE, new Type[] {A_OBJECT_TYPE});
  private static final Method METHOD_RUNTIME_SUPPORT__JSON_ELEMENTS =
      new Method("jsonElements", A_STREAM_TYPE, new Type[] {Type.getType(JsonNode.class)});
  private static final Method METHOD_RUNTIME_SUPPORT__JSON_ELEMENTS_OPTIONAL =
      new Method("jsonElements", A_STREAM_TYPE, new Type[] {A_OPTIONAL_TYPE});
  private static final Method METHOD_RUNTIME_SUPPORT__STREAM_MAP =
      new Method("stream", A_STREAM_TYPE, new Type[] {Type.getType(Map.class)});
  private static final Method METHOD_RUNTIME_SUPPORT__STREAM_MAP_OPTIONAL =
      new Method("streamMap", A_STREAM_TYPE, new Type[] {A_OPTIONAL_TYPE});
  private static final Method METHOD_RUNTIME_SUPPORT__STREAM_OPTIONAL =
      new Method("stream", A_STREAM_TYPE, new Type[] {A_OPTIONAL_TYPE});
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
  public EcmaStreamBuilder render(EcmaScriptRenderer renderer) {
    return renderer.buildStream(initialType, mode.render(renderer, expression));
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
    Imyhat type = expression.type();
    if (type == Imyhat.EMPTY) {
      errorHandler.accept(
          String.format(
              "%d:%d: Cannot iterate over empty list. No type to check subsequent operations.",
              line(), column()));
      return false;
    }
    if (type == Imyhat.NOTHING) {
      errorHandler.accept(
          String.format(
              "%d:%d: Cannot iterate over empty optional. No type to check subsequent operations.",
              line(), column()));
      return false;
    }
    final boolean lifted;
    if (type instanceof Imyhat.OptionalImyhat) {
      type = ((Imyhat.OptionalImyhat) type).inner();
      lifted = true;
      if (type == Imyhat.EMPTY) {
        errorHandler.accept(
            String.format(
                "%d:%d: Cannot iterate over empty list. No type to check subsequent operations.",
                line(), column()));
        return false;
      }
    } else {
      lifted = false;
    }
    if (type instanceof Imyhat.ListImyhat) {
      initialType = ((Imyhat.ListImyhat) type).inner();
      mode = lifted ? Mode.LIFTED_LIST : Mode.LIST;
      return true;
    } else if (type instanceof Imyhat.DictionaryImyhat) {
      final Imyhat.DictionaryImyhat inner = ((Imyhat.DictionaryImyhat) type);
      initialType = Imyhat.tuple(inner.key(), inner.value());
      mode = lifted ? Mode.LIFTED_MAP : Mode.MAP;
      return true;
    } else if (type.isSame(Imyhat.JSON)) {
      initialType = Imyhat.JSON;
      mode = lifted ? Mode.LIFTED_JSON : Mode.JSON;
      return true;
    } else if (lifted) {
      initialType = type;
      mode = Mode.OPTIONAL;
      return true;

    } else {
      expression.typeError("list or json or map or optional", type, errorHandler);
      return false;
    }
  }
}

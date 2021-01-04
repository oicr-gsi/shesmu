package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.JsonWrapper;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class DestructuredArgumentNodeConvertedVariable extends DestructuredArgumentNode {
  private abstract class BaseConvertedValue extends LoadableValue {
    private final Consumer<Renderer> loader;

    public BaseConvertedValue(Consumer<Renderer> loader) {
      this.loader = loader;
    }

    @Override
    public final void accept(Renderer renderer) {
      prepare(renderer);
      loader.accept(renderer);
      convert(renderer);
    }

    protected abstract void convert(Renderer renderer);

    @Override
    public final String name() {
      return name;
    }

    protected void prepare(Renderer renderer) {}

    @Override
    public final Type type() {
      return convertedType.apply(TO_ASM);
    }
  }

  private class EcmaStringConverter extends EcmaLoadableValue {

    private final Function<EcmaScriptRenderer, String> loader;

    public EcmaStringConverter(Function<EcmaScriptRenderer, String> loader) {
      this.loader = loader;
    }

    @Override
    public String apply(EcmaScriptRenderer renderer) {
      return loader.apply(renderer) + ".toString()";
    }

    @Override
    public String name() {
      return name;
    }
  }

  private class FloatToStringConvertedValue extends BaseConvertedValue {

    public FloatToStringConvertedValue(Consumer<Renderer> loader) {
      super(loader);
    }

    @Override
    protected void convert(Renderer renderer) {
      renderer.methodGen().invokeStatic(A_DOUBLE_TYPE, METHOD_DOUBLE__TO_STRING);
    }
  }

  private class IntegerToStringConvertedValue extends BaseConvertedValue {

    public IntegerToStringConvertedValue(Consumer<Renderer> loader) {
      super(loader);
    }

    @Override
    protected void convert(Renderer renderer) {
      renderer.methodGen().invokeStatic(A_LONG_TYPE, METHOD_LONG__TO_STRING);
    }
  }

  private class JsonConvertedValue extends BaseConvertedValue {

    public JsonConvertedValue(Consumer<Renderer> loader) {
      super(loader);
    }

    @Override
    protected void convert(Renderer renderer) {
      renderer.methodGen().valueOf(type.apply(TO_ASM));
      renderer.methodGen().invokeStatic(A_JSON_WRAPPER_TYPE, JSON_CONVERTER__CONVERT);
    }

    @Override
    protected void prepare(Renderer renderer) {
      renderer.loadImyhat(type.descriptor());
    }
  }

  private class JsonToStringConvertedValue extends BaseConvertedValue {

    public JsonToStringConvertedValue(Consumer<Renderer> loader) {
      super(loader);
    }

    @Override
    protected void convert(Renderer renderer) {
      renderer.methodGen().getStatic(A_RUNTIME_SUPPORT_TYPE, "MAPPER", A_OBJECT_MAPPER_TYPE);
      renderer.methodGen().swap();
      renderer
          .methodGen()
          .invokeVirtual(A_OBJECT_MAPPER_TYPE, METHOD_OBJECT_MAPPER__WRITE_VALUE_AS_STRING);
    }
  }

  private class ObjectToStringConvertedValue extends BaseConvertedValue {

    public ObjectToStringConvertedValue(Consumer<Renderer> loader) {
      super(loader);
    }

    @Override
    protected void convert(Renderer renderer) {
      renderer.methodGen().invokeVirtual(A_OBJECT_TYPE, METHOD_OBJECT__TO_STRING);
    }
  }

  private static final Type A_DOUBLE_TYPE = Type.getType(Double.class);
  private static final Type A_JSON_WRAPPER_TYPE = Type.getType(JsonWrapper.class);
  private static final Type A_LONG_TYPE = Type.getType(Long.class);
  private static final Type A_OBJECT_MAPPER_TYPE = Type.getType(ObjectMapper.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
  private static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final Method JSON_CONVERTER__CONVERT =
      new Method(
          "convert",
          Type.getType(JsonNode.class),
          new Type[] {Type.getType(Imyhat.class), Type.getType(Object.class)});
  private static final Method METHOD_DOUBLE__TO_STRING =
      new Method("toString", A_STRING_TYPE, new Type[] {Type.DOUBLE_TYPE});
  private static final Method METHOD_LONG__TO_STRING =
      new Method("toString", A_STRING_TYPE, new Type[] {Type.LONG_TYPE});
  private static final Method METHOD_OBJECT_MAPPER__WRITE_VALUE_AS_STRING =
      new Method("writeValueAsString", A_STRING_TYPE, new Type[] {A_OBJECT_TYPE});
  private static final Method METHOD_OBJECT__TO_STRING =
      new Method("toString", A_STRING_TYPE, new Type[] {});
  private final int column;
  private Imyhat convertedType = Imyhat.BAD;
  private Target.Flavour flavour;
  private final int line;
  private final String name;
  private boolean read;
  private final DefinedTarget target =
      new DefinedTarget() {
        @Override
        public int column() {
          return column;
        }

        @Override
        public int line() {
          return line;
        }

        @Override
        public Flavour flavour() {
          return flavour;
        }

        @Override
        public String name() {
          return name;
        }

        @Override
        public void read() {
          read = true;
        }

        @Override
        public Imyhat type() {
          return convertedType;
        }
      };
  private Imyhat type = Imyhat.BAD;
  private final ImyhatNode typeNode;

  public DestructuredArgumentNodeConvertedVariable(
      int line, int column, String name, ImyhatNode typeNode) {
    this.name = name;
    this.typeNode = typeNode;
    this.line = line;
    this.column = column;
  }

  @Override
  public boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
    if (read) {
      return true;
    } else {
      errorHandler.accept(String.format("%d:%d: Variable “%s” is never used.", line, column, name));
      return false;
    }
  }

  @Override
  public WildcardCheck checkWildcard(Consumer<String> errorHandler) {
    return WildcardCheck.NONE;
  }

  @Override
  public boolean isBlank() {
    return false;
  }

  @Override
  public Stream<LoadableValue> render(Consumer<Renderer> loader) {
    if (convertedType.isSame(Imyhat.JSON)) {
      return Stream.of(new JsonConvertedValue(loader));
    }
    if (convertedType.isSame(Imyhat.STRING)) {
      if (type.isSame(Imyhat.INTEGER)) {
        return Stream.of(new IntegerToStringConvertedValue(loader));
      }
      if (type.isSame(Imyhat.FLOAT)) {
        return Stream.of(new FloatToStringConvertedValue(loader));
      }
      if (type.isSame(Imyhat.JSON)) {
        return Stream.of(new JsonToStringConvertedValue(loader));
      }
      return Stream.of(new ObjectToStringConvertedValue(loader));
    }
    throw new IllegalStateException(
        "Trying to write bytecode for a type situation that should have been rejected");
  }

  @Override
  public Stream<EcmaLoadableValue> renderEcma(Function<EcmaScriptRenderer, String> loader) {
    if (convertedType.isSame(Imyhat.JSON)) {
      return Stream.of(
          new EcmaLoadableValue() {
            @Override
            public String apply(EcmaScriptRenderer renderer) {
              return loader.apply(renderer);
            }

            @Override
            public String name() {
              return name;
            }
          });
    }
    if (convertedType.isSame(Imyhat.STRING)) {
      if (type.isSame(Imyhat.INTEGER)) {
        return Stream.of(new EcmaStringConverter(loader));
      }
      if (type.isSame(Imyhat.FLOAT)) {
        return Stream.of(new EcmaStringConverter(loader));
      }
      if (type.isSame(Imyhat.JSON)) {
        return Stream.of(
            new EcmaLoadableValue() {
              @Override
              public String apply(EcmaScriptRenderer renderer) {
                return String.format("JSON.toString(%s)", loader.apply(renderer));
              }

              @Override
              public String name() {
                return name;
              }
            });
      }
      return Stream.of(new EcmaStringConverter(loader));
    }
    throw new IllegalStateException(
        "Trying to write ECMAScript for a type situation that should have been rejected");
  }

  @Override
  public boolean resolve(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    convertedType = typeNode.render(expressionCompilerServices, errorHandler);
    return !convertedType.isSame(Imyhat.BAD);
  }

  @Override
  public void setFlavour(Target.Flavour flavour) {
    this.flavour = flavour;
  }

  @Override
  public Stream<DefinedTarget> targets() {
    return Stream.of(target);
  }

  @Override
  public boolean typeCheck(Imyhat type, Consumer<String> errorHandler) {
    this.type = type;
    if (convertedType.isSame(Imyhat.JSON)) {
      return true;
    }
    if (convertedType.isSame(Imyhat.STRING) && StringNodeExpression.canBeConverted(type)) {
      return true;
    }
    errorHandler.accept(
        String.format("%d:%d: Cannot convert %s to %s.", line, column, type, convertedType));
    return false;
  }
}

package ca.on.oicr.gsi.shesmu.compiler.definitions;

import ca.on.oicr.gsi.shesmu.compiler.LoadableValue;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import ca.on.oicr.gsi.shesmu.compiler.Target;
import ca.on.oicr.gsi.shesmu.compiler.TypeUtils;
import ca.on.oicr.gsi.shesmu.plugin.json.PackJsonObject;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatConsumer;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.server.BaseHotloadingCompiler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
 * A constant value that can be injected into a Shesmu program
 *
 * <p>Constant values get written into the program, so they are not updated until the program is
 * recompiled even if the definition is changed.
 *
 * <p>They aren't constant in the sense that they can be arbitrary bytecode, so <tt>now</tt> is
 * considered a constant even though it varies. All that matters is that it has no direct
 * interaction with any other part of the Shesmu script.
 */
public abstract class ConstantDefinition implements Target {

  /** Write the value of a constant into the <tt>value</tt> property of a JSON object. */
  public interface ConstantLoader {
    @RuntimeInterop
    void load(ObjectNode target);
  }

  public static class AliasedConstantDefinition extends ConstantDefinition {
    private final ConstantDefinition original;

    public AliasedConstantDefinition(ConstantDefinition original, String alias) {
      super(alias, original.type(), original.description(), original.filename());
      this.original = original;
    }

    @Override
    public void load(GeneratorAdapter methodGen) {
      original.load(methodGen);
    }

    @Override
    public String load() {
      return original.load();
    }

    @Override
    public String unaliasedName() {
      return original.unaliasedName();
    }
  }

  private class ConstantCompiler extends BaseHotloadingCompiler {

    public ConstantLoader compile() {
      final var classVisitor = createClassVisitor();
      classVisitor.visit(
          Opcodes.V1_8,
          Opcodes.ACC_PUBLIC,
          BaseHotloadingCompiler.TARGET_INTERNAL,
          null,
          A_OBJECT_TYPE.getInternalName(),
          new String[] {A_CONSTANT_LOADER_TYPE.getInternalName()});

      final var ctor =
          new GeneratorAdapter(Opcodes.ACC_PUBLIC, DEFAULT_CTOR, null, null, classVisitor);
      ctor.visitCode();
      ctor.loadThis();
      ctor.invokeConstructor(A_OBJECT_TYPE, DEFAULT_CTOR);
      ctor.visitInsn(Opcodes.RETURN);
      ctor.visitMaxs(0, 0);
      ctor.visitEnd();

      final var handle =
          new GeneratorAdapter(Opcodes.ACC_PUBLIC, LOAD_METHOD, null, null, classVisitor);
      handle.visitCode();
      handle.invokeDynamic(type.descriptor(), METHOD_IMYHAT_DESC, HANDLER_IMYHAT);
      handle.newInstance(A_PACK_JSON_OBJECT_TYPE);
      handle.dup();
      handle.loadArg(0);
      handle.push("value");
      handle.invokeConstructor(A_PACK_JSON_OBJECT_TYPE, PACK_JSON_OBJECT_CTOR);
      ConstantDefinition.this.load(handle);
      handle.valueOf(type.apply(TypeUtils.TO_ASM));
      handle.invokeVirtual(A_IMYHAT_TYPE, METHOD_IMYHAT__ACCEPT_OBJ);
      handle.visitInsn(Opcodes.RETURN);
      handle.visitMaxs(0, 0);
      handle.visitEnd();

      classVisitor.visitEnd();

      try {
        return load(ConstantLoader.class, BaseHotloadingCompiler.TARGET);
      } catch (InstantiationException
          | IllegalAccessException
          | ClassNotFoundException
          | NoSuchMethodException
          | InvocationTargetException e) {
        e.printStackTrace();
        return o -> o.put("error", e.getMessage());
      }
    }
  }

  public abstract static class ConstantList<T> extends ConstantDefinition {

    private final List<T> values;

    public ConstantList(String name, Imyhat type, Stream<T> values, String description) {
      super(name, type.asList(), description, null);
      this.values = values.collect(Collectors.toList());
    }

    @Override
    public final void load(GeneratorAdapter methodGen) {
      Renderer.loadImyhatInMethod(methodGen, type().descriptor());
      methodGen.invokeVirtual(A_IMYHAT_TYPE, METHOD_IMYHAT__NEW_SET);
      for (final var value : values) {
        methodGen.dup();
        write(methodGen, value);
        methodGen.invokeVirtual(A_SET_TYPE, SET__ADD);
        methodGen.pop();
      }
    }

    protected abstract void write(GeneratorAdapter methodGen, T value);
  }

  private static final Type A_CONSTANT_LOADER_TYPE = Type.getType(ConstantLoader.class);
  private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);
  private static final Type A_JSON_OBJECT_TYPE = Type.getType(ObjectNode.class);
  private static final Type A_OBJECT_NODE_TYPE = Type.getType(ObjectNode.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_PACK_JSON_OBJECT_TYPE = Type.getType(PackJsonObject.class);
  private static final Type A_SET_TYPE = Type.getType(Set.class);
  private static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final Method DEFAULT_CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] {});
  private static final Handle HANDLER_IMYHAT =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          A_IMYHAT_TYPE.getInternalName(),
          "bootstrap",
          Type.getMethodDescriptor(
              Type.getType(CallSite.class),
              Type.getType(MethodHandles.Lookup.class),
              A_STRING_TYPE,
              Type.getType(MethodType.class)),
          false);
  private static final Method INSTANT_CTOR =
      new Method("ofEpochMilli", Imyhat.DATE.apply(TypeUtils.TO_ASM), new Type[] {Type.LONG_TYPE});
  private static final Method LOAD_METHOD =
      new Method("load", Type.VOID_TYPE, new Type[] {A_JSON_OBJECT_TYPE});
  private static final String METHOD_IMYHAT_DESC = Type.getMethodDescriptor(A_IMYHAT_TYPE);
  private static final Method METHOD_IMYHAT__ACCEPT_OBJ =
      new Method(
          "accept", Type.VOID_TYPE, new Type[] {Type.getType(ImyhatConsumer.class), A_OBJECT_TYPE});
  private static final Method METHOD_IMYHAT__NEW_SET =
      new Method("newSet", A_SET_TYPE, new Type[] {});
  private static final Method PACK_JSON_OBJECT_CTOR =
      new Method("<init>", Type.VOID_TYPE, new Type[] {A_OBJECT_NODE_TYPE, A_STRING_TYPE});
  private static final Method SET__ADD =
      new Method("add", Type.BOOLEAN_TYPE, new Type[] {Type.getType(Object.class)});

  /**
   * Define a boolean constant
   *
   * @param name the name, which must be a valid Shesmu identifier
   */
  public static ConstantDefinition of(String name, boolean value, String description) {
    return new ConstantDefinition(name, Imyhat.BOOLEAN, description, null) {

      @Override
      public void load(GeneratorAdapter methodGen) {
        methodGen.push(value);
      }

      @Override
      public String load() {
        return Boolean.toString(value);
      }
    };
  }

  /**
   * Define a date constant
   *
   * @param name the name, which must be a valid Shesmu identifier
   */
  public static ConstantDefinition of(String name, Instant value, String description) {
    return new ConstantDefinition(name, Imyhat.DATE, description, null) {

      @Override
      public void load(GeneratorAdapter methodGen) {
        methodGen.push(value.toEpochMilli());
        methodGen.invokeStatic(type().apply(TypeUtils.TO_ASM), INSTANT_CTOR);
      }

      @Override
      public String load() {
        return String.format("new Date(%s)", value.toEpochMilli());
      }
    };
  }

  /**
   * Define an integer constant
   *
   * @param name the name, which must be a valid Shesmu identifier
   */
  public static ConstantDefinition of(String name, long value, String description) {
    return new ConstantDefinition(name, Imyhat.INTEGER, description, null) {

      @Override
      public void load(GeneratorAdapter methodGen) {
        methodGen.push(value);
      }

      @Override
      public String load() {
        return Long.toString(value);
      }
    };
  }

  /**
   * Define a string constant
   *
   * @param name the name, which must be a valid Shesmu identifier
   */
  public static ConstantDefinition of(String name, String value, String description) {
    return new ConstantDefinition(name, Imyhat.STRING, description, null) {

      @Override
      public void load(GeneratorAdapter methodGen) {
        methodGen.push(value);
      }

      @Override
      public String load() {
        try {
          return RuntimeSupport.MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  private final String description;
  private final String name;
  private final Path path;
  private final Imyhat type;
  private final LoadableValue loadable =
      new LoadableValue() {

        @Override
        public void accept(Renderer renderer) {
          load(renderer.methodGen());
        }

        @Override
        public String name() {
          return name;
        }

        @Override
        public Type type() {
          return type.apply(TypeUtils.TO_ASM);
        }
      };

  /**
   * Create a new constant
   *
   * @param name the name of the constant, which must be valid Shesmu identifier
   * @param type the Shemsu type of the constant
   */
  public ConstantDefinition(String name, Imyhat type, String description, Path path) {
    super();
    this.name = name;
    this.type = type;
    this.description = description;
    this.path = path;
  }

  /** Convert the constant into a form that can be used during bytecode generation */
  public final LoadableValue asLoadable() {
    return loadable;
  }

  /** Generate a class that write the constant to JSON when called. */
  public final ConstantLoader compile() {
    return new ConstantCompiler().compile();
  }

  /** The documentation text for a constant. */
  public final String description() {
    return description;
  }

  public final Path filename() {
    return path;
  }

  @Override
  public final Flavour flavour() {
    return Flavour.CONSTANT;
  }

  /**
   * Generate bytecode in the supplied method to load this constant on the operand stack.
   *
   * @param methodGen the method to load the value in
   */
  public abstract void load(GeneratorAdapter methodGen);

  public abstract String load();

  /**
   * The name of the constant.
   *
   * <p>This must be a valid identifier.
   */
  @Override
  public final String name() {
    return name;
  }

  @Override
  public final void read() {
    // Grand. No one cares.
  }

  /**
   * The type of the constant.
   *
   * <p>Although a constant can have any type, there isn't a straight-forward implementation for
   * arbitrary types, so only simple types are provided here.
   */
  @Override
  public final Imyhat type() {
    return type;
  }
}

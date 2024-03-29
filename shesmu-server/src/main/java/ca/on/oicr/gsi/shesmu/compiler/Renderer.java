package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.InputVariable;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/** Helper class to hold state and context for bytecode generation. */
public abstract class Renderer {

  public static void loadImyhatInMethod(GeneratorAdapter methodGen, String descriptor) {
    methodGen.invokeDynamic(descriptor, METHOD_IMYHAT_DESC, HANDLER_IMYHAT);
  }

  private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);
  private static final Type A_PATTERN_TYPE = Type.getType(Pattern.class);
  private static final Type A_STRING_TYPE = Type.getType(String.class);
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
  private static final String METHOD_IMYHAT_DESC = Type.getMethodDescriptor(A_IMYHAT_TYPE);
  private static final String METHOD_REGEX = Type.getMethodDescriptor(A_PATTERN_TYPE);
  private static final Handle REGEX_BSM =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          Type.getType(RuntimeSupport.class).getInternalName(),
          "regexBootstrap",
          Type.getMethodDescriptor(
              Type.getType(CallSite.class),
              Type.getType(MethodHandles.Lookup.class),
              Type.getType(String.class),
              Type.getType(MethodType.class),
              Type.getType(String.class),
              Type.INT_TYPE),
          false);
  private final Map<String, LoadableValue> loadables;

  private final GeneratorAdapter methodGen;

  private final OwningBuilder rootBuilder;

  private final BiConsumer<SignatureDefinition, Renderer> signerEmitter;

  public Renderer(
      OwningBuilder rootBuilder,
      GeneratorAdapter methodGen,
      Stream<LoadableValue> loadables,
      BiConsumer<SignatureDefinition, Renderer> signerEmitter) {
    this.rootBuilder = rootBuilder;
    this.methodGen = methodGen;
    this.signerEmitter = signerEmitter;
    this.loadables =
        loadables.collect(Collectors.toMap(LoadableValue::name, Function.identity(), (a, b) -> a));
  }

  public final Stream<LoadableValue> allValues() {
    return loadables.values().stream();
  }

  public final JavaStreamBuilder buildStream(Imyhat initialType) {
    return new JavaStreamBuilder(rootBuilder, this, initialType);
  }

  public final void define(String name, LoadableValue value) {
    loadables.put(name, value);
  }

  public abstract Renderer duplicate();

  /** Find a known variable by name and load it on the stack. */
  public final void emitNamed(String name) {
    final var value = loadables.get(name);
    if (value == null) {
      throw new IllegalStateException(
          String.format("Attempt to emit “%s”, but this is an unknown name in this context", name));
    }
    value.accept(this);
  }

  public final void emitSigner(SignatureDefinition name) {
    signerEmitter.accept(name, this);
  }

  public final LoadableValue getNamed(String name) {
    return loadables.get(name);
  }

  public final void invokeInterfaceStatic(Type interfaceType, Method method) {
    methodGen.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        interfaceType.getInternalName(),
        method.getName(),
        method.getDescriptor(),
        true);
  }

  public final void loadImyhat(String descriptor) {
    loadImyhatInMethod(methodGen, descriptor);
  }

  /**
   * Load the current stream value on the stack
   *
   * <p>This is a no-op in the contexts where the stream hasn't started.
   */
  public abstract void loadStream();

  public final void loadTarget(Target target) {
    if (target instanceof SignatureDefinition) {
      emitSigner((SignatureDefinition) target);
    } else if (target.flavour().isStream()) {

      loadStream();
      if (target instanceof InputVariable) {
        ((InputVariable) target).extract(methodGen());
      } else {
        methodGen()
            .invokeVirtual(
                streamType(),
                new Method(target.name(), target.type().apply(TypeUtils.TO_ASM), new Type[] {}));
      }
    } else {
      emitNamed(target.unaliasedName());
    }
  }

  /** Write the line number into the debugger for future reference. */
  public final void mark(int line) {
    methodGen.visitLineNumber(line, methodGen.mark());
  }

  /** Get the method currently being written. */
  public final GeneratorAdapter methodGen() {
    return methodGen;
  }

  public final void regex(String regex, int flags) {
    methodGen.invokeDynamic("regex", METHOD_REGEX, REGEX_BSM, regex, flags);
  }

  /** The owner of this method */
  public final OwningBuilder root() {
    return rootBuilder;
  }

  public final BiConsumer<SignatureDefinition, Renderer> signerEmitter() {
    return signerEmitter;
  }

  /**
   * Get the type of the current stream variable
   *
   * <p>This will vary if the stream has been grouped.
   */
  public abstract Type streamType();
}

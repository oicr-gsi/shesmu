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
public class Renderer {

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

  private final RootBuilder rootBuilder;

  private final BiConsumer<SignatureDefinition, Renderer> signerEmitter;

  private final int streamArg;
  private final Type streamType;

  public Renderer(
      RootBuilder rootBuilder,
      GeneratorAdapter methodGen,
      int streamArg,
      Type streamType,
      Stream<LoadableValue> loadables,
      BiConsumer<SignatureDefinition, Renderer> signerEmitter) {
    this.rootBuilder = rootBuilder;
    this.methodGen = methodGen;
    this.streamArg = streamArg;
    this.streamType = streamType;
    this.signerEmitter = signerEmitter;
    this.loadables =
        loadables.collect(Collectors.toMap(LoadableValue::name, Function.identity(), (a, b) -> a));
  }

  public Stream<LoadableValue> allValues() {
    return loadables.values().stream();
  }

  public JavaStreamBuilder buildStream(Imyhat initialType) {
    return new JavaStreamBuilder(rootBuilder, this, initialType);
  }

  public void define(String name, LoadableValue value) {
    loadables.put(name, value);
  }

  public Renderer duplicate() {
    return new Renderer(
        root(), methodGen, streamArg, streamType, loadables.values().stream(), signerEmitter);
  }

  /** Find a known variable by name and load it on the stack. */
  public void emitNamed(String name) {
    loadables.get(name).accept(this);
  }

  public void emitSigner(SignatureDefinition name) {
    signerEmitter.accept(name, this);
  }

  public LoadableValue getNamed(String name) {
    return loadables.get(name);
  }

  public void invokeInterfaceStatic(Type interfaceType, Method method) {
    methodGen.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        interfaceType.getInternalName(),
        method.getName(),
        method.getDescriptor(),
        true);
  }

  public void loadImyhat(String descriptor) {
    loadImyhatInMethod(methodGen, descriptor);
  }

  /**
   * Load the current stream value on the stack
   *
   * <p>This is a no-op in the contexts where the stream hasn't started.
   */
  public void loadStream() {
    if (streamType != null) {
      methodGen.loadArg(streamArg);
    }
  }

  public void loadTarget(Target target) {
    if (target.flavour() == Target.Flavour.STREAM_SIGNATURE) {

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
      emitNamed(target.name());
    }
  }

  /** Write the line number into the debugger for future reference. */
  public void mark(int line) {
    methodGen.visitLineNumber(line, methodGen.mark());
  }

  /** Get the method currently being written. */
  public GeneratorAdapter methodGen() {
    return methodGen;
  }

  public void regex(String regex, int flags) {
    methodGen.invokeDynamic("regex", METHOD_REGEX, REGEX_BSM, regex, flags);
  }

  /** The the owner of this method */
  public RootBuilder root() {
    return rootBuilder;
  }

  public BiConsumer<SignatureDefinition, Renderer> signerEmitter() {
    return signerEmitter;
  }

  /**
   * Get the type of the current stream variable
   *
   * <p>This will vary if the stream has been grouped.
   */
  public Type streamType() {
    return streamType;
  }
}

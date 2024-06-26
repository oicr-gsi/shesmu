package ca.on.oicr.gsi.shesmu.compiler.definitions;

import ca.on.oicr.gsi.shesmu.compiler.SignableRenderer;
import ca.on.oicr.gsi.shesmu.compiler.Target;
import ca.on.oicr.gsi.shesmu.compiler.TypeUtils;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
 * A special stream variable that can generate a signature based on other signable variables in the
 * input
 */
public abstract class SignatureDefinition implements Target {
  public static class AliasedSignatureDefinition extends SignatureDefinition {
    private final SignatureDefinition original;

    public AliasedSignatureDefinition(SignatureDefinition original, String alias) {
      super(alias, original.storage(), original.type());
      this.original = original;
    }

    @Override
    public void build(
        GeneratorAdapter method, Type streamType, Stream<SignableRenderer> variables) {
      original.build(method, streamType, variables);
    }

    @Override
    public Path filename() {
      return original.filename();
    }

    @Override
    public void read() {
      original.read();
    }
  }

  private final String name;
  private final SignatureStorage storage;
  private final Imyhat type;

  public SignatureDefinition(String name, SignatureStorage storage, Imyhat type) {
    super();
    this.name = name;
    this.storage = storage;
    this.type = type;
  }

  /**
   * Generate bytecode to produce the signature
   *
   * <p>This method operates different depending on the storage of the signature.
   *
   * <ul>
   *   <li>If the signature does not actually depend on the input ({@link SignatureStorage#STATIC}),
   *       then the method passed will be a <code>clinit</code> method and the constant value should
   *       be pushed on the stack.
   *   <li>If the signature depends on the input ({@link SignatureStorage#DYNAMIC}), then the method
   *       passed will be a method that takes one input, the stream variable to be signed, and the
   *       resulting value should be pushed on the stack.
   * </ul>
   *
   * @param method the method in which to generate the byte code
   * @param streamType the type of the input data
   * @param variables the input variables to capture
   */
  public abstract void build(
      GeneratorAdapter method, Type streamType, Stream<SignableRenderer> variables);

  public abstract Path filename();

  @Override
  public final Flavour flavour() {
    return Flavour.STREAM_SIGNATURE;
  }

  @Override
  public final String name() {
    return name;
  }

  @Override
  public void read() {
    // Stellar. Don't care.
  }

  public final SignatureStorage storage() {
    return storage;
  }

  public Class<?> storageClass() {
    return storage.holderClass(type.javaType());
  }

  public final Type storageType() {
    return storage.holderType(type.apply(TypeUtils.TO_ASM));
  }

  @Override
  public final Imyhat type() {
    return type;
  }
}

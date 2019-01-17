package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.shesmu.compiler.Target;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
 * A special stream variable that can generate a signature based on other signable variables in the
 * input
 */
public abstract class SignatureVariable implements Target {

  private final String name;
  private final SignatureStorage storage;
  private final Imyhat type;

  public SignatureVariable(String name, SignatureStorage storage, Imyhat type) {
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
   *   <li>If the signature does not actually depend on the input ({@link
   *       SignatureStorage#STATIC_FIELD}), then the method passed will be a <tt>clinit</tt> method
   *       and the constant value should be pushed on the stack.
   *   <li>If the signature depends on the input ({@link SignatureStorage#STATIC_METHOD}), then the
   *       method passed will be a method that takes one input, the stream variable to be signed,
   *       and the resulting value should be pushed on the stack.
   * </ul>
   *
   * @param method the method in which to generate the byte code
   * @param streamType the type of the input data
   * @param variables the input variables to capture
   */
  public abstract void build(GeneratorAdapter method, Type streamType, Stream<Target> variables);

  @Override
  public final Flavour flavour() {
    return Flavour.STREAM_SIGNATURE;
  }

  @Override
  public final String name() {
    return name;
  }

  public final SignatureStorage storage() {
    return storage;
  }

  public final Type storageType() {
    return storage.holderType(type.asmType());
  }

  @Override
  public final Imyhat type() {
    return type;
  }
}

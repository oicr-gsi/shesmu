package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/** Helper class to hold state and context for bytecode generation. */
public class RendererNoStream extends Renderer {

  public RendererNoStream(
      OwningBuilder rootBuilder,
      GeneratorAdapter methodGen,
      Stream<LoadableValue> loadables,
      BiConsumer<SignatureDefinition, Renderer> signerEmitter) {
    super(rootBuilder, methodGen, loadables, signerEmitter);
  }

  public RendererNoStream duplicate() {
    return new RendererNoStream(root(), methodGen(), allValues(), signerEmitter());
  }

  /** Load the current stream value on the stack */
  public void loadStream() {
    throw new UnsupportedOperationException();
  }

  /**
   * Get the type of the current stream variable
   *
   * <p>This will vary if the stream has been grouped.
   */
  public Type streamType() {
    return null;
  }
}

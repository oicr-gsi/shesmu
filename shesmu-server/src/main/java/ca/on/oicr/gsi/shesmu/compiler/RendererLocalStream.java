package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/** Helper class to hold state and context for bytecode generation. */
public class RendererLocalStream extends Renderer {

  private final int streamLocal;
  private final Type streamType;

  public RendererLocalStream(
      OwningBuilder rootBuilder,
      GeneratorAdapter methodGen,
      int streamLocal,
      Type streamType,
      Stream<LoadableValue> loadables,
      BiConsumer<SignatureDefinition, Renderer> signerEmitter) {
    super(rootBuilder, methodGen, loadables, signerEmitter);
    this.streamLocal = streamLocal;
    this.streamType = streamType;
  }

  public RendererLocalStream duplicate() {
    return new RendererLocalStream(
        root(), methodGen(), streamLocal, streamType, allValues(), signerEmitter());
  }

  /**
   * Load the current stream value on the stack
   *
   * <p>This is a no-op in the contexts where the stream hasn't started.
   */
  public void loadStream() {
    if (streamType != null) {
      methodGen().loadLocal(streamLocal);
    }
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

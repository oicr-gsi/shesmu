package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/** Helper class to hold state and context for bytecode generation. */
public class RendererArgumentStream extends Renderer {

  private final int streamArg;
  private final Type streamType;

  public RendererArgumentStream(
      RootBuilder rootBuilder,
      GeneratorAdapter methodGen,
      int streamArg,
      Type streamType,
      Stream<LoadableValue> loadables,
      BiConsumer<SignatureDefinition, Renderer> signerEmitter) {
    super(rootBuilder, methodGen, loadables, signerEmitter);
    this.streamArg = streamArg;
    this.streamType = streamType;
  }

  public RendererArgumentStream duplicate() {
    return new RendererArgumentStream(
        root(), methodGen(), streamArg, streamType, allValues(), signerEmitter());
  }

  /**
   * Load the current stream value on the stack
   *
   * <p>This is a no-op in the contexts where the stream hasn't started.
   */
  public void loadStream() {
    if (streamType != null) {
      methodGen().loadArg(streamArg);
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

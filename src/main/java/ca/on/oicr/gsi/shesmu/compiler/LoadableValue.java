package ca.on.oicr.gsi.shesmu.compiler;

import java.util.function.Consumer;

import org.objectweb.asm.Type;

/**
 * A value that can be put on the operand stack in a method.
 *
 */
public abstract class LoadableValue implements Consumer<Renderer> {
	public abstract String name();

	public abstract Type type();
}

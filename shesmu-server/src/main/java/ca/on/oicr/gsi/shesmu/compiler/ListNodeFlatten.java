package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.Imyhat;

public class ListNodeFlatten extends ListNode {

	private static final Type A_SET_TYPE = Type.getType(Set.class);

	private static final Type A_STREAM_TYPE = Type.getType(Stream.class);
	private static final Method METHOD_SET__STREAM = new Method("stream", A_STREAM_TYPE, new Type[] {});
	private final String nextName;

	private Imyhat type;

	public ListNodeFlatten(int line, int column, String nextName, ExpressionNode expression) {
		super(line, column, expression);
		this.nextName = nextName;
	}

	@Override
	protected void finishMethod(Renderer renderer) {
		renderer.methodGen().invokeInterface(A_SET_TYPE, METHOD_SET__STREAM);
	}

	@Override
	protected Renderer makeMethod(JavaStreamBuilder builder, LoadableValue[] loadables) {
		return builder.flatten(name(), type.asmType(), loadables);
	}

	@Override
	public String nextName() {
		return nextName;
	}

	@Override
	public Imyhat nextType() {
		return type;
	}

	@Override
	protected boolean typeCheckExtra(Imyhat incoming, Consumer<String> errorHandler) {
		if (incoming instanceof Imyhat.ListImyhat) {
			type = ((Imyhat.ListImyhat) incoming).inner();
			return true;
		}
		errorHandler.accept(
				String.format("%d:%d: Expected list for flattenting but got %s.", line(), column(), incoming.name()));
		return false;
	}

}

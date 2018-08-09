package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;

public class SourceNodeRange extends SourceNode {

	private static final Type A_LONG_STREAM_TYPE = Type.getType(LongStream.class);

	private static final Method METHOD_LONG_STREAM__BOXED = new Method("boxed", Type.getType(Stream.class), new Type[] {});
	private static final Method METHOD_LONG_STREAM__RANGE = new Method("range", A_LONG_STREAM_TYPE,
			new Type[] { Type.LONG_TYPE, Type.LONG_TYPE });
	private final ExpressionNode end;
	private final ExpressionNode start;

	public SourceNodeRange(int line, int column, ExpressionNode start, ExpressionNode end) {
		super(line, column);
		this.start = start;
		this.end = end;
	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		start.collectFreeVariables(names);
		end.collectFreeVariables(names);
	}

	@Override
	public Ordering ordering() {
		return Ordering.REQESTED;
	}

	@Override
	public JavaStreamBuilder render(Renderer renderer) {
		start.render(renderer);
		end.render(renderer);
		renderer.invokeInterfaceStatic(A_LONG_STREAM_TYPE, METHOD_LONG_STREAM__RANGE);
		renderer.methodGen().invokeInterface(A_LONG_STREAM_TYPE, METHOD_LONG_STREAM__BOXED);
		return renderer.buildStream(Imyhat.INTEGER);
	}

	@Override
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return start.resolve(defs, errorHandler) & end.resolve(defs, errorHandler);
	}

	@Override
	public boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler) {
		return start.resolveFunctions(definedFunctions, errorHandler)
				& end.resolveFunctions(definedFunctions, errorHandler);
	}

	@Override
	public Imyhat streamType() {
		return Imyhat.INTEGER;
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		boolean ok = start.typeCheck(errorHandler) & end.typeCheck(errorHandler);
		if (ok) {
			if (!start.type().isSame(Imyhat.INTEGER)) {
				start.typeError(Imyhat.INTEGER.name(), start.type(), errorHandler);
				ok = false;
			}
			if (!end.type().isSame(Imyhat.INTEGER)) {
				end.typeError(Imyhat.INTEGER.name(), end.type(), errorHandler);
				ok = false;
			}
		}
		return ok;
	}

}

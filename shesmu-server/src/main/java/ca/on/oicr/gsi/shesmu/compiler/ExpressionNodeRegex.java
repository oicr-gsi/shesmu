package ca.on.oicr.gsi.shesmu.compiler;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;

public class ExpressionNodeRegex extends ExpressionNode {

	private static final Handle BSM = new Handle(Opcodes.H_INVOKESTATIC,
			Type.getType(RuntimeSupport.class).getInternalName(), "regexBootstrap",
			Type.getMethodDescriptor(Type.getType(CallSite.class), Type.getType(MethodHandles.Lookup.class),
					Type.getType(String.class), Type.getType(MethodType.class)),
			false);
	
	private static final String METHOD = Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getType(CharSequence.class));
	
	private final ExpressionNode expression;

	private final String regex;

	public ExpressionNodeRegex(int line, int column, ExpressionNode expression, String regex) {
		super(line, column);
		this.expression = expression;
		this.regex = regex;

	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		expression.collectFreeVariables(names);
	}

	@Override
	public void render(Renderer renderer) {
		expression.render(renderer);
		renderer.methodGen().invokeDynamic(regex, METHOD, BSM);
	}

	@Override
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return expression.resolve(defs, errorHandler);
	}

	@Override
	public boolean resolveLookups(Function<String, Lookup> definedLookups, Consumer<String> errorHandler) {
		return expression.resolveLookups(definedLookups, errorHandler);
	}

	@Override
	public Imyhat type() {
		return Imyhat.BOOLEAN;
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		final boolean ok = expression.typeCheck(errorHandler);
		if (ok) {
			if (expression.type().isSame(Imyhat.STRING)) {
				return true;
			}
			typeError(Imyhat.STRING.name(), expression.type(), errorHandler);
			return false;
		}
		return ok;
	}
}

package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.VOID_TYPE;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;

public class ExpressionNodeString extends ExpressionNode {

	private static final Type A_STRING_TYPE = Type.getType(String.class);

	private static final Type A_STRINGBUILDER_TYPE = Type.getType(StringBuilder.class);

	private static final Method CTOR_DEFAULT = new Method("<init>", VOID_TYPE, new Type[] {});

	private static final Method METHOD_OBJECT__TO_STRING = new Method("toString", A_STRING_TYPE, new Type[] {});

	private final List<StringNode> parts;

	public ExpressionNodeString(int line, int column, List<StringNode> parts) {
		super(line, column);
		this.parts = parts;
	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		parts.forEach(part -> part.collectFreeVariables(names));
	}

	@Override
	public void render(Renderer renderer) {
		if (parts.stream().allMatch(StringNode::isPassive)) {
			final String renderedString = parts.stream().map(StringNode::text).collect(Collectors.joining());
			renderer.methodGen().push(renderedString);
		} else {
			renderer.methodGen().newInstance(A_STRINGBUILDER_TYPE);
			renderer.methodGen().dup();
			renderer.methodGen().invokeConstructor(A_STRINGBUILDER_TYPE, CTOR_DEFAULT);
			parts.forEach(part -> part.render(renderer));
			renderer.methodGen().invokeVirtual(A_STRINGBUILDER_TYPE, METHOD_OBJECT__TO_STRING);
		}
	}

	@Override
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return parts.stream().filter(item -> item.resolve(defs, errorHandler)).count() == parts.size();
	}

	@Override
	public boolean resolveLookups(Function<String, Lookup> definedLookups, Consumer<String> errorHandler) {
		return parts.stream().filter(part -> part.resolveLookups(definedLookups, errorHandler)).count() == parts.size();
	}

	@Override
	public Imyhat type() {
		return Imyhat.STRING;
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		return parts.stream().filter(part -> part.typeCheck(errorHandler)).count() == parts.size();
	}

}

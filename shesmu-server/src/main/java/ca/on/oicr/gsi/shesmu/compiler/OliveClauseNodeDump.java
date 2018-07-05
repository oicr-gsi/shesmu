package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.Dumper;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;

public final class OliveClauseNodeDump extends OliveClauseNode implements RejectNode {

	private static final Type A_DUMPER_TYPE = Type.getType(Dumper.class);
	private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
	private static final Method DUMPER__WRITE = new Method("write", Type.VOID_TYPE,
			new Type[] { Type.getType(Object[].class) });
	private final List<ExpressionNode> columns;
	private final String dumper;

	public OliveClauseNodeDump(String dumper, List<ExpressionNode> columns) {
		super();
		this.dumper = dumper;
		this.columns = columns;
	}

	@Override
	public void collectFreeVariables(Set<String> freeVariables) {
		columns.forEach(column -> column.collectFreeVariables(freeVariables));
	}

	@Override
	public ClauseStreamOrder ensureRoot(ClauseStreamOrder state, Consumer<String> errorHandler) {
		return state;
	}

	@Override
	public void render(RootBuilder builder, BaseOliveBuilder oliveBuilder,
			Map<String, OliveDefineBuilder> definitions) {
		final Set<String> freeVariables = new HashSet<>();
		columns.forEach(e -> e.collectFreeVariables(freeVariables));
		final Renderer renderer = oliveBuilder.peek(oliveBuilder.loadableValues()
				.filter(v -> freeVariables.contains(v.name())).toArray(LoadableValue[]::new));
		renderer.methodGen().visitCode();
		render(builder, renderer);
		renderer.methodGen().visitInsn(Opcodes.RETURN);
		renderer.methodGen().visitMaxs(0, 0);
		renderer.methodGen().visitEnd();
	}

	@Override
	public void render(RootBuilder builder, Renderer renderer) {
		builder.loadDumper(dumper, renderer.methodGen());
		renderer.methodGen().push(columns.size());
		renderer.methodGen().newArray(A_OBJECT_TYPE);
		for (int it = 0; it < columns.size(); it++) {
			renderer.methodGen().dup();
			renderer.methodGen().push(it);
			columns.get(it).render(renderer);
			renderer.methodGen().box(columns.get(it).type().asmType());
			renderer.methodGen().arrayStore(A_OBJECT_TYPE);
		}
		renderer.methodGen().invokeInterface(A_DUMPER_TYPE, DUMPER__WRITE);
	}

	@Override
	public NameDefinitions resolve(InputFormatDefinition inputFormatDefinition, NameDefinitions defs,
			Supplier<Stream<Constant>> constants, Consumer<String> errorHandler) {
		return defs.fail(columns.stream()//
				.filter(e -> e.resolve(defs, errorHandler))//
				.count() == columns.size());
	}

	@Override
	public boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, FunctionDefinition> definedFunctions, Function<String, ActionDefinition> definedActions,
			Set<String> metricNames, Consumer<String> errorHandler) {
		return columns.stream()//
				.filter(e -> e.resolveFunctions(definedFunctions, errorHandler))//
				.count() == columns.size();
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		return columns.stream()//
				.filter(e -> e.typeCheck(errorHandler))//
				.count() == columns.size();
	}

}

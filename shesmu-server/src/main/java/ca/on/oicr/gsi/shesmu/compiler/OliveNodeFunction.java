package ca.on.oicr.gsi.shesmu.compiler;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionParameter;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveTable;

public class OliveNodeFunction extends OliveNode implements FunctionDefinition {
	private final ExpressionNode body;
	private final int column;
	private final int line;
	private Method method;
	private final String name;
	private Type ownerType;
	private final List<OliveParameter> parameters;

	public OliveNodeFunction(int line, int column, String name, List<OliveParameter> parameters, ExpressionNode body) {
		super();
		this.line = line;
		this.column = column;
		this.name = name;
		this.parameters = parameters;
		this.body = body;
	}

	@Override
	public void build(RootBuilder builder, Map<String, OliveDefineBuilder> definitions) {
		ownerType = builder.selfType();
		method = new Method("$" + name, body.type().asmType(),
				parameters.stream().map(p -> p.type().asmType()).toArray(Type[]::new));
	}

	@Override
	public boolean checkVariableStream(Consumer<String> errorHandler) {
		return true;
	}

	@Override
	public boolean collectDefinitions(Map<String, OliveNodeDefinition> definedOlives, Consumer<String> errorHandler) {
		return true;
	}

	@Override
	public boolean collectFunctions(Predicate<String> isDefined, Consumer<FunctionDefinition> defineFunctions,
			Consumer<String> errorHandler) {
		if (isDefined.test(name)) {
			errorHandler.accept(String.format("%d:%d: Function “%s” is already defined.", line, column, name));
			return false;
		}
		defineFunctions.accept(this);
		return true;
	}

	@Override
	public Stream<OliveTable> dashboard() {
		return Stream.empty();
	}

	@Override
	public String description() {
		return "User-defined function";
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public Stream<FunctionParameter> parameters() {
		return parameters.stream().map(p -> new FunctionParameter(p.name(), p.type()));
	}

	@Override
	public void render(GeneratorAdapter methodGen) {
		methodGen.invokeVirtual(ownerType, method);
	}

	@Override
	public void render(RootBuilder builder, Map<String, OliveDefineBuilder> definitions) {
		final GeneratorAdapter methodGen = new GeneratorAdapter(Opcodes.ACC_PUBLIC, method, null, null,
				builder.classVisitor);
		methodGen.visitCode();
		methodGen.visitLineNumber(line, methodGen.mark());
		body.render(new Renderer(builder, methodGen, -1, null, //
				Stream.concat(//
						parameters.stream()//
								.map(Pair.number())//
								.map(Pair.transform(LoadParameter::new)), //
						builder.constants()), //
				(sv, r) -> {
					throw new UnsupportedOperationException("Cannot have signature in function.");
				}));
		methodGen.returnValue();
		methodGen.visitMaxs(0, 0);
		methodGen.visitEnd();
	}

	@Override
	public void renderStart(GeneratorAdapter methodGen) {
		methodGen.loadThis();
	}

	@Override
	public boolean resolve(InputFormatDefinition inputFormatDefinition,
			Function<String, InputFormatDefinition> definedFormats, Consumer<String> errorHandler,
			Supplier<Stream<Constant>> constants) {
		final NameDefinitions defs = new NameDefinitions(Stream.concat(//
				constants.get(), parameters.stream())//
				.collect(Collectors.toMap(Target::name, Function.identity())), //
				true);
		return body.resolve(defs, errorHandler);
	}

	@Override
	public boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, FunctionDefinition> definedFunctions, Function<String, ActionDefinition> definedActions,
			Set<String> metricNames, Map<String, List<Imyhat>> dumpers, Consumer<String> errorHandler) {
		return body.resolveFunctions(definedFunctions, errorHandler);
	}

	@Override
	public boolean resolveTypes(Function<String, Imyhat> definedTypes, Consumer<String> errorHandler) {
		return parameters.stream()//
				.filter(p -> p.resolveTypes(definedTypes, errorHandler))//
				.count() == parameters.size();
	}

	@Override
	public Imyhat returnType() {
		return body.type();
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		return body.typeCheck(errorHandler);
	}
}

package ca.on.oicr.gsi.shesmu.compiler;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;

public interface RejectNode {
	void collectFreeVariables(Set<String> freeVariables);

	void render(RootBuilder builder, Renderer renderer);

	NameDefinitions resolve(InputFormatDefinition inputFormatDefinition, NameDefinitions defs,
			Supplier<Stream<Constant>> constants, Consumer<String> errorHandler);

	boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, FunctionDefinition> definedFunctions, Function<String, ActionDefinition> definedActions,
			Set<String> metricNames, Map<String, List<Imyhat>> dumpers, Consumer<String> errorHandler);

	boolean typeCheck(Consumer<String> errorHandler);
}

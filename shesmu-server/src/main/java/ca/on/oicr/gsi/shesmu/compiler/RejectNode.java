package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface RejectNode {
  void collectFreeVariables(Set<String> freeVariables);

  void collectPlugins(Set<Path> pluginFileNames);

  void render(RootBuilder builder, Renderer renderer);

  NameDefinitions resolve(
      InputFormatDefinition inputFormatDefinition,
      Function<String, InputFormatDefinition> definedFormats,
      NameDefinitions defs,
      Supplier<Stream<SignatureDefinition>> signatureDefinitions,
      ConstantRetriever constants,
      Consumer<String> errorHandler);

  boolean resolveDefinitions(
      Map<String, OliveNodeDefinition> definedOlives,
      Function<String, FunctionDefinition> definedFunctions,
      Function<String, ActionDefinition> definedActions,
      Set<String> metricNames,
      Map<String, List<Imyhat>> dumpers,
      Consumer<String> errorHandler);

  boolean typeCheck(Consumer<String> errorHandler);
}

package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveTable;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class OliveNodeConstant extends OliveNode implements Target {
  private final ExpressionNode body;

  private final int column;

  private final int line;

  private final String name;

  public OliveNodeConstant(int line, int column, String name, ExpressionNode body) {
    this.line = line;
    this.column = column;
    this.name = name;
    this.body = body;
  }

  @Override
  public void build(RootBuilder builder, Map<String, OliveDefineBuilder> definitions) {
    builder.defineConstant(
        name,
        body.type().apply(TypeUtils.TO_ASM),
        method -> body.render(builder.rootRenderer(false)));
  }

  @Override
  public boolean checkVariableStream(Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public boolean collectDefinitions(
      Map<String, OliveNodeDefinition> definedOlives,
      Map<String, Target> definedConstants,
      Consumer<String> errorHandler) {
    if (definedConstants.containsKey(name)) {
      errorHandler.accept(
          String.format("%d:%d: Cannot redefine constant “%s”.", line, column, name));
      return false;
    }
    definedConstants.put(name, this);
    return true;
  }

  @Override
  public boolean collectFunctions(
      Predicate<String> isDefined,
      Consumer<FunctionDefinition> defineFunctions,
      Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    body.collectPlugins(pluginFileNames);
  }

  @Override
  public Stream<OliveTable> dashboard() {
    return Stream.empty();
  }

  @Override
  public Flavour flavour() {
    return Flavour.CONSTANT;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public void processExport(ExportConsumer exportConsumer) {
    // Not exportable
  }

  @Override
  public void render(RootBuilder builder, Map<String, OliveDefineBuilder> definitions) {
    // Nothing to do.

  }

  @Override
  public boolean resolve(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    return body.resolve(
        new NameDefinitions(
            oliveCompilerServices
                .constants(false)
                .collect(Collectors.toMap(Target::name, Function.identity())),
            true),
        errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    return body.resolveDefinitions(oliveCompilerServices, errorHandler);
  }

  @Override
  public boolean resolveTypes(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public Imyhat type() {
    return body.type();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return body.typeCheck(errorHandler);
  }
}

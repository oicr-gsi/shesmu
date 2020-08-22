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
  private final boolean exported;
  private final int line;
  private final String name;
  private boolean read;

  public OliveNodeConstant(
      int line, int column, boolean exported, String name, ExpressionNode body) {
    this.line = line;
    this.column = column;
    this.exported = exported;
    this.name = name;
    this.body = body;
  }

  @Override
  public void build(RootBuilder builder, Map<String, CallableDefinitionRenderer> definitions) {
    builder.defineConstant(
        name,
        body.type().apply(TypeUtils.TO_ASM),
        method -> body.render(builder.rootRenderer(false, null)));
  }

  @Override
  public boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
    if (read || exported) {
      return true;
    } else {
      errorHandler.accept(
          String.format("%d:%d: Constant “%s” is neither used nor exported.", line, column, name));
      return false;
    }
  }

  @Override
  public boolean checkVariableStream(Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public boolean collectDefinitions(
      Map<String, CallableDefinition> definedOlives,
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
    if (exported) {
      exportConsumer.constant(name, type());
    }
  }

  @Override
  public void read() {
    read = true;
  }

  @Override
  public void render(
      RootBuilder builder, Function<String, CallableDefinitionRenderer> definitions) {
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

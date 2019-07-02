package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.dumper.Dumper;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public final class OliveClauseNodeDump extends OliveClauseNode implements RejectNode {

  private static final Type A_DUMPER_TYPE = Type.getType(Dumper.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Method DUMPER__WRITE =
      new Method("write", Type.VOID_TYPE, new Type[] {Type.getType(Object[].class)});
  private final int column;
  private final List<ExpressionNode> columns;
  private final String dumper;
  private List<Imyhat> dumperTypes;

  private final int line;

  public OliveClauseNodeDump(int line, int column, String dumper, List<ExpressionNode> columns) {
    super();
    this.line = line;
    this.column = column;
    this.dumper = dumper;
    this.columns = columns;
  }

  @Override
  public void collectFreeVariables(Set<String> freeVariables) {
    columns.forEach(column -> column.collectFreeVariables(freeVariables, Flavour::needsCapture));
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    columns.forEach(column -> column.collectPlugins(pluginFileNames));
  }

  @Override
  public int column() {
    return column;
  }

  @Override
  public Stream<OliveClauseRow> dashboard() {
    return Stream.of(
        new OliveClauseRow(
            "Dump",
            line,
            column,
            false,
            false,
            columns
                .stream()
                .map(
                    new Function<ExpressionNode, VariableInformation>() {
                      private int index;

                      @Override
                      public VariableInformation apply(ExpressionNode expression) {
                        final Set<String> inputs = new TreeSet<>();
                        expression.collectFreeVariables(inputs, Flavour::isStream);
                        return new VariableInformation(
                            String.format("%d:%d(%d)", line, column, index++),
                            expression.type(),
                            inputs.stream(),
                            Behaviour.DEFINITION);
                      }
                    })));
  }

  @Override
  public ClauseStreamOrder ensureRoot(
      ClauseStreamOrder state, Set<String> signableNames, Consumer<String> errorHandler) {
    return state;
  }

  @Override
  public int line() {
    return line;
  }

  @Override
  public void render(
      RootBuilder builder,
      BaseOliveBuilder oliveBuilder,
      Map<String, OliveDefineBuilder> definitions) {
    final Set<String> freeVariables = new HashSet<>();
    columns.forEach(e -> e.collectFreeVariables(freeVariables, Flavour::needsCapture));
    final Renderer renderer =
        oliveBuilder.peek(
            "Dump " + dumper,
            line,
            column,
            oliveBuilder
                .loadableValues()
                .filter(v -> freeVariables.contains(v.name()))
                .toArray(LoadableValue[]::new));
    renderer.methodGen().visitCode();
    render(builder, renderer);
    renderer.methodGen().visitInsn(Opcodes.RETURN);
    renderer.methodGen().visitMaxs(0, 0);
    renderer.methodGen().visitEnd();
  }

  @Override
  public void render(RootBuilder builder, Renderer renderer) {
    builder.loadDumper(
        dumper,
        renderer.methodGen(),
        columns.stream().map(ExpressionNode::type).toArray(Imyhat[]::new));
    renderer.methodGen().push(columns.size());
    renderer.methodGen().newArray(A_OBJECT_TYPE);
    for (int it = 0; it < columns.size(); it++) {
      renderer.methodGen().dup();
      renderer.methodGen().push(it);
      columns.get(it).render(renderer);
      renderer.methodGen().valueOf(columns.get(it).type().apply(TypeUtils.TO_ASM));
      renderer.methodGen().arrayStore(A_OBJECT_TYPE);
    }
    renderer.methodGen().invokeInterface(A_DUMPER_TYPE, DUMPER__WRITE);
  }

  @Override
  public NameDefinitions resolve(
      InputFormatDefinition inputFormatDefinition,
      Function<String, InputFormatDefinition> definedFormats,
      NameDefinitions defs,
      Supplier<Stream<SignatureDefinition>> signatureDefinitions,
      ConstantRetriever constants,
      Consumer<String> errorHandler) {
    return defs.fail(
        columns.stream().filter(e -> e.resolve(defs, errorHandler)).count() == columns.size());
  }

  @Override
  public boolean resolveDefinitions(
      Map<String, OliveNodeDefinition> definedOlives,
      Function<String, FunctionDefinition> definedFunctions,
      Function<String, ActionDefinition> definedActions,
      Set<String> metricNames,
      Map<String, List<Imyhat>> dumpers,
      Consumer<String> errorHandler) {

    if (dumpers.containsKey(dumper)) {
      dumperTypes = dumpers.get(dumper);
    } else {
      dumperTypes = new ArrayList<>();
    }

    return columns.stream().filter(e -> e.resolveFunctions(definedFunctions, errorHandler)).count()
        == columns.size();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (columns.stream().filter(e -> e.typeCheck(errorHandler)).count() != columns.size()) {
      return false;
    }
    if (dumperTypes.isEmpty()) {
      columns.stream().map(ExpressionNode::type).forEachOrdered(dumperTypes::add);
      return true;
    }
    if (dumperTypes.size() != columns.size()) {
      errorHandler.accept(
          String.format(
              "%d:%d: Number of arguments (%d) to dumper %s is different from previously (%d).",
              line, column, columns.size(), dumper, dumperTypes.size()));
      return false;
    }
    boolean ok = true;
    for (int i = 0; i < dumperTypes.size(); i++) {
      if (!dumperTypes.get(i).isSame(columns.get(i).type())) {
        errorHandler.accept(
            String.format(
                "%d:%d: The %d argument to dumper %s is was previously %s and is now %s.",
                line, column, i, dumper, dumperTypes.get(i).name(), columns.get(i).type().name()));
        ok = false;
      }
    }
    return ok;
  }
}

package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.ParseDispatch;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

public abstract class WizardMatchBranchNode {
  private interface NodeConstructor {
    WizardMatchBranchNode create(int line, int column, String name, WizardNode value);
  }

  private static final ParseDispatch<NodeConstructor> CONSTRUCTOR = new ParseDispatch<>();

  static {
    CONSTRUCTOR.addSymbol(
        "{",
        (p, o) ->
            DestructuredArgumentNode.parseTupleOrObject(
                p,
                o,
                f ->
                    (line, column, name, value) ->
                        new WizardMatchBranchNodeObject(line, column, name, value, f),
                e ->
                    (line, column, name, value) ->
                        new WizardMatchBranchNodeTuple(line, column, name, value, e)));
    CONSTRUCTOR.addKeyword(
        "_",
        (p, o) -> {
          o.accept(WizardMatchBranchNodeDiscard::new);
          return p;
        });
    CONSTRUCTOR.addRaw(
        "nothing",
        (p, o) -> {
          o.accept(WizardMatchBranchNodeEmpty::new);
          return p;
        });
  }

  public static Parser parse(Parser input, Consumer<WizardMatchBranchNode> output) {
    final AtomicReference<String> name = new AtomicReference<>();
    final AtomicReference<NodeConstructor> ctor = new AtomicReference<>();
    final AtomicReference<WizardNode> value = new AtomicReference<>();
    final Parser result =
        input
            .whitespace()
            .keyword("When")
            .whitespace()
            .algebraicIdentifier(name::set)
            .whitespace()
            .dispatch(CONSTRUCTOR, ctor::set)
            .whitespace()
            .keyword("Then")
            .whitespace()
            .then(WizardNode::parse, value::set);
    if (result.isGood()) {
      output.accept(ctor.get().create(input.line(), input.column(), name.get(), value.get()));
    }
    return result;
  }

  private final String name;
  private final WizardNode value;
  private final int line;
  private final int column;

  protected WizardMatchBranchNode(int line, int column, String name, WizardNode value) {
    this.line = line;
    this.column = column;
    this.name = name;
    this.value = value;
  }

  protected abstract NameDefinitions bind(NameDefinitions definitions);

  protected abstract Stream<Target> boundNames();

  public final String name() {
    return name;
  }

  public String renderEcma(EcmaScriptRenderer renderer, EcmaLoadableConstructor name) {
    return value.renderEcma(renderer, name);
  }

  public final boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return value.resolve(bind(defs), errorHandler);
  }

  public boolean resolveCrossReferences(
      Map<String, WizardDefineNode> references, Consumer<String> errorHandler) {
    return value.resolveCrossReferences(references, errorHandler);
  }

  public final boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return value.resolveDefinitions(expressionCompilerServices, nativeDefinitions, errorHandler);
  }

  public final int line() {
    return line;
  }

  public final int column() {
    return column;
  }

  public final boolean typeCheck(Imyhat argumentType, Consumer<String> errorHandler) {
    return typeCheckBindings(argumentType, errorHandler) && value.typeCheck(errorHandler);
  }

  protected abstract boolean typeCheckBindings(Imyhat argumentType, Consumer<String> errorHandler);
}

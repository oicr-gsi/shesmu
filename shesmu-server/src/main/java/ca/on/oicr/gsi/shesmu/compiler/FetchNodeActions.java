package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilter;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilter.ActionFilterNode;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.time.Instant;
import java.util.function.Consumer;

public class FetchNodeActions extends FetchNode {

  private final String fetchType;
  private final ActionFilter.ActionFilterNode<
          InformationParameterNode<ActionState>,
          InformationParameterNode<String>,
          InformationParameterNode<Instant>,
          InformationParameterNode<Long>>
      filter;
  private final Imyhat type;

  public FetchNodeActions(
      String fetchType,
      Imyhat type,
      String name,
      ActionFilterNode<
              InformationParameterNode<ActionState>,
              InformationParameterNode<String>,
              InformationParameterNode<Instant>,
              InformationParameterNode<Long>>
          filter) {
    super(name);
    this.fetchType = fetchType;
    this.type = type;
    this.filter = filter;
  }

  @Override
  public Flavour flavour() {
    return Flavour.LAMBDA;
  }

  @Override
  public void read() {
    // Do nothing.
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    return String.format(
        "{type: \"%s\", filter: %s}",
        fetchType, InformationNodeActions.renderEcmaForFilter(filter, renderer));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return InformationNodeActions.resolveForFilter(filter, defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return InformationNodeActions.resolveDefinitionsForFilter(
        filter, expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return type;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return InformationNodeActions.typeCheckForFilter(filter, errorHandler);
  }
}

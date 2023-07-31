package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.InformationNodeTable.ColumnNode;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.ParseDispatch;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilter;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilter.ActionFilterNode;
import ca.on.oicr.gsi.shesmu.plugin.filter.AlertFilter;
import ca.on.oicr.gsi.shesmu.plugin.filter.AlertFilter.AlertFilterNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class InformationNode {
  private interface RepeatCollectorConstructor {
    InformationNode create(
        DestructuredArgumentNode name, SourceNode source, List<ListNode> transforms);
  }

  private interface SimulationConstructor {
    InformationNode create(List<ObjectElementNode> constants);
  }

  private static final Parser.ParseDispatch<List<ObjectElementNode>> CONSTANTS =
      new ParseDispatch<>();
  private static final Parser.ParseDispatch<InformationNode> DISPATCH = new ParseDispatch<>();
  private static final Parser.ParseDispatch<Optional<ExpressionNode>> MIME_TYPE =
      new ParseDispatch<>();
  private static final ParseDispatch<RepeatCollectorConstructor> REPEAT_COLLECTOR =
      new ParseDispatch<>();
  private static final Parser.ParseDispatch<SimulationConstructor> SIMULATION =
      new ParseDispatch<>();

  static {
    CONSTANTS.addKeyword("Let", (p, o) -> p.list(o, ObjectElementNode::parse));
    CONSTANTS.addRaw("nothing", Parser.just(List.of()));
    MIME_TYPE.addKeyword(
        "MimeType",
        (p, o) -> p.whitespace().then(ExpressionNode::parse, v -> o.accept(Optional.of(v))));
    MIME_TYPE.addRaw("nothing", Parser.just(Optional.empty()));
    SIMULATION.addKeyword(
        "Existing",
        (p, o) ->
            p.then(
                    ActionFilter.PARSE_STRING,
                    s -> o.accept(c -> new InformationNodeSimulationExisting(c, s)))
                .whitespace());
    SIMULATION.addRaw(
        "script",
        (p, o) -> {
          final var refillers = new AtomicReference<List<RefillerDefinitionNode>>();
          final var script = new AtomicReference<ProgramNode>();
          final var start =
              p.whitespace()
                  .listEmpty(refillers::set, RefillerDefinitionNode::parse, ';')
                  .whitespace();
          final var end = start.then(ProgramNode::parse, script::set);
          if (end.isGood()) {
            final var raw = start.slice(end);
            o.accept(
                c ->
                    new InformationNodeSimulation(
                        p.line(), p.column(), c, refillers.get(), raw, script.get()));
          }
          return end;
        });

    REPEAT_COLLECTOR.addKeyword(
        "Table",
        (p, o) -> {
          final var columns = new AtomicReference<List<ColumnNode>>();
          final var result =
              p.whitespace()
                  .list(
                      columns::set,
                      (pc, po) -> {
                        final var header = new AtomicReference<String>();
                        final var contents = new AtomicReference<List<DisplayNode>>();
                        final var cResult =
                            pc.whitespace()
                                .keyword("Column")
                                .whitespace()
                                .symbol("\"")
                                .regex(
                                    ActionFilter.STRING_CONTENTS,
                                    m -> header.set(m.group(0)),
                                    "string contents")
                                .symbol("\"")
                                .whitespace()
                                .list(contents::set, DisplayNode::parse)
                                .whitespace();
                        if (cResult.isGood()) {
                          po.accept(new ColumnNode(header.get(), contents.get()));
                        }
                        return cResult;
                      })
                  .whitespace();
          if (result.isGood()) {
            o.accept(
                (name, source, transforms) ->
                    new InformationNodeTable(name, source, transforms, columns.get()));
          }
          return result;
        });
    REPEAT_COLLECTOR.addKeyword(
        "Begin",
        (p, o) -> {
          final var collectors = new AtomicReference<List<InformationNode>>();
          final var result =
              p.whitespace()
                  .list(collectors::set, InformationNode::parse)
                  .whitespace()
                  .keyword("End")
                  .whitespace();
          o.accept(
              (name, source, transforms) ->
                  new InformationNodeRepeat(name, source, transforms, collectors.get()));
          return result;
        });

    DISPATCH.addKeyword(
        "Alerts",
        (p, o) -> {
          final var filter =
              new AtomicReference<AlertFilterNode<InformationParameterNode<String>>>();
          final var result =
              AlertFilter.parse(p.whitespace(), InformationParameterNode.STRING, filter::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(new InformationNodeAlerts(filter.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Actions",
        (p, o) -> {
          final var filter =
              new AtomicReference<
                  ActionFilterNode<
                      InformationParameterNode<ActionState>,
                      InformationParameterNode<String>,
                      InformationParameterNode<Instant>,
                      InformationParameterNode<Long>>>();
          final var result =
              ActionFilter.parse(
                      p.whitespace(),
                      InformationParameterNode.ACTION_STATE,
                      InformationParameterNode.STRING,
                      InformationParameterNode.STRINGS,
                      InformationParameterNode.INSTANT,
                      InformationParameterNode.OFFSET,
                      filter::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(new InformationNodeActions(filter.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Download",
        (p, o) -> {
          final var fileName = new AtomicReference<ExpressionNode>();
          final var mimeType = new AtomicReference<Optional<ExpressionNode>>();
          final var contents = new AtomicReference<ExpressionNode>();
          final var result =
              p.whitespace()
                  .then(ExpressionNode::parse, contents::set)
                  .whitespace()
                  .keyword("To")
                  .whitespace()
                  .then(ExpressionNode::parse, fileName::set)
                  .whitespace()
                  .dispatch(MIME_TYPE, mimeType::set);
          if (result.isGood()) {
            o.accept(new InformationNodeDownload(fileName.get(), mimeType.get(), contents.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "RepeatFor",
        (input, output) -> {
          final var name = new AtomicReference<DestructuredArgumentNode>();
          final var source = new AtomicReference<SourceNode>();
          final var transforms = new AtomicReference<List<ListNode>>();
          return input
              .whitespace()
              .then(DestructuredArgumentNode::parse, name::set)
              .whitespace()
              .then(SourceNode::parse, source::set)
              .whitespace()
              .symbol(":")
              .whitespace()
              .list(transforms::set, ListNode::parse)
              .dispatch(
                  REPEAT_COLLECTOR,
                  c -> output.accept(c.create(name.get(), source.get(), transforms.get())));
        });
    DISPATCH.addKeyword(
        "Simulate",
        (p, o) -> {
          final var constants = new AtomicReference<List<ObjectElementNode>>();
          final var simulation = new AtomicReference<SimulationConstructor>();

          final var result =
              p.whitespace()
                  .dispatch(CONSTANTS, constants::set)
                  .dispatch(SIMULATION, simulation::set);
          if (result.isGood()) {
            o.accept(simulation.get().create(constants.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Print",
        (p, o) -> {
          final List<DisplayNode> elements = new ArrayList<>();
          final var result =
              p.whitespace()
                  .then(DisplayNode::parse, elements::add)
                  .list(elements::addAll, DisplayNode::parse);
          if (result.isGood()) {
            o.accept(new InformationNodePrint(elements));
          }
          return result;
        });
  }

  public static Parser parse(Parser parser, Consumer<InformationNode> output) {
    return parser.whitespace().dispatch(DISPATCH, output).whitespace();
  }

  public abstract String renderEcma(EcmaScriptRenderer renderer);

  public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

  public abstract boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler);

  public abstract boolean typeCheck(Consumer<String> errorHandler);
}

package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.ParseDispatch;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

public abstract class WizardNode {
  private static final Parser.ParseDispatch<WizardNode> DISPATCH = new ParseDispatch<>();
  private static final Parser.ParseDispatch<WizardNode> FLOW = new ParseDispatch<>();
  public static final String STATE = "Meditation State";
  public static final Pattern STRING_CONTENTS = Pattern.compile("^[^\"\n\\\\]*");
  private static final Parser.ParseDispatch<Function<List<InformationNode>, WizardNode>> WIZARD =
      new ParseDispatch<>();

  static {
    WIZARD.addKeyword(
        "Stop",
        (p, o) -> {
          final var withResult = p.whitespace().keyword("With");
          if (withResult.isGood()) {
            final var status = new AtomicReference<ExpressionNode>();
            final var result =
                withResult.whitespace().then(ExpressionNode::parse, status::set).whitespace();
            if (result.isGood()) {
              o.accept(information -> new WizardNodeEndWithStatus(information, status.get()));
              return result;
            }
          }
          o.accept(WizardNodeEnd::new);
          return p.whitespace();
        });
    WIZARD.addKeyword(
        "Form",
        (p, o) -> {
          final var entries = new AtomicReference<List<FormNode>>();
          final var next = new AtomicReference<WizardNode>();
          final var result =
              p.whitespace()
                  .list(entries::set, FormNode::parse, ',')
                  .whitespace()
                  .keyword("Then")
                  .whitespace()
                  .then(WizardNode::parse, next::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(
                information ->
                    new WizardNodeForm(
                        p.line(), p.column(), information, entries.get(), next.get()));
          }
          return result;
        });
    WIZARD.addKeyword(
        "Choice",
        (p, o) -> {
          final var choices = new AtomicReference<List<Pair<String, WizardNode>>>();
          final var result =
              p.whitespace()
                  .list(
                      choices::set,
                      (cp, co) -> {
                        final var name = new AtomicReference<String>();
                        final var next = new AtomicReference<WizardNode>();
                        final var cr =
                            cp.whitespace()
                                .keyword("When")
                                .whitespace()
                                .symbol("\"")
                                .regex(
                                    STRING_CONTENTS, m -> name.set(m.group(0)), "string contents")
                                .symbol("\"")
                                .whitespace()
                                .then(WizardNode::parse, next::set)
                                .whitespace();
                        if (cr.isGood()) {
                          co.accept(new Pair<>(name.get(), next.get()));
                        }
                        return cr;
                      })
                  .whitespace();
          if (result.isGood()) {
            o.accept(
                information ->
                    new WizardNodeChoice(p.line(), p.column(), information, choices.get()));
          }
          return result;
        });
    FLOW.addKeyword(
        "Switch",
        (p, o) -> {
          final var cases = new AtomicReference<List<Pair<ExpressionNode, WizardNode>>>();
          final var test = new AtomicReference<ExpressionNode>();
          final var alternative = new AtomicReference<WizardNode>();
          final var result =
              p.whitespace()
                  .then(ExpressionNode::parse, test::set)
                  .whitespace()
                  .list(
                      cases::set,
                      (cp, co) -> {
                        final var condition = new AtomicReference<ExpressionNode>();
                        final var value = new AtomicReference<WizardNode>();
                        final var cresult =
                            cp.whitespace()
                                .keyword("When")
                                .whitespace()
                                .then(ExpressionNode::parse0, condition::set)
                                .whitespace()
                                .keyword("Then")
                                .whitespace()
                                .then(WizardNode::parse, value::set);
                        if (cresult.isGood()) {
                          co.accept(new Pair<>(condition.get(), value.get()));
                        }
                        return cresult;
                      })
                  .whitespace()
                  .keyword("Else")
                  .whitespace()
                  .then(WizardNode::parse, alternative::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(new WizardNodeSwitch(test.get(), cases.get(), alternative.get()));
          }
          return result;
        });
    FLOW.addKeyword(
        "Match",
        (p, o) -> {
          final var cases = new AtomicReference<List<WizardMatchBranchNode>>();
          final var test = new AtomicReference<ExpressionNode>();
          final var alternative = new AtomicReference<WizardMatchAlternativeNode>();
          final var result =
              p.whitespace()
                  .then(ExpressionNode::parse, test::set)
                  .whitespace()
                  .list(cases::set, WizardMatchBranchNode::parse)
                  .whitespace()
                  .then(WizardMatchAlternativeNode::parse, alternative::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(
                new WizardNodeMatch(
                    p.line(), p.column(), test.get(), cases.get(), alternative.get()));
          }
          return result;
        });
    FLOW.addKeyword(
        "If",
        (p, o) -> {
          final var test = new AtomicReference<ExpressionNode>();
          final var trueStep = new AtomicReference<WizardNode>();
          final var falseStep = new AtomicReference<WizardNode>();
          final var result =
              p.whitespace()
                  .then(ExpressionNode::parse, test::set)
                  .whitespace()
                  .keyword("Then")
                  .whitespace()
                  .then(WizardNode::parse, trueStep::set)
                  .whitespace()
                  .keyword("Else")
                  .whitespace()
                  .then(WizardNode::parse, falseStep::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(new WizardNodeConditional(test.get(), trueStep.get(), falseStep.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Flow", (p, o) -> p.whitespace().keyword("By").whitespace().dispatch(FLOW, o));
    DISPATCH.addKeyword(
        "Let",
        (p, o) -> {
          final var entries =
              new AtomicReference<List<Pair<DestructuredArgumentNode, ExpressionNode>>>();
          final var step = new AtomicReference<WizardNode>();
          final var result =
              p.whitespace()
                  .whitespace()
                  .list(
                      entries::set,
                      (pe, oe) -> {
                        final var name = new AtomicReference<DestructuredArgumentNode>();
                        final var expr = new AtomicReference<ExpressionNode>();
                        final var entryResult =
                            pe.whitespace()
                                .then(DestructuredArgumentNode::parse, name::set)
                                .whitespace()
                                .symbol("=")
                                .whitespace()
                                .then(ExpressionNode::parse, expr::set);
                        if (entryResult.isGood()) {
                          oe.accept(new Pair<>(name.get(), expr.get()));
                        }
                        return entryResult;
                      },
                      ',')
                  .symbol("Then")
                  .whitespace()
                  .then(WizardNode::parse, step::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(new WizardNodeLet(p.line(), p.column(), entries.get(), step.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "GoTo",
        (p, o) -> {
          final var name = new AtomicReference<String>();
          final var arguments = new AtomicReference<List<ExpressionNode>>();
          final var result =
              p.whitespace()
                  .identifier(name::set)
                  .whitespace()
                  .symbol("(")
                  .list(arguments::set, ExpressionNode::parse, ',')
                  .symbol(")")
                  .whitespace();
          if (result.isGood()) {
            o.accept(new WizardNodeGoto(p.line(), p.column(), name.get(), arguments.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Fetch",
        (p, o) -> {
          final var entries = new AtomicReference<List<FetchNode>>();
          final var next = new AtomicReference<WizardNode>();
          final var result =
              p.whitespace()
                  .list(entries::set, FetchNode::parse, ',')
                  .whitespace()
                  .symbol("Then")
                  .whitespace()
                  .then(WizardNode::parse, next::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(new WizardNodeFetch(p.line(), p.column(), entries.get(), next.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Fork",
        (p, o) -> {
          final var name = new AtomicReference<DestructuredArgumentNode>();
          final var source = new AtomicReference<SourceNode>();
          final var transforms = new AtomicReference<List<ListNode>>();
          final var title = new AtomicReference<ExpressionNode>();
          final var step = new AtomicReference<WizardNode>();
          final var result =
              p.whitespace()
                  .then(DestructuredArgumentNode::parse, name::set)
                  .whitespace()
                  .then(SourceNode::parse, source::set)
                  .whitespace()
                  .symbol(":")
                  .whitespace()
                  .list(transforms::set, ListNode::parse)
                  .keyword("Title")
                  .whitespace()
                  .then(ExpressionNode::parse, title::set)
                  .whitespace()
                  .then(WizardNode::parse, step::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(
                new WizardNodeFork(
                    name.get(), source.get(), transforms.get(), title.get(), step.get()));
          }
          return result;
        });
    DISPATCH.addRaw(
        "display information and next step",
        (p, o) -> {
          final var information = new AtomicReference<List<InformationNode>>();
          return p.whitespace()
              .list(information::set, InformationNode::parse)
              .whitespace()
              .dispatch(WIZARD, f -> o.accept(f.apply(information.get())));
        });
  }

  public static Parser parse(Parser parser, Consumer<WizardNode> output) {
    return parser.whitespace().dispatch(DISPATCH, output);
  }

  /** Produce ES6/JavaScript code for this expression */
  public abstract String renderEcma(EcmaScriptRenderer renderer);

  /** Resolve all variable plugins in this expression and its children. */
  public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

  public abstract boolean resolveCrossReferences(
      Map<String, WizardDefineNode> references, Consumer<String> errorHandler);

  /** Resolve all function plugins in this expression */
  public abstract boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler);

  /** Perform type checking on this expression and its children. */
  public abstract boolean typeCheck(Consumer<String> errorHandler);
}

package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.ParseDispatch;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class FormNode implements Target {
  public enum FormType implements FormConstructor {
    TEXT(Imyhat.STRING),
    NUMBER(Imyhat.INTEGER),
    OFFSET(Imyhat.INTEGER),
    BOOLEAN(Imyhat.BOOLEAN);

    private final Imyhat type;

    FormType(Imyhat type) {
      this.type = type;
    }

    @Override
    public FormNode create(List<DisplayNode> label, String name) {
      return new FormNodeSimple(label, name, this);
    }

    public Imyhat type() {
      return type;
    }
  }

  private interface FormConstructor {
    FormNode create(List<DisplayNode> label, String name);
  }

  private static final Parser.ParseDispatch<FormConstructor> FORM_TYPE = new ParseDispatch<>();

  static {
    for (final FormType type : FormType.values()) {
      FORM_TYPE.addKeyword(type.name().toLowerCase(), Parser.just(type));
    }
    FORM_TYPE.addKeyword(
        "Select",
        (p, o) -> {
          final AtomicReference<List<Pair<DisplayNode, ExpressionNode>>> options =
              new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .list(
                      options::set,
                      (po, oo) -> {
                        final AtomicReference<DisplayNode> label = new AtomicReference<>();
                        final AtomicReference<ExpressionNode> value = new AtomicReference<>();
                        final Parser oResult =
                            po.whitespace()
                                .then(ExpressionNode::parse, value::set)
                                .whitespace()
                                .keyword("As")
                                .whitespace()
                                .then(DisplayNode::parse, label::set)
                                .whitespace();
                        if (oResult.isGood()) {
                          oo.accept(new Pair<>(label.get(), value.get()));
                        }
                        return oResult;
                      })
                  .whitespace();
          if (result.isGood()) {
            o.accept((label, name) -> new FormNodeSelect(label, name, options.get()));
          }
          return result;
        });
    FORM_TYPE.addKeyword(
        "Subset",
        (p, o) -> {
          final AtomicReference<ExpressionNode> values = new AtomicReference<>();
          final Parser result =
              p.whitespace().then(ExpressionNode::parse, values::set).whitespace();
          if (result.isGood()) {
            o.accept((label, name) -> new FormNodeSubset(label, name, values.get()));
          }
          return result;
        });
  }

  public static Parser parse(Parser parser, Consumer<FormNode> output) {
    final AtomicReference<String> name = new AtomicReference<>();
    final AtomicReference<List<DisplayNode>> label = new AtomicReference<>();
    final AtomicReference<FormConstructor> type = new AtomicReference<>();
    final Parser result =
        parser
            .whitespace()
            .keyword("Entry")
            .whitespace()
            .dispatch(FORM_TYPE, type::set)
            .whitespace()
            .identifier(name::set)
            .whitespace()
            .keyword("Label")
            .whitespace()
            .list(label::set, DisplayNode::parse)
            .whitespace();
    if (result.isGood()) {
      output.accept(type.get().create(label.get(), name.get()));
    }
    return result;
  }

  public abstract String renderEcma(EcmaScriptRenderer renderer);

  public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

  public abstract boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler);

  public abstract boolean typeCheck(Consumer<String> errorHandler);
}

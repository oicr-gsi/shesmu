package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** The types, as written in the script */
public abstract class ImyhatNode {

  public static Parser parse(Parser input, Consumer<ImyhatNode> output) {
    final var type = new AtomicReference<ImyhatNode>();
    final var result = parse0(input, type::set);
    if (result.isGood()) {
      final var right = new AtomicReference<ImyhatNode>();
      final var rightResult = result.symbol("->").whitespace().then(ImyhatNode::parse, right::set);
      if (rightResult.isGood()) {
        output.accept(new ImyhatNodeMap(type.get(), right.get()));
        return rightResult;
      }
      output.accept(type.get());
    }
    return result;
  }

  private static Parser parse0(Parser input, Consumer<ImyhatNode> output) {
    final var type = new AtomicReference<ImyhatNode>();
    final var result = parse1(input, type::set);
    if (result.isGood()) {
      final var optionalResult = result.symbol("?").whitespace();
      if (optionalResult.isGood()) {
        output.accept(new ImyhatNodeOptional(type.get()));
        return optionalResult;
      }
      output.accept(type.get());
    }
    return result;
  }

  private static Parser parse1(Parser input, Consumer<ImyhatNode> output) {
    final var type = new AtomicReference<ImyhatNode>();
    var result = parse2(input, type::set);
    while (result.isGood()) {
      final var index = new AtomicLong();
      final var nextTuple =
          result
              .symbol("[")
              .whitespace()
              .integer(index::set, 10)
              .whitespace()
              .symbol("]")
              .whitespace();
      if (nextTuple.isGood()) {
        type.set(new ImyhatNodeUntuple(type.get(), (int) index.get()));
        result = nextTuple;
      } else {
        final var name = new AtomicReference<String>();
        final var nextObject = result.symbol(".").whitespace().identifier(name::set).whitespace();
        if (nextObject.isGood()) {
          type.set(new ImyhatNodeUnobject(type.get(), name.get()));
          result = nextObject;
        } else {
          break;
        }
      }
    }
    output.accept(type.get());
    return result;
  }

  private static Parser parse2(Parser input, Consumer<ImyhatNode> output) {
    final var listParser = input.symbol("[");
    if (listParser.isGood()) {
      final var inner = new AtomicReference<ImyhatNode>();
      final var result =
          listParser
              .whitespace()
              .then(ImyhatNode::parse, inner::set)
              .whitespace()
              .symbol("]")
              .whitespace();
      if (result.isGood()) {
        output.accept(new ImyhatNodeList(inner.get()));
      }
      return result;
    }

    final var tupleParser = input.symbol("{");
    if (tupleParser.isGood()) {
      final var fields = new AtomicReference<List<Pair<String, ImyhatNode>>>();
      final var objectParser =
          tupleParser
              .whitespace()
              .list(
                  fields::set,
                  (fp, fo) -> {
                    final var name = new AtomicReference<String>();
                    final var value = new AtomicReference<ImyhatNode>();
                    final var fresult =
                        fp.whitespace()
                            .identifier(name::set)
                            .whitespace()
                            .symbol("=")
                            .whitespace()
                            .then(ImyhatNode::parse, value::set);
                    if (fresult.isGood()) {
                      fo.accept(new Pair<>(name.get(), value.get()));
                    }
                    return fresult;
                  },
                  ',')
              .whitespace()
              .symbol("}")
              .whitespace();
      if (objectParser.isGood()) {
        output.accept(new ImyhatNodeObject(fields.get()));
        return objectParser;
      }

      final var inner = new AtomicReference<List<ImyhatNode>>(List.of());
      final var result =
          tupleParser
              .whitespace()
              .list(inner::set, (p, o) -> parse(p.whitespace(), o).whitespace(), ',')
              .symbol("}")
              .whitespace();
      output.accept(new ImyhatNodeTuple(inner.get()));
      return result;
    }
    final var inputVariableParser = input.keyword("InputType");
    if (inputVariableParser.isGood()) {
      final var inputFormat = new AtomicReference<String>();
      final var variable = new AtomicReference<String>();
      final var result =
          inputVariableParser
              .whitespace()
              .qualifiedIdentifier(inputFormat::set)
              .whitespace()
              .qualifiedIdentifier(variable::set)
              .whitespace();
      output.accept(
          new ImyhatNodeInputVariable(
              input.line(), input.column(), inputFormat.get(), variable.get()));
      return result;
    }
    final var unlistParser = input.keyword("In");
    if (unlistParser.isGood()) {
      final var inner = new AtomicReference<ImyhatNode>();
      final var result = unlistParser.whitespace().then(ImyhatNode::parse, inner::set).whitespace();
      output.accept(new ImyhatNodeUncontainer(inner.get()));
      return result;
    }
    final var returnParser = input.keyword("ReturnType");
    if (returnParser.isGood()) {
      final var function = new AtomicReference<String>();
      final var result = returnParser.whitespace().qualifiedIdentifier(function::set).whitespace();
      output.accept(new ImyhatNodeReturn(input.line(), input.column(), function.get()));
      return result;
    }

    final var argumentParser = input.keyword("ArgumentType");
    if (argumentParser.isGood()) {
      final var function = new AtomicReference<String>();
      final var index = new AtomicLong();
      final var result =
          argumentParser
              .whitespace()
              .qualifiedIdentifier(function::set)
              .whitespace()
              .symbol("(")
              .whitespace()
              .integer(index::set, 10)
              .whitespace()
              .symbol(")")
              .whitespace();
      output.accept(
          new ImyhatNodeArgument(input.line(), input.column(), function.get(), (int) index.get()));
      return result;
    }

    final var nestedParser = input.symbol("(");
    if (nestedParser.isGood()) {
      return nestedParser
          .whitespace()
          .then(ImyhatNode::parse, output)
          .whitespace()
          .symbol(")")
          .whitespace();
    }

    final var unions = new AtomicReference<List<AlgebraicImyhatNode>>(List.of());
    final var algebraicParser = input.listEmpty(unions::set, AlgebraicImyhatNode::parse, '|');
    if (!unions.get().isEmpty()) {
      if (algebraicParser.isGood()) {
        output.accept(new ImyhatNodeAlgebraic(input.line(), input.column(), unions.get()));
      }
      return algebraicParser;
    }

    final var name = new AtomicReference<String>();
    final var result = input.identifier(name::set).whitespace();
    if (!result.isGood()) {
      return result;
    }
    output.accept(
        Imyhat.baseTypes()
            .filter(base -> base.name().equals(name.get()))
            .findFirst()
            .<ImyhatNode>map(ImyhatNodeLiteral::new)
            .orElseGet(() -> new ImyhatNodeVariable(name.get())));
    return result;
  }

  public abstract Imyhat render(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler);
}

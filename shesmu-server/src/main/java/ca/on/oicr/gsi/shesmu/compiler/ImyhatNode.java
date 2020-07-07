package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** The types, as written in the script */
public abstract class ImyhatNode {

  public static Parser parse(Parser input, Consumer<ImyhatNode> output) {
    final AtomicReference<ImyhatNode> type = new AtomicReference<>();
    final Parser result = parse0(input, type::set);
    if (result.isGood()) {
      final AtomicReference<ImyhatNode> right = new AtomicReference<>();
      final Parser rightResult =
          result.symbol("->").whitespace().then(ImyhatNode::parse, right::set);
      if (rightResult.isGood()) {
        output.accept(new ImyhatNodeMap(type.get(), right.get()));
        return rightResult;
      }
      output.accept(type.get());
    }
    return result;
  }

  private static Parser parse0(Parser input, Consumer<ImyhatNode> output) {
    final AtomicReference<ImyhatNode> type = new AtomicReference<>();
    final Parser result = parse1(input, type::set);
    if (result.isGood()) {
      final Parser optionalResult = result.symbol("?").whitespace();
      if (optionalResult.isGood()) {
        output.accept(new ImyhatNodeOptional(type.get()));
        return optionalResult;
      }
      output.accept(type.get());
    }
    return result;
  }

  private static Parser parse1(Parser input, Consumer<ImyhatNode> output) {
    final AtomicReference<ImyhatNode> type = new AtomicReference<>();
    Parser result = parse2(input, type::set);
    while (result.isGood()) {
      final AtomicLong index = new AtomicLong();
      final Parser nextTuple =
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
        final AtomicReference<String> name = new AtomicReference<>();
        final Parser nextObject =
            result.symbol(".").whitespace().identifier(name::set).whitespace();
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
    final Parser listParser = input.symbol("[");
    if (listParser.isGood()) {
      final AtomicReference<ImyhatNode> inner = new AtomicReference<>();
      final Parser result =
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

    final Parser tupleParser = input.symbol("{");
    if (tupleParser.isGood()) {
      final AtomicReference<List<Pair<String, ImyhatNode>>> fields = new AtomicReference<>();
      final Parser objectParser =
          tupleParser
              .whitespace()
              .<Pair<String, ImyhatNode>>list(
                  fields::set,
                  (fp, fo) -> {
                    final AtomicReference<String> name = new AtomicReference<>();
                    final AtomicReference<ImyhatNode> value = new AtomicReference<>();
                    final Parser fresult =
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

      final AtomicReference<List<ImyhatNode>> inner =
          new AtomicReference<>(Collections.emptyList());
      final Parser result =
          tupleParser
              .whitespace()
              .list(inner::set, (p, o) -> parse(p.whitespace(), o).whitespace(), ',')
              .symbol("}")
              .whitespace();
      output.accept(new ImyhatNodeTuple(inner.get()));
      return result;
    }
    final Parser unlistParser = input.keyword("In");
    if (unlistParser.isGood()) {
      final AtomicReference<ImyhatNode> inner = new AtomicReference<>();
      final Parser result =
          unlistParser.whitespace().then(ImyhatNode::parse, inner::set).whitespace();
      output.accept(new ImyhatNodeUncontainer(inner.get()));
      return result;
    }
    final Parser returnParser = input.keyword("ReturnType");
    if (returnParser.isGood()) {
      final AtomicReference<String> function = new AtomicReference<>();
      final Parser result =
          returnParser.whitespace().qualifiedIdentifier(function::set).whitespace();
      output.accept(new ImyhatNodeReturn(input.line(), input.column(), function.get()));
      return result;
    }

    final Parser argumentParser = input.keyword("ArgumentType");
    if (argumentParser.isGood()) {
      final AtomicReference<String> function = new AtomicReference<>();
      final AtomicLong index = new AtomicLong();
      final Parser result =
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

    final Parser nestedParser = input.symbol("(");
    if (nestedParser.isGood()) {
      return nestedParser
          .whitespace()
          .then(ImyhatNode::parse, output)
          .whitespace()
          .symbol(")")
          .whitespace();
    }

    final AtomicReference<String> name = new AtomicReference<>();
    final Parser result = input.identifier(name::set).whitespace();
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

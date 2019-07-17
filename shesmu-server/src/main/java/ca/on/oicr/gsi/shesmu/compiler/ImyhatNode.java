package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/** The types, as written in the script */
public abstract class ImyhatNode {

  private static final Imyhat[] BASE_TYPES =
      new Imyhat[] {Imyhat.BOOLEAN, Imyhat.DATE, Imyhat.INTEGER, Imyhat.PATH, Imyhat.STRING};

  public static boolean isBaseType(String name) {
    return Stream.of(BASE_TYPES).anyMatch(t -> t.name().equals(name));
  }

  public static Parser parse(Parser input, Consumer<ImyhatNode> output) {
    final AtomicReference<ImyhatNode> type = new AtomicReference<>();
    Parser result = parse0(input, type::set);
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

  private static Parser parse0(Parser input, Consumer<ImyhatNode> output) {
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
      output.accept(new ImyhatNodeList(inner.get()));
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
      output.accept(new ImyhatNodeUnlist(inner.get()));
      return result;
    }
    final Parser returnParser = input.keyword("Return");
    if (returnParser.isGood()) {
      final AtomicReference<String> function = new AtomicReference<>();
      final Parser result = returnParser.whitespace().identifier(function::set).whitespace();
      output.accept(new ImyhatNodeReturn(input.line(), input.column(), function.get()));
      return result;
    }

    final Parser argumentParser = input.keyword("Argument");
    if (argumentParser.isGood()) {
      final AtomicReference<String> function = new AtomicReference<>();
      final AtomicLong index = new AtomicLong();
      final Parser result =
          argumentParser
              .whitespace()
              .identifier(function::set)
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

    final AtomicReference<String> name = new AtomicReference<String>();
    final Parser result = input.identifier(name::set).whitespace();
    if (!result.isGood()) {
      return result;
    }

    for (final Imyhat base : BASE_TYPES) {
      if (base.name().equals(name.get())) {
        output.accept(new ImyhatNodeLiteral(base));
        return result;
      }
    }
    output.accept(new ImyhatNodeVariable(name.get()));
    return result;
  }

  public abstract Imyhat render(
      Function<String, Imyhat> definedTypes,
      Function<String, FunctionDefinition> definedFunctions,
      Consumer<String> errorHandler);
}

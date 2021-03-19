package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.definitions.GangDefinition;
import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.*;
import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Type;

public class TypeUtils {
  public interface GangProcessor<T> {
    T apply(Target input, Imyhat expectedType, boolean dropIfEmpty);
  }

  private static final Type A_ALGEBRAIC_VALUE_TYPE = Type.getType(AlgebraicValue.class);
  private static final Type A_BOOLEAN_TYPE = Type.getType(Boolean.class);
  private static final Type A_DOUBLE_TYPE = Type.getType(Double.class);
  private static final Type A_INSTANT_TYPE = Type.getType(Instant.class);
  private static final Type A_JSON_NODE_TYPE = Type.getType(JsonNode.class);
  private static final Type A_LONG_TYPE = Type.getType(Long.class);
  private static final Type A_MAP_TYPE = Type.getType(Map.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  private static final Type A_PATH_TYPE = Type.getType(Path.class);
  private static final Type A_SET_TYPE = Type.getType(Set.class);
  private static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
  public static final GenericTransformer<Type> TO_ASM =
      new GenericTransformer<>() {

        @Override
        public Type algebraic(Stream<AlgebraicTransformer> contents) {
          contents.close();
          return A_ALGEBRAIC_VALUE_TYPE;
        }

        @Override
        public Type bool() {
          return Type.BOOLEAN_TYPE;
        }

        @Override
        public Type date() {
          return A_INSTANT_TYPE;
        }

        @Override
        public Type floating() {
          return Type.DOUBLE_TYPE;
        }

        @Override
        public Type generic(String id) {
          return A_OBJECT_TYPE;
        }

        @Override
        public <T> Type genericAlgebraic(Stream<GenericAlgebraicGuarantee<? extends T>> inner) {
          inner.close();
          return A_ALGEBRAIC_VALUE_TYPE;
        }

        @Override
        public Type genericList(GenericTypeGuarantee<?> inner) {
          return A_SET_TYPE;
        }

        @Override
        public Type genericMap(GenericTypeGuarantee<?> key, GenericTypeGuarantee<?> value) {
          return A_MAP_TYPE;
        }

        @Override
        public Type genericOptional(GenericTypeGuarantee<?> inner) {
          return A_OPTIONAL_TYPE;
        }

        @Override
        public Type genericTuple(Stream<GenericTypeGuarantee<?>> elements) {
          return A_TUPLE_TYPE;
        }

        @Override
        public Type integer() {
          return Type.LONG_TYPE;
        }

        @Override
        public Type json() {
          return A_JSON_NODE_TYPE;
        }

        @Override
        public Type list(Imyhat inner) {
          return A_SET_TYPE;
        }

        @Override
        public Type map(Imyhat key, Imyhat value) {
          return A_MAP_TYPE;
        }

        @Override
        public Type object(Stream<Pair<String, Imyhat>> contents) {
          return A_TUPLE_TYPE;
        }

        @Override
        public Type optional(Imyhat inner) {
          return A_OPTIONAL_TYPE;
        }

        @Override
        public Type path() {
          return A_PATH_TYPE;
        }

        @Override
        public Type string() {
          return A_STRING_TYPE;
        }

        @Override
        public Type tuple(Stream<Imyhat> contents) {
          contents.close();
          return A_TUPLE_TYPE;
        }
      };
  public static final ImyhatTransformer<Type> TO_BOXED_ASM =
      new ImyhatTransformer<>() {

        @Override
        public Type algebraic(Stream<AlgebraicTransformer> contents) {
          contents.close();
          return A_ALGEBRAIC_VALUE_TYPE;
        }

        @Override
        public Type bool() {
          return A_BOOLEAN_TYPE;
        }

        @Override
        public Type date() {
          return A_INSTANT_TYPE;
        }

        @Override
        public Type floating() {
          return A_DOUBLE_TYPE;
        }

        @Override
        public Type integer() {
          return A_LONG_TYPE;
        }

        @Override
        public Type json() {
          return A_JSON_NODE_TYPE;
        }

        @Override
        public Type list(Imyhat inner) {
          return A_SET_TYPE;
        }

        @Override
        public Type map(Imyhat key, Imyhat value) {
          return A_MAP_TYPE;
        }

        @Override
        public Type object(Stream<Pair<String, Imyhat>> contents) {
          return A_TUPLE_TYPE;
        }

        @Override
        public Type optional(Imyhat inner) {
          return A_OPTIONAL_TYPE;
        }

        @Override
        public Type path() {
          return A_PATH_TYPE;
        }

        @Override
        public Type string() {
          return A_STRING_TYPE;
        }

        @Override
        public Type tuple(Stream<Imyhat> contents) {
          contents.close();
          return A_TUPLE_TYPE;
        }
      };
  /**
   * Convert a type into a JavaScript command to parse a string containing this type in the front
   * end
   */
  public static final ImyhatTransformer<String> TO_JS_PARSER =
      new ImyhatTransformer<>() {

        @Override
        public String algebraic(Stream<AlgebraicTransformer> contents) {
          return contents
              .map(
                  c ->
                      c.visit(
                          new AlgebraicVisitor<String>() {
                            @Override
                            public String empty(String name) {
                              return quote(name) + ": null";
                            }

                            @Override
                            public String object(
                                String name, Stream<Pair<String, Imyhat>> contents) {
                              return quote(name) + ": " + TO_JS_PARSER.object(contents);
                            }

                            @Override
                            public String tuple(String name, Stream<Imyhat> contents) {
                              return quote(name) + ": " + TO_JS_PARSER.tuple(contents);
                            }
                          }))
              .collect(Collectors.joining(",", "parser.u({", "})"));
        }

        private String quote(String name) {
          return "\"" + new String(JsonStringEncoder.getInstance().quoteAsString(name)) + "\"";
        }

        @Override
        public String bool() {
          return "parser.b";
        }

        @Override
        public String date() {
          return "parser.d";
        }

        @Override
        public String floating() {
          return "parser.f";
        }

        @Override
        public String integer() {
          return "parser.i";
        }

        @Override
        public String json() {
          return "parser.j";
        }

        @Override
        public String list(Imyhat inner) {
          return "parser.a(" + inner.apply(this) + ")";
        }

        @Override
        public String map(Imyhat key, Imyhat value) {
          return "parser.m(" + key.apply(this) + "," + value.apply(this) + ")";
        }

        @Override
        public String object(Stream<Pair<String, Imyhat>> fields) {
          return fields
              .map(e -> quote(e.first()) + ":" + e.second().apply(this))
              .collect(Collectors.joining(", ", "parser.o({", "})"));
        }

        @Override
        public String optional(Imyhat inner) {
          return "parser.q(" + inner.apply(this) + ")";
        }

        @Override
        public String path() {
          return "parser.p";
        }

        @Override
        public String string() {
          return "parser.s";
        }

        @Override
        public String tuple(Stream<Imyhat> types) {
          return types.map(t -> t.apply(this)).collect(Collectors.joining(",", "parser.t([", "])"));
        }
      };

  public static <T> Optional<List<T>> matchGang(
      int line,
      int column,
      NameDefinitions defs,
      GangDefinition definition,
      GangProcessor<? extends T> constructor,
      Consumer<String> errorHandler) {
    final var ok = new AtomicBoolean(true);
    final List<T> result =
        definition
            .elements()
            .flatMap(
                p -> {
                  final var source = defs.get(p.name());
                  if (source.isEmpty()) {
                    ok.set(false);
                    errorHandler.accept(
                        String.format(
                            "%d:%d: Cannot find variable ”%s” from gang “%s” as the stream has been manipulated.",
                            line, column, p.name(), definition.name()));
                    return Stream.empty();
                  }
                  if (!source.get().flavour().isStream()) {
                    ok.set(false);
                    errorHandler.accept(
                        String.format(
                            "%d:%d: Variable ”%s” from gang “%s” has been shadowed by a non-stream local.",
                            line, column, p.name(), definition.name()));
                    return Stream.empty();
                  }
                  source.get().read();
                  return Stream.of(constructor.apply(source.get(), p.type(), p.dropIfDefault()));
                })
            .collect(Collectors.toList());
    return ok.get() ? Optional.of(result) : Optional.empty();
  }
}

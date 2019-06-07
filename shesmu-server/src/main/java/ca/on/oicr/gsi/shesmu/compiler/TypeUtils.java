package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.GenericTransformer;
import ca.on.oicr.gsi.shesmu.plugin.types.GenericTypeGuarantee;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatTransformer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Type;

public class TypeUtils {
  private static final Type A_BOOLEAN_TYPE = Type.getType(Boolean.class);
  private static final Type A_INSTANT_TYPE = Type.getType(Instant.class);
  private static final Type A_LONG_TYPE = Type.getType(Long.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_PATH_TYPE = Type.getType(Path.class);
  private static final Type A_SET_TYPE = Type.getType(Set.class);
  private static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
  public static final GenericTransformer<Type> TO_ASM =
      new GenericTransformer<Type>() {

        @Override
        public Type bool() {
          return Type.BOOLEAN_TYPE;
        }

        @Override
        public Type date() {
          return A_INSTANT_TYPE;
        }

        @Override
        public Type generic(String id) {
          return A_OBJECT_TYPE;
        }

        @Override
        public Type genericList(GenericTypeGuarantee inner) {
          return A_SET_TYPE;
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
        public Type list(Imyhat inner) {
          return A_SET_TYPE;
        }

        @Override
        public Type object(Stream<Pair<String, Imyhat>> contents) {
          return A_TUPLE_TYPE;
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
      new ImyhatTransformer<Type>() {

        @Override
        public Type bool() {
          return A_BOOLEAN_TYPE;
        }

        @Override
        public Type date() {
          return A_INSTANT_TYPE;
        }

        @Override
        public Type integer() {
          return A_LONG_TYPE;
        }

        @Override
        public Type list(Imyhat inner) {
          return A_SET_TYPE;
        }

        @Override
        public Type object(Stream<Pair<String, Imyhat>> contents) {
          return A_TUPLE_TYPE;
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
      new ImyhatTransformer<String>() {

        @Override
        public String bool() {
          return "parser.b";
        }

        @Override
        public String date() {
          return "parser.d";
        }

        @Override
        public String integer() {
          return "parser.i";
        }

        @Override
        public String list(Imyhat inner) {
          return "parser.a(" + inner.apply(this) + ")";
        }

        @Override
        public String object(Stream<Pair<String, Imyhat>> fields) {
          return fields
              .map(e -> e.first() + ":" + e.second().apply(this))
              .collect(Collectors.joining(", ", "parser.o({", "})"));
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
}

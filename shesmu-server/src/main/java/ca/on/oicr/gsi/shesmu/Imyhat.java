package ca.on.oicr.gsi.shesmu;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.runtime.Tuple;

/**
 * Types as represented in Shesmu
 *
 * This word means “which/pattern of conduct” in ancient Egyptian to avoid
 * naming conflicts with other classes named type.
 *
 * Java's {@link Class} (and, by extension, ASM {@link Type}) are unsuitable for
 * this purpose because generic erasure has happened. Shesmu types also have an
 * interchange string format, called a descriptor.
 */
public abstract class Imyhat {
	/**
	 * A subclass of types for base types
	 */
	public static abstract class BaseImyhat extends Imyhat {

		public abstract Object defaultValue();

		@Override
		public final boolean isBad() {
			return false;
		}

		@Override
		public boolean isSame(Imyhat other) {
			return this == other;
		}

		/**
		 * Parse a string literal containing a value of this type
		 *
		 * @param input
		 *            the string value
		 * @return the result as an object, or null if an error occurs
		 */
		public abstract Object parse(String input);

	}

	public static final class ListImyhat extends Imyhat {
		private final Imyhat inner;

		private ListImyhat(Imyhat inner) {
			this.inner = inner;
		}

		@Override
		public Type asmType() {
			return A_SET_TYPE;
		}

		@Override
		public Comparator<?> comparator() {
			@SuppressWarnings("unchecked")
			final Comparator<Object> innerComparator = (Comparator<Object>) inner.comparator();
			return (Set<?> a, Set<?> b) -> {
				final Iterator<?> aIt = a.iterator();
				final Iterator<?> bIt = b.iterator();
				while (aIt.hasNext() && bIt.hasNext()) {
					final int result = innerComparator.compare(aIt.next(), bIt.next());
					if (result != 0) {
						return result;
					}
				}
				return Boolean.compare(aIt.hasNext(), bIt.hasNext());
			};
		}

		@Override
		public void consume(ImyhatDispatcher dispatcher, Object value) {
			@SuppressWarnings("unchecked")
			final Set<Object> values = (Set<Object>) value;
			dispatcher.consume(values.stream(), inner);
		}

		@Override
		public String descriptor() {
			return "a" + inner.descriptor();
		}

		public Imyhat inner() {
			return inner;
		}

		@Override
		public boolean isBad() {
			return inner.isBad();
		}

		@Override
		public boolean isOrderable() {
			return false;
		}

		@Override
		public boolean isSame(Imyhat other) {
			if (other instanceof ListImyhat) {
				return inner.isSame(((ListImyhat) other).inner);
			}
			return false;
		}

		@Override
		public String javaScriptParser() {
			return "parser.a(" + inner.javaScriptParser() + ")";
		}

		@Override
		public Class<?> javaType() {
			return Set.class;
		}

		@Override
		public String name() {
			return "[" + inner.name() + "]";
		}

		@Override
		protected void packJson(ArrayNode array, Object value) {
			final ArrayNode listJson = array.addArray();
			((Collection<?>) value).forEach(item -> inner.packJson(listJson, item));
		}

		@Override
		public void packJson(ObjectNode node, String key, Object value) {
			final ArrayNode listJson = node.putArray(key);
			((Collection<?>) value).forEach(item -> inner.packJson(listJson, item));
		}

		@Override
		public void streamJson(GeneratorAdapter method) {
			final int jsonLocal = method.newLocal(A_JSON_GENERATOR_TYPE);
			final int iteratorLocal = method.newLocal(A_ITERATOR_TYPE);
			method.invokeInterface(A_SET_TYPE, METHOD_SET__ITERATOR);
			method.storeLocal(iteratorLocal);
			method.storeLocal(jsonLocal);
			method.loadLocal(jsonLocal);
			method.invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_JSON_GENERATOR__ARRAY_START);
			final Label start = method.mark();
			final Label end = method.newLabel();
			method.loadLocal(iteratorLocal);
			method.invokeInterface(A_ITERATOR_TYPE, METHOD_ITERATOR__HAS_NEXT);
			method.ifZCmp(GeneratorAdapter.EQ, end);
			method.loadLocal(jsonLocal);
			method.loadLocal(iteratorLocal);
			method.invokeInterface(A_ITERATOR_TYPE, METHOD_ITERATOR__NEXT);
			method.unbox(inner.asmType());
			inner.streamJson(method);
			method.goTo(start);
			method.mark(end);
			method.loadLocal(jsonLocal);
			method.invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_JSON_GENERATOR__ARRAY_END);
		}

		@Override
		public Object unpackJson(JsonNode node) {
			return RuntimeSupport.stream(node.elements()).map(inner::unpackJson).collect(Collectors.toSet());
		}

		@Override
		public String wdlInputType() {
			return "Array[" + inner.wdlInputType() + "]";
		}
	}

	public static final class ObjectImyhat extends Imyhat {

		private final Map<String, Pair<Imyhat, Integer>> fields;

		public ObjectImyhat(Stream<Pair<String, Imyhat>> fields) {
			this.fields = fields//
					.sorted(Comparator.comparing(Pair::first))//
					.collect(Collectors.toMap(Pair::first, new Function<Pair<String, Imyhat>, Pair<Imyhat, Integer>>() {
						int index;

						@Override
						public Pair<Imyhat, Integer> apply(Pair<String, Imyhat> pair) {
							return new Pair<>(pair.second(), index++);
						}
					}));
		}

		@Override
		public Type asmType() {
			return A_TUPLE_TYPE;
		}

		@SuppressWarnings("unchecked")
		@Override
		public Comparator<?> comparator() {
			return fields.values().stream()//
					.sorted(Comparator.comparing(Pair::second))//
					.map(p -> Comparator.comparing((Tuple t) -> t.get(p.second()),
							(Comparator<Object>) p.first().comparator()))//
					.reduce(Comparator::thenComparing).get();
		}

		@Override
		public void consume(ImyhatDispatcher dispatcher, Object value) {
			final Tuple tuple = (Tuple) value;
			for (final Entry<String, Pair<Imyhat, Integer>> entry : fields.entrySet()) {
				dispatcher.consume(entry.getKey(), tuple.get(entry.getValue().second()), entry.getValue().first());
			}
		}

		@Override
		public String descriptor() {
			return "o" + fields.size() + //
					fields.entrySet().stream()//
							.map(e -> e.getKey() + "$" + e.getValue().first().descriptor())//
							.collect(Collectors.joining());
		}

		public Imyhat get(String field) {
			return fields.getOrDefault(field, new Pair<>(BAD, 0)).first();
		}

		public int index(String field) {
			return fields.getOrDefault(field, new Pair<>(BAD, 0)).second();
		}

		@Override
		public boolean isBad() {
			return fields.values().stream().map(Pair::first).anyMatch(Imyhat::isBad);
		}

		@Override
		public boolean isOrderable() {
			return false;
		}

		@Override
		public boolean isSame(Imyhat other) {
			if (!(other instanceof ObjectImyhat)) {
				return false;
			}
			final Map<String, Pair<Imyhat, Integer>> otherFields = ((ObjectImyhat) other).fields;
			if (fields.size() != otherFields.size()) {
				return false;
			}
			return fields.entrySet().stream()//
					.allMatch(e -> otherFields.getOrDefault(e.getKey(), new Pair<>(Imyhat.BAD, 0)).first()
							.isSame(e.getValue().first()));
		}

		@Override
		public String javaScriptParser() {
			return fields.entrySet().stream()//
					.map(e -> e.getKey() + ":" + e.getValue().first().javaScriptParser())//
					.collect(Collectors.joining(", ", "parser.o({", "})"));
		}

		@Override
		public Class<?> javaType() {
			return Tuple.class;
		}

		@Override
		public String name() {
			return fields.entrySet().stream()//
					.map(e -> e.getKey() + " = " + e.getValue().first().name())//
					.sorted()//
					.collect(Collectors.joining(",", "{ ", " }"));
		}

		@Override
		protected void packJson(ArrayNode array, Object value) {
			final ObjectNode object = array.addObject();
			final Tuple tuple = (Tuple) value;
			for (final Entry<String, Pair<Imyhat, Integer>> entry : fields.entrySet()) {
				entry.getValue().first().packJson(object, entry.getKey(), tuple.get(entry.getValue().second()));
			}
		}

		@Override
		public void packJson(ObjectNode node, String key, Object value) {
			final ObjectNode object = node.putObject(key);
			final Tuple tuple = (Tuple) value;
			for (final Entry<String, Pair<Imyhat, Integer>> entry : fields.entrySet()) {
				entry.getValue().first().packJson(object, entry.getKey(), tuple.get(entry.getValue().second()));
			}
		}

		@Override
		public void streamJson(GeneratorAdapter method) {
			final int local = method.newLocal(A_TUPLE_TYPE);
			method.storeLocal(local);
			method.dup();
			method.invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_JSON_GENERATOR__ARRAY_START);

			for (final Entry<String, Pair<Imyhat, Integer>> field : fields.entrySet()) {
				method.dup();
				method.loadLocal(local);
				method.push(field.getValue().second());
				method.invokeVirtual(A_TUPLE_TYPE, METHOD_TUPLE__GET);
				method.unbox(field.getValue().first().asmType());
				field.getValue().first().streamJson(method);
			}
			method.invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_JSON_GENERATOR__ARRAY_END);
		}

		@Override
		public Object unpackJson(JsonNode node) {
			return fields.entrySet().stream()//
					.collect(Collectors.toMap(Entry::getKey,
							e -> e.getValue().first().unpackJson(node.get(e.getKey()))));
		}

		@Override
		public String wdlInputType() {
			return "Object";
		}

	}

	public static final class TupleImyhat extends Imyhat {
		private final Imyhat[] types;

		private TupleImyhat(Imyhat[] types) {
			this.types = types;
		}

		@Override
		public Type asmType() {
			return A_TUPLE_TYPE;
		}

		@SuppressWarnings("unchecked")
		@Override
		public Comparator<?> comparator() {
			Comparator<Tuple> comparator = Comparator.comparing((Tuple t) -> t.get(0),
					(Comparator<Object>) types[0].comparator());
			for (int i = 1; i < types.length; i++) {
				final int index = i;
				comparator = comparator.thenComparing((Tuple t) -> t.get(index),
						(Comparator<Object>) types[index].comparator());
			}
			return comparator;
		}

		@Override
		public void consume(ImyhatDispatcher dispatcher, Object value) {
			final Tuple tuple = (Tuple) value;
			for (int it = 0; it < types.length; it++) {
				dispatcher.consume(it, tuple.get(it), types[it]);
			}
		}

		@Override
		public String descriptor() {
			return Arrays.stream(types).map(Imyhat::descriptor).collect(Collectors.joining("", "t" + types.length, ""));
		}

		public Imyhat get(int index) {
			return index >= 0 && index < types.length ? types[index] : BAD;
		}

		@Override
		public boolean isBad() {
			return Arrays.stream(types).anyMatch(Imyhat::isBad);
		}

		@Override
		public boolean isOrderable() {
			return false;
		}

		@Override
		public boolean isSame(Imyhat other) {
			if (other instanceof TupleImyhat) {
				final Imyhat[] others = ((TupleImyhat) other).types;
				if (others.length != types.length) {
					return false;
				}
				for (int i = 0; i < types.length; i++) {
					if (!others[i].isSame(types[i])) {
						return false;
					}
				}
				return true;
			}
			return false;
		}

		@Override
		public String javaScriptParser() {
			return Stream.of(types).map(Imyhat::javaScriptParser).collect(Collectors.joining(",", "parser.t([", "])"));
		}

		@Override
		public Class<?> javaType() {
			return Tuple.class;
		}

		@Override
		public String name() {
			return Arrays.stream(types).map(Imyhat::name).collect(Collectors.joining(", ", "{", "}"));
		}

		@Override
		protected void packJson(ArrayNode array, Object value) {
			packTuple(array.addArray(), (Tuple) value);
		}

		@Override
		public void packJson(ObjectNode node, String key, Object value) {
			packTuple(node.putArray(key), (Tuple) value);
		}

		private void packTuple(ArrayNode tupleJson, Tuple tuple) {
			for (int i = 0; i < types.length; i++) {
				types[i].packJson(tupleJson, tuple.get(i));
			}
		}

		@Override
		public void streamJson(GeneratorAdapter method) {
			final int local = method.newLocal(A_TUPLE_TYPE);
			method.storeLocal(local);
			method.dup();
			method.invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_JSON_GENERATOR__ARRAY_START);

			for (int i = 0; i < types.length; i++) {
				method.dup();
				method.loadLocal(local);
				method.push(i);
				method.invokeVirtual(A_TUPLE_TYPE, METHOD_TUPLE__GET);
				method.unbox(types[i].asmType());
				types[i].streamJson(method);
			}
			method.invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_JSON_GENERATOR__ARRAY_END);
		}

		@Override
		public Object unpackJson(JsonNode node) {
			final Object[] elements = new Object[types.length];
			for (int it = 0; it < types.length; it++) {
				elements[it] = types[it].unpackJson(node.get(it));
			}
			return new Tuple(elements);
		}

		@Override
		public String wdlInputType() {
			String wdl = types[types.length - 1].wdlInputType();
			for (int i = types.length - 2; i >= 0; i--) {
				wdl = "Pair[" + types[i].wdlInputType() + "," + wdl + "]";
			}
			return wdl;
		}

	}

	private static final Type A_INSTANT_TYPE = Type.getType(Instant.class);
	private static final Type A_ITERATOR_TYPE = Type.getType(Iterator.class);
	private static final Type A_JSON_GENERATOR_TYPE = Type.getType(JsonGenerator.class);

	private static final Type A_SET_TYPE = Type.getType(Set.class);

	private static final Type A_STRING_TYPE = Type.getType(String.class);
	private static final Type A_PATH_TYPE = Type.getType(Path.class);
	private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
	public static final Imyhat BAD = new Imyhat() {

		@Override
		public Type asmType() {
			return Type.VOID_TYPE;
		}

		@Override
		public Comparator<?> comparator() {
			return (a, b) -> 0;
		}

		@Override
		public void consume(ImyhatDispatcher dispatcher, Object value) {
			throw new UnsupportedOperationException("Cannot consume value of bad type.");
		}

		@Override
		public String descriptor() {
			return "$";
		}

		@Override
		public boolean isBad() {
			return true;
		}

		@Override
		public boolean isOrderable() {
			return false;
		}

		@Override
		public boolean isSame(Imyhat other) {
			return false;
		}

		@Override
		public String javaScriptParser() {
			return "parser._";
		}

		@Override
		public Class<?> javaType() {
			return Object.class;
		}

		@Override
		public String name() {
			return "💩";
		}

		@Override
		protected void packJson(ArrayNode array, Object value) {
			array.addNull();
		}

		@Override
		public void packJson(ObjectNode node, String key, Object value) {
			node.putNull(key);
		}

		@Override
		public void streamJson(GeneratorAdapter method) {
			method.pop();
			method.invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_JSON_GENERATOR__WRITE_NULL);
		}

		@Override
		public Object unpackJson(JsonNode node) {
			return null;
		}

		@Override
		public String wdlInputType() {
			return "Object";
		}

	};

	public static final BaseImyhat BOOLEAN = new BaseImyhat() {

		@Override
		public Type asmType() {
			return Type.BOOLEAN_TYPE;
		}

		@Override
		public Type boxedAsmType() {
			return Type.getType(Boolean.class);
		}

		@Override
		public Comparator<?> comparator() {
			return Comparator.naturalOrder();
		}

		@Override
		public void consume(ImyhatDispatcher dispatcher, Object value) {
			dispatcher.consume((Boolean) value);
		}

		@Override
		public Object defaultValue() {
			return false;
		}

		@Override
		public String descriptor() {
			return "b";
		}

		@Override
		public boolean isOrderable() {
			return false;
		}

		@Override
		public String javaScriptParser() {
			return "parser.b";
		}

		@Override
		public Class<?> javaType() {
			return boolean.class;
		}

		@Override
		public String name() {
			return "boolean";
		}

		@Override
		protected void packJson(ArrayNode array, Object value) {
			array.add((Boolean) value);
		}

		@Override
		public void packJson(ObjectNode node, String key, Object value) {
			node.put(key, (Boolean) value);
		}

		@Override
		public Object parse(String s) {
			return "true".equals(s);
		}

		@Override
		public void streamJson(GeneratorAdapter method) {
			method.invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_JSON_GENERATOR__WRITE_BOOLEAN);
		}

		@Override
		public Object unpackJson(JsonNode node) {
			return node.asBoolean();
		}

		@Override
		public String wdlInputType() {
			return "Boolean";
		}

	};

	private static final Map<String, CallSite> callsites = new HashMap<>();

	public static final BaseImyhat DATE = new BaseImyhat() {

		@Override
		public Type asmType() {
			return A_INSTANT_TYPE;
		}

		@Override
		public Comparator<?> comparator() {
			return Comparator.naturalOrder();
		}

		@Override
		public void consume(ImyhatDispatcher dispatcher, Object value) {
			dispatcher.consume((Instant) value);
		}

		@Override
		public Object defaultValue() {
			return Instant.EPOCH;
		}

		@Override
		public String descriptor() {
			return "d";
		}

		@Override
		public boolean isOrderable() {
			return true;
		}

		@Override
		public String javaScriptParser() {
			return "parser.d";
		}

		@Override
		public Class<?> javaType() {
			return Instant.class;
		}

		@Override
		public String name() {
			return "date";
		}

		@Override
		protected void packJson(ArrayNode array, Object value) {
			array.add(((Instant) value).toEpochMilli());
		}

		@Override
		public void packJson(ObjectNode node, String key, Object value) {
			node.put(key, ((Instant) value).toEpochMilli());
		}

		@Override
		public Object parse(String s) {
			return ZonedDateTime.parse(s).toInstant();
		}

		@Override
		public void streamJson(GeneratorAdapter method) {
			method.invokeVirtual(A_INSTANT_TYPE, METHOD_INSTANT__TO_EPOCH_MILLI);
			method.invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_JSON_GENERATOR__WRITE_NUMBER);
		}

		@Override
		public Object unpackJson(JsonNode node) {
			return Instant.ofEpochMilli(node.asLong());
		}

		@Override
		public String wdlInputType() {
			return "String";
		}

	};

	public static final BaseImyhat INTEGER = new BaseImyhat() {

		@Override
		public Type asmType() {
			return Type.LONG_TYPE;
		}

		@Override
		public Type boxedAsmType() {
			return Type.getType(Long.class);
		}

		@Override
		public Comparator<?> comparator() {
			return Comparator.naturalOrder();
		}

		@Override
		public void consume(ImyhatDispatcher dispatcher, Object value) {
			dispatcher.consume((Long) value);
		}

		@Override
		public Object defaultValue() {
			return 0L;
		}

		@Override
		public String descriptor() {
			return "i";
		}

		@Override
		public boolean isOrderable() {
			return true;
		}

		@Override
		public String javaScriptParser() {
			return "parser.i";
		}

		@Override
		public Class<?> javaType() {
			return long.class;
		}

		@Override
		public String name() {
			return "integer";
		}

		@Override
		protected void packJson(ArrayNode array, Object value) {
			array.add((Long) value);
		}

		@Override
		public void packJson(ObjectNode node, String key, Object value) {
			node.put(key, (Long) value);
		}

		@Override
		public Object parse(String s) {
			return Long.parseLong(s);
		}

		@Override
		public void streamJson(GeneratorAdapter method) {
			method.invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_JSON_GENERATOR__WRITE_NUMBER);
		}

		@Override
		public Object unpackJson(JsonNode node) {
			return node.asLong();
		}

		@Override
		public String wdlInputType() {
			return "Int";
		}

	};
	protected static final Method METHOD_INSTANT__TO_EPOCH_MILLI = new Method("toEpochMilli", Type.LONG_TYPE,
			new Type[] {});

	private static final Method METHOD_ITERATOR__HAS_NEXT = new Method("hasNext", Type.BOOLEAN_TYPE, new Type[] {});

	private static final Method METHOD_ITERATOR__NEXT = new Method("next", Type.getType(Object.class), new Type[] {});
	private static final Method METHOD_JSON_GENERATOR__ARRAY_END = new Method("writeEndArray", Type.VOID_TYPE,
			new Type[] {});

	private static final Method METHOD_JSON_GENERATOR__ARRAY_START = new Method("writeStartArray", Type.VOID_TYPE,
			new Type[] {});
	private static final Method METHOD_JSON_GENERATOR__WRITE_BOOLEAN = new Method("writeBoolean", Type.VOID_TYPE,
			new Type[] { Type.BOOLEAN_TYPE });
	private static final Method METHOD_JSON_GENERATOR__WRITE_NULL = new Method("writeNull", Type.VOID_TYPE,
			new Type[] {});
	private static final Method METHOD_JSON_GENERATOR__WRITE_NUMBER = new Method("writeNumber", Type.VOID_TYPE,
			new Type[] { Type.LONG_TYPE });
	private static final Method METHOD_JSON_GENERATOR__WRITE_STRING = new Method("writeString", Type.VOID_TYPE,
			new Type[] { A_STRING_TYPE });
	private static final Method METHOD_SET__ITERATOR = new Method("iterator", A_ITERATOR_TYPE, new Type[] {});
	private static final Method METHOD_TUPLE__GET = new Method("get", Type.getType(Object.class),
			new Type[] { Type.INT_TYPE });
	public static final BaseImyhat STRING = new BaseImyhat() {

		@Override
		public Type asmType() {
			return A_STRING_TYPE;
		}

		@Override
		public Comparator<?> comparator() {
			return Comparator.naturalOrder();
		}

		@Override
		public void consume(ImyhatDispatcher dispatcher, Object value) {
			dispatcher.consume((String) value);
		}

		@Override
		public Object defaultValue() {
			return "";
		}

		@Override
		public String descriptor() {
			return "s";
		}

		@Override
		public boolean isOrderable() {
			return false;
		}

		@Override
		public String javaScriptParser() {
			return "parser.s";
		}

		@Override
		public Class<?> javaType() {
			return String.class;
		}

		@Override
		public String name() {
			return "string";
		}

		@Override
		protected void packJson(ArrayNode array, Object value) {
			array.add((String) value);
		}

		@Override
		public void packJson(ObjectNode node, String key, Object value) {
			node.put(key, (String) value);
		}

		@Override
		public Object parse(String s) {
			return s;
		}

		@Override
		public void streamJson(GeneratorAdapter method) {
			method.invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_JSON_GENERATOR__WRITE_STRING);
		}

		@Override
		public Object unpackJson(JsonNode node) {
			return node.asText();
		}

		@Override
		public String wdlInputType() {
			return "String";
		}
	};
	protected static final Method METHOD_OBJECT__TO_STRING = new Method("toString", A_STRING_TYPE, new Type[] {});
	public static final BaseImyhat PATH = new BaseImyhat() {

		@Override
		public Type asmType() {
			return A_PATH_TYPE;
		}

		@Override
		public Comparator<?> comparator() {
			return Comparator.naturalOrder();
		}

		@Override
		public void consume(ImyhatDispatcher dispatcher, Object value) {
			dispatcher.consume((String) value);
		}

		@Override
		public Object defaultValue() {
			return Paths.get(".");
		}

		@Override
		public String descriptor() {
			return "p";
		}

		@Override
		public boolean isOrderable() {
			return false;
		}

		@Override
		public String javaScriptParser() {
			return "parser.p";
		}

		@Override
		public Class<?> javaType() {
			return Path.class;
		}

		@Override
		public String name() {
			return "path";
		}

		@Override
		protected void packJson(ArrayNode array, Object value) {
			array.add(((Path) value).toString());
		}

		@Override
		public void packJson(ObjectNode node, String key, Object value) {
			node.put(key, ((Path) value).toString());
		}

		@Override
		public Object parse(String s) {
			return Paths.get(s);
		}

		@Override
		public void streamJson(GeneratorAdapter method) {
			method.invokeVirtual(A_PATH_TYPE, METHOD_OBJECT__TO_STRING);
			method.invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_JSON_GENERATOR__WRITE_STRING);
		}

		@Override
		public Object unpackJson(JsonNode node) {
			return Paths.get(node.asText());
		}

		@Override
		public String wdlInputType() {
			return "File";
		}
	};

	/**
	 * A bootstrap method that returns the appropriate {@link Imyhat} from a
	 * descriptor.
	 *
	 * @param descriptor
	 *            the method name, which is the type descriptor; descriptor are
	 *            guaranteed to be valid JVM identifiers
	 * @param type
	 *            the type of this call site, which must take no arguments and
	 *            return {@link Imyhat}
	 * @return
	 */
	@RuntimeInterop
	public static CallSite bootstrap(Lookup lookup, String descriptor, MethodType type) {
		if (!type.returnType().equals(Imyhat.class)) {
			throw new IllegalArgumentException("Method cannot return non-Imyhat type.");
		}
		if (type.parameterCount() != 0) {
			throw new IllegalArgumentException("Method cannot take parameters.");
		}
		if (callsites.containsKey(descriptor)) {
			return callsites.get(descriptor);
		}
		final Imyhat imyhat = parse(descriptor);
		if (imyhat.isBad()) {
			throw new IllegalArgumentException("Bad type descriptor: " + descriptor);
		}
		final CallSite callsite = new ConstantCallSite(MethodHandles.constant(Imyhat.class, imyhat));
		callsites.put(descriptor, callsite);
		return callsite;
	}

	/**
	 * Convert a possibly annotated Java type into a Shesmu type
	 *
	 * @param context
	 *            the location to be displayed in error messages
	 * @param descriptor
	 *            the annotated Shesmu descriptor
	 * @param clazz
	 *            the class of the type
	 */
	public static Imyhat convert(String context, String descriptor, Class<?> clazz) {
		if (descriptor.isEmpty()) {
			return Imyhat.of(clazz)
					.orElseThrow(() -> new IllegalArgumentException(
							String.format("%s has no type annotation and %s type isn't a valid Shesmu type.", context,
									clazz.getName())));
		} else {
			final Imyhat type = Imyhat.parse(descriptor);
			if (type.isBad()) {
				throw new IllegalArgumentException(
						String.format("%s has invalid type descriptor %s", context, descriptor));
			}
			if (!type.javaType().equals(clazz)) {
				throw new IllegalArgumentException(
						String.format("%s has Java type %s but Shesmu type descriptor implies %s.", context,
								clazz.getName(), type.javaType()));
			}
			return type;
		}

	}

	/**
	 * Parse a name which must be one of the base types (no lists or tuples)
	 */
	public static BaseImyhat forName(String s) {
		return Stream.of(BOOLEAN, DATE, INTEGER, PATH, STRING)//
				.filter(t -> t.name().equals(s))//
				.findAny()//
				.orElseThrow(() -> new IllegalArgumentException(String.format("No such base type %s.", s)));
	}

	public static Optional<BaseImyhat> of(Class<?> c) {
		return Stream.of(BOOLEAN, DATE, INTEGER, PATH, STRING)//
				.filter(t -> t.javaType().equals(c))//
				.findAny();
	}

	/**
	 * Parse a string-representation of a type
	 *
	 * @param input
	 *            the Shesmu string (as generated by {@link #descriptor()}
	 * @return the parsed type; if the type is malformed, {@link #BAD} is returned
	 */
	@RuntimeInterop
	public static Imyhat parse(CharSequence input) {
		final AtomicReference<CharSequence> output = new AtomicReference<>();
		final Imyhat result = parse(input, output);
		return output.get().length() == 0 ? result : BAD;
	}

	/**
	 * Parse a descriptor and return the corresponding type
	 *
	 * @param input
	 *            the Shesmu string (as generated by {@link #descriptor()}
	 * @param output
	 *            the remaining subsequence of the input after parsing
	 * @return the parsed type; if the type is malformed, {@link #BAD} is returned
	 */
	public static Imyhat parse(CharSequence input, AtomicReference<CharSequence> output) {
		if (input.length() == 0) {
			output.set(input);
			return BAD;
		}
		switch (input.charAt(0)) {
		case 'b':
			output.set(input.subSequence(1, input.length()));
			return BOOLEAN;
		case 'd':
			output.set(input.subSequence(1, input.length()));
			return DATE;
		case 'i':
			output.set(input.subSequence(1, input.length()));
			return INTEGER;
		case 'p':
			output.set(input.subSequence(1, input.length()));
			return PATH;
		case 's':
			output.set(input.subSequence(1, input.length()));
			return STRING;
		case 'a':
			return parse(input.subSequence(1, input.length()), output).asList();
		case 't':
		case 'o':
			int count = 0;
			int index;
			for (index = 1; Character.isDigit(input.charAt(index)); index++) {
				count = 10 * count + Character.digit(input.charAt(index), 10);
			}
			if (count == 0) {
				return BAD;
			}
			output.set(input.subSequence(index, input.length()));
			if (input.charAt(0) == 't') {
				final Imyhat[] inner = new Imyhat[count];
				for (int i = 0; i < count; i++) {
					inner[i] = parse(output.get(), output);
				}
				return tuple(inner);
			} else {
				final List<Pair<String, Imyhat>> fields = new ArrayList<>();
				for (int i = 0; i < count; i++) {
					final StringBuilder name = new StringBuilder();
					int dollar = 0;
					while (output.get().charAt(dollar) != '$') {
						name.append(output.get().charAt(dollar));
						dollar++;
					}
					output.set(output.get().subSequence(dollar + 1, output.get().length()));
					fields.add(new Pair<>(name.toString(), parse(output.get(), output)));
				}
				return new ObjectImyhat(fields.stream());
			}
		default:
			output.set(input);
			return BAD;
		}

	}

	/**
	 * Create a tuple type from the types of its elements.
	 *
	 * @param types
	 *            the element types, in order
	 */
	public static Imyhat tuple(Imyhat... types) {
		return new TupleImyhat(types);
	}

	/**
	 * Create a list type containing the current type.
	 */
	public final Imyhat asList() {
		return new ListImyhat(this);
	}

	/**
	 * The ASM/Java type for bytecode generation
	 */
	public abstract Type asmType();

	/**
	 * The ASM/Java type of the wrapper class for bytecode generation
	 *
	 * For object types, this is the same as {{@link #asmType()}; for primitive
	 * types, this is an appropriate wrapper type.
	 */
	public Type boxedAsmType() {
		return asmType();
	}

	/**
	 * Create a comparator for sorting sets.
	 */
	public abstract Comparator<?> comparator();

	public abstract void consume(ImyhatDispatcher dispatcher, Object value);

	/**
	 * Create a machine-friendly string describing this type.
	 *
	 * @see #parse(CharSequence)
	 */
	public abstract String descriptor();

	/**
	 * Check if this type is malformed
	 */
	public abstract boolean isBad();

	/**
	 * Check if this type can be ordered.
	 *
	 * It must implement the {@link Comparable} interface.
	 */
	public abstract boolean isOrderable();

	/**
	 * Checks if two types are the same.
	 *
	 * This is not the same as {@link #equals(Object)} since bad types are never
	 * equivalent.
	 */
	public abstract boolean isSame(Imyhat other);

	/**
	 * Produce a string containing JavaScript code capable of parsing this type in
	 * the UI.
	 */
	public abstract String javaScriptParser();

	/**
	 * Get the matching Java type for this type
	 *
	 * The Java type will be less descriptive than this type due to erasure.
	 */
	public abstract Class<?> javaType();

	/**
	 * Create a human-friendly string describing this type.
	 */
	public abstract String name();

	public <T> Set<T> newSet() {
		@SuppressWarnings("unchecked")
		final Comparator<T> comparator = (Comparator<T>) comparator();
		return new TreeSet<>(comparator);
	}

	protected abstract void packJson(ArrayNode array, Object value);

	/**
	 * Write an instance of this type into JSON
	 *
	 * @param node
	 *            the JSON object to write to
	 * @param key
	 *            the JSON property to set
	 * @param value
	 *            the value to write
	 */
	public abstract void packJson(ObjectNode node, String key, Object value);

	public abstract void streamJson(GeneratorAdapter method);

	@SuppressWarnings("unchecked")
	public final <T> Collector<T, ?, TreeSet<T>> toSet() {
		final Comparator<T> comparator = (Comparator<T>) comparator();
		return Collectors.toCollection(() -> new TreeSet<T>(comparator));
	}

	@Override
	public final String toString() {
		return descriptor();
	}

	/**
	 * Convert a Shesmu type to its equivalent WDL type
	 */
	public abstract String wdlInputType();

	/**
	 * Extract a value stored in a JSON document into the matching Java object for
	 * this type
	 */
	public abstract Object unpackJson(JsonNode node);
}

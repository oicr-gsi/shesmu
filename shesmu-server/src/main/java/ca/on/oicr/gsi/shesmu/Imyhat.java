package ca.on.oicr.gsi.shesmu;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.text.html.HTMLDocument.Iterator;

import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Types as represented in Shesmu
 *
 * This word means “which/pattern of conduct” in ancient Egyptian to avoid
 * naming conflicts with other classes named type.
 *
 * Java's {@link Class} (and, by extension, ASM {@link Type}) are unsuitable for
 * this purpose because generic erasure has happened. Shesmu types also have an
 * interchange string format, called a signature.
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
		public String signature() {
			return "a" + inner.signature();
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
			return Stream.of(types).map(Imyhat::javaScriptParser)
					.collect(Collectors.joining(",", "parser.t([", "])"));
		}

		@Override
		public Class<?> javaType() {
			return Tuple.class;
		}

		@Override
		public String name() {
			return Arrays.stream(types).map(Imyhat::name).collect(Collectors.joining(",", "{", "}"));
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
		public String signature() {
			return Arrays.stream(types).map(Imyhat::signature).collect(Collectors.joining("", "t" + types.length, ""));
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
	}

	private static final Type A_INSTANT_TYPE = Type.getType(Instant.class);
	private static final Type A_ITERATOR_TYPE = Type.getType(Iterator.class);
	private static final Type A_JSON_GENERATOR_TYPE = Type.getType(JsonGenerator.class);

	private static final Type A_SET_TYPE = Type.getType(Set.class);

	private static final Type A_STRING_TYPE = Type.getType(String.class);
	private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
	public static final Imyhat BAD = new Imyhat() {

		@Override
		public Type asmType() {
			return Type.VOID_TYPE;
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
		public String signature() {
			return "$";
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
		public Object defaultValue() {
			return false;
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
		public String signature() {
			return "b";
		}

		@Override
		public void streamJson(GeneratorAdapter method) {
			method.invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_JSON_GENERATOR__WRITE_BOOLEAN);
		}

		@Override
		public Object unpackJson(JsonNode node) {
			return node.asBoolean();
		}

	};

	private static final Map<String, CallSite> callsites = new HashMap<>();

	public static final BaseImyhat DATE = new BaseImyhat() {

		@Override
		public Type asmType() {
			return A_INSTANT_TYPE;
		}

		@Override
		public Object defaultValue() {
			return Instant.EPOCH;
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
		public String signature() {
			return "d";
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
		public Object defaultValue() {
			return 0L;
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
		public String signature() {
			return "i";
		}

		@Override
		public void streamJson(GeneratorAdapter method) {
			method.invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_JSON_GENERATOR__WRITE_NUMBER);
		}

		@Override
		public Object unpackJson(JsonNode node) {
			return node.asLong();
		}

	};
	protected static final Method METHOD_INSTANT__TO_EPOCH_MILLI = new Method("toEpochMilli", Type.LONG_TYPE,
			new Type[] {});

	private static final Method METHOD_ITERATOR__HAS_NEXT = new Method("hasNext", Type.BOOLEAN_TYPE, new Type[] {});

	private static final Method METHOD_ITERATOR__NEXT = new Method("next", Type.getType(Object.class), new Type[] {});
	private static final Method METHOD_JSON_GENERATOR__ARRAY_END = new Method("writeArrayEnd", Type.VOID_TYPE,
			new Type[] {});

	private static final Method METHOD_JSON_GENERATOR__ARRAY_START = new Method("writeArrayStart", Type.VOID_TYPE,
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
		public Object defaultValue() {
			return "";
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
		public String signature() {
			return "s";
		}

		@Override
		public void streamJson(GeneratorAdapter method) {
			method.invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_JSON_GENERATOR__WRITE_STRING);
		}

		@Override
		public Object unpackJson(JsonNode node) {
			return node.asText();
		}
	};

	/**
	 * A bootstrap method that returns the appropriate {@link Imyhat} from a
	 * signature.
	 *
	 * @param signature
	 *            the method name, which is the type signature; signatures are
	 *            guaranteed to be valid JVM identifiers
	 * @param type
	 *            the type of this call site, which must take no arguments and
	 *            return {@link Imyhat}
	 * @return
	 */
	@RuntimeInterop
	public static CallSite bootstrap(Lookup lookup, String signature, MethodType type) {
		if (!type.returnType().equals(Imyhat.class)) {
			throw new IllegalArgumentException("Method cannot return non-Imyhat type.");
		}
		if (type.parameterCount() != 0) {
			throw new IllegalArgumentException("Method cannot take parameters.");
		}
		if (callsites.containsKey(signature)) {
			return callsites.get(signature);
		}
		final Imyhat imyhat = parse(signature);
		if (imyhat.isBad()) {
			throw new IllegalArgumentException("Bad type signature: " + signature);
		}
		final CallSite callsite = new ConstantCallSite(MethodHandles.constant(Imyhat.class, imyhat));
		callsites.put(signature, callsite);
		return callsite;
	}

	/**
	 * Parse a signature which must be one of the base types (no lists or tuples)
	 */
	public static BaseImyhat forName(String s) {
		return Stream.of(BOOLEAN, DATE, INTEGER, STRING).filter(t -> t.name().equals(s)).findAny()
				.orElseThrow(() -> new IllegalArgumentException(String.format("No such base type %s.", s)));
	}

	/**
	 * Parse a string-representation of a type
	 *
	 * @param input
	 *            the Shesmu string (as generated by {@link #signature()}
	 * @return the parsed type; if the type is malformed, {@link #BAD} is returned
	 */
	@RuntimeInterop
	public static Imyhat parse(CharSequence input) {
		final AtomicReference<CharSequence> output = new AtomicReference<>();
		final Imyhat result = parse(input, output);
		return output.get().length() == 0 ? result : BAD;
	}

	/**
	 * Parse a signature and return the corresponding type
	 *
	 * @param input
	 *            the Shesmu string (as generated by {@link #signature()}
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
		case 's':
			output.set(input.subSequence(1, input.length()));
			return STRING;
		case 'a':
			return parse(input.subSequence(1, input.length()), output).asList();
		case 't':
			int count = 0;
			int index;
			for (index = 1; Character.isDigit(input.charAt(index)); index++) {
				count = 10 * count + Character.digit(input.charAt(index), 10);
			}
			if (count == 0) {
				return BAD;
			}
			final Imyhat[] inner = new Imyhat[count];
			output.set(input.subSequence(index, input.length()));
			for (int i = 0; i < count; i++) {
				inner[i] = parse(output.get(), output);
			}
			return tuple(inner);
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

	/**
	 * Create a machine-friendly string describing this type.
	 *
	 * @see #parse(CharSequence)
	 */
	public abstract String signature();

	public abstract void streamJson(GeneratorAdapter method);

	@Override
	public final String toString() {
		return signature();
	}

	/**
	 * Extract a value stored in a JSON document into the matching Java object for
	 * this type
	 */
	public abstract Object unpackJson(JsonNode node);
}

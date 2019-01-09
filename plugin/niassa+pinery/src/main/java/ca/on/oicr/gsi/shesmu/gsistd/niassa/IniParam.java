package ca.on.oicr.gsi.shesmu.gsistd.niassa;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import ca.on.oicr.gsi.shesmu.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.runtime.Tuple;

/**
 * Creates a parameter that will be formatted and saved as INI parameter for a
 * {@link WorkflowAction}
 */
public final class IniParam implements ActionParameterDefinition {
	/**
	 * Convert a date to the specified format, in UTC.
	 *
	 * @param format
	 *            a format understandable by
	 *            {@link DateTimeFormatter#ofPattern(String)}
	 */
	public static final class DateStringifier extends Stringifier {
		private final String format;

		public DateStringifier(String format) {
			this.format = format;
		}

		@Override
		public void stringify(GeneratorAdapter methodGen, Type actionType, int actionLocal) {
			methodGen.push(format);
			methodGen.invokeStatic(A_RUNTIME_SUPPORT_TYPE, RUNTIME_SUPPORT__TO_STRING);
		}

		@Override
		public Imyhat type() {
			return Imyhat.DATE;
		}
	}

	/**
	 * Convert a list of items into a delimited string
	 *
	 * No attempt is made to check that the items do not contain the delimiter
	 *
	 * @param delimiter
	 *            the delimiter between the items
	 * @param inner
	 *            the type of the items to be concatenated
	 */
	public static final class ListStringifier extends Stringifier {
		private final String delimiter;
		private final Stringifier inner;

		public ListStringifier(String delimiter, Stringifier inner) {
			super();
			this.delimiter = delimiter;
			this.inner = inner;
		}

		@Override
		public void stringify(GeneratorAdapter methodGen, Type actionType, int actionLocal) {
			final int iterator = methodGen.newLocal(A_ITERATOR_TYPE);
			methodGen.invokeInterface(A_SET_TYPE, SET__ITERATOR);
			methodGen.storeLocal(iterator);
			final int stringBuilder = methodGen.newLocal(A_STRING_BUILDER_TYPE);
			final int first = methodGen.newLocal(Type.BOOLEAN_TYPE);
			methodGen.push(true);
			methodGen.storeLocal(first);
			methodGen.newInstance(A_STRING_BUILDER_TYPE);
			methodGen.storeLocal(stringBuilder);
			methodGen.loadLocal(stringBuilder);
			methodGen.invokeConstructor(A_STRING_BUILDER_TYPE, DEFAULT_CTOR);

			final Label end = methodGen.newLabel();
			final Label start = methodGen.mark();

			methodGen.loadLocal(iterator);
			methodGen.invokeInterface(A_ITERATOR_TYPE, ITERATOR__HAS_NEXT);
			methodGen.ifZCmp(GeneratorAdapter.EQ, end);

			methodGen.loadLocal(stringBuilder);

			final Label convertItem = methodGen.newLabel();
			methodGen.loadLocal(first);
			methodGen.ifZCmp(GeneratorAdapter.NE, convertItem);
			methodGen.push(delimiter);
			methodGen.invokeVirtual(A_STRING_BUILDER_TYPE, STRING_BUILDER__APPEND);
			methodGen.mark(convertItem);

			methodGen.push(false);
			methodGen.storeLocal(first);

			methodGen.loadLocal(iterator);
			methodGen.invokeInterface(A_ITERATOR_TYPE, ITERATOR__NEXT);
			methodGen.unbox(inner.type().asmType());
			inner.stringify(methodGen, actionType, actionLocal);
			methodGen.invokeVirtual(A_STRING_BUILDER_TYPE, STRING_BUILDER__APPEND);
			methodGen.pop();

			methodGen.goTo(start);
			methodGen.mark(end);

			methodGen.loadLocal(stringBuilder);
			methodGen.invokeVirtual(A_STRING_BUILDER_TYPE, OBJECT__TO_STRING);
		}

		@Override
		public Imyhat type() {
			return inner.type().asList();
		}

	}

	@JsonDeserialize(using = StringifierDeserializer.class)
	public static abstract class Stringifier {
		public abstract void stringify(GeneratorAdapter methodGen, Type actionType, int actionLocal);

		public abstract Imyhat type();
	}

	public static class StringifierDeserializer extends JsonDeserializer<Stringifier> {

		private Stringifier deserialize(JsonNode node) {
			if (node.isTextual()) {
				final String str = node.asText();
				switch (str) {
				case "boolean":
					return BOOLEAN;
				case "fileSWID":
					return FILE_SWID;
				case "integer":
					return INTEGER;
				case "path":
					return PATH;
				case "processingSWID":
					return PROCESSING_SWID;
				case "string":
					return STRING;
				default:
					throw new IllegalArgumentException("Unknown INI type: " + str);
				}
			}
			if (node.isNumber()) {
				return new UnitCorrectedIntegerStringifier(node.asInt());
			}
			if (node.isObject()) {
				final String type = node.get("is").asText();
				switch (type) {

				case "date":
					return new DateStringifier(node.get("format").asText());
				case "list":
					return new ListStringifier(node.get("delimiter").asText(), deserialize(node.get("of")));
				case "tuple":
					return new TupleStringifier(node.get("delimiter").asText(),
							RuntimeSupport.stream(node.get("of")).map(this::deserialize).toArray(Stringifier[]::new));
				default:
					throw new IllegalArgumentException("Unknown INI type: " + type);
				}
			}
			throw new IllegalArgumentException("Cannot parse INI type: " + node.getNodeType());
		}

		@Override
		public Stringifier deserialize(JsonParser parser, DeserializationContext context)
				throws IOException, JsonProcessingException {
			final ObjectCodec oc = parser.getCodec();
			final JsonNode node = oc.readTree(parser);
			return deserialize(node);
		}
	}

	/**
	 * Concatenate a tuple of different items as a delimited string
	 *
	 * @param delimiter
	 *            the delimiter between the items
	 * @param inner
	 *            the items in the tuple
	 */
	public static class TupleStringifier extends Stringifier {
		private final String delimiter;
		private final Stringifier[] inner;

		public TupleStringifier(String delimiter, Stringifier[] inner) {
			super();
			this.delimiter = delimiter;
			this.inner = inner;
		}

		@Override
		public void stringify(GeneratorAdapter methodGen, Type actionType, int actionLocal) {
			final int stringBuilder = methodGen.newLocal(A_STRING_BUILDER_TYPE);
			methodGen.newInstance(A_STRING_BUILDER_TYPE);
			methodGen.storeLocal(stringBuilder);
			methodGen.loadLocal(stringBuilder);
			methodGen.invokeConstructor(A_STRING_BUILDER_TYPE, DEFAULT_CTOR);

			for (int i = 0; i < inner.length; i++) {
				if (i < inner.length - 1) {
					methodGen.dup();
				}
				methodGen.push(i);
				methodGen.invokeVirtual(A_TUPLE_TYPE, TUPLE__GET);
				inner[i].stringify(methodGen, actionType, actionLocal);
				methodGen.loadLocal(stringBuilder);
				if (i > 0) {
					methodGen.push(delimiter);
					methodGen.invokeVirtual(A_STRING_BUILDER_TYPE, STRING_BUILDER__APPEND);
				}
				methodGen.swap();
				methodGen.invokeVirtual(A_STRING_BUILDER_TYPE, STRING_BUILDER__APPEND);
				methodGen.pop();
			}
			methodGen.loadLocal(stringBuilder);
			methodGen.invokeVirtual(A_STRING_BUILDER_TYPE, OBJECT__TO_STRING);
		}

		@Override
		public Imyhat type() {
			return Imyhat.tuple(Stream.of(inner).map(Stringifier::type).toArray(Imyhat[]::new));
		}
	}

	/**
	 * Save an integer, but first correct the units
	 *
	 * We have this problem where workflows use different units as parameters (e.g.,
	 * memory is in megabytes). We want all values in Shesmu to be specified in base
	 * units (bytes, bases) because it has convenient suffixes. This will divide the
	 * value specified into those units and round accordingly so the user never has
	 * to be concerned about this.
	 *
	 * @param factor
	 *            the units of the target value (i.e., 1024*1024 for a value in
	 *            megabytes)
	 */
	public static class UnitCorrectedIntegerStringifier extends Stringifier {

		private final int factor;

		public UnitCorrectedIntegerStringifier(int factor) {
			this.factor = factor;
		}

		@Override
		public void stringify(GeneratorAdapter methodGen, Type actionType, int actionLocal) {
			methodGen.push(factor);
			methodGen.invokeStatic(A_INI_TYPE, INI__CORRECT_LONG);
		}

		@Override
		public Imyhat type() {
			return Imyhat.INTEGER;
		}
	}

	private static final Type A_BOOLEAN_TYPE = Type.getType(Boolean.class);

	private static final Type A_INI_TYPE = Type.getType(IniParam.class);

	private static final Type A_INSTANT_TYPE = Type.getType(Instant.class);

	private static final Type A_ITERATOR_TYPE = Type.getType(Iterator.class);

	private static final Type A_LONG_TYPE = Type.getType(Long.class);

	private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
	private static final Type A_PROPERTIES_TYPE = Type.getType(Properties.class);
	private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);

	private static final Type A_SET_TYPE = Type.getType(Set.class);

	private static final Type A_STRING_BUILDER_TYPE = Type.getType(StringBuilder.class);
	private static final Type A_STRING_TYPE = Type.getType(String.class);

	private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);

	/**
	 * Save a Boolean value as "true" or "false"
	 */
	public static final Stringifier BOOLEAN = new Stringifier() {

		@Override
		public void stringify(GeneratorAdapter methodGen, Type actionType, int actionLocal) {
			methodGen.invokeStatic(A_BOOLEAN_TYPE, BOOLEAN__TO_STRING);
		}

		@Override
		public Imyhat type() {
			return Imyhat.BOOLEAN;
		}

	};

	private static final Method BOOLEAN__TO_STRING = new Method("toString", A_STRING_TYPE,
			new Type[] { Type.BOOLEAN_TYPE });

	private static final Method DEFAULT_CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] {});
	/**
	 * Save a file SWID
	 */
	public static final Stringifier FILE_SWID = new Stringifier() {

		@Override
		public void stringify(GeneratorAdapter methodGen, Type actionType, int actionLocal) {
			methodGen.loadLocal(actionLocal);
			methodGen.swap();
			methodGen.invokeVirtual(actionType, SQWACTION__ADD_FILE_SWID);
		}

		@Override
		public Imyhat type() {
			return Imyhat.STRING;
		}

	};
	private static final Method INI__CORRECT_LONG = new Method("correctInteger", A_STRING_TYPE,
			new Type[] { Type.LONG_TYPE, Type.INT_TYPE });
	/**
	 * Save an integer in the way you'd expect
	 */
	public static final Stringifier INTEGER = new Stringifier() {

		@Override
		public void stringify(GeneratorAdapter methodGen, Type actionType, int actionLocal) {
			methodGen.invokeStatic(A_LONG_TYPE, LONG__TO_STRING);
		}

		@Override
		public Imyhat type() {
			return Imyhat.INTEGER;
		}

	};

	private static final Method ITERATOR__HAS_NEXT = new Method("hasNext", Type.BOOLEAN_TYPE, new Type[] {});

	private static final Method ITERATOR__NEXT = new Method("next", A_OBJECT_TYPE, new Type[] {});
	private static final Method LONG__TO_STRING = new Method("toString", A_STRING_TYPE, new Type[] { Type.LONG_TYPE });
	private static final Method OBJECT__TO_STRING = new Method("toString", A_STRING_TYPE, new Type[] {});
	/**
	 * Save a processing SWID
	 */
	public static final Stringifier PROCESSING_SWID = new Stringifier() {

		@Override
		public void stringify(GeneratorAdapter methodGen, Type actionType, int actionLocal) {
			methodGen.loadLocal(actionLocal);
			methodGen.swap();
			methodGen.invokeVirtual(actionType, SQWACTION__ADD_PROCESSING_SWID);
		}

		@Override
		public Imyhat type() {
			return Imyhat.STRING;
		}

	};
	private static final Method PROPERTIES__PUT = new Method("put", A_OBJECT_TYPE,
			new Type[] { A_OBJECT_TYPE, A_OBJECT_TYPE });

	private static final Method RUNTIME_SUPPORT__TO_STRING = new Method("toString", A_STRING_TYPE,
			new Type[] { A_INSTANT_TYPE, A_STRING_TYPE });

	private static final Method SET__ITERATOR = new Method("iterator", A_ITERATOR_TYPE, new Type[] {});

	protected static final Method SQWACTION__ADD_FILE_SWID = new Method("addFileSwid", A_STRING_TYPE,
			new Type[] { A_STRING_TYPE });

	protected static final Method SQWACTION__ADD_PROCESSING_SWID = new Method("addProcessingSwid", A_STRING_TYPE,
			new Type[] { A_STRING_TYPE });

	/**
	 * Save a string exactly as it is passed by the user
	 */
	public static final Stringifier STRING = new Stringifier() {

		@Override
		public void stringify(GeneratorAdapter methodGen, Type actionType, int actionLocal) {
			// Do nothing.
		}

		@Override
		public Imyhat type() {
			return Imyhat.STRING;
		}

	};
	/**
	 * Save a path
	 */
	public static final Stringifier PATH = new Stringifier() {

		@Override
		public void stringify(GeneratorAdapter methodGen, Type actionType, int actionLocal) {
			methodGen.invokeVirtual(A_OBJECT_TYPE, OBJECT__TO_STRING);
		}

		@Override
		public Imyhat type() {
			return Imyhat.PATH;
		}

	};
	private static final Method STRING_BUILDER__APPEND = new Method("append", A_STRING_BUILDER_TYPE,
			new Type[] { A_STRING_TYPE });

	private static final Method TUPLE__GET = new Method("get", A_OBJECT_TYPE, new Type[] { Type.INT_TYPE });

	@RuntimeInterop
	public static String correctInteger(long value, int factor) {
		if (value == 0) {
			return "0";
		}
		int round;
		if (value % factor == 0) {
			round = 0;
		} else {
			round = value < 0 ? -1 : 1;
		}
		return Long.toString(value / factor + round);
	}

	private String iniName;

	private String name;

	private boolean required;

	private Stringifier type;

	public IniParam() {
	}

	public String getIniName() {
		return iniName;
	}

	public String getName() {
		return name;
	}

	public Stringifier getType() {
		return type;
	}

	public boolean isRequired() {
		return required;
	}

	@Override
	public final String name() {
		return name;
	}

	@Override
	public final boolean required() {
		return required;
	}

	public void setIniName(String iniName) {
		this.iniName = iniName;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setRequired(boolean required) {
		this.required = required;
	}

	public void setType(Stringifier type) {
		this.type = type;
	}

	@Override
	public final void store(Renderer renderer, Type actionType, int actionLocal, Consumer<Renderer> loadParameter) {
		renderer.methodGen().loadLocal(actionLocal);
		renderer.methodGen().getField(actionType, "ini", A_PROPERTIES_TYPE);
		renderer.methodGen().push(iniName == null ? name : iniName);
		loadParameter.accept(renderer);
		type.stringify(renderer.methodGen(), actionType, actionLocal);
		renderer.methodGen().invokeVirtual(A_PROPERTIES_TYPE, PROPERTIES__PUT);
		renderer.methodGen().pop();
	}

	@Override
	public final Imyhat type() {
		return type.type();
	}

}

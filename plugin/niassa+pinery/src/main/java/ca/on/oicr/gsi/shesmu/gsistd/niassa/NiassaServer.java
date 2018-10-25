package ca.on.oicr.gsi.shesmu.gsistd.niassa;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.provenance.model.LimsKey;
import ca.on.oicr.gsi.shesmu.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedConfiguration;
import ca.on.oicr.gsi.shesmu.util.definitions.UserDefiner;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;

public class NiassaServer extends AutoUpdatingJsonFile<Configuration> implements FileBackedConfiguration {
	private static final Map<Class<? extends WorkflowAction<?>>, List<ActionParameterDefinition>> EXTRA_PARAMS = new ConcurrentHashMap<>();
	private static final Type A_ACP_TYPE = Type.getType(NiassaServer.class);
	private static final Type A_SET_TYPE = Type.getType(Set.class);
	private static final Type A_STRING_TYPE = Type.getType(String.class);
	private static final Method ACP__PARSE_LONGS = new Method("parseLongs", A_STRING_TYPE, new Type[] { A_SET_TYPE });

	public static String parseLongs(Set<String> ids) {
		return ids.stream().map(Long::parseUnsignedLong)//
				.sorted()//
				.map(Object::toString)//
				.collect(Collectors.joining(","));
	}

	private static Stream<ActionParameterDefinition> extraParameters(Class<? extends WorkflowAction<?>> clazz) {
		return EXTRA_PARAMS.computeIfAbsent(clazz, c -> {
			List<ActionParameterDefinition> parameters = new ArrayList<>();

			for (Field field : clazz.getFields()) {
				AccessionCollection annotation = field.getAnnotation(AccessionCollection.class);
				if (annotation == null)
					continue;
				if (!Modifier.isPublic(field.getModifiers()) || Modifier.isStatic(field.getModifiers())
						|| !field.getType().isAssignableFrom(String.class)) {
					throw new IllegalArgumentException(String.format(
							"Field %s in %s is annotated for a SWID collection, but it's not a public string.",
							field.getName(), clazz.getName()));
				}
				String name = annotation.name().isEmpty() ? field.getName() : annotation.name();
				parameters.add(new ActionParameterDefinition() {

					@Override
					public String name() {
						return name;
					}

					@Override
					public boolean required() {
						return true;
					}

					@Override
					public void store(Renderer renderer, Type type, int actionLocal, Consumer<Renderer> loadParameter) {
						renderer.methodGen().loadLocal(actionLocal);
						loadParameter.accept(renderer);
						renderer.methodGen().invokeStatic(A_ACP_TYPE, ACP__PARSE_LONGS);
						renderer.methodGen().putField(type, field.getName(), A_STRING_TYPE);

					}

					@Override
					public Imyhat type() {
						return Imyhat.STRING.asList();
					}
				});
			}

			return parameters;

		}).stream();
	}

	private Optional<Configuration> configuration = Optional.empty();
	private final UserDefiner definer;

	public NiassaServer(Path fileName, UserDefiner definer) {
		super(fileName, Configuration.class);
		this.definer = definer;
	}

	@Override
	public ConfigurationSection configuration() {
		return new ConfigurationSection(fileName().toString()) {

			@Override
			public void emit(SectionRenderer renderer) throws XMLStreamException {
				renderer.line("Filename", fileName().toString());
				configuration.ifPresent(c -> {
					renderer.line("JAR File", c.getJar());
					renderer.line("Settings", c.getSettings());
					renderer.line("Registered Workflows Count", c.getWorkflows().length);
				});

			}
		};
	}

	@Override
	protected Optional<Integer> update(Configuration value) {
		definer.clearActions();
		for (final WorkflowConfiguration wc : value.getWorkflows()) {
			WorkflowAction.MAX_IN_FLIGHT.putIfAbsent(wc.getAccession(), new Semaphore(wc.getMaxInFlight()));
			final String description = //
					String.format("Runs SeqWare/Niassa workflow %d using %s with settings in %s.", wc.getAccession(), //
							value.getJar(), //
							value.getSettings())
							+ (wc.getPreviousAccessions().length == 0 ? ""
									: LongStream.of(wc.getPreviousAccessions())//
											.sorted()//
											.mapToObj(Long::toString)//
											.collect(Collectors.joining(", ", " Considered equivalent to workflows: ",
													"")));
			wc.getType().define(new WorkflowType.JavaGenericTypeSafeInstantiator() {

				@Override
				public <T extends WorkflowAction<K>, K extends LimsKey> void define(Class<T> clazz,
						Supplier<T> supplier) {
					definer.defineAction(wc.getName(), //
							description, //
							clazz, //
							supplier, //
							Stream.concat(wc.getType().parameters(), extraParameters(clazz)));
				}
			}, wc.getAccession(), wc.getPreviousAccessions(), value.getJar(), value.getSettings(), wc.getServices());
		}
		configuration = Optional.of(value);
		return Optional.empty();
	}
}
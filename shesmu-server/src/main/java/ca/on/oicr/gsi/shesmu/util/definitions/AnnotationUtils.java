package ca.on.oicr.gsi.shesmu.util.definitions;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.objectweb.asm.Type;

import ca.on.oicr.gsi.shesmu.Action;
import ca.on.oicr.gsi.shesmu.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;

public final class AnnotationUtils {
	private static Map<Class<? extends Action>, List<ActionParameterDefinition>> ACTION_PARAMETERS = new ConcurrentHashMap<>();

	private static final Pattern VALID_SHESMU_NAME = Pattern.compile("[a-z][a-z_]*");

	private static final Pattern VALID_SHESMU_NAME_WITH_INSTANCE = Pattern.compile("([a-z][a-z_]*)?\\$[a-z_]*");

	public static String checkName(String annotationName, Method method, boolean isInstance) {
		final String name = annotationName.isEmpty() ? method.getName() : annotationName;
		final Matcher m = (isInstance ? VALID_SHESMU_NAME_WITH_INSTANCE : VALID_SHESMU_NAME).matcher(name);
		if (!m.matches()) {
			throw new IllegalArgumentException(String.format("Method %s of %s is not a valid Shesmu name.",
					method.getName(), method.getDeclaringClass().getName()));
		}
		return name;
	}

	public static Stream<ActionParameterDefinition> findActionDefinitionsByAnnotation(Class<? extends Action> clazz) {
		return ACTION_PARAMETERS.computeIfAbsent(clazz, actionType -> {
			final List<ActionParameterDefinition> parameters = new ArrayList<>();

			for (final Field field : actionType.getFields()) {
				final ActionParameter fieldAnnotation = field.getAnnotation(ActionParameter.class);
				if (fieldAnnotation == null) {
					continue;
				}
				if (!Modifier.isPublic(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) {
					throw new IllegalArgumentException(String.format(
							"Field %s in %s is annotated with ShesmuParameter, but not a public instance field.",
							field.getName(), field.getDeclaringClass().getName()));
				}
				final String fieldName = fieldAnnotation.name().isEmpty() ? field.getName() : fieldAnnotation.name();
				final Matcher m = VALID_SHESMU_NAME.matcher(fieldName);
				if (!m.matches()) {
					throw new IllegalArgumentException(String.format("Field %s of %s is not a valid Shesmu name.",
							field.getName(), field.getDeclaringClass().getName()));
				}

				final Imyhat fieldType = Imyhat.convert(
						String.format("Field %s in %s", field.getName(), field.getDeclaringClass().getName()),
						fieldAnnotation.type(), field.getType());
				parameters.add(ActionParameterDefinition.forField(fieldName, field.getName(), fieldType,
						fieldAnnotation.required()));
			}

			for (final Method setter : actionType.getMethods()) {
				final ActionParameter setterAnnotation = setter.getAnnotation(ActionParameter.class);
				if (setterAnnotation == null) {
					continue;
				}
				if (!Modifier.isPublic(setter.getModifiers()) || Modifier.isStatic(setter.getModifiers())
						|| !setter.getReturnType().equals(void.class) || setter.getParameterCount() != 1) {
					throw new IllegalArgumentException(String.format(
							"Setter %s in %s is annotated with ShesmuParameter, but not a public instance method with no return type and one parameter.",
							setter.getName(), setter.getDeclaringClass().getName()));
				}
				final String setterName = checkName(setterAnnotation.name(), setter, false);
				final Imyhat setterType = Imyhat.convert(
						String.format("Setter %s in %s", setter.getName(), setter.getDeclaringClass().getName()),
						setterAnnotation.type(), setter.getReturnType());
				final org.objectweb.asm.commons.Method asmMethod = org.objectweb.asm.commons.Method.getMethod(setter);
				parameters.add(new ActionParameterDefinition() {

					@Override
					public String name() {
						return setterName;
					}

					@Override
					public boolean required() {
						return setterAnnotation.required();
					}

					@Override
					public void store(Renderer renderer, Type owner, int actionLocal,
							Consumer<Renderer> loadParameter) {
						renderer.methodGen().loadArg(actionLocal);
						loadParameter.accept(renderer);
						renderer.methodGen().invokeVirtual(owner, asmMethod);

					}

					@Override
					public Imyhat type() {
						return setterType;
					}
				});

			}

			final JsonActionParameter[] jsonParameters = actionType.getAnnotationsByType(JsonActionParameter.class);
			if (jsonParameters.length > 0) {
				if (!JsonParameterised.class.isAssignableFrom(actionType)) {
					throw new IllegalArgumentException(String.format(
							"Action class %s is annotated with JSON parameters but doesn't implement JsonParameterised interface.",
							actionType.getName()));
				}
				for (final JsonActionParameter jsonParameter : jsonParameters) {
					parameters.add(new JsonParameter(jsonParameter.name(), Imyhat.parse(jsonParameter.type()),
							jsonParameter.required()));
				}
			}
			return parameters;
		}).stream();
	}

	private AnnotationUtils() {
	}
}

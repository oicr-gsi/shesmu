package ca.on.oicr.gsi.shesmu.util.definitions;

import ca.on.oicr.gsi.shesmu.Action;
import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionParameter;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.WatchedFileListener;
import ca.on.oicr.gsi.status.ConfigurationSection;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
 * A source of actions, constants, and functions based on configuration files where every
 * configuration file can create definitions using annotations on methods.
 *
 * @param <T>the configuration file representation
 */
public abstract class FileBackedMatchedDefinitionRepository<T extends FileBackedConfiguration>
    implements DefinitionRepository {
  private class Wrapper implements WatchedFileListener {
    private final List<ActionDefinition> actions;
    private final List<ConstantDefinition> constants;
    private final List<FunctionDefinition> functions;
    private final T instance;

    public Wrapper(Path path) {
      instance = ctor.apply(path);
      constants = binder.bindConstants(instance);
      functions = binder.bindFunctions(instance);
      actions = binder.bindActions(instance);
    }

    public Stream<ActionDefinition> actions() {
      return actions.stream();
    }

    public ConfigurationSection configuration() {
      return instance.configuration();
    }

    public Stream<ConstantDefinition> constants() {
      return constants.stream();
    }

    public Stream<FunctionDefinition> functions() {
      return functions.stream();
    }

    @Override
    public void start() {
      instance.start();
    }

    @Override
    public void stop() {
      instance.stop();
    }

    @Override
    public Optional<Integer> update() {
      return instance.update();
    }
  }

  private static String mangleDescription(String description) {
    return description.replace("%", "%%").replace("{instance}", "%1$s").replace("{file}", "%2$s");
  }

  private final List<ActionDefinition> actions = new ArrayList<>();
  private final RuntimeBinding<T> binder;
  private final AutoUpdatingDirectory<Wrapper> configuration;
  private final List<ConstantDefinition> constants = new ArrayList<>();
  private final Function<Path, T> ctor;

  private final List<FunctionDefinition> functions = new ArrayList<>();

  public FileBackedMatchedDefinitionRepository(
      Class<T> clazz, String extension, Function<Path, T> ctor) {
    this.ctor = ctor;
    binder = new RuntimeBinding<>(clazz, extension);
    if (!Modifier.isPublic(clazz.getModifiers())) {
      throw new IllegalArgumentException(String.format("Class %s is not public.", clazz.getName()));
    }
    for (final Method method : getClass().getMethods()) {
      checkRepositoryMethod(clazz, method);
      checkRepositoryAction(method);
    }
    for (final Method method : clazz.getMethods()) {
      checkInstanceMethod(method);
      checkInstanceAction(method);
    }
    configuration = new AutoUpdatingDirectory<>(extension, Wrapper::new);
  }

  @Override
  public final Stream<ActionDefinition> actions() {
    return Stream.concat(actions.stream(), configuration.stream().flatMap(Wrapper::actions));
  }

  private void checkInstanceAction(final Method method) {
    final ShesmuAction actionAnnotation = method.getAnnotation(ShesmuAction.class);
    if (actionAnnotation != null) {
      if (Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers())) {
        throw new IllegalArgumentException(
            String.format(
                "Method %s of %s has ShesmuAction annotation but is not public and vritual.",
                method.getName(), method.getDeclaringClass().getName()));
      }
      processMethod(actionAnnotation, method, true);
    }
  }

  private void checkInstanceMethod(final Method method) {
    final ShesmuMethod methodAnnotation = method.getAnnotation(ShesmuMethod.class);
    if (methodAnnotation != null) {
      if (Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers())) {
        throw new IllegalArgumentException(
            String.format(
                "Method %s of %s has ShesmuMethod annotation but is not public and virtual.",
                method.getName(), method.getDeclaringClass().getName()));
      }
      processMethod(methodAnnotation, method, true, 0);
    }
  }

  private void checkRepositoryAction(final Method method) {
    final ShesmuAction actionAnnotation = method.getAnnotation(ShesmuAction.class);
    if (actionAnnotation != null) {
      if (!Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers())) {
        throw new IllegalArgumentException(
            String.format(
                "Method %s of %s has ShesmuAction annotation but is not public and static.",
                method.getName(), method.getDeclaringClass().getName()));
      }
      processMethod(actionAnnotation, method, false);
    }
  }

  private void checkRepositoryMethod(Class<T> clazz, final Method method) {
    final ShesmuMethod methodAnnotation = method.getAnnotation(ShesmuMethod.class);
    if (methodAnnotation != null) {
      if (!Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers())) {
        throw new IllegalArgumentException(
            String.format(
                "Method %s of %s has annotation but is not public and static.",
                method.getName(), method.getDeclaringClass().getName()));
      }
      final boolean isInstance =
          method.getParameterCount() > 0 && method.getParameterTypes()[0].isAssignableFrom(clazz);
      processMethod(methodAnnotation, method, isInstance, isInstance ? 1 : 0);
    }
  }

  @Override
  public final Stream<ConstantDefinition> constants() {
    return Stream.concat(constants.stream(), configuration.stream().flatMap(Wrapper::constants));
  }

  @Override
  public final Stream<FunctionDefinition> functions() {
    return Stream.concat(functions.stream(), configuration.stream().flatMap(Wrapper::functions));
  }

  @Override
  public final Stream<ConfigurationSection> listConfiguration() {
    return configuration.stream().map(Wrapper::configuration);
  }

  private void processMethod(ShesmuAction annotation, Method method, boolean isInstance) {
    final String name = AnnotationUtils.checkName(annotation.name(), method, isInstance);
    if (!Action.class.isAssignableFrom(method.getReturnType())) {
      throw new IllegalArgumentException(
          String.format(
              "Method %s of %s is not an action.",
              method.getName(), method.getDeclaringClass().getName()));
    }
    final Type type = Type.getType(method.getReturnType());
    final Stream<ActionParameterDefinition> parameters =
        AnnotationUtils.findActionDefinitionsByAnnotation(
            method.getReturnType().asSubclass(Action.class));

    if (isInstance) {
      binder.action(
          name.replace("$", "%1$s"),
          method.getReturnType().asSubclass(Action.class),
          mangleDescription(annotation.description()),
          parameters.toArray(ActionParameterDefinition[]::new));
    } else {
      final org.objectweb.asm.commons.Method asmMethod =
          org.objectweb.asm.commons.Method.getMethod(method);
      final Type owner = Type.getType(method.getDeclaringClass());
      actions.add(
          new ActionDefinition(name, type, annotation.description(), parameters) {

            @Override
            public void initialize(GeneratorAdapter methodGen) {
              methodGen.invokeStatic(owner, asmMethod);
            }
          });
    }
  }

  private void processMethod(
      ShesmuMethod annotation, Method method, boolean isInstance, int offset) {
    final String name = AnnotationUtils.checkName(annotation.name(), method, isInstance);
    final Imyhat returnType =
        Imyhat.convert(
            String.format(
                "Return type of %s in %s", method.getName(), method.getDeclaringClass().getName()),
            annotation.type(),
            method.getReturnType());
    if (method.getParameterCount() == offset) {
      if (isInstance) {
        if (Modifier.isStatic(method.getModifiers())) {
          binder.staticConstant(
              name.replace("$", "%1$s"),
              method.getDeclaringClass(),
              method.getName(),
              returnType,
              mangleDescription(annotation.description()));
        } else {
          binder.constant(
              name.replace("$", "%1$s"),
              method.getName(),
              returnType,
              mangleDescription(annotation.description()));
        }
      } else {
        final Type type = Type.getType(method.getDeclaringClass());
        final org.objectweb.asm.commons.Method asmMethod =
            org.objectweb.asm.commons.Method.getMethod(method);
        constants.add(
            new ConstantDefinition(name, returnType, annotation.description()) {

              @Override
              protected void load(GeneratorAdapter methodGen) {
                methodGen.invokeStatic(type, asmMethod);
              }
            });
      }
    } else {
      final FunctionParameter[] functionParameters =
          Stream.of(method.getParameters())
              .skip(offset)
              .map(
                  p -> {
                    final ShesmuParameter parameterAnnotation =
                        p.getAnnotation(ShesmuParameter.class);
                    final Imyhat type =
                        Imyhat.convert(
                            String.format(
                                "Parameter %s from %s in %s",
                                p.getName(),
                                method.getName(),
                                method.getDeclaringClass().getName()),
                            parameterAnnotation == null ? "" : parameterAnnotation.type(),
                            p.getType());
                    return new FunctionParameter(
                        parameterAnnotation == null
                            ? p.getName()
                            : parameterAnnotation.description(),
                        type);
                  })
              .toArray(FunctionParameter[]::new);
      if (isInstance) {
        if (Modifier.isStatic(method.getModifiers())) {
          binder.staticFunction(
              name.replace("$", "%1$s"),
              method.getDeclaringClass(),
              method.getName(),
              returnType,
              mangleDescription(annotation.description()),
              functionParameters);
        } else {
          binder.function(
              name.replace("$", "%1$s"),
              method.getName(),
              returnType,
              mangleDescription(annotation.description()),
              functionParameters);
        }
      } else {
        functions.add(
            FunctionDefinition.staticMethod(
                name,
                method.getDeclaringClass(),
                method.getName(),
                annotation.description(),
                returnType,
                functionParameters));
      }
    }
  }
}

package ca.on.oicr.gsi.shesmu.server.plugins;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import ca.on.oicr.gsi.shesmu.compiler.TypeUtils;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.CustomActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.JsonActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.JsonParameterisedAction;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public final class InvokeDynamicActionParameterDescriptor implements ActionParameterDefinition {
  private static final Type A_ACTION_TYPE = Type.getType(Action.class);

  private static Map<Class<? extends Action>, List<ActionParameterDefinition>> ACTION_PARAMETERS =
      new ConcurrentHashMap<>();

  private static final Handle BSM_HANDLE =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          Type.getType(InvokeDynamicActionParameterDescriptor.class).getInternalName(),
          "bootstrap",
          Type.getMethodDescriptor(
              Type.getType(CallSite.class),
              Type.getType(Lookup.class),
              Type.getType(String.class),
              Type.getType(MethodType.class),
              Type.getType(String.class)),
          false);

  private static final MethodHandle MH_STORE;

  private static final CallSiteRegistry<Pair<String, String>> REGISTRY = new CallSiteRegistry<>();

  static {
    MethodHandle mh_store = null;
    try {
      mh_store =
          MethodHandles.publicLookup()
              .findVirtual(
                  CustomActionParameter.class,
                  "store",
                  MethodType.methodType(Void.TYPE, Action.class, Object.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      e.printStackTrace();
    }
    MH_STORE = mh_store;
  }

  public static CallSite bootstrap(
      Lookup lookup, String methodName, MethodType type, String actionName) {
    return new ConstantCallSite(
        REGISTRY.get(new Pair<>(actionName, methodName)).dynamicInvoker().asType(type));
  }

  public static <A extends Action>
      Stream<ActionParameterDefinition> findActionDefinitionsByAnnotation(
          Class<A> clazz, Lookup lookup) {
    return ACTION_PARAMETERS
        .computeIfAbsent(
            clazz,
            actionType -> {
              try {
                final List<ActionParameterDefinition> parameters = new ArrayList<>();
                final String actionName = clazz.getName().replace(".", "·");

                for (final Field field : actionType.getFields()) {
                  final ActionParameter fieldAnnotation =
                      field.getAnnotation(ActionParameter.class);
                  if (fieldAnnotation == null) {
                    continue;
                  }
                  if (Modifier.isStatic(field.getModifiers())) {
                    throw new IllegalArgumentException(
                        String.format(
                            "Field %s in %s is annotated with ShesmuParameter, but not a public instance field.",
                            field.getName(), field.getDeclaringClass().getName()));
                  }
                  final String fieldName =
                      AnnotationUtils.checkName(fieldAnnotation.name(), field, false);

                  final Imyhat fieldType =
                      Imyhat.convert(
                          String.format(
                              "Field %s in %s",
                              field.getName(), field.getDeclaringClass().getName()),
                          fieldAnnotation.type(),
                          field.getType());
                  parameters.add(
                      new InvokeDynamicActionParameterDescriptor(
                          actionName,
                          fieldName,
                          fieldType,
                          fieldAnnotation.required(),
                          lookup.unreflectSetter(field)));
                }

                for (final Method setter : actionType.getMethods()) {
                  final ActionParameter setterAnnotation =
                      setter.getAnnotation(ActionParameter.class);
                  if (setterAnnotation == null) {
                    continue;
                  }
                  if (Modifier.isStatic(setter.getModifiers())
                      || !setter.getReturnType().equals(void.class)
                      || setter.getParameterCount() != 1) {
                    throw new IllegalArgumentException(
                        String.format(
                            "Setter %s in %s is annotated with ShesmuParameter, but not a public instance method with no return type and one parameter.",
                            setter.getName(), setter.getDeclaringClass().getName()));
                  }
                  final String setterName =
                      AnnotationUtils.checkName(setterAnnotation.name(), setter, false);
                  final Imyhat setterType =
                      Imyhat.convert(
                          String.format(
                              "Setter %s in %s",
                              setter.getName(), setter.getDeclaringClass().getName()),
                          setterAnnotation.type(),
                          setter.getParameterTypes()[0]);
                  parameters.add(
                      new InvokeDynamicActionParameterDescriptor(
                          actionName,
                          setterName,
                          setterType,
                          setterAnnotation.required(),
                          lookup.unreflect(setter)));
                }

                final JsonActionParameter[] jsonParameters =
                    actionType.getAnnotationsByType(JsonActionParameter.class);
                if (jsonParameters.length > 0) {
                  if (!JsonParameterisedAction.class.isAssignableFrom(actionType)) {
                    throw new IllegalArgumentException(
                        String.format(
                            "Action class %s is annotated with JSON parameters but doesn't extend JsonParameterisedAction.",
                            actionType.getName()));
                  }
                  for (final JsonActionParameter jsonParameter : jsonParameters) {
                    parameters.add(
                        new InvokeDynamicActionParameterDescriptor(
                            actionName,
                            new JsonParameter<>(
                                jsonParameter.name(),
                                jsonParameter.required(),
                                Imyhat.parse(jsonParameter.type()))));
                  }
                }
                return parameters;
              } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
              }
            })
        .stream();
  }

  private final String actionName;
  // Keep this referenced so the call site doesn't get dropped from the registry
  // if there is no olive actively using it
  @SuppressWarnings("unused")
  private final MutableCallSite callsite;

  private final String methodName;
  private final String name;
  private final boolean required;
  private final Imyhat type;

  public InvokeDynamicActionParameterDescriptor(
      String actionName, CustomActionParameter<?, ?> parameter) {
    this(
        actionName,
        parameter.name(),
        parameter.type(),
        parameter.required(),
        MethodHandles.foldArguments(
            MH_STORE, MethodHandles.constant(CustomActionParameter.class, parameter)));
  }

  InvokeDynamicActionParameterDescriptor(
      String actionName, String name, Imyhat type, boolean required, MethodHandle handle) {
    this.actionName = actionName;
    this.name = name;
    this.type = type;
    this.required = required;
    methodName = name() + " " + type.descriptor();
    callsite = REGISTRY.upsert(new Pair<>(actionName, methodName), handle);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public boolean required() {
    return required;
  }

  @Override
  public void store(Renderer renderer, int actionLocal, Consumer<Renderer> loadParameter) {
    renderer.methodGen().loadLocal(actionLocal);
    loadParameter.accept(renderer);
    renderer
        .methodGen()
        .invokeDynamic(
            methodName,
            Type.getMethodDescriptor(Type.VOID_TYPE, A_ACTION_TYPE, type.apply(TypeUtils.TO_ASM)),
            BSM_HANDLE,
            actionName);
  }

  @Override
  public Imyhat type() {
    return type;
  }
}

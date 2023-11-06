package ca.on.oicr.gsi.shesmu.server.plugins;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.RefillerParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import ca.on.oicr.gsi.shesmu.plugin.refill.CustomRefillerParameter;
import ca.on.oicr.gsi.shesmu.plugin.refill.Refiller;
import ca.on.oicr.gsi.shesmu.plugin.refill.RefillerParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import java.lang.invoke.*;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public final class InvokeDynamicRefillerParameterDescriptor implements RefillerParameterDefinition {
  public static CallSite bootstrap(
      Lookup lookup, String methodName, MethodType type, String refillerName) {
    return new ConstantCallSite(
        REGISTRY.get(new Pair<>(refillerName, methodName)).dynamicInvoker().asType(type));
  }

  public static Stream<RefillerParameterDefinition> findRefillerDefinitionsByAnnotation(
      Class<? extends Refiller> clazz, Lookup lookup) {
    return REFILLER_PARAMETERS
        .computeIfAbsent(
            clazz,
            refillerType -> {
              // Okay, this requires deep digging into Java's generic type system. We want to insist
              // that the final object we get is parameterised over <T>, the output rows we are
              // providing. So, we expect to be given a type, not a class, that's parameterised by
              // exactly one type variable.

              // Now, we need to travel through the type hierarchy to determine if the type
              // parameter we saw is actually the one to Refiller. So, we want a relationship like
              // this: X<T> extends Y<T>; Y<K> extends Refiller<K>. We don't care if the layers in
              // the middle rename or shuffle values. So, X<T> extends Y<T, Integer>; Y<A, B>
              // extends Refiller<A> is fine.
              var varPosition = -1;
              Class<?> current = refillerType;
              while (!current.equals(Refiller.class)) {

                // If the class isn't Refiller, then get the generic description of this class,
                // which must be parameterised
                var parentSuper = current.getGenericSuperclass();
                if (!(parentSuper instanceof ParameterizedType)) {
                  throw new IllegalArgumentException(
                      String.format(
                          "Refiller type %s is has generic supertype %s that is unexpected.",
                          refillerType.getTypeName(), parentSuper.getTypeName()));
                }
                var parameterizedParentSuper = (ParameterizedType) parentSuper;
                // Okay, our class must have some type variables
                final TypeVariable<?> parentVariable;
                if (varPosition == -1) {
                  // The first layer should have exactly one type variable
                  if (current.getTypeParameters().length != 1) {
                    throw new IllegalArgumentException(
                        String.format(
                            "Refiller type %s is not parameterised by exactly one type variable.",
                            refillerType.getTypeName()));
                  }
                  parentVariable = current.getTypeParameters()[0];
                } else {
                  // The layer below us tells us which type parameter we care about
                  parentVariable = current.getTypeParameters()[varPosition];
                }
                // Check that this type variable is just a raw type variable; none of that T extends
                // Foo allowed.
                for (final var bound : parentVariable.getBounds()) {
                  if (!bound.equals(Object.class)) {
                    throw new IllegalArgumentException(
                        String.format(
                            "Refiller type %s is parameterised with a bound %s in %s.",
                            refillerType.getTypeName(),
                            bound.getTypeName(),
                            current.getTypeName()));
                  }
                }
                // Now, figure out the position of this variable in the superclass's type argument
                // list
                var parentParams = parameterizedParentSuper.getActualTypeArguments();
                varPosition = -1;
                for (var i = 0; i < parentParams.length; i++) {
                  if (parentParams[i].equals(parentVariable)) {
                    varPosition = i;
                    break;
                  }
                }
                if (varPosition == -1) {
                  throw new IllegalArgumentException(
                      String.format(
                          "Refiller type %s throws away type parameter %s in %s.",
                          refillerType.getTypeName(),
                          parentVariable.getTypeName(),
                          current.getTypeName()));
                }
                final var parent = parameterizedParentSuper.getRawType();
                // We expect the current layer to be a class
                if (!(parent instanceof Class)) {
                  throw new IllegalArgumentException(
                      String.format(
                          "Refiller type %s is has supertype %s that is unexpected.",
                          refillerType.getTypeName(), parent.getTypeName()));
                }
                current = (Class<?>) parent;
              }
              try {
                final List<RefillerParameterDefinition> parameters = new ArrayList<>();
                final var actionName = refillerType.getName().replace(".", "·");

                for (final var field : refillerType.getFields()) {
                  final var fieldAnnotation = field.getAnnotation(RefillerParameter.class);
                  if (fieldAnnotation == null) {
                    continue;
                  }
                  if (Modifier.isStatic(field.getModifiers())) {
                    throw new IllegalArgumentException(
                        String.format(
                            "Field %s in %s is annotated with RefillerParameter, but not an instance field.",
                            field.getName(), field.getDeclaringClass().getName()));
                  }
                  final var fieldName = AnnotationUtils.checkName(fieldAnnotation.name(), field);
                  final var fieldType =
                      unpack(
                          String.format(
                              "Field %s in %s",
                              field.getName(), field.getDeclaringClass().getName()),
                          fieldAnnotation.type(),
                          field.getGenericType(),
                          refillerType.getTypeParameters()[0]);
                  parameters.add(
                      new InvokeDynamicRefillerParameterDescriptor(
                          actionName, fieldName, fieldType, lookup.unreflectSetter(field)));
                }

                for (final var setter : refillerType.getMethods()) {
                  final var setterAnnotation = setter.getAnnotation(RefillerParameter.class);
                  if (setterAnnotation == null) {
                    continue;
                  }
                  if (Modifier.isStatic(setter.getModifiers())
                      || !setter.getReturnType().equals(void.class)
                      || setter.getParameterCount() != 1
                      || !Function.class.isAssignableFrom(setter.getParameterTypes()[0])) {
                    throw new IllegalArgumentException(
                        String.format(
                            "Setter %s in %s is annotated with RefillerParameter, but not an instance method with no return type and one java.util.function.Function parameter.",
                            setter.getName(), setter.getDeclaringClass().getName()));
                  }
                  final var setterName = AnnotationUtils.checkName(setterAnnotation.name(), setter);
                  final var setterType =
                      unpack(
                          String.format(
                              "Setter %s in %s",
                              setter.getName(), setter.getDeclaringClass().getName()),
                          setterAnnotation.type(),
                          setter.getGenericParameterTypes()[0],
                          refillerType.getTypeParameters()[0]);
                  parameters.add(
                      new InvokeDynamicRefillerParameterDescriptor(
                          actionName, setterName, setterType, lookup.unreflect(setter)));
                }
                return parameters;
              } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
              }
            })
        .stream();
  }

  private static Imyhat unpack(
      String context,
      String descriptor,
      java.lang.reflect.Type genericType,
      java.lang.reflect.Type typeArgument) {
    if (!(genericType instanceof ParameterizedType)) {
      throw new IllegalArgumentException(
          String.format("%s has non-parameterised type %s.", context, genericType.getTypeName()));
    }
    final var parameterizedType = (ParameterizedType) genericType;
    if (!parameterizedType.getRawType().equals(Function.class)) {
      throw new IllegalArgumentException(
          String.format("%s has non-Function type %s.", context, genericType.getTypeName()));
    }
    if (!parameterizedType.getActualTypeArguments()[0].equals(typeArgument)) {
      throw new IllegalArgumentException(
          String.format(
              "%s takes argument %s which is not type parameter %s.",
              context,
              parameterizedType.getActualTypeArguments()[0].getTypeName(),
              typeArgument.getTypeName()));
    }
    return Imyhat.convert(context, descriptor, parameterizedType.getActualTypeArguments()[1]);
  }

  private static final Type A_FUNCTION_TYPE = Type.getType(Function.class);
  private static final Type A_UPSERT_TYPE = Type.getType(Refiller.class);
  private static final Handle BSM_HANDLE =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          Type.getType(RuntimeSupport.class).getInternalName(),
          "refillerParameterBootstrap",
          Type.getMethodDescriptor(
              Type.getType(CallSite.class),
              Type.getType(Lookup.class),
              Type.getType(String.class),
              Type.getType(MethodType.class),
              Type.getType(String.class)),
          false);
  private static final MethodHandle MH_STORE;
  private static final Map<Class<? extends Refiller>, List<RefillerParameterDefinition>>
      REFILLER_PARAMETERS = new ConcurrentHashMap<>();
  private static final CallSiteRegistry<Pair<String, String>> REGISTRY = new CallSiteRegistry<>();

  static {
    MethodHandle mh_store = null;
    try {
      mh_store =
          MethodHandles.publicLookup()
              .findVirtual(
                  CustomRefillerParameter.class,
                  "store",
                  MethodType.methodType(void.class, Object.class, Function.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      e.printStackTrace();
    }
    MH_STORE = mh_store;
  }

  // Keep this referenced so the call site doesn't get dropped from the registry
  // if there is no olive actively using it
  @SuppressWarnings("unused")
  private final MutableCallSite callsite;

  private final String methodName;
  private final String name;
  private final String refillerName;
  private final Imyhat type;

  public InvokeDynamicRefillerParameterDescriptor(
      String refillerName, CustomRefillerParameter<?, ?> parameter) {
    this(
        refillerName,
        parameter.name(),
        parameter.type(),
        MethodHandles.foldArguments(
            MH_STORE, MethodHandles.constant(CustomRefillerParameter.class, parameter)));
  }

  InvokeDynamicRefillerParameterDescriptor(
      String refillerName, String name, Imyhat type, MethodHandle handle) {
    this.refillerName = refillerName;
    this.name = name;
    this.type = type;
    methodName = name() + " " + type.descriptor();
    callsite = REGISTRY.upsert(new Pair<>(refillerName, methodName), handle);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public void render(Renderer renderer, int refillerLocal, int functionLocal) {
    renderer.methodGen().loadLocal(refillerLocal);
    renderer.methodGen().loadLocal(functionLocal);
    renderer
        .methodGen()
        .invokeDynamic(
            methodName,
            Type.getMethodDescriptor(Type.VOID_TYPE, A_UPSERT_TYPE, A_FUNCTION_TYPE),
            BSM_HANDLE,
            refillerName);
  }

  @Override
  public Imyhat type() {
    return type;
  }
}

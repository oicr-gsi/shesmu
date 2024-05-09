package ca.on.oicr.gsi.shesmu.plugin.grouper;

import ca.on.oicr.gsi.shesmu.plugin.types.TypeGuarantee;
import ca.on.oicr.gsi.shesmu.plugin.types.TypeGuarantee.Pack3;
import ca.on.oicr.gsi.shesmu.plugin.types.TypeGuarantee.Pack4;
import ca.on.oicr.gsi.shesmu.plugin.types.TypeGuarantee.Pack5;
import java.lang.invoke.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Define a complex subgroup operation to be made available to olives
 *
 * <p>Create concrete subclass with a no-arguments constructor.
 */
public abstract class GrouperDefinition {
  public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType type) {
    return new ConstantCallSite(
        HANDLES
            .getOrDefault(
                name,
                MethodHandles.throwException(
                    type.returnType(), UnsupportedOperationException.class))
            .asType(type));
  }

  private static final Map<String, MethodHandle> HANDLES = new ConcurrentHashMap<>();
  private static final MethodHandle MH_BIFUNCTION_APPLY;
  private static final MethodHandle MH_FUNCTION__APPLY;
  private static final MethodHandle MH_PACK3__PACK;
  private static final MethodHandle MH_PACK4__PACK;
  private static final MethodHandle MH_PACK5__PACK;

  static {
    MethodHandle mh_function__apply;
    MethodHandle mh_bifunction_apply;
    MethodHandle mh_pack3__pack;
    MethodHandle mh_pack4__pack;
    MethodHandle mh_pack5__pack;

    try {
      var lookup = MethodHandles.publicLookup();
      mh_function__apply =
          lookup.findVirtual(
              Function.class, "apply", MethodType.methodType(Object.class, Object.class));
      mh_bifunction_apply =
          lookup.findVirtual(
              BiFunction.class,
              "apply",
              MethodType.methodType(Object.class, Object.class, Object.class));
      mh_pack3__pack =
          lookup.findVirtual(
              TypeGuarantee.Pack3.class,
              "pack",
              MethodType.methodType(Object.class, Object.class, Object.class, Object.class));
      mh_pack4__pack =
          lookup.findVirtual(
              TypeGuarantee.Pack4.class,
              "pack",
              MethodType.methodType(
                  Object.class, Object.class, Object.class, Object.class, Object.class));
      mh_pack5__pack =
          lookup.findVirtual(
              TypeGuarantee.Pack5.class,
              "pack",
              MethodType.methodType(
                  Object.class,
                  Object.class,
                  Object.class,
                  Object.class,
                  Object.class,
                  Object.class));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    MH_FUNCTION__APPLY = mh_function__apply;
    MH_BIFUNCTION_APPLY = mh_bifunction_apply;
    MH_PACK3__PACK = mh_pack3__pack;
    MH_PACK4__PACK = mh_pack4__pack;
    MH_PACK5__PACK = mh_pack5__pack;
  }

  private final List<GrouperParameter<?, ?>> inputs;
  private final String name;
  private final GrouperOutputs<?, ?, ?> outputs;

  /**
   * Define a new grouper that takes no arguments
   *
   * @param name the name of the grouper that should be used in the olive; it must be a valid Shesmu
   *     identifier
   * @param outputs the collector format the olive will provided for exporting the extra subgroup
   *     information back to the olive
   * @param factory a function that creates a new grouper
   * @param <I> the type of the input rows; this is controlled by Shesmu
   * @param <O> the type of the output rows; this is controlled by Shesmu
   * @param <G> the type of the grouping operation
   * @param <C> the type of the grouped collector
   */
  public <I, O, G extends Grouper<I, ?>, C> GrouperDefinition(
      String name, GrouperOutputs<I, O, C> outputs, Function<? super C, G> factory) {
    this.name = name;
    HANDLES.put(name, MH_FUNCTION__APPLY.bindTo(factory));
    this.inputs = List.of();
    this.outputs = outputs;
  }

  /**
   * Define a new grouper that takes one argument
   *
   * @param name the name of the grouper that should be used in the olive; it must be a valid Shesmu
   *     identifier
   * @param parameter the input the olives must provide
   * @param outputs the collector format the olive will provided for exporting the extra subgroup
   *     information back to the olive
   * @param factory a function that creates a new grouper
   * @param <I> the type of the input rows; this is controlled by Shesmu
   * @param <O> the type of the output rows; this is controlled by Shesmu
   * @param <G> the type of the grouping operation
   * @param <T> the type of the argument to the grouping operation
   * @param <C> the type of the grouped collector
   */
  public <I, O, G extends Grouper<I, ?>, T, C> GrouperDefinition(
      String name,
      GrouperParameter<I, T> parameter,
      GrouperOutputs<I, O, C> outputs,
      BiFunction<? super T, ? super C, G> factory) {
    this.name = name;
    HANDLES.put(
        name,
        MH_BIFUNCTION_APPLY.bindTo(
            (BiFunction<Object, C, Grouper<?, ?>>)
                (first, outputGenerator) ->
                    factory.apply(parameter.unpack(first), outputGenerator)));
    this.inputs = List.of(parameter);
    this.outputs = outputs;
  }

  /**
   * Define a new grouper that takes two arguments
   *
   * @param name the name of the grouper that should be used in the olive; it must be a valid Shesmu
   *     identifier
   * @param parameter1 the first input the olives must provide
   * @param parameter2 the second input the olives must provide
   * @param outputs the collector format the olive will provided for exporting the extra subgroup
   *     information back to the olive
   * @param factory a function that creates a new grouper
   * @param <I> the type of the input rows; this is controlled by Shesmu
   * @param <O> the type of the output rows; this is controlled by Shesmu
   * @param <G> the type of the grouping operation
   * @param <T> the type of the argument to the grouping operation
   * @param <C> the type of the grouped collector
   */
  public <I, O, G extends Grouper<I, O>, T, S, C> GrouperDefinition(
      String name,
      GrouperParameter<I, T> parameter1,
      GrouperParameter<I, S> parameter2,
      GrouperOutputs<I, O, C> outputs,
      TypeGuarantee.Pack3<? super T, ? super S, ? super C, G> factory) {
    this.name = name;
    HANDLES.put(
        name,
        MH_PACK3__PACK.bindTo(
            (Pack3<Object, Object, C, Grouper<I, O>>)
                (first, second, outputGenerator) ->
                    factory.pack(
                        parameter1.unpack(first), parameter2.unpack(second), outputGenerator)));
    this.inputs = List.of(parameter1, parameter2);
    this.outputs = outputs;
  }

  /**
   * Define a new grouper that takes three arguments
   *
   * @param name the name of the grouper that should be used in the olive; it must be a valid Shesmu
   *     identifier
   * @param parameter1 the first input the olives must provide
   * @param parameter2 the second input the olives must provide
   * @param parameter3 the third input the olives must provide
   * @param outputs the collector format the olive will provided for exporting the extra subgroup
   *     information back to the olive
   * @param factory a function that creates a new grouper
   * @param <I> the type of the input rows; this is controlled by Shesmu
   * @param <O> the type of the output rows; this is controlled by Shesmu
   * @param <G> the type of the grouping operation
   * @param <T> the type of the first argument to the grouping operation
   * @param <S> the type of the second argument to the grouping operation
   * @param <U> the type of the third argument to the grouping operation
   * @param <C> the type of the grouped collector
   */
  public <I, O, G extends Grouper<I, O>, T, S, U, C> GrouperDefinition(
      String name,
      GrouperParameter<I, T> parameter1,
      GrouperParameter<I, S> parameter2,
      GrouperParameter<I, U> parameter3,
      GrouperOutputs<I, O, C> outputs,
      TypeGuarantee.Pack4<? super T, ? super S, ? super U, ? super C, G> factory) {
    this.name = name;
    HANDLES.put(
        name,
        MH_PACK4__PACK.bindTo(
            (Pack4<Object, Object, Object, C, Grouper<I, O>>)
                (first, second, third, outputGenerator) ->
                    factory.pack(
                        parameter1.unpack(first),
                        parameter2.unpack(second),
                        parameter3.unpack(third),
                        outputGenerator)));
    this.inputs = List.of(parameter1, parameter2, parameter3);
    this.outputs = outputs;
  }

  /**
   * Define a new grouper that takes three arguments
   *
   * @param name the name of the grouper that should be used in the olive; it must be a valid Shesmu
   *     identifier
   * @param parameter1 the first input the olives must provide
   * @param parameter2 the second input the olives must provide
   * @param parameter3 the third input the olives must provide
   * @param parameter4 the fourth input the olives must provide
   * @param outputs the collector format the olive will provided for exporting the extra subgroup
   *     information back to the olive
   * @param factory a function that creates a new grouper
   * @param <I> the type of the input rows; this is controlled by Shesmu
   * @param <O> the type of the output rows; this is controlled by Shesmu
   * @param <G> the type of the grouping operation
   * @param <T> the type of the first argument to the grouping operation
   * @param <S> the type of the second argument to the grouping operation
   * @param <U> the type of the third argument to the grouping operation
   * @param <V> the type of the fourth argument to the grouping operation
   * @param <C> the type of the grouped collector
   */
  public <I, O, G extends Grouper<I, O>, T, S, U, V, C> GrouperDefinition(
      String name,
      GrouperParameter<I, T> parameter1,
      GrouperParameter<I, S> parameter2,
      GrouperParameter<I, U> parameter3,
      GrouperParameter<I, V> parameter4,
      GrouperOutputs<I, O, C> outputs,
      TypeGuarantee.Pack5<? super T, ? super S, ? super U, ? super V, ? super C, G> factory) {
    this.name = name;
    HANDLES.put(
        name,
        MH_PACK5__PACK.bindTo(
            (Pack5<Object, Object, Object, Object, C, Grouper<I, O>>)
                (first, second, third, fourth, outputGenerator) ->
                    factory.pack(
                        parameter1.unpack(first),
                        parameter2.unpack(second),
                        parameter3.unpack(third),
                        parameter4.unpack(fourth),
                        outputGenerator)));
    this.inputs = List.of(parameter1, parameter2, parameter3, parameter4);
    this.outputs = outputs;
  }

  public abstract String description();

  public final GrouperParameter<?, ?> input(int i) {
    return inputs.get(i);
  }

  public int inputs() {
    return inputs.size();
  }

  public final String name() {
    return name;
  }

  public final GrouperOutput<?, ?> output(int i) {
    return outputs.get(i);
  }

  public final int outputs() {
    return outputs.size();
  }
}

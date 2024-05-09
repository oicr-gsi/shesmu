package ca.on.oicr.gsi.shesmu.plugin.refill;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.TypeGuarantee;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Create a custom refiller parameter
 *
 * @param <F> the refiller type
 * @param <T> the type of the input row
 */
public abstract class CustomRefillerParameter<F, T> {

  private final String name;
  private final Imyhat type;

  /**
   * Create a new custom refiller parameter
   *
   * @param name the name of the parameter
   * @param store a callback to install the transformer function into the refiller object
   * @param type the type being consumed from Shesmu
   * @param <F> the type of the refiller
   * @param <T> the type of the input row
   * @param <R> the type of the value from the olive
   */
  public static <F, T, R> CustomRefillerParameter<F, T> of(
      String name, BiConsumer<F, Function<T, R>> store, TypeGuarantee<R> type) {
    return new CustomRefillerParameter<>(name, type.type()) {
      @Override
      public void store(F refiller, Function<T, Object> value) {
        store.accept(refiller, value.andThen(type::unpack));
      }
    };
  }

  public CustomRefillerParameter(String name, Imyhat type) {
    super();
    this.name = name;
    this.type = type;
  }

  /** The name of the parameter as the user will set it. */
  public final String name() {
    return name;
  }

  /** Store the value extraction function in the refiller */
  public abstract void store(F refiller, Function<T, Object> value);

  /** The type of the parameter */
  public final Imyhat type() {
    return type;
  }
}

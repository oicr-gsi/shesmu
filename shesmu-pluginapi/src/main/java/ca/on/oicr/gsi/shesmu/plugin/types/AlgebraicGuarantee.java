package ca.on.oicr.gsi.shesmu.plugin.types;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatTransformer.AlgebraicTransformer;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatTransformer.AlgebraicVisitor;
import ca.on.oicr.gsi.shesmu.plugin.types.TypeGuarantee.Pack2;
import ca.on.oicr.gsi.shesmu.plugin.types.TypeGuarantee.Pack3;
import ca.on.oicr.gsi.shesmu.plugin.types.TypeGuarantee.Pack4;
import ca.on.oicr.gsi.shesmu.plugin.types.TypeGuarantee.Pack5;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A type binding between Shesmu algebraic data types and Java types
 *
 * @param <T> the Java type
 */
public abstract class AlgebraicGuarantee<T> extends GenericAlgebraicGuarantee<T> {

  /**
   * A restriction for an empty algebraic type entry
   *
   * @param name the Shesmu identifier for the algebraic type
   * @param result the value that should be returned
   * @return the newly created binding
   * @param <R> the Java type
   */
  public static <R> AlgebraicGuarantee<R> empty(String name, R result) {
    final var tupleType = Imyhat.algebraicTuple(name);
    return new AlgebraicGuarantee<>() {
      @Override
      public <G> G apply(GenericAlgebraicVisitor<G> visitor) {
        return visitor.empty(name);
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      protected boolean checkEmpty() {
        return true;
      }

      @Override
      protected boolean checkObject(Stream<Pair<String, Imyhat>> contents) {
        return false;
      }

      @Override
      protected boolean checkTuple(Stream<Imyhat> contents) {
        return false;
      }

      @Override
      public Imyhat type() {
        return tupleType;
      }

      @Override
      public R unpack(AlgebraicValue input) {
        return result;
      }
    };
  }

  /**
   * A restriction for an empty algebraic type entry
   *
   * @param name the Shesmu identifier for the algebraic type
   * @param result a callback to generate value that should be returned
   * @return the newly created binding
   * @param <R> the Java type
   */
  public static <R> AlgebraicGuarantee<R> emptyFactory(String name, Supplier<R> result) {
    final var tupleType = Imyhat.algebraicTuple(name);
    return new AlgebraicGuarantee<>() {
      @Override
      public <G> G apply(GenericAlgebraicVisitor<G> visitor) {
        return visitor.empty(name);
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      protected boolean checkEmpty() {
        return true;
      }

      @Override
      protected boolean checkObject(Stream<Pair<String, Imyhat>> contents) {
        return false;
      }

      @Override
      protected boolean checkTuple(Stream<Imyhat> contents) {
        return false;
      }

      @Override
      public Imyhat type() {
        return tupleType;
      }

      @Override
      public R unpack(AlgebraicValue input) {
        return result.get();
      }
    };
  }

  /**
   * Add a restriction for an algebraic datatype with a single named parameter
   *
   * @param name the Shesmu identifier for the algebraic type
   * @param convert a function to generate a value for the parameter value provided
   * @param paramName the parameter name, which must be a valid Shesmu identifier
   * @param param the type guarantee for the parameter
   * @return the newly constructed guarantee
   * @param <R> the Java type of the result
   * @param <T> the Java type of the parameter
   */
  public static <R, T> AlgebraicGuarantee<R> object(
      String name,
      Function<? super T, ? extends R> convert,
      String paramName,
      TypeGuarantee<T> param) {
    final var tupleType =
        Imyhat.algebraicObject(name, Stream.of(new Pair<>(paramName, param.type())));
    return new AlgebraicGuarantee<>() {
      @Override
      public <G> G apply(GenericAlgebraicVisitor<G> visitor) {
        return visitor.object(name, Stream.of(new Pair<>(paramName, param.type())));
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      protected boolean checkEmpty() {
        return false;
      }

      @Override
      protected boolean checkObject(Stream<Pair<String, Imyhat>> contents) {
        return contents.allMatch(
            new Predicate<>() {
              int index;

              @Override
              public boolean test(Pair<String, Imyhat> field) {
                switch (index++) {
                  case 0:
                    return field.first().equals(paramName)
                        && param.type().isAssignableFrom(field.second());
                  default:
                    return false;
                }
              }
            });
      }

      @Override
      protected boolean checkTuple(Stream<Imyhat> contents) {
        return false;
      }

      @Override
      public Imyhat type() {
        return tupleType;
      }

      @Override
      public R unpack(AlgebraicValue input) {
        return convert.apply(param.unpack(input.get(0)));
      }
    };
  }

  /**
   * Add a restriction for an algebraic datatype with two named parameters
   *
   * @param name the Shesmu identifier for the algebraic type
   * @param convert a function to generate a value for the two parameter values provided
   * @param firstName the first parameter name, which must be a valid Shesmu identifier
   * @param first the type guarantee for the first parameter
   * @param secondName the second parameter name, which must be a valid Shesmu identifier
   * @param second the type guarantee for the second parameter
   * @return the newly constructed guarantee
   * @param <R> the Java type of the result
   * @param <T> the Java type of the first parameter
   * @param <U> the Java type of the second parameter
   */
  public static <R, T, U> AlgebraicGuarantee<R> object(
      String name,
      Pack2<? super T, ? super U, ? extends R> convert,
      String firstName,
      TypeGuarantee<T> first,
      String secondName,
      TypeGuarantee<U> second) {
    final var tupleType =
        Imyhat.algebraicObject(
            name,
            Stream.of(new Pair<>(firstName, first.type()), new Pair<>(secondName, second.type())));
    if (firstName.compareTo(secondName) >= 0) {
      throw new IllegalArgumentException("Object properties must be alphabetically ordered.");
    }
    return new AlgebraicGuarantee<>() {
      @Override
      public <G> G apply(GenericAlgebraicVisitor<G> visitor) {
        return visitor.object(
            name,
            Stream.of(new Pair<>(firstName, first.type()), new Pair<>(secondName, second.type())));
      }

      @Override
      protected boolean checkEmpty() {
        return false;
      }

      @Override
      protected boolean checkObject(Stream<Pair<String, Imyhat>> contents) {
        return contents.allMatch(
            new Predicate<>() {
              int index;

              @Override
              public boolean test(Pair<String, Imyhat> field) {
                switch (index++) {
                  case 0:
                    return field.first().equals(firstName)
                        && first.type().isAssignableFrom(field.second());
                  case 1:
                    return field.first().equals(secondName)
                        && second.type().isAssignableFrom(field.second());
                  default:
                    return false;
                }
              }
            });
      }

      @Override
      protected boolean checkTuple(Stream<Imyhat> contents) {
        return false;
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Imyhat type() {
        return tupleType;
      }

      @Override
      public R unpack(AlgebraicValue input) {
        return convert.pack(first.unpack(input.get(0)), second.unpack(input.get(1)));
      }
    };
  }

  /**
   * Add a restriction for an algebraic datatype with three named parameters
   *
   * @param name the Shesmu identifier for the algebraic type
   * @param convert a function to generate a value for the three parameter values provided
   * @param firstName the first parameter name, which must be a valid Shesmu identifier
   * @param first the type guarantee for the first parameter
   * @param secondName the second parameter name, which must be a valid Shesmu identifier
   * @param second the type guarantee for the second parameter
   * @param thirdName the third parameter name, which must be a valid Shesmu identifier
   * @param third the type guarantee for the third parameter
   * @return the newly constructed guarantee
   * @param <R> the Java type of the result
   * @param <T> the Java type of the first parameter
   * @param <U> the Java type of the second parameter
   * @param <V> the Java type of the third parameter
   */
  public static <R, T, U, V> AlgebraicGuarantee<R> object(
      String name,
      Pack3<? super T, ? super U, ? super V, ? extends R> convert,
      String firstName,
      TypeGuarantee<T> first,
      String secondName,
      TypeGuarantee<U> second,
      String thirdName,
      TypeGuarantee<V> third) {
    final var tupleType =
        Imyhat.algebraicObject(
            name,
            Stream.of(
                new Pair<>(firstName, first.type()),
                new Pair<>(secondName, second.type()),
                new Pair<>(thirdName, third.type())));
    if (firstName.compareTo(secondName) >= 0 || secondName.compareTo(thirdName) >= 0) {
      throw new IllegalArgumentException("Object properties must be alphabetically ordered.");
    }
    return new AlgebraicGuarantee<>() {
      @Override
      public <G> G apply(GenericAlgebraicVisitor<G> visitor) {
        return visitor.object(
            name,
            Stream.of(
                new Pair<>(firstName, first.type()),
                new Pair<>(secondName, second.type()),
                new Pair<>(thirdName, third.type())));
      }

      @Override
      protected boolean checkEmpty() {
        return false;
      }

      @Override
      protected boolean checkObject(Stream<Pair<String, Imyhat>> contents) {
        return contents.allMatch(
            new Predicate<>() {
              int index;

              @Override
              public boolean test(Pair<String, Imyhat> field) {
                switch (index++) {
                  case 0:
                    return field.first().equals(firstName)
                        && first.type().isAssignableFrom(field.second());
                  case 1:
                    return field.first().equals(secondName)
                        && second.type().isAssignableFrom(field.second());
                  case 2:
                    return field.first().equals(thirdName)
                        && third.type().isAssignableFrom(field.second());
                  default:
                    return false;
                }
              }
            });
      }

      @Override
      protected boolean checkTuple(Stream<Imyhat> contents) {
        return false;
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Imyhat type() {
        return tupleType;
      }

      @Override
      public R unpack(AlgebraicValue input) {
        return convert.pack(
            first.unpack(input.get(0)), second.unpack(input.get(1)), third.unpack(input.get(2)));
      }
    };
  }

  /**
   * Add a restriction for an algebraic datatype with four named parameters
   *
   * @param name the Shesmu identifier for the algebraic type
   * @param convert a function to generate a value for the four parameter values provided
   * @param firstName the first parameter name, which must be a valid Shesmu identifier
   * @param first the type guarantee for the first parameter
   * @param secondName the second parameter name, which must be a valid Shesmu identifier
   * @param second the type guarantee for the second parameter
   * @param thirdName the third parameter name, which must be a valid Shesmu identifier
   * @param third the type guarantee for the third parameter
   * @param fourthName the fourth parameter name, which must be a valid Shesmu identifier
   * @param fourth the type guarantee for the fourth parameter
   * @return the newly constructed guarantee
   * @param <R> the Java type of the result
   * @param <T> the Java type of the first parameter
   * @param <U> the Java type of the second parameter
   * @param <V> the Java type of the third parameter
   * @param <W> the Java type of the fourth parameter
   */
  public static <R, T, U, V, W> AlgebraicGuarantee<R> object(
      String name,
      Pack4<? super T, ? super U, ? super V, ? super W, ? extends R> convert,
      String firstName,
      TypeGuarantee<T> first,
      String secondName,
      TypeGuarantee<U> second,
      String thirdName,
      TypeGuarantee<V> third,
      String fourthName,
      TypeGuarantee<W> fourth) {
    final var tupleType =
        Imyhat.algebraicObject(
            name,
            Stream.of(
                new Pair<>(firstName, first.type()),
                new Pair<>(secondName, second.type()),
                new Pair<>(thirdName, third.type()),
                new Pair<>(fourthName, fourth.type())));
    if (firstName.compareTo(secondName) >= 0
        || secondName.compareTo(thirdName) >= 0
        || thirdName.compareTo(fourthName) >= 0) {
      throw new IllegalArgumentException("Object properties must be alphabetically ordered.");
    }
    return new AlgebraicGuarantee<>() {
      @Override
      public <G> G apply(GenericAlgebraicVisitor<G> visitor) {
        return visitor.object(
            name,
            Stream.of(
                new Pair<>(firstName, first.type()),
                new Pair<>(secondName, second.type()),
                new Pair<>(thirdName, third.type()),
                new Pair<>(fourthName, fourth.type())));
      }

      @Override
      protected boolean checkEmpty() {
        return false;
      }

      @Override
      protected boolean checkObject(Stream<Pair<String, Imyhat>> contents) {
        return contents.allMatch(
            new Predicate<>() {
              int index;

              @Override
              public boolean test(Pair<String, Imyhat> field) {
                switch (index++) {
                  case 0:
                    return field.first().equals(firstName)
                        && first.type().isAssignableFrom(field.second());
                  case 1:
                    return field.first().equals(secondName)
                        && second.type().isAssignableFrom(field.second());
                  case 2:
                    return field.first().equals(thirdName)
                        && third.type().isAssignableFrom(field.second());
                  case 3:
                    return field.first().equals(fourthName)
                        && fourth.type().isAssignableFrom(field.second());
                  default:
                    return false;
                }
              }
            });
      }

      @Override
      protected boolean checkTuple(Stream<Imyhat> contents) {
        return false;
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Imyhat type() {
        return tupleType;
      }

      @Override
      public R unpack(AlgebraicValue input) {
        return convert.pack(
            first.unpack(input.get(0)),
            second.unpack(input.get(1)),
            third.unpack(input.get(2)),
            fourth.unpack(input.get(3)));
      }
    };
  }

  /**
   * Add a restriction for an algebraic datatype with five named parameters
   *
   * @param name the Shesmu identifier for the algebraic type
   * @param convert a function to generate a value for the five parameter values provided
   * @param firstName the first parameter name, which must be a valid Shesmu identifier
   * @param first the type guarantee for the first parameter
   * @param secondName the second parameter name, which must be a valid Shesmu identifier
   * @param second the type guarantee for the second parameter
   * @param thirdName the third parameter name, which must be a valid Shesmu identifier
   * @param third the type guarantee for the third parameter
   * @param fourthName the fourth parameter name, which must be a valid Shesmu identifier
   * @param fourth the type guarantee for the fourth parameter
   * @param fifthName the fifth parameter name, which must be a valid Shesmu identifier
   * @param fifth the type guarantee for the fifth parameter
   * @return the newly constructed guarantee
   * @param <R> the Java type of the result
   * @param <T> the Java type of the first parameter
   * @param <U> the Java type of the second parameter
   * @param <V> the Java type of the third parameter
   * @param <W> the Java type of the fourth parameter
   * @param <X> the Java type of the fifth parameter
   */
  public static <R, T, U, V, W, X> AlgebraicGuarantee<R> object(
      String name,
      Pack5<? super T, ? super U, ? super V, ? super W, ? super X, ? extends R> convert,
      String firstName,
      TypeGuarantee<T> first,
      String secondName,
      TypeGuarantee<U> second,
      String thirdName,
      TypeGuarantee<V> third,
      String fourthName,
      TypeGuarantee<W> fourth,
      String fifthName,
      TypeGuarantee<X> fifth) {
    final var tupleType =
        Imyhat.algebraicObject(
            name,
            Stream.of(
                new Pair<>(firstName, first.type()),
                new Pair<>(secondName, second.type()),
                new Pair<>(thirdName, third.type()),
                new Pair<>(fourthName, fourth.type()),
                new Pair<>(fifthName, fifth.type())));
    if (firstName.compareTo(secondName) >= 0
        || secondName.compareTo(thirdName) >= 0
        || thirdName.compareTo(fourthName) >= 0
        || fourthName.compareTo(fifthName) >= 0) {
      throw new IllegalArgumentException("Object properties must be alphabetically ordered.");
    }
    return new AlgebraicGuarantee<>() {
      @Override
      public <G> G apply(GenericAlgebraicVisitor<G> visitor) {
        return visitor.object(
            name,
            Stream.of(
                new Pair<>(firstName, first.type()),
                new Pair<>(secondName, second.type()),
                new Pair<>(thirdName, third.type()),
                new Pair<>(fourthName, fourth.type()),
                new Pair<>(fifthName, fifth.type())));
      }

      @Override
      protected boolean checkEmpty() {
        return false;
      }

      @Override
      protected boolean checkObject(Stream<Pair<String, Imyhat>> contents) {
        return contents.allMatch(
            new Predicate<>() {
              int index;

              @Override
              public boolean test(Pair<String, Imyhat> field) {
                switch (index++) {
                  case 0:
                    return field.first().equals(firstName)
                        && first.type().isAssignableFrom(field.second());
                  case 1:
                    return field.first().equals(secondName)
                        && second.type().isAssignableFrom(field.second());
                  case 2:
                    return field.first().equals(thirdName)
                        && third.type().isAssignableFrom(field.second());
                  case 3:
                    return field.first().equals(fourthName)
                        && fourth.type().isAssignableFrom(field.second());
                  case 4:
                    return field.first().equals(fifthName)
                        && fifth.type().isAssignableFrom(field.second());
                  default:
                    return false;
                }
              }
            });
      }

      @Override
      protected boolean checkTuple(Stream<Imyhat> contents) {
        return false;
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Imyhat type() {
        return tupleType;
      }

      @Override
      public R unpack(AlgebraicValue input) {
        return convert.pack(
            first.unpack(input.get(0)),
            second.unpack(input.get(1)),
            third.unpack(input.get(2)),
            fourth.unpack(input.get(3)),
            fifth.unpack(input.get(4)));
      }
    };
  }
  /**
   * Add a restriction for an algebraic datatype with a single parameter
   *
   * @param name the Shesmu identifier for the algebraic type
   * @param convert a function to generate a value for the parameter value provided
   * @param param the type guarantee for the parameter
   * @return the newly constructed guarantee
   * @param <R> the Java type of the result
   * @param <T> the Java type of the parameter
   */
  public static <R, T> AlgebraicGuarantee<R> tuple(
      String name, Function<? super T, ? extends R> convert, TypeGuarantee<T> param) {
    final var tupleType = Imyhat.algebraicTuple(name, param.type());
    return new AlgebraicGuarantee<>() {
      @Override
      public <G> G apply(GenericAlgebraicVisitor<G> visitor) {
        return visitor.tuple(name, Stream.of(param.type()));
      }

      @Override
      protected boolean checkEmpty() {
        return false;
      }

      @Override
      protected boolean checkObject(Stream<Pair<String, Imyhat>> contents) {
        return false;
      }

      @Override
      protected boolean checkTuple(Stream<Imyhat> contents) {
        return contents.allMatch(
            new Predicate<>() {
              int index;

              @Override
              public boolean test(Imyhat type) {
                switch (index++) {
                  case 0:
                    return param.type().isAssignableFrom(type);
                  default:
                    return false;
                }
              }
            });
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Imyhat type() {
        return tupleType;
      }

      @Override
      public R unpack(AlgebraicValue input) {
        return convert.apply(param.unpack(input.get(0)));
      }
    };
  }

  /**
   * Add a restriction for an algebraic datatype with two positional parameters
   *
   * @param name the Shesmu identifier for the algebraic type
   * @param convert a function to generate a value for the two parameter values provided
   * @param first the type guarantee for the first parameter
   * @param second the type guarantee for the second parameter
   * @return the newly constructed guarantee
   * @param <R> the Java type of the result
   * @param <T> the Java type of the first parameter
   * @param <U> the Java type of the second parameter
   */
  public static <R, T, U> AlgebraicGuarantee<R> tuple(
      String name,
      Pack2<? super T, ? super U, ? extends R> convert,
      TypeGuarantee<T> first,
      TypeGuarantee<U> second) {
    final var tupleType = Imyhat.algebraicTuple(name, first.type(), second.type());
    return new AlgebraicGuarantee<>() {
      @Override
      protected boolean checkEmpty() {
        return false;
      }

      @Override
      protected boolean checkObject(Stream<Pair<String, Imyhat>> contents) {
        return false;
      }

      @Override
      protected boolean checkTuple(Stream<Imyhat> contents) {
        return contents.allMatch(
            new Predicate<>() {
              int index;

              @Override
              public boolean test(Imyhat type) {
                switch (index++) {
                  case 0:
                    return first.type().isAssignableFrom(type);
                  case 1:
                    return second.type().isAssignableFrom(type);
                  default:
                    return false;
                }
              }
            });
      }

      @Override
      public <G> G apply(GenericAlgebraicVisitor<G> visitor) {
        return visitor.tuple(name, Stream.of(first.type(), second.type()));
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Imyhat type() {
        return tupleType;
      }

      @Override
      public R unpack(AlgebraicValue input) {
        return convert.pack(first.unpack(input.get(0)), second.unpack(input.get(1)));
      }
    };
  }

  /**
   * Add a restriction for an algebraic datatype with three positional parameters
   *
   * @param name the Shesmu identifier for the algebraic type
   * @param convert a function to generate a value for the three parameter values provided
   * @param first the type guarantee for the first parameter
   * @param second the type guarantee for the second parameter
   * @param third the type guarantee for the third parameter
   * @return the newly constructed guarantee
   * @param <R> the Java type of the result
   * @param <T> the Java type of the first parameter
   * @param <U> the Java type of the second parameter
   * @param <V> the Java type of the third parameter
   */
  public static <R, T, U, V> AlgebraicGuarantee<R> tuple(
      String name,
      Pack3<? super T, ? super U, ? super V, ? extends R> convert,
      TypeGuarantee<T> first,
      TypeGuarantee<U> second,
      TypeGuarantee<V> third) {
    final var tupleType = Imyhat.algebraicTuple(name, first.type(), second.type(), third.type());
    return new AlgebraicGuarantee<>() {
      @Override
      public <G> G apply(GenericAlgebraicVisitor<G> visitor) {
        return visitor.tuple(name, Stream.of(first.type(), second.type(), third.type()));
      }

      @Override
      protected boolean checkEmpty() {
        return false;
      }

      @Override
      protected boolean checkObject(Stream<Pair<String, Imyhat>> contents) {
        return false;
      }

      @Override
      protected boolean checkTuple(Stream<Imyhat> contents) {
        return contents.allMatch(
            new Predicate<>() {
              int index;

              @Override
              public boolean test(Imyhat type) {
                switch (index++) {
                  case 0:
                    return first.type().isAssignableFrom(type);
                  case 1:
                    return second.type().isAssignableFrom(type);
                  case 2:
                    return third.type().isAssignableFrom(type);
                  default:
                    return false;
                }
              }
            });
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Imyhat type() {
        return tupleType;
      }

      @Override
      public R unpack(AlgebraicValue input) {
        return convert.pack(
            first.unpack(input.get(0)), second.unpack(input.get(1)), third.unpack(input.get(2)));
      }
    };
  }

  /**
   * Add a restriction for an algebraic datatype with four positional parameters
   *
   * @param name the Shesmu identifier for the algebraic type
   * @param convert a function to generate a value for the four parameter values provided
   * @param first the type guarantee for the first parameter
   * @param second the type guarantee for the second parameter
   * @param third the type guarantee for the third parameter
   * @param fourth the type guarantee for the fourth parameter
   * @return the newly constructed guarantee
   * @param <R> the Java type of the result
   * @param <T> the Java type of the first parameter
   * @param <U> the Java type of the second parameter
   * @param <V> the Java type of the third parameter
   * @param <W> the Java type of the fourth parameter
   */
  public static <R, T, U, V, W> AlgebraicGuarantee<R> tuple(
      String name,
      Pack4<? super T, ? super U, ? super V, ? super W, ? extends R> convert,
      TypeGuarantee<T> first,
      TypeGuarantee<U> second,
      TypeGuarantee<V> third,
      TypeGuarantee<W> fourth) {
    final var tupleType =
        Imyhat.algebraicTuple(name, first.type(), second.type(), third.type(), fourth.type());
    return new AlgebraicGuarantee<>() {
      @Override
      public <G> G apply(GenericAlgebraicVisitor<G> visitor) {
        return visitor.tuple(
            name, Stream.of(first.type(), second.type(), third.type(), fourth.type()));
      }

      @Override
      protected boolean checkEmpty() {
        return false;
      }

      @Override
      protected boolean checkObject(Stream<Pair<String, Imyhat>> contents) {
        return false;
      }

      @Override
      protected boolean checkTuple(Stream<Imyhat> contents) {
        return contents.allMatch(
            new Predicate<>() {
              int index;

              @Override
              public boolean test(Imyhat type) {
                switch (index++) {
                  case 0:
                    return first.type().isAssignableFrom(type);
                  case 1:
                    return second.type().isAssignableFrom(type);
                  case 2:
                    return third.type().isAssignableFrom(type);
                  case 3:
                    return fourth.type().isAssignableFrom(type);
                  default:
                    return false;
                }
              }
            });
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Imyhat type() {
        return tupleType;
      }

      @Override
      public R unpack(AlgebraicValue input) {
        return convert.pack(
            first.unpack(input.get(0)),
            second.unpack(input.get(1)),
            third.unpack(input.get(2)),
            fourth.unpack(input.get(3)));
      }
    };
  }

  @Override
  public boolean check(Map<String, Imyhat> variables, AlgebraicTransformer reference) {
    return reference.visit(
        new AlgebraicVisitor<>() {
          @Override
          public Boolean empty(String name) {
            return checkEmpty();
          }

          @Override
          public Boolean object(String name, Stream<Pair<String, Imyhat>> contents) {
            return checkObject(contents);
          }

          @Override
          public Boolean tuple(String name, Stream<Imyhat> contents) {
            return checkTuple(contents);
          }
        });
  }

  /**
   * Check if the guarantee can convert an empty algebraic value
   *
   * @return true if guarantee can perform this conversion
   */
  protected abstract boolean checkEmpty();

  /**
   * Check if the guarantee can convert an object algebraic value
   *
   * @param contents the types of the fields in the object
   * @return true if guarantee can perform this conversion
   */
  protected abstract boolean checkObject(Stream<Pair<String, Imyhat>> contents);

  /**
   * Check if the guarantee can convert a tuple algebraic value
   *
   * @param contents the types of the elements in the tuple
   * @return true if guarantee can perform this conversion
   */
  protected abstract boolean checkTuple(Stream<Imyhat> contents);

  @Override
  public Imyhat render(Map<String, Imyhat> variables) {
    return type();
  }

  @Override
  public String toString(Map<String, Imyhat> typeVariables) {
    return type().name();
  }

  /**
   * The Shesmu type for this algebraic value
   *
   * @return the type descriptor
   */
  public abstract Imyhat type();

  /**
   * Convert an algebraic value to the corresponding Java value
   *
   * @param algebraicValue the algebraic value to convert
   * @return the converted Java value
   */
  public abstract T unpack(AlgebraicValue algebraicValue);
}

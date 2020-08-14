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

public abstract class AlgebraicGuarantee<T> extends GenericAlgebraicGuarantee<T> {
  public static <R> AlgebraicGuarantee<R> empty(String name, R result) {
    final Imyhat tupleType = Imyhat.algebraicTuple(name);
    return new AlgebraicGuarantee<R>() {
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

  public static <R> AlgebraicGuarantee<R> emptyFactory(String name, Supplier<R> result) {
    final Imyhat tupleType = Imyhat.algebraicTuple(name);
    return new AlgebraicGuarantee<R>() {
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

  public static <R, T> AlgebraicGuarantee<R> object(
      String name,
      Function<? super T, ? extends R> convert,
      String paramName,
      TypeGuarantee<T> param) {
    final Imyhat tupleType =
        Imyhat.algebraicObject(name, Stream.of(new Pair<>(paramName, param.type())));
    return new AlgebraicGuarantee<R>() {
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
            new Predicate<Pair<String, Imyhat>>() {
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

  public static <R, T, U> AlgebraicGuarantee<R> object(
      String name,
      Pack2<? super T, ? super U, ? extends R> convert,
      String firstName,
      TypeGuarantee<T> first,
      String secondName,
      TypeGuarantee<U> second) {
    final Imyhat tupleType =
        Imyhat.algebraicObject(
            name,
            Stream.of(new Pair<>(firstName, first.type()), new Pair<>(secondName, second.type())));
    if (firstName.compareTo(secondName) >= 0) {
      throw new IllegalArgumentException("Object properties must be alphabetically ordered.");
    }
    return new AlgebraicGuarantee<R>() {
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
            new Predicate<Pair<String, Imyhat>>() {
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

  public static <R, T, U, V> AlgebraicGuarantee<R> object(
      String name,
      Pack3<? super T, ? super U, ? super V, ? extends R> convert,
      String firstName,
      TypeGuarantee<T> first,
      String secondName,
      TypeGuarantee<U> second,
      String thirdName,
      TypeGuarantee<V> third) {
    final Imyhat tupleType =
        Imyhat.algebraicObject(
            name,
            Stream.of(
                new Pair<>(firstName, first.type()),
                new Pair<>(secondName, second.type()),
                new Pair<>(thirdName, third.type())));
    if (firstName.compareTo(secondName) >= 0 || secondName.compareTo(thirdName) >= 0) {
      throw new IllegalArgumentException("Object properties must be alphabetically ordered.");
    }
    return new AlgebraicGuarantee<R>() {
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
            new Predicate<Pair<String, Imyhat>>() {
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
    final Imyhat tupleType =
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
    return new AlgebraicGuarantee<R>() {
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
            new Predicate<Pair<String, Imyhat>>() {
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
    final Imyhat tupleType =
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
    return new AlgebraicGuarantee<R>() {
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
            new Predicate<Pair<String, Imyhat>>() {
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

  public static <R, T> AlgebraicGuarantee<R> tuple(
      String name, Function<? super T, ? extends R> convert, TypeGuarantee<T> param) {
    final Imyhat tupleType = Imyhat.algebraicTuple(name, param.type());
    return new AlgebraicGuarantee<R>() {
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
            new Predicate<Imyhat>() {
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

  public static <R, T, U> AlgebraicGuarantee<R> tuple(
      String name,
      Pack2<? super T, ? super U, ? extends R> convert,
      TypeGuarantee<T> first,
      TypeGuarantee<U> second) {
    final Imyhat tupleType = Imyhat.algebraicTuple(name, first.type(), second.type());
    return new AlgebraicGuarantee<R>() {
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
            new Predicate<Imyhat>() {
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

  public static <R, T, U, V> AlgebraicGuarantee<R> tuple(
      String name,
      Pack3<? super T, ? super U, ? super V, ? extends R> convert,
      TypeGuarantee<T> first,
      TypeGuarantee<U> second,
      TypeGuarantee<V> third) {
    final Imyhat tupleType = Imyhat.algebraicTuple(name, first.type(), second.type(), third.type());
    return new AlgebraicGuarantee<R>() {
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
            new Predicate<Imyhat>() {
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

  public static <R, T, U, V, W> AlgebraicGuarantee<R> tuple(
      String name,
      Pack4<? super T, ? super U, ? super V, ? super W, ? extends R> convert,
      TypeGuarantee<T> first,
      TypeGuarantee<U> second,
      TypeGuarantee<V> third,
      TypeGuarantee<W> fourth) {
    final Imyhat tupleType =
        Imyhat.algebraicTuple(name, first.type(), second.type(), third.type(), fourth.type());
    return new AlgebraicGuarantee<R>() {
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
            new Predicate<Imyhat>() {
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
        new AlgebraicVisitor<Boolean>() {
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

  protected abstract boolean checkEmpty();

  protected abstract boolean checkObject(Stream<Pair<String, Imyhat>> contents);

  protected abstract boolean checkTuple(Stream<Imyhat> contents);

  @Override
  public Imyhat render(Map<String, Imyhat> variables) {
    return type();
  }

  @Override
  public String toString(Map<String, Imyhat> typeVariables) {
    return type().name();
  }

  public abstract Imyhat type();

  public abstract T unpack(AlgebraicValue algebraicValue);
}

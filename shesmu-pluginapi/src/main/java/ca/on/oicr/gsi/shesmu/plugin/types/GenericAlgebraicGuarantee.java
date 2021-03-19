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
import java.util.stream.Stream;

public abstract class GenericAlgebraicGuarantee<T> {

  public static <R, T> GenericAlgebraicGuarantee<R> object(
      String name,
      Function<? super T, ? extends R> convert,
      String paramName,
      GenericTypeGuarantee<T> param) {
    return new GenericAlgebraicGuarantee<>() {
      @Override
      public <G> G apply(GenericAlgebraicVisitor<G> visitor) {
        return visitor.genericObject(name, Stream.of(new Pair<>(paramName, param)));
      }

      @Override
      public boolean check(Map<String, Imyhat> variables, AlgebraicTransformer reference) {
        return reference.visit(
            new AlgebraicVisitor<>() {
              @Override
              public Boolean empty(String name) {
                return false;
              }

              @Override
              public Boolean object(String name, Stream<Pair<String, Imyhat>> contents) {
                return contents.allMatch(
                    new Predicate<>() {
                      int index;

                      @Override
                      public boolean test(Pair<String, Imyhat> field) {
                        if (index++ == 0) {
                          return field.first().equals(paramName)
                              && param.check(variables, field.second());
                        }
                        return false;
                      }
                    });
              }

              @Override
              public Boolean tuple(String name, Stream<Imyhat> contents) {
                return false;
              }
            });
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Imyhat render(Map<String, Imyhat> variables) {
        return Imyhat.algebraicObject(
            name, Stream.of(new Pair<>(paramName, param.render(variables))));
      }

      @Override
      public String toString(Map<String, Imyhat> typeVariables) {
        return String.format("%s {%s = %s}", name, paramName, param.toString(typeVariables));
      }

      @Override
      public R unpack(AlgebraicValue input) {
        return convert.apply(param.unpack(input.get(0)));
      }
    };
  }

  public static <R, T, U> GenericAlgebraicGuarantee<R> object(
      String name,
      Pack2<? super T, ? super U, ? extends R> convert,
      String firstName,
      GenericTypeGuarantee<T> first,
      String secondName,
      GenericTypeGuarantee<U> second) {
    if (firstName.compareTo(secondName) >= 0) {
      throw new IllegalArgumentException("Object properties must be alphabetically ordered.");
    }
    return new GenericAlgebraicGuarantee<>() {
      @Override
      public <G> G apply(GenericAlgebraicVisitor<G> visitor) {
        return visitor.genericObject(
            name, Stream.of(new Pair<>(firstName, first), new Pair<>(secondName, second)));
      }

      @Override
      public boolean check(Map<String, Imyhat> variables, AlgebraicTransformer reference) {
        return reference.visit(
            new AlgebraicVisitor<>() {
              @Override
              public Boolean empty(String name) {
                return false;
              }

              @Override
              public Boolean object(String name, Stream<Pair<String, Imyhat>> contents) {
                return contents.allMatch(
                    new Predicate<>() {
                      int index;

                      @Override
                      public boolean test(Pair<String, Imyhat> field) {
                        switch (index++) {
                          case 0:
                            return field.first().equals(firstName)
                                && first.check(variables, field.second());
                          case 1:
                            return field.first().equals(secondName)
                                && second.check(variables, field.second());
                          default:
                            return false;
                        }
                      }
                    });
              }

              @Override
              public Boolean tuple(String name, Stream<Imyhat> contents) {
                return false;
              }
            });
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Imyhat render(Map<String, Imyhat> variables) {
        return Imyhat.algebraicObject(
            name,
            Stream.of(
                new Pair<>(firstName, first.render(variables)),
                new Pair<>(secondName, second.render(variables))));
      }

      @Override
      public String toString(Map<String, Imyhat> typeVariables) {
        return String.format(
            "%s {%s = %s, %s = %s}",
            name,
            firstName,
            first.toString(typeVariables),
            secondName,
            second.toString(typeVariables));
      }

      @Override
      public R unpack(AlgebraicValue input) {
        return convert.pack(first.unpack(input.get(0)), second.unpack(input.get(1)));
      }
    };
  }

  public static <R, T, U, V> GenericAlgebraicGuarantee<R> object(
      String name,
      Pack3<? super T, ? super U, ? super V, ? extends R> convert,
      String firstName,
      GenericTypeGuarantee<T> first,
      String secondName,
      GenericTypeGuarantee<U> second,
      String thirdName,
      GenericTypeGuarantee<V> third) {
    if (firstName.compareTo(secondName) >= 0 || secondName.compareTo(thirdName) >= 0) {
      throw new IllegalArgumentException("Object properties must be alphabetically ordered.");
    }
    return new GenericAlgebraicGuarantee<>() {
      @Override
      public <G> G apply(GenericAlgebraicVisitor<G> visitor) {
        return visitor.genericObject(
            name,
            Stream.of(
                new Pair<>(firstName, first),
                new Pair<>(secondName, second),
                new Pair<>(thirdName, third)));
      }

      @Override
      public boolean check(Map<String, Imyhat> variables, AlgebraicTransformer reference) {
        return reference.visit(
            new AlgebraicVisitor<>() {
              @Override
              public Boolean empty(String name) {
                return false;
              }

              @Override
              public Boolean object(String name, Stream<Pair<String, Imyhat>> contents) {
                return contents.allMatch(
                    new Predicate<>() {
                      int index;

                      @Override
                      public boolean test(Pair<String, Imyhat> field) {
                        switch (index++) {
                          case 0:
                            return field.first().equals(firstName)
                                && first.check(variables, field.second());
                          case 1:
                            return field.first().equals(secondName)
                                && second.check(variables, field.second());
                          case 2:
                            return field.first().equals(thirdName)
                                && third.check(variables, field.second());
                          default:
                            return false;
                        }
                      }
                    });
              }

              @Override
              public Boolean tuple(String name, Stream<Imyhat> contents) {
                return false;
              }
            });
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Imyhat render(Map<String, Imyhat> variables) {
        return Imyhat.algebraicObject(
            name,
            Stream.of(
                new Pair<>(firstName, first.render(variables)),
                new Pair<>(secondName, second.render(variables)),
                new Pair<>(thirdName, third.render(variables))));
      }

      @Override
      public String toString(Map<String, Imyhat> typeVariables) {
        return String.format(
            "%s {%s = %s, %s = %s, %s = %s}",
            name,
            firstName,
            first.toString(typeVariables),
            secondName,
            second.toString(typeVariables),
            thirdName,
            third.toString(typeVariables));
      }

      @Override
      public R unpack(AlgebraicValue input) {
        return convert.pack(
            first.unpack(input.get(0)), second.unpack(input.get(1)), third.unpack(input.get(2)));
      }
    };
  }

  public static <R, T, U, V, W> GenericAlgebraicGuarantee<R> object(
      String name,
      Pack4<? super T, ? super U, ? super V, ? super W, ? extends R> convert,
      String firstName,
      GenericTypeGuarantee<T> first,
      String secondName,
      GenericTypeGuarantee<U> second,
      String thirdName,
      GenericTypeGuarantee<V> third,
      String fourthName,
      GenericTypeGuarantee<W> fourth) {
    if (firstName.compareTo(secondName) >= 0
        || secondName.compareTo(thirdName) >= 0
        || thirdName.compareTo(fourthName) >= 0) {
      throw new IllegalArgumentException("Object properties must be alphabetically ordered.");
    }
    return new GenericAlgebraicGuarantee<>() {
      @Override
      public <G> G apply(GenericAlgebraicVisitor<G> visitor) {
        return visitor.genericObject(
            name,
            Stream.of(
                new Pair<>(firstName, first),
                new Pair<>(secondName, second),
                new Pair<>(thirdName, third),
                new Pair<>(fourthName, fourth)));
      }

      @Override
      public boolean check(Map<String, Imyhat> variables, AlgebraicTransformer reference) {
        return reference.visit(
            new AlgebraicVisitor<>() {
              @Override
              public Boolean empty(String name) {
                return false;
              }

              @Override
              public Boolean object(String name, Stream<Pair<String, Imyhat>> contents) {
                return contents.allMatch(
                    new Predicate<>() {
                      int index;

                      @Override
                      public boolean test(Pair<String, Imyhat> field) {
                        switch (index++) {
                          case 0:
                            return field.first().equals(firstName)
                                && first.check(variables, field.second());
                          case 1:
                            return field.first().equals(secondName)
                                && second.check(variables, field.second());
                          case 2:
                            return field.first().equals(thirdName)
                                && third.check(variables, field.second());
                          case 3:
                            return field.first().equals(fourthName)
                                && fourth.check(variables, field.second());
                          default:
                            return false;
                        }
                      }
                    });
              }

              @Override
              public Boolean tuple(String name, Stream<Imyhat> contents) {
                return false;
              }
            });
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Imyhat render(Map<String, Imyhat> variables) {
        return Imyhat.algebraicObject(
            name,
            Stream.of(
                new Pair<>(firstName, first.render(variables)),
                new Pair<>(secondName, second.render(variables)),
                new Pair<>(thirdName, third.render(variables)),
                new Pair<>(fourthName, fourth.render(variables))));
      }

      @Override
      public String toString(Map<String, Imyhat> typeVariables) {
        return String.format(
            "%s {%s = %s, %s = %s, %s = %s, %s = %s}",
            name,
            firstName,
            first.toString(typeVariables),
            secondName,
            second.toString(typeVariables),
            thirdName,
            third.toString(typeVariables),
            fourthName,
            fourth.toString(typeVariables));
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

  public static <R, T, U, V, W, X> GenericAlgebraicGuarantee<R> object(
      String name,
      Pack5<? super T, ? super U, ? super V, ? super W, ? super X, ? extends R> convert,
      String firstName,
      GenericTypeGuarantee<T> first,
      String secondName,
      GenericTypeGuarantee<U> second,
      String thirdName,
      GenericTypeGuarantee<V> third,
      String fourthName,
      GenericTypeGuarantee<W> fourth,
      String fifthName,
      GenericTypeGuarantee<X> fifth) {
    if (firstName.compareTo(secondName) >= 0
        || secondName.compareTo(thirdName) >= 0
        || thirdName.compareTo(fourthName) >= 0
        || fourthName.compareTo(fifthName) >= 0) {
      throw new IllegalArgumentException("Object properties must be alphabetically ordered.");
    }
    return new GenericAlgebraicGuarantee<>() {
      @Override
      public <G> G apply(GenericAlgebraicVisitor<G> visitor) {
        return visitor.genericObject(
            name,
            Stream.of(
                new Pair<>(firstName, first),
                new Pair<>(secondName, second),
                new Pair<>(thirdName, third),
                new Pair<>(fourthName, fourth),
                new Pair<>(fifthName, fifth)));
      }

      @Override
      public boolean check(Map<String, Imyhat> variables, AlgebraicTransformer reference) {
        return reference.visit(
            new AlgebraicVisitor<>() {
              @Override
              public Boolean empty(String name) {
                return false;
              }

              @Override
              public Boolean object(String name, Stream<Pair<String, Imyhat>> contents) {
                return contents.allMatch(
                    new Predicate<>() {
                      int index;

                      @Override
                      public boolean test(Pair<String, Imyhat> field) {
                        switch (index++) {
                          case 0:
                            return field.first().equals(firstName)
                                && first.check(variables, field.second());
                          case 1:
                            return field.first().equals(secondName)
                                && second.check(variables, field.second());
                          case 2:
                            return field.first().equals(thirdName)
                                && third.check(variables, field.second());
                          case 3:
                            return field.first().equals(fourthName)
                                && fourth.check(variables, field.second());
                          case 4:
                            return field.first().equals(fifthName)
                                && fifth.check(variables, field.second());
                          default:
                            return false;
                        }
                      }
                    });
              }

              @Override
              public Boolean tuple(String name, Stream<Imyhat> contents) {
                return false;
              }
            });
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Imyhat render(Map<String, Imyhat> variables) {
        return Imyhat.algebraicObject(
            name,
            Stream.of(
                new Pair<>(firstName, first.render(variables)),
                new Pair<>(secondName, second.render(variables)),
                new Pair<>(thirdName, third.render(variables)),
                new Pair<>(fourthName, fourth.render(variables)),
                new Pair<>(fifthName, fifth.render(variables))));
      }

      @Override
      public String toString(Map<String, Imyhat> typeVariables) {
        return String.format(
            "%s {%s = %s, %s = %s, %s = %s, %s = %s, %s = %s}",
            name,
            firstName,
            first.toString(typeVariables),
            secondName,
            second.toString(typeVariables),
            thirdName,
            third.toString(typeVariables),
            fourthName,
            fourth.toString(typeVariables),
            fifthName,
            fifth.toString(typeVariables));
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

  public static <R, T> GenericAlgebraicGuarantee<R> tuple(
      String name, Function<? super T, ? extends R> convert, GenericTypeGuarantee<T> param) {
    return new GenericAlgebraicGuarantee<>() {
      @Override
      public <G> G apply(GenericAlgebraicVisitor<G> visitor) {
        return visitor.genericTuple(name, Stream.of(param));
      }

      @Override
      public boolean check(Map<String, Imyhat> variables, AlgebraicTransformer reference) {
        return reference.visit(
            new AlgebraicVisitor<>() {
              @Override
              public Boolean empty(String name) {
                return false;
              }

              @Override
              public Boolean object(String name, Stream<Pair<String, Imyhat>> contents) {
                return false;
              }

              @Override
              public Boolean tuple(String name, Stream<Imyhat> contents) {
                return contents.allMatch(
                    new Predicate<>() {
                      int index;

                      @Override
                      public boolean test(Imyhat element) {
                        if (index++ == 0) {
                          return param.check(variables, element);
                        }
                        return false;
                      }
                    });
              }
            });
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Imyhat render(Map<String, Imyhat> variables) {
        return Imyhat.algebraicTuple(name, param.render(variables));
      }

      @Override
      public String toString(Map<String, Imyhat> typeVariables) {
        return String.format("%s {%s}", name, param.toString(typeVariables));
      }

      @Override
      public R unpack(AlgebraicValue input) {
        return convert.apply(param.unpack(input.get(0)));
      }
    };
  }

  public static <R, T, U> GenericAlgebraicGuarantee<R> tuple(
      String name,
      Pack2<? super T, ? super U, ? extends R> convert,
      GenericTypeGuarantee<T> first,
      GenericTypeGuarantee<U> second) {
    return new GenericAlgebraicGuarantee<>() {
      @Override
      public <G> G apply(GenericAlgebraicVisitor<G> visitor) {
        return visitor.genericTuple(name, Stream.of(first, second));
      }

      @Override
      public boolean check(Map<String, Imyhat> variables, AlgebraicTransformer reference) {
        return reference.visit(
            new AlgebraicVisitor<>() {
              @Override
              public Boolean empty(String name) {
                return false;
              }

              @Override
              public Boolean object(String name, Stream<Pair<String, Imyhat>> contents) {
                return false;
              }

              @Override
              public Boolean tuple(String name, Stream<Imyhat> contents) {
                return contents.allMatch(
                    new Predicate<>() {
                      int index;

                      @Override
                      public boolean test(Imyhat element) {
                        switch (index++) {
                          case 0:
                            return first.check(variables, element);
                          case 1:
                            return second.check(variables, element);
                          default:
                            return false;
                        }
                      }
                    });
              }
            });
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Imyhat render(Map<String, Imyhat> variables) {
        return Imyhat.algebraicTuple(name, first.render(variables), second.render(variables));
      }

      @Override
      public String toString(Map<String, Imyhat> typeVariables) {
        return String.format(
            "%s {%s, %s}", name, first.toString(typeVariables), second.toString(typeVariables));
      }

      @Override
      public R unpack(AlgebraicValue input) {
        return convert.pack(first.unpack(input.get(0)), second.unpack(input.get(1)));
      }
    };
  }

  public static <R, T, U, V> GenericAlgebraicGuarantee<R> tuple(
      String name,
      Pack3<? super T, ? super U, ? super V, ? extends R> convert,
      GenericTypeGuarantee<T> first,
      GenericTypeGuarantee<U> second,
      GenericTypeGuarantee<V> third) {
    return new GenericAlgebraicGuarantee<>() {
      @Override
      public <G> G apply(GenericAlgebraicVisitor<G> visitor) {
        return visitor.genericTuple(name, Stream.of(first, second, third));
      }

      @Override
      public boolean check(Map<String, Imyhat> variables, AlgebraicTransformer reference) {
        return reference.visit(
            new AlgebraicVisitor<>() {
              @Override
              public Boolean empty(String name) {
                return false;
              }

              @Override
              public Boolean object(String name, Stream<Pair<String, Imyhat>> contents) {
                return false;
              }

              @Override
              public Boolean tuple(String name, Stream<Imyhat> contents) {
                return contents.allMatch(
                    new Predicate<>() {
                      int index;

                      @Override
                      public boolean test(Imyhat element) {
                        switch (index++) {
                          case 0:
                            return first.check(variables, element);
                          case 1:
                            return second.check(variables, element);
                          case 2:
                            return third.check(variables, element);
                          default:
                            return false;
                        }
                      }
                    });
              }
            });
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Imyhat render(Map<String, Imyhat> variables) {
        return Imyhat.algebraicTuple(
            name, first.render(variables), second.render(variables), third.render(variables));
      }

      @Override
      public String toString(Map<String, Imyhat> typeVariables) {
        return String.format(
            "%s {%s, %s, %s}",
            name,
            first.toString(typeVariables),
            second.toString(typeVariables),
            third.toString(typeVariables));
      }

      @Override
      public R unpack(AlgebraicValue input) {
        return convert.pack(
            first.unpack(input.get(0)), second.unpack(input.get(1)), third.unpack(input.get(2)));
      }
    };
  }

  public static <R, T, U, V, W> GenericAlgebraicGuarantee<R> tuple(
      String name,
      Pack4<? super T, ? super U, ? super V, ? super W, ? extends R> convert,
      GenericTypeGuarantee<T> first,
      GenericTypeGuarantee<U> second,
      GenericTypeGuarantee<V> third,
      GenericTypeGuarantee<W> fourth) {
    return new GenericAlgebraicGuarantee<>() {
      @Override
      public <G> G apply(GenericAlgebraicVisitor<G> visitor) {
        return visitor.genericTuple(name, Stream.of(first, second, third, fourth));
      }

      @Override
      public boolean check(Map<String, Imyhat> variables, AlgebraicTransformer reference) {
        return reference.visit(
            new AlgebraicVisitor<>() {
              @Override
              public Boolean empty(String name) {
                return false;
              }

              @Override
              public Boolean object(String name, Stream<Pair<String, Imyhat>> contents) {
                return false;
              }

              @Override
              public Boolean tuple(String name, Stream<Imyhat> contents) {
                return contents.allMatch(
                    new Predicate<>() {
                      int index;

                      @Override
                      public boolean test(Imyhat element) {
                        switch (index++) {
                          case 0:
                            return first.check(variables, element);
                          case 1:
                            return second.check(variables, element);
                          case 2:
                            return third.check(variables, element);
                          case 3:
                            return fourth.check(variables, element);
                          default:
                            return false;
                        }
                      }
                    });
              }
            });
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Imyhat render(Map<String, Imyhat> variables) {
        return Imyhat.algebraicTuple(
            name,
            first.render(variables),
            second.render(variables),
            third.render(variables),
            fourth.render(variables));
      }

      @Override
      public String toString(Map<String, Imyhat> typeVariables) {
        return String.format(
            "%s {%s, %s, %s, %s}",
            name,
            first.toString(typeVariables),
            second.toString(typeVariables),
            third.toString(typeVariables),
            fourth.toString(typeVariables));
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

  public abstract <R> R apply(GenericAlgebraicVisitor<R> visitor);

  public abstract boolean check(Map<String, Imyhat> variables, AlgebraicTransformer reference);

  public abstract String name();

  public abstract Imyhat render(Map<String, Imyhat> variables);

  public abstract String toString(Map<String, Imyhat> typeVariables);

  public abstract T unpack(AlgebraicValue value);
}

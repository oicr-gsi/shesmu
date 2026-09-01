package ca.on.oicr.gsi.shesmu.plugin.types;

import ca.on.oicr.gsi.Pair;
import java.io.IOException;
import java.util.stream.Stream;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class ImyhatSerializer extends ValueSerializer<Imyhat> {
  private interface Generator {
    void generate(JsonGenerator generator) throws IOException;
  }

  private static Generator field(String name, Generator value) {
    return g -> {
      g.writeName(name);
      value.generate(g);
    };
  }

  private static Generator just(Imyhat imyhat) {
    return g -> g.writeString(imyhat.descriptor());
  }

  @Override
  public void serialize(Imyhat imyhat, JsonGenerator jsonGenerator, SerializationContext context) {
    try {
      imyhat
          .apply(
              new ImyhatTransformer<Generator>() {
                @Override
                public Generator algebraic(Stream<AlgebraicTransformer> contents) {
                  final var generators =
                      contents
                          .map(
                              t ->
                                  t.visit(
                                      new AlgebraicVisitor<Generator>() {
                                        @Override
                                        public Generator empty(String name) {
                                          return g -> g.writeNullProperty(name);
                                        }

                                        @Override
                                        public Generator object(
                                            String name, Stream<Pair<String, Imyhat>> contents) {
                                          final var members =
                                              contents
                                                  .<Generator>map(
                                                      p ->
                                                          g -> {
                                                            g.writeName(p.first());
                                                            serialize(p.second(), g, context);
                                                          })
                                                  .toList();
                                          return g -> {
                                            g.writeObjectPropertyStart(name);
                                            for (final var member : members) {
                                              member.generate(g);
                                            }
                                            g.writeEndObject();
                                          };
                                        }

                                        @Override
                                        public Generator tuple(
                                            String name, Stream<Imyhat> contents) {
                                          final var members = contents.toList();
                                          return g -> {
                                            g.writeArrayPropertyStart(name);
                                            for (final var member : members) {
                                              serialize(member, g, context);
                                            }
                                            g.writeEndArray();
                                          };
                                        }
                                      }))
                          .toList();
                  return g -> {
                    g.writeStartObject();
                    g.writeStringProperty("is", "algebraic");
                    g.writeObjectPropertyStart("union");
                    for (final var union : generators) {
                      union.generate(g);
                    }
                    g.writeEndObject();
                    g.writeEndObject();
                  };
                }

                @Override
                public Generator bool() {
                  return just(Imyhat.BOOLEAN);
                }

                @Override
                public Generator date() {
                  return just(Imyhat.DATE);
                }

                @Override
                public Generator floating() {
                  return just(Imyhat.FLOAT);
                }

                @Override
                public Generator integer() {
                  return just(Imyhat.INTEGER);
                }

                @Override
                public Generator json() {
                  return just(Imyhat.JSON);
                }

                @Override
                public Generator list(Imyhat inner) {
                  return single(inner, "list", Imyhat.EMPTY);
                }

                @Override
                public Generator map(Imyhat key, Imyhat value) {
                  final var keyGenerator = key.apply(this);
                  final var valueGenerator = value.apply(this);
                  return g -> {
                    g.writeStartObject();
                    g.writeStringProperty("is", "dictionary");
                    g.writeName("key");
                    keyGenerator.generate(g);
                    g.writeName("value");
                    valueGenerator.generate(g);
                    g.writeEndObject();
                  };
                }

                @Override
                public Generator object(Stream<Pair<String, Imyhat>> contents) {
                  final var fields =
                      contents.map(p -> field(p.first(), p.second().apply(this))).toList();
                  return g -> {
                    g.writeStartObject();
                    g.writeStringProperty("is", "object");
                    g.writeName("fields");
                    g.writeStartObject();
                    for (final var field : fields) {
                      field.generate(g);
                    }
                    g.writeEndObject();
                    g.writeEndObject();
                  };
                }

                @Override
                public Generator optional(Imyhat inner) {
                  return single(inner, "optional", Imyhat.NOTHING);
                }

                @Override
                public Generator path() {
                  return just(Imyhat.PATH);
                }

                private Generator single(Imyhat inner, String name, Imyhat whenNull) {
                  if (inner == null) return just(whenNull);
                  final var generator = inner.apply(this);
                  return g -> {
                    g.writeStartObject();
                    g.writeStringProperty("is", name);
                    g.writeName("inner");
                    generator.generate(g);
                    g.writeEndObject();
                  };
                }

                @Override
                public Generator string() {
                  return just(Imyhat.STRING);
                }

                @Override
                public Generator tuple(Stream<Imyhat> contents) {
                  final var elements = contents.map(e -> e.apply(this)).toList();
                  return g -> {
                    g.writeStartArray();
                    for (final var element : elements) {
                      element.generate(g);
                    }
                    g.writeEndArray();
                  };
                }
              })
          .generate(jsonGenerator);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

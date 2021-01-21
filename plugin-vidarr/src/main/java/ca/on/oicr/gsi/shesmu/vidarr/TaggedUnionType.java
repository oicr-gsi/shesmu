package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.vidarr.BasicType;
import java.util.Map.Entry;
import java.util.stream.Stream;

final class TaggedUnionType implements BasicType.Visitor<Imyhat> {

  private final String key;

  public TaggedUnionType(String key) {
    this.key = key;
  }

  @Override
  public Imyhat bool() {
    return Imyhat.BAD;
  }

  @Override
  public Imyhat date() {
    return Imyhat.BAD;
  }

  @Override
  public Imyhat dictionary(BasicType simpleType, BasicType simpleType1) {
    return Imyhat.BAD;
  }

  @Override
  public Imyhat floating() {
    return Imyhat.BAD;
  }

  @Override
  public Imyhat integer() {
    return Imyhat.BAD;
  }

  @Override
  public Imyhat json() {
    return Imyhat.BAD;
  }

  @Override
  public Imyhat list(BasicType simpleType) {
    return Imyhat.BAD;
  }

  @Override
  public Imyhat object(Stream<Pair<String, BasicType>> fields) {
    return Imyhat.algebraicObject(
        key,
        fields.map(f -> new Pair<>(f.first(), f.second().apply(VidarrPlugin.SIMPLE_TO_IMYHAT))));
  }

  @Override
  public Imyhat optional(BasicType simpleType) {
    return Imyhat.BAD;
  }

  @Override
  public Imyhat pair(BasicType left, BasicType right) {
    return Imyhat.algebraicObject(
        key,
        Stream.of(
            new Pair<>("left", left.apply(VidarrPlugin.SIMPLE_TO_IMYHAT)),
            new Pair<>("right", right.apply(VidarrPlugin.SIMPLE_TO_IMYHAT))));
  }

  @Override
  public Imyhat string() {
    return Imyhat.BAD;
  }

  @Override
  public Imyhat taggedUnion(Stream<Entry<String, BasicType>> stream) {
    return Imyhat.BAD;
  }

  @Override
  public Imyhat tuple(Stream<BasicType> elements) {
    return Imyhat.algebraicTuple(
        key, elements.map(e -> e.apply(VidarrPlugin.SIMPLE_TO_IMYHAT)).toArray(Imyhat[]::new));
  }
}

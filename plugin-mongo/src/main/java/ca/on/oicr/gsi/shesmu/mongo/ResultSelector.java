package ca.on.oicr.gsi.shesmu.mongo;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import com.mongodb.client.MongoIterable;
import java.util.Optional;
import java.util.function.Consumer;
import org.bson.Document;

public enum ResultSelector {
  FIRST {
    @Override
    public Imyhat type(Imyhat inner) {
      return inner.asOptional();
    }

    @Override
    public Object process(MongoIterable<Document> iterable, ReturnConverter converter) {
      return Optional.ofNullable(iterable.first()).map(converter::unpackRoot);
    }
  },
  ALL {
    @Override
    public Imyhat type(Imyhat inner) {
      return inner.asList();
    }

    @Override
    public Object process(MongoIterable<Document> iterable, ReturnConverter converter) {
      final var set = converter.type().newSet();
      iterable.map(converter::unpackRoot).forEach((Consumer<Object>) set::add);
      return set;
    }
  };

  public abstract Imyhat type(Imyhat inner);

  public abstract Object process(MongoIterable<Document> iterable, ReturnConverter converter);
}

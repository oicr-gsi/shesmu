package ca.on.oicr.gsi.shesmu.mongo;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import com.mongodb.Block;
import com.mongodb.client.MongoIterable;
import java.util.Optional;
import java.util.Set;
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
      final Set<Object> set = converter.type().newSet();
      iterable.map(converter::unpackRoot).forEach((Block<Object>) set::add);
      return set;
    }
  };

  public abstract Imyhat type(Imyhat inner);

  public abstract Object process(MongoIterable<Document> iterable, ReturnConverter converter);
}

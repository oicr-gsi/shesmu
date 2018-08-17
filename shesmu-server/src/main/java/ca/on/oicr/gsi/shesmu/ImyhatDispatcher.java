package ca.on.oicr.gsi.shesmu;

import java.time.Instant;
import java.util.stream.Stream;

public interface ImyhatDispatcher {

	void consume(boolean value);

	void consume(Instant value);

	void consume(int position, Object value, Imyhat type);

	void consume(long value);

	<T> void consume(Stream<T> values, Imyhat inner);

	void consume(String value);
}

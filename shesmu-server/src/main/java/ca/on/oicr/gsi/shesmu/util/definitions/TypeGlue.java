package ca.on.oicr.gsi.shesmu.util.definitions;

import java.time.Instant;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.Tuple;

public interface TypeGlue<T> {
	TypeGlue<Boolean> BOOLEAN = new TypeGlue<Boolean>() {

		@Override
		public Imyhat type() {
			return Imyhat.BOOLEAN;
		}

	};
	TypeGlue<Instant> DATE = new TypeGlue<Instant>() {

		@Override
		public Imyhat type() {
			return Imyhat.DATE;
		}

	};
	TypeGlue<Long> LONG = new TypeGlue<Long>() {

		@Override
		public Imyhat type() {
			return Imyhat.INTEGER;
		}

	};

	public static TypeGlue<Tuple> tuple(Imyhat... inner) {
		final Imyhat tupleType = Imyhat.tuple(inner);
		return () -> tupleType;
	};

	Imyhat type();
}
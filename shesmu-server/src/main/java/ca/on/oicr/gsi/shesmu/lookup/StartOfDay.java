package ca.on.oicr.gsi.shesmu.lookup;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;

@MetaInfServices
public class StartOfDay implements Lookup {

	@Override
	public Object lookup(Object... parameters) {
		return ((Instant)parameters[0]).truncatedTo(ChronoUnit.DAYS);
	}

	@Override
	public String name() {
		return "start_of_day";
	}

	@Override
	public Imyhat returnType() {
		return Imyhat.DATE;
	}

	@Override
	public Stream<Imyhat> types() {
		return Stream.of(Imyhat.DATE);
	}

}

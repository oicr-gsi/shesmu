package ca.on.oicr.gsi.shesmu.lookup;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;

@MetaInfServices
public class FileName implements Lookup {

	@Override
	public Object lookup(Object... parameters) {
		final Path path = Paths.get((String) parameters[0]);
		return path.getFileName().toString();
	}

	@Override
	public String name() {
		return "file_name";
	}

	@Override
	public Imyhat returnType() {
		return Imyhat.STRING;
	}

	@Override
	public Stream<Imyhat> types() {
		return Stream.of(Imyhat.STRING);
	}

}

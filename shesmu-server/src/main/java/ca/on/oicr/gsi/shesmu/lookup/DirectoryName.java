package ca.on.oicr.gsi.shesmu.lookup;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;

@MetaInfServices
public class DirectoryName implements Lookup {

	@Override
	public Object lookup(Object... parameters) {
		final Path path = Paths.get((String) parameters[0]).getParent();
		return path == null ? "" : path.toString();
	}

	@Override
	public String name() {
		return "dir_name";
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

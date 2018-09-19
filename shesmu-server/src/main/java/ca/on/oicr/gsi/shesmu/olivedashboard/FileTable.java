package ca.on.oicr.gsi.shesmu.olivedashboard;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.InputFormatDefinition;

public final class FileTable {
	private final String filename;
	private final InputFormatDefinition format;
	private final List<OliveTable> olives;
	private final Instant timestamp;

	public FileTable(String filename, InputFormatDefinition format, Instant timestamp, Stream<OliveTable> olives) {
		super();
		this.filename = filename;
		this.format = format;
		this.timestamp = timestamp;
		this.olives = olives.collect(Collectors.toList());
	}

	public String filename() {
		return filename;
	}

	public InputFormatDefinition format() {
		return format;
	}

	public Stream<OliveTable> olives() {
		return olives.stream();
	}

	public Instant timestamp() {
		return timestamp;
	}

}

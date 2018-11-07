package ca.on.oicr.gsi.shesmu.gsistd.niassa;

import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import ca.on.oicr.gsi.shesmu.util.definitions.UserDefiner;

public class WorkflowConfiguration {
	private long accession;
	private LanesType lanes;
	private int maxInFlight;
	private String name;
	private IniParam[] parameters;
	private long[] previousAccessions;
	private String[] services;

	public void define(NiassaServer server, UserDefiner definer, Configuration value) {
		final String description = //
				String.format("Runs SeqWare/Niassa workflow %d using %s with settings in %s.", //
						accession, //
						value.getJar(), //
						value.getSettings())
						+ (previousAccessions.length == 0 ? ""
								: LongStream.of(getPreviousAccessions())//
										.sorted()//
										.mapToObj(Long::toString)//
										.collect(
												Collectors.joining(", ", " Considered equivalent to workflows: ", "")));
		definer.defineAction(name, description, WorkflowAction.class,
				() -> new WorkflowAction(server, getLanes(), accession, previousAccessions, value.getJar(),
						value.getSettings(), services), //
				Stream.concat(//
						getLanes() == null ? Stream.empty() : Stream.of(new ActionParameterDefinition() {

							@Override
							public String name() {
								return "lanes";
							}

							@Override
							public boolean required() {
								return true;
							}

							@Override
							public void store(Renderer renderer, Type type, int actionLocal,
									Consumer<Renderer> loadParameter) {
								renderer.methodGen().loadLocal(actionLocal);
								loadParameter.accept(renderer);
								renderer.methodGen().invokeVirtual(type,
										new Method("lanes", Type.VOID_TYPE, new Type[] { Type.getType(Set.class) }));
							}

							@Override
							public Imyhat type() {
								return getLanes().innerType().asList();
							}
						}), //
						Stream.of(getParameters())));
	}

	public long getAccession() {
		return accession;
	}

	public int getMaxInFlight() {
		return maxInFlight;
	}

	public String getName() {
		return name;
	}

	public long[] getPreviousAccessions() {
		return previousAccessions;
	}

	public String[] getServices() {
		return services;
	}

	public void setAccession(long accession) {
		this.accession = accession;
	}

	public void setMaxInFlight(int maxInFlight) {
		this.maxInFlight = maxInFlight;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setPreviousAccessions(long[] previousAccessions) {
		this.previousAccessions = previousAccessions;
	}

	public void setServices(String[] services) {
		this.services = services;
	}

	public IniParam[] getParameters() {
		return parameters;
	}

	public void setParameters(IniParam[] parameters) {
		this.parameters = parameters;
	}

	public LanesType getLanes() {
		return lanes;
	}

	public void setLanes(LanesType lanes) {
		this.lanes = lanes;
	}

}

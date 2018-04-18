package ca.on.oicr.gsi.shesmu.actions.jira;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionRepository;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.ParameterDefinition;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import io.prometheus.client.Gauge;

@MetaInfServices
public class JiraActionRepository implements ActionRepository {

	private static class TicketActionDefinition extends ActionDefinition {
		private final Configuration config;

		public TicketActionDefinition(Configuration config, String prefix, Type type,
				Stream<ParameterDefinition> parameters) {
			super(String.format("%s_%s", prefix, config.getName()), type, Stream.concat(parameters,
					Stream.of(ParameterDefinition.forField(A_FILE_TICKET_TYPE, "summary", Imyhat.STRING, true))));
			this.config = config;
		}

		@Override
		public void initialize(GeneratorAdapter methodGen) {
			methodGen.newInstance(type());
			methodGen.dup();
			methodGen.push(config.getName());
			methodGen.push(config.getUrl());
			methodGen.push(config.getToken());
			methodGen.push(config.getProjectKey());
			methodGen.invokeConstructor(type(), CTOR_FILE_TICKET);
		}

	}

	private static final Type A_FILE_TICKET_TYPE = Type.getType(FileTicket.class);
	private static final Type A_RESOLVE_TICKET_TYPE = Type.getType(ResolveTicket.class);
	private static final Type A_STRING_TYPE = Type.getType(String.class);

	private static final Method CTOR_FILE_TICKET = new Method("<init>", Type.VOID_TYPE,
			new Type[] { A_STRING_TYPE, A_STRING_TYPE, A_STRING_TYPE, A_STRING_TYPE, A_STRING_TYPE });

	private static final Gauge lastRead = Gauge.build("shesmu_jira_config_last_read",
			"The last time, in seconds since the epoch, that the configuration was read.").register();

	private final List<Pair<String, Map<String, String>>> configuration = new ArrayList<>();

	private Stream<ActionDefinition> createActionDefinitions(Configuration config) {
		return Stream.of(
				new TicketActionDefinition(config, "ticket", A_FILE_TICKET_TYPE,
						Stream.of(
								ParameterDefinition.forField(A_FILE_TICKET_TYPE, "description", Imyhat.STRING, true))),
				new TicketActionDefinition(config, "resolve_ticket", A_RESOLVE_TICKET_TYPE, Stream.of(
						ParameterDefinition.forField(A_RESOLVE_TICKET_TYPE, "comment", Imyhat.STRING, false))));
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return configuration.stream();
	}

	@Override
	public Stream<ActionDefinition> queryActions() {
		lastRead.setToCurrentTime();
		configuration.clear();
		return RuntimeSupport.dataFiles(Configuration.class, ".jira")//
				.peek(this::writeConfigBlock)//
				.flatMap(this::createActionDefinitions);

	}

	private void writeConfigBlock(Configuration config) {
		final Map<String, String> properties = new TreeMap<>();
		properties.put("instance", config.getName());
		properties.put("project", config.getProjectKey());
		properties.put("url", config.getUrl());
		configuration.add(new Pair<>("JIRA Instance", properties));
	}

}
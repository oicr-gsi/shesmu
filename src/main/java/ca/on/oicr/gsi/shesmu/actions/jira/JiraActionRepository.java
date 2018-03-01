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

	private static final Type A_FILE_TICKET_TYPE = Type.getType(FileTicket.class);
	private static final Type A_STRING_TYPE = Type.getType(String.class);

	private static final Method CTOR_FILE_TICKET = new Method("<init>", Type.VOID_TYPE,
			new Type[] { A_STRING_TYPE, A_STRING_TYPE, A_STRING_TYPE, A_STRING_TYPE, A_STRING_TYPE });

	private static final Gauge lastRead = Gauge.build("shesmu_jira_config_last_read",
			"The last time, in seconds since the epoch, that the configuration was read.").register();

	private final List<Pair<String, Map<String, String>>> configuration = new ArrayList<>();

	private ActionDefinition createActionDefinition(Configuration config) {
		return new ActionDefinition(String.format("ticket_%s", config.getName()), A_FILE_TICKET_TYPE,
				Stream.of(ParameterDefinition.forField(A_FILE_TICKET_TYPE, "summary", Imyhat.STRING, false),
						ParameterDefinition.forField(A_FILE_TICKET_TYPE, "description", Imyhat.STRING, false))) {

			@Override
			public void initialize(GeneratorAdapter methodGen) {
				methodGen.newInstance(A_FILE_TICKET_TYPE);
				methodGen.dup();
				methodGen.push(config.getName());
				methodGen.push(config.getUrl());
				methodGen.push(config.getToken());
				methodGen.push(config.getProjectKey());
				methodGen.invokeConstructor(A_FILE_TICKET_TYPE, CTOR_FILE_TICKET);
			}
		};
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return configuration.stream();
	}

	@Override
	public Stream<ActionDefinition> query() {
		lastRead.setToCurrentTime();
		configuration.clear();
		return RuntimeSupport.dataFiles(Configuration.class, ".jira")//
				.peek(this::writeConfigBlock)//
				.map(this::createActionDefinition);

	}

	private void writeConfigBlock(Configuration config) {
		final Map<String, String> properties = new TreeMap<>();
		properties.put("instance", config.getName());
		properties.put("url", config.getUrl());
		configuration.add(new Pair<>("jira:instance", properties));
	}

}
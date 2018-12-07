package ca.on.oicr.gsi.shesmu.guanyin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import javax.xml.stream.XMLStreamException;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Server;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedConfiguration;
import ca.on.oicr.gsi.shesmu.util.definitions.JsonParameter;
import ca.on.oicr.gsi.shesmu.util.definitions.UserDefiner;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;

public class GuanyinFile extends AutoUpdatingJsonFile<Configuration> implements FileBackedConfiguration {

	private Optional<Configuration> configuration = Optional.empty();
	private final UserDefiner definer;

	public GuanyinFile(Path fileName, UserDefiner definer) {
		super(fileName, Configuration.class);
		this.definer = definer;
	}

	@Override
	public ConfigurationSection configuration() {
		return new ConfigurationSection("观音 Report Repository: " + fileName()) {

			@Override
			public void emit(SectionRenderer renderer) throws XMLStreamException {
				configuration.ifPresent(configuration -> {
					renderer.link("DRMAAWS", configuration.getDrmaa(), configuration.getDrmaa());
					renderer.link("观音", configuration.getGuanyin(), configuration.getGuanyin());
					renderer.line("Script", configuration.getScript());
				});
			}
		};

	}

	public String drmaaPsk() {
		return configuration.get().getDrmaaPsk();
	}

	public String drmaaUrl() {
		return configuration.get().getDrmaa();
	}

	public String script() {
		return configuration.get().getScript();
	}

	@Override
	protected Optional<Integer> update(Configuration configuration) {
		try (CloseableHttpResponse response = Server.HTTP_CLIENT
				.execute(new HttpGet(configuration.getGuanyin() + "/reportdb/reports"))) {
			definer.clearActions();
			for (final ReportDto report : RuntimeSupport.MAPPER.readValue(response.getEntity().getContent(),
					ReportDto[].class)) {
				if (!report.isValid()) {
					continue;
				}
				final long reportId = report.getId();
				final String actionName = report.getName() + "_" + report.getVersion().replaceAll("[^A-Za-z0-9_]", "_");
				final String description = String.format("Runs report %s-%s (%d) on Guanyin instance defined in %s.",
						report.getName(), report.getVersion(), report.getId(), fileName());
				final String reportName = String.format("%s %s[%s]", report.getName(), report.getVersion(),
						report.getCategory());
				definer.defineAction(actionName, description, RunReport.class,
						() -> new RunReport(this, reportId, reportName), //
						report.getPermittedParameters()//
								.entrySet().stream()//
								.map(e -> new JsonParameter(e.getKey(), Imyhat.parse(e.getValue().getType()),
										e.getValue().isRequired())));
			}
			this.configuration = Optional.of(configuration);
			return Optional.of(60);
		} catch (final IOException e) {
			e.printStackTrace();
			return Optional.of(5);
		}
	}

	public String 观音Url() {
		return configuration.get().getGuanyin();
	}
}

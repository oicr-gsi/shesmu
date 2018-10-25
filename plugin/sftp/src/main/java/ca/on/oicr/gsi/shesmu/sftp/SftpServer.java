package ca.on.oicr.gsi.shesmu.sftp;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import javax.xml.stream.XMLStreamException;

import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.Cache;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedConfiguration;
import ca.on.oicr.gsi.shesmu.util.definitions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.util.definitions.ShesmuParameter;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.SFTPClient;

public class SftpServer extends AutoUpdatingJsonFile<Configuration> implements FileBackedConfiguration {
	private static final Gauge connectionConnected = Gauge
			.build("shesmu_sftp_connection_connected", "Whether the SFTP connection is connected or not.")
			.labelNames("host", "port").register();

	private static final Counter connectionErrors = Counter
			.build("shesmu_sftp_connection_errors", "Number of SFTP errors encountered.").labelNames("host", "port")
			.register();

	private Instant backoff = Instant.EPOCH;

	private SSHClient client;

	private Optional<Configuration> configuration = Optional.empty();

	private final Cache<String, FileAttributes> fileAttributes = new Cache<String, FileAttributes>("sftp", 10) {

		@Override
		protected FileAttributes fetch(String fileName) throws IOException {
			return sftp == null ? null : sftp.statExistence(fileName);
		}
	};

	private volatile SFTPClient sftp;

	public SftpServer(Path fileName) {
		super(fileName, Configuration.class);
	}

	@ShesmuMethod(description = "Returns true if the file or directory exists on the SFTP server described in {file}.")
	public synchronized boolean $_exists(@ShesmuParameter(description = "path to file") String fileName,
			@ShesmuParameter(description = "value to return on error") boolean errorValue) {
		if (!attemptConnect()) {
			return errorValue;
		}
		return fileAttributes.get(fileName).isPresent();
	}

	@ShesmuMethod(description = "Gets the last modification timestamp of a file or directory living on the SFTP server described in {file}.")
	public synchronized Instant $_mtime(@ShesmuParameter(description = "path to file") String fileName,
			@ShesmuParameter(description = "time to return on error") Instant errorValue) {
		if (!attemptConnect()) {
			return errorValue;
		}
		return fileAttributes.get(fileName).map(a -> Instant.ofEpochSecond(a.getMtime())).orElse(errorValue);
	}

	@ShesmuMethod(description = "Get the size of a file, in bytes, living on the SFTP server described in {file}.")
	public synchronized long $_size(@ShesmuParameter(description = "path to file") String fileName,
			@ShesmuParameter(description = "size to return on error") long errorValue) {
		if (!attemptConnect()) {
			return errorValue;
		}
		return fileAttributes.get(fileName).map(FileAttributes::getSize).orElse(errorValue);
	}

	private boolean attemptConnect() {
		if (client != null && client.isAuthenticated() && sftp == null) {
			return true;
		}
		if (Duration.between(backoff, Instant.now()).toMinutes() < 2 || !configuration.isPresent()) {
			return false;
		}
		for (int i = 0; i < 3; i++) {
			if (client != null) {
				try {
					client.close();
				} catch (final Exception e) {
					e.printStackTrace();
					client = null;
				}
			}
			try {
				client = new SSHClient();
				client.loadKnownHosts();

				client.connect(configuration.get().getHost(), configuration.get().getPort());
				client.authPublickey(configuration.get().getUser());
				sftp = client.newSFTPClient();
				connectionConnected
						.labels(configuration.get().getHost(), Integer.toString(configuration.get().getPort())).set(1);
				return true;
			} catch (final Exception e) {
				sftp = null;
				e.printStackTrace();
				connectionErrors.labels(configuration.get().getHost(), Integer.toString(configuration.get().getPort()))
						.inc();
				connectionConnected
						.labels(configuration.get().getHost(), Integer.toString(configuration.get().getPort())).set(0);

			}
		}
		backoff = Instant.now();
		return false;
	}

	@Override
	public ConfigurationSection configuration() {
		return new ConfigurationSection(fileName().toString()) {

			@Override
			public void emit(SectionRenderer renderer) throws XMLStreamException {
				renderer.line("Filename", fileName().toString());
				configuration.ifPresent(configuration -> {
					renderer.line("Host", configuration.getHost());
					renderer.line("Port", configuration.getPort());
					renderer.line("User", configuration.getUser());
				});
				renderer.line("Active", client == null ? "No" : "Yes");
			}
		};
	}

	@Override
	public synchronized Optional<Integer> update(Configuration configuration) {
		this.configuration = Optional.of(configuration);
		try {
			if (client != null && client.isConnected()) {
				client.disconnect();
				connectionConnected.labels(configuration.getHost(), Integer.toString(configuration.getPort())).set(0);
			}
		} catch (final Exception e) {
			sftp = null;
			e.printStackTrace();
		}
		attemptConnect();
		return Optional.empty();
	}
}
package ca.on.oicr.gsi.shesmu.sftp;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionRepository;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.function.FunctionForInstance;
import io.prometheus.client.Counter;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;

@MetaInfServices
public class SftpFunctionRepository implements FunctionRepository {
	private class SftpServer extends AutoUpdatingJsonFile<Configuration> {
		private Instant backoff = Instant.EPOCH;

		private final SSHClient client = new SSHClient();

		private Optional<Configuration> configuration = Optional.empty();

		private final List<FunctionDefinition> definitions = new ArrayList<>();

		private final Cache<Boolean> exists = new Cache<Boolean>("fileexists") {

			@Override
			protected Boolean fetch(String fileName) throws IOException {
				return sftp.statExistence(fileName) != null;
			}
		};

		private final Cache<Instant> mtime = new Cache<Instant>("modificationtime") {

			@Override
			protected Instant fetch(String fileName) throws IOException {
				return Instant.ofEpochSecond(sftp.mtime(fileName));
			}
		};

		private final Map<String, String> properties = new TreeMap<>();

		private final String service;

		private volatile SFTPClient sftp;

		private final Cache<Long> size = new Cache<Long>("filesize") {

			@Override
			protected Long fetch(String fileName) throws IOException {
				return sftp.size(fileName);
			}

		};

		public SftpServer(Path fileName) {
			super(fileName, Configuration.class);
			try {
				client.loadKnownHosts();
			} catch (final IOException e) {
				e.printStackTrace();
			}
			properties.put("path", fileName.toString());
			final String filePart = fileName.getFileName().toString();
			service = filePart.substring(0, filePart.length() - EXTENSION.length());

			try {
				final Lookup lookup = MethodHandles.lookup();
				definitions.add(FunctionForInstance.bind(lookup, SftpServer.class, this, "size",
						String.format("%s_size", service),
						String.format("Get the size of a file, in bytes, living on the SFTP server described in %s.",
								fileName),
						Imyhat.INTEGER, Imyhat.STRING, Imyhat.INTEGER));
				definitions.add(FunctionForInstance.bind(lookup, SftpServer.class, this, "exists",
						String.format("%s_exists", service),
						String.format(
								"Returns true if the file or directory exists on the SFTP server described in %s.",
								fileName),
						Imyhat.BOOLEAN, Imyhat.STRING));
				definitions.add(FunctionForInstance.bind(lookup, SftpServer.class, this, "mtime",
						String.format("%s_mtime", service),
						String.format(
								"Gets the last modification timestamp of a file or directory living on the SFTP server described in %s.",
								fileName),
						Imyhat.DATE, Imyhat.STRING, Imyhat.DATE));
			} catch (NoSuchMethodException | IllegalAccessException e) {
				e.printStackTrace();
			}
		}

		private boolean attemptConnect() {
			if (client.isAuthenticated() && sftp == null) {
				return true;
			}
			if (Duration.between(backoff, Instant.now()).toMinutes() < 2 || !configuration.isPresent()) {
				return false;
			}
			for (int i = 0; i < 3; i++) {
				try {
					client.connect(configuration.get().getHost(), configuration.get().getPort());
					client.authPublickey(configuration.get().getUser());
					sftp = client.newSFTPClient();
					return true;
				} catch (final Exception e) {
					sftp = null;
					e.printStackTrace();
					connectionErrors
							.labels(configuration.get().getHost(), Integer.toString(configuration.get().getPort()))
							.inc();
				}
			}
			backoff = Instant.now();
			return false;
		}

		public Pair<String, Map<String, String>> configuration() {
			return new Pair<>(String.format("SFTP “%s”", service), properties);
		}

		public Stream<FunctionDefinition> definitions() {
			return definitions.stream();
		}

		@RuntimeInterop
		public synchronized boolean exists(String fileName) {
			if (!attemptConnect()) {
				return false;
			}
			return exists.get(fileName).orElse(false);
		}

		@RuntimeInterop
		public synchronized Instant mtime(String fileName, Instant errorValue) {
			if (!attemptConnect()) {
				return errorValue;
			}
			return mtime.get(fileName).orElse(errorValue);
		}

		@RuntimeInterop
		public synchronized long size(String fileName, long errorValue) {
			if (!attemptConnect()) {
				return errorValue;
			}
			return size.get(fileName).orElse(errorValue);
		}

		@Override
		public synchronized Optional<Integer> update(Configuration configuration) {
			properties.put("host", configuration.getHost());
			properties.put("port", Integer.toString(configuration.getPort()));
			properties.put("user", configuration.getUser());
			this.configuration = Optional.of(configuration);
			try {
				if (client.isConnected()) {
					client.disconnect();
				}
			} catch (final Exception e) {
				sftp = null;
				e.printStackTrace();
			}
			attemptConnect();
			return Optional.empty();
		}
	}

	private static final Counter connectionErrors = Counter
			.build("shesmu_sftp_connection_errors", "Number of SFTP errors encountered.").labelNames("host", "port")
			.register();

	private static final String EXTENSION = ".sftp";

	private final AutoUpdatingDirectory<SftpServer> configurations;

	public SftpFunctionRepository() {
		configurations = new AutoUpdatingDirectory<>(EXTENSION,SftpServer::new);
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return configurations.stream().map(SftpServer::configuration);
	}

	@Override
	public Stream<FunctionDefinition> queryFunctions() {
		return configurations.stream().flatMap(SftpServer::definitions);
	}

}

package ca.on.oicr.gsi.shesmu.actions.rest;

import java.io.IOException;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.Action;
import ca.on.oicr.gsi.shesmu.ActionState;
import ca.on.oicr.gsi.shesmu.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.actions.util.JsonParameterised;
import io.prometheus.client.Counter;

public final class LaunchRemote extends Action implements JsonParameterised {
	private static final CloseableHttpClient httpclient = HttpClients.createDefault();
	private static final Counter remoteError = Counter
			.build("shesmu_remote_action_errors", "Number of errors contacting the remote action service.")
			.labelNames("target").register();
	private static final LatencyHistogram requestTime = new LatencyHistogram("shesmu_remote_action_request_time",
			"The request time latency to launch a remote action.", "target");

	private final String launchUrl;

	private final ObjectNode parameters = RuntimeSupport.MAPPER.createObjectNode();

	private final ObjectNode request = RuntimeSupport.MAPPER.createObjectNode();

	private String resultUrl;

	private LaunchRemote(String launchUrl, String name) {
		this.launchUrl = launchUrl;
		request.put("name", name);
		request.set("parameters", parameters);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final LaunchRemote other = (LaunchRemote) obj;
		if (launchUrl == null) {
			if (other.launchUrl != null) {
				return false;
			}
		} else if (!launchUrl.equals(other.launchUrl)) {
			return false;
		}
		if (parameters == null) {
			if (other.parameters != null) {
				return false;
			}
		} else if (!parameters.equals(other.parameters)) {
			return false;
		}
		if (request == null) {
			if (other.request != null) {
				return false;
			}
		} else if (!request.equals(other.request)) {
			return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (launchUrl == null ? 0 : launchUrl.hashCode());
		result = prime * result + (parameters == null ? 0 : parameters.hashCode());
		result = prime * result + (request == null ? 0 : request.hashCode());
		return result;
	}

	@Override
	public ObjectNode parameters() {
		return parameters;
	}

	@Override
	public ActionState perform() {
		try {
			final HttpPost post = new HttpPost(launchUrl + "/launchaction");
			post.setEntity(
					new StringEntity(RuntimeSupport.MAPPER.writeValueAsString(request), ContentType.APPLICATION_JSON));
			try (AutoCloseable timer = requestTime.start(launchUrl);
					CloseableHttpResponse response = httpclient.execute(post)) {
				switch (response.getStatusLine().getStatusCode()) {
				case HttpStatus.SC_OK:
					final ObjectNode result = RuntimeSupport.MAPPER.readValue(response.getEntity().getContent(),
							ObjectNode.class);
					resultUrl = result.get("url").textValue();
					return ActionState.SUCCEEDED;
				case HttpStatus.SC_ACCEPTED:
					return ActionState.INFLIGHT;
				case HttpStatus.SC_BAD_REQUEST:
				case HttpStatus.SC_INTERNAL_SERVER_ERROR:
					return ActionState.FAILED;
				case HttpStatus.SC_SERVICE_UNAVAILABLE:
					return ActionState.THROTTLED;
				}
			} catch (final Exception e) {
				e.printStackTrace();
				remoteError.labels(launchUrl).inc();
			}
			return ActionState.UNKNOWN;
		} catch (final JsonProcessingException e) {
			return ActionState.FAILED;
		}
	}

	@Override
	public int priority() {
		return -10;
	}

	@Override
	public long retryMinutes() {
		return 15;
	}

	@Override
	public ObjectNode toJson(ObjectMapper mapper) {
		try {
			final ObjectNode node = mapper.readValue(mapper.writeValueAsString(request), ObjectNode.class);
			if (resultUrl != null) {
				node.put("url", resultUrl);
			}
			return node;
		} catch (final IOException e) {
			e.printStackTrace();
			return mapper.createObjectNode();
		}
	}

}
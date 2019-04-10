package org.kairosdb.plugin.remote;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.json.JSONException;
import org.json.JSONObject;
import org.kairosdb.core.exception.DatastoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.kairosdb.util.Preconditions.checkNotNullOrEmpty;

public class RemoteHostImpl implements RemoteHost
{
	private static final Logger logger = LoggerFactory.getLogger(RemoteHostImpl.class);
	private static final String REMOTE_URL_PROP = "kairosdb.remote.remote_url";
	private static final String CONNECTION_REQUEST_TIMEOUT = "kairosdb.remote.connection_request_timeout";
	private static final String CONNECTION_TIMEOUT = "kairosdb.remote.connection_timeout";
	private static final String SOCKET_TIMEOUT = "kairosdb.remote.socket_timeout";

	private final String url;
	private CloseableHttpAsyncClient client;
	private final long futureTimeout;

	@Inject
	public RemoteHostImpl(@Named(REMOTE_URL_PROP) String remoteUrl,
			@Named(CONNECTION_REQUEST_TIMEOUT) int requestTimeout,
			@Named(CONNECTION_TIMEOUT) int connectionTimeout,
			@Named(SOCKET_TIMEOUT) int socketTimeout)
	{
		this.url = checkNotNullOrEmpty(remoteUrl, "url must not be null or empty");
		client = HttpAsyncClients.createDefault();
		RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(socketTimeout)
				.setConnectionRequestTimeout(requestTimeout)
				.setConnectTimeout(connectionTimeout)
				.build();
		HttpClients.custom().setDefaultRequestConfig(requestConfig);
		futureTimeout = socketTimeout + requestTimeout + connectionTimeout;
	}

	@Override
	public void sendZipFile(File zipFile) throws IOException
	{
		logger.debug("Sending {}", zipFile);
		HttpPost post = new HttpPost(url + "/api/v1/datapoints");

		FileInputStream zipStream = new FileInputStream(zipFile);
		post.setHeader("Content-Type", "application/gzip");

		post.setEntity(new InputStreamEntity(zipStream, zipFile.length()));
		Future<HttpResponse> responseFuture = client.execute(post, null);

		try
		{
			HttpResponse response = responseFuture.get(futureTimeout, TimeUnit.MILLISECONDS);

			zipStream.close();
			if (response.getStatusLine().getStatusCode() == 204)
			{
				try
				{
					Files.delete(zipFile.toPath());
				}
				catch (IOException e)
				{
					logger.error("Could not delete zip file: " + zipFile.getName());
				}
			}
			else if (response.getStatusLine().getStatusCode() == 400)
			{
				//This means it was a bad file, more than likely the json is not well formed
				//renaming it will make sure we don't try it again as it will likely fail again
				//All of the data likely was loaded into kairos especially if it was missing the last ]
				ByteArrayOutputStream body = new ByteArrayOutputStream();
				response.getEntity().writeTo(body);
				logger.error("Unable to send file " + zipFile + ": " + response.getStatusLine() +
						" - " + body.toString("UTF-8"));

				zipFile.renameTo(new File(zipFile.getPath() + ".failed"));
			}
			else
			{
				ByteArrayOutputStream body = new ByteArrayOutputStream();
				response.getEntity().writeTo(body);
				logger.error("Unable to send file " + zipFile + ": " + response.getStatusLine() +
						" - " + body.toString("UTF-8"));
			}
		}
		catch (Exception e)
		{
			throw new IOException("Unable to connect to remote host", e);
		}
	}

	@Override
	public void getKairosVersion() throws DatastoreException
	{
		try
		{
			HttpGet get = new HttpGet(url + "/api/v1/version");
			//get.setConfig(RequestConfig.custom().

			HttpResponse response = client.execute(get, null).get(futureTimeout, TimeUnit.MILLISECONDS);
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			response.getEntity().writeTo(bout);

			JSONObject respJson = new JSONObject(bout.toString("UTF-8"));

			logger.info("Connecting to remote Kairos version: " + respJson.getString("version"));
		}
		catch (JSONException e)
		{
			throw new DatastoreException("Unable to parse response from remote kairos node.", e);
		}
		catch (Exception e)
		{
			throw new DatastoreException("Unable to connect to remote kairos node.", e);
		}

	}
}

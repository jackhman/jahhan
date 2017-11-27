/**
 * Copyright 1999-2014 dangdang.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.dubbo.rpc.protocol.rest;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;
import javax.servlet.ServletContext;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;

import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpResponse;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.ibatis.plugin.Signature;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient4Engine;
import org.jboss.resteasy.util.GetRestful;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.ConstantsX;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.remoting.http.HttpBinder;
import com.alibaba.dubbo.remoting.http.servlet.BootstrapListener;
import com.alibaba.dubbo.remoting.http.servlet.ServletManager;
import com.alibaba.dubbo.rpc.ProxyFactory;
import com.frameworkx.exception.FrameWorkXException;
import com.alibaba.dubbo.rpc.ServiceClassHolder;
import com.alibaba.dubbo.rpc.protocol.AbstractProxyProtocol;

/**
 * @author lishen
 */
@Singleton
public class RestProtocol extends AbstractProxyProtocol {

	private static final int DEFAULT_PORT = 80;

	private final Map<String, RestServer> servers = new ConcurrentHashMap<String, RestServer>();

	private final RestServerFactory serverFactory = new RestServerFactory();

	// TODO in the future maybe we can just use a single rest client and
	// connection manager
	private final List<ResteasyClient> clients = Collections.synchronizedList(new LinkedList<ResteasyClient>());

	private volatile ConnectionMonitor connectionMonitor;

	public RestProtocol() {
		super(WebApplicationException.class, ProcessingException.class);
		setProxyFactory(ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension());
	}

	public void setHttpBinder(HttpBinder httpBinder) {
		serverFactory.setHttpBinder(httpBinder);
	}

	public int getDefaultPort() {
		return DEFAULT_PORT;
	}

	protected <T> Runnable doExport(T impl, Class<T> type, URL url) throws FrameWorkXException {
		String addr = url.getIp() + ":" + url.getPort();
		Class implClass = ServiceClassHolder.getInstance().popServiceClass();
		RestServer server = servers.get(addr);
		if (server == null) {
			server = serverFactory.createServer(url.getParameter(Constants.SERVER_KEY, "jetty"));
			server.start(url);
			servers.put(addr, server);
		}

		String contextPath = getContextPath(url);
		if ("servlet".equalsIgnoreCase(url.getParameter(Constants.SERVER_KEY, "jetty"))) {
			ServletContext servletContext = ServletManager.getInstance()
					.getServletContext(ServletManager.EXTERNAL_SERVER_PORT);
			if (servletContext == null) {
				throw new FrameWorkXException("No servlet context found. Since you are using server='servlet', "
						+ "make sure that you've configured " + BootstrapListener.class.getName() + " in web.xml");
			}
			String webappPath = servletContext.getContextPath();
			if (StringUtils.isNotEmpty(webappPath)) {
				webappPath = webappPath.substring(1);
				if (!contextPath.startsWith(webappPath)) {
					throw new FrameWorkXException("Since you are using server='servlet', "
							+ "make sure that the 'contextpath' property starts with the path of external webapp");
				}
				contextPath = contextPath.substring(webappPath.length());
				if (contextPath.startsWith("/")) {
					contextPath = contextPath.substring(1);
				}
			}
		}

		final Class resourceDef = GetRestful.getRootResourceClass(implClass) != null ? implClass : type;

		server.deploy(resourceDef, impl, contextPath);

		final RestServer s = server;
		return new Runnable() {
			public void run() {
				// TODO due to dubbo's current architecture,
				// it will be called from registry protocol in the shutdown
				// process and won't appear in logs
				s.undeploy(resourceDef);
			}
		};
	}

	protected <T> T doRefer(Class<T> serviceType, URL url) throws FrameWorkXException {
		if (connectionMonitor == null) {
			connectionMonitor = new ConnectionMonitor();
		}

		// TODO more configs to add

		PoolingClientConnectionManager connectionManager = new PoolingClientConnectionManager();
		// 20 is the default maxTotal of current PoolingClientConnectionManager
		connectionManager.setMaxTotal(url.getParameter(Constants.CONNECTIONS_KEY, 20));
		connectionManager.setDefaultMaxPerRoute(url.getParameter(Constants.CONNECTIONS_KEY, 20));

		connectionMonitor.addConnectionManager(connectionManager);

		// BasicHttpContext localContext = new BasicHttpContext();

		DefaultHttpClient httpClient = new DefaultHttpClient(connectionManager);

		httpClient.setKeepAliveStrategy(new ConnectionKeepAliveStrategy() {
			public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
				HeaderElementIterator it = new BasicHeaderElementIterator(
						response.headerIterator(HTTP.CONN_KEEP_ALIVE));
				while (it.hasNext()) {
					HeaderElement he = it.nextElement();
					String param = he.getName();
					String value = he.getValue();
					if (value != null && param.equalsIgnoreCase("timeout")) {
						return Long.parseLong(value) * 1000;
					}
				}
				// TODO constant
				return 30 * 1000;
			}
		});

		HttpParams params = httpClient.getParams();
		// TODO currently no xml config for Constants.CONNECT_TIMEOUT_KEY so we
		// directly reuse Constants.TIMEOUT_KEY for now
		HttpConnectionParams.setConnectionTimeout(params,
				url.getParameter(Constants.TIMEOUT_KEY, Constants.DEFAULT_TIMEOUT));
		HttpConnectionParams.setSoTimeout(params, url.getParameter(Constants.TIMEOUT_KEY, Constants.DEFAULT_TIMEOUT));
		HttpConnectionParams.setTcpNoDelay(params, true);
		HttpConnectionParams.setSoKeepalive(params, true);

		ApacheHttpClient4Engine engine = new ApacheHttpClient4Engine(
				httpClient/* , localContext */);

		ResteasyClient client = new ResteasyClientBuilder().httpEngine(engine).build();
		clients.add(client);

		client.register(RpcContextFilter.class);
		for (String clazz : Constants.COMMA_SPLIT_PATTERN.split(url.getParameter(ConstantsX.EXTENSION_KEY, ""))) {
			if (!StringUtils.isEmpty(clazz)) {
				try {
					client.register(Thread.currentThread().getContextClassLoader().loadClass(clazz.trim()));
				} catch (ClassNotFoundException e) {
					throw new FrameWorkXException("Error loading JAX-RS extension class: " + clazz.trim(), e);
				}
			}
		}

		// TODO protocol
		ResteasyWebTarget target = client
				.target("http://" + url.getHost() + ":" + url.getPort() + "/" );
		return target.proxy(serviceType);
	}

	protected int getErrorCode(Throwable e) {
		// TODO
		return super.getErrorCode(e);
	}

	public void destroy() {
		super.destroy();

		if (connectionMonitor != null) {
			connectionMonitor.shutdown();
		}

		for (Map.Entry<String, RestServer> entry : servers.entrySet()) {
			try {
				if (logger.isInfoEnabled()) {
					logger.info("Closing the rest server at " + entry.getKey());
				}
				entry.getValue().stop();
			} catch (Throwable t) {
				logger.warn("Error closing rest server", t);
			}
		}
		servers.clear();

		if (logger.isInfoEnabled()) {
			logger.info("Closing rest clients");
		}
		for (ResteasyClient client : clients) {
			try {
				client.close();
			} catch (Throwable t) {
				logger.warn("Error closing rest client", t);
			}
		}
		clients.clear();
	}

	protected String getContextPath(URL url) {
		int pos = url.getPath().lastIndexOf("/");
		return pos > 0 ? url.getPath().substring(0, pos) : "";
	}

	protected class ConnectionMonitor extends Thread {
		private volatile boolean shutdown;
		private final List<ClientConnectionManager> connectionManagers = Collections
				.synchronizedList(new LinkedList<ClientConnectionManager>());

		public void addConnectionManager(ClientConnectionManager connectionManager) {
			connectionManagers.add(connectionManager);
		}

		public void run() {
			try {
				while (!shutdown) {
					synchronized (this) {
						wait(1000);
						for (ClientConnectionManager connectionManager : connectionManagers) {
							connectionManager.closeExpiredConnections();
							// TODO constant
							connectionManager.closeIdleConnections(30, TimeUnit.SECONDS);
						}
					}
				}
			} catch (InterruptedException ex) {
				shutdown();
			}
		}

		public void shutdown() {
			shutdown = true;
			connectionManagers.clear();
			synchronized (this) {
				notifyAll();
			}
		}
	}
}
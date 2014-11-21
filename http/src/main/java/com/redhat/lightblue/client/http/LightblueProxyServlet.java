package com.redhat.lightblue.client.http;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.lightblue.client.http.auth.HttpClientCertAuth;
import com.redhat.lightblue.client.http.auth.HttpClientNoAuth;
import com.redhat.lightblue.client.util.ClientConstants;

public class LightblueProxyServlet extends HttpServlet implements Servlet {

    private static final long serialVersionUID = 1L;

    private String serviceURI;
    private boolean useCertAuth = true;

    private static final Logger LOGGER = LoggerFactory.getLogger(LightblueProxyServlet.class);

    private String serviceURI() {    	
		try {
			Properties properties = new Properties();
			properties.load(getClass().getClassLoader().getResourceAsStream(ClientConstants.DEFAULT_CONFIG_FILE));
			serviceURI = properties.getProperty("serviceURI");
			if(serviceURI == null) {
				throw new RuntimeException("serviceURI must be defined in " + ClientConstants.DEFAULT_CONFIG_FILE);
			}
			useCertAuth = Boolean.parseBoolean(properties.getProperty("useCertAuth"));
			
		} catch (IOException io) {
			LOGGER.error(ClientConstants.DEFAULT_CONFIG_FILE + " could not be found/read", io);
			throw new RuntimeException(io);
		}

		return serviceURI;
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpGet httpGet = new HttpGet(serviceURI(req));
        serviceCall(httpGet, req, res);
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpPost httpPost = new HttpPost(serviceURI(req));
        httpPost.setEntity(new StringEntity(IOUtils.toString(req.getReader())));
        serviceCall(httpPost, req, res);
    }

    @Override
    public void doPut(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpPut httpPut = new HttpPut(serviceURI(req));
        httpPut.setEntity(new StringEntity(IOUtils.toString(req.getReader())));
        serviceCall(httpPut, req, res);
    }

    @Override
    public void doDelete(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpDelete httpDelete = new HttpDelete(serviceURI(req));
        serviceCall(httpDelete, req, res);
    }

    private void serviceCall(HttpRequestBase httpOperation, HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json");
        PrintWriter out = res.getWriter();
        try {
            try (CloseableHttpClient httpClient = getLightblueHttpClient()) {
                httpOperation.setHeader("Content-Type", "application/json");
                
                try (CloseableHttpResponse httpResponse = httpClient.execute(httpOperation)) {
                    HttpEntity entity = httpResponse.getEntity();
                    String serviceResponse = EntityUtils.toString(entity);
                    LOGGER.debug("Response received from service" + serviceResponse);
                    out.println(serviceResponse);
                }
            }
        } catch (RuntimeException e) {
            out.println("{\"error\":\"There was a problem calling the lightblue service\"}");
            LOGGER.error("There was a problem calling the lightblue service", e);
        }
    }

    private CloseableHttpClient getLightblueHttpClient() {
    	if(useCertAuth) {
    		LOGGER.debug("Using certificate authentication");
    		return new HttpClientCertAuth().getClient();
    	} else {
    		LOGGER.debug("Using no authentication");
    		return new HttpClientNoAuth().getClient();
    	}
    }
    
	private String serviceURI(HttpServletRequest request) throws IOException {
		return serviceURI() + request.getRequestURI().replace(request.getContextPath() + "/rest-request", "");
	}
}

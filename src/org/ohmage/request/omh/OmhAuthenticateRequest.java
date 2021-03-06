package org.ohmage.request.omh;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonGenerator.Feature;
import org.joda.time.format.ISODateTimeFormat;
import org.ohmage.cache.UserBin;
import org.ohmage.exception.InvalidRequestException;
import org.ohmage.request.InputKeys;
import org.ohmage.request.Request;
import org.ohmage.request.auth.AuthTokenRequest;

public class OmhAuthenticateRequest extends Request {
	private static final Logger LOGGER = 
		Logger.getLogger(OmhAuthenticateRequest.class);
	
	private static final JsonFactory JSON_FACTORY = 
		(new JsonFactory()).configure(Feature.AUTO_CLOSE_TARGET, true);
	
	private static final String JSON_KEY_AUTH_TOKEN = "auth_token";
	private static final String JSON_KEY_EXPIRES = "expires";
	
	private final AuthTokenRequest authTokenRequest;
	
	/**
	 * Creates a new OMH authentication request.
	 * 
	 * @param httpRequest The HTTP request.
	 * 
	 * @throws InvalidRequestException Thrown if the parameters cannot be 
	 * 								   parsed.
	 * 
	 * @throws IOException There was an error reading from the request.
	 */
	public OmhAuthenticateRequest(
			final HttpServletRequest httpRequest)
			throws IOException, InvalidRequestException {
		
		super(httpRequest, null);
		
		AuthTokenRequest tAuthTokenRequest = null;
		
		if(! isFailed()) {
			LOGGER.info("Creating an OMH authenticate request.");
			
			// Convert the 'requester' parameter to the 'client' parameter.
			Map<String, String[]> parameters = 
				new HashMap<String, String[]>(this.getParameters());
			parameters.put(
				InputKeys.CLIENT, 
				parameters.get(InputKeys.OMH_REQUESTER));
			tAuthTokenRequest = new AuthTokenRequest(httpRequest, parameters);
		}
		
		authTokenRequest = tAuthTokenRequest;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.ohmage.request.Request#service()
	 */
	@Override
	public void service() {
		LOGGER.info("Servicing the OMH authenticate request.");
		
		authTokenRequest.service();
	}

	/*
	 * (non-Javadoc)
	 * @see org.ohmage.request.Request#respond(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	@Override
	public void respond(
			final HttpServletRequest httpRequest,
			final HttpServletResponse httpResponse) {
		
		LOGGER.info("Responding to the authentication token request.");
		
		// Check for failure.
		if(isFailed()) {
			super.respond(httpRequest, httpResponse, null);
			return;
		}
		
		if(authTokenRequest.isFailed()) {
			authTokenRequest.respond(httpRequest, httpResponse);
			return;
		}
		
		// Expire the response, but this may be a bad idea.
		expireResponse(httpResponse);
		
		// Set the CORS headers.
		handleCORS(httpRequest, httpResponse);
		
		// Set the content type to JSON.
		httpResponse.setContentType("application/json");
		
		// Connect a stream to the response.
		OutputStream outputStream;
		try {
			outputStream = getOutputStream(httpRequest, httpResponse);
		}
		catch(IOException e) {
			LOGGER.warn("Could not connect to the output stream.", e);
			return;
		}

		// Create the generator that will stream to the requester.
		JsonGenerator generator;
		try {
			generator = JSON_FACTORY.createJsonGenerator(outputStream);
		}
		catch(IOException generatorException) {
			LOGGER.error(
				"Could not create the JSON generator.",
				generatorException);
			
			try {
				outputStream.close();
			}
			catch(IOException streamCloseException) {
				LOGGER.info(
					"Could not close the output stream.",
					streamCloseException);
			}
			
			return;
		}
		
		try {
			generator.writeStartObject();
			
			String token = authTokenRequest.getUser().getToken();
			
			generator.writeStringField(JSON_KEY_AUTH_TOKEN, token);
			
			generator.writeStringField(
				JSON_KEY_EXPIRES, 
				ISODateTimeFormat.dateTime().print(
					System.currentTimeMillis() +
					UserBin.getTokenRemainingLifetimeInMillis(token)));
			
			generator.writeEndObject();
		}
		catch(JsonGenerationException e) {
			LOGGER.error("Error writing the data.", e);
		}
		catch(IOException e) {
			LOGGER.info("The output stream most likely closed.", e);
		}
		finally {
			try {
				generator.close();
			}
			catch(IOException e) {
				LOGGER.info("Could not close the output stream.", e);
			}
		}
	}
}
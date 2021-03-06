package org.ohmage.request.observer;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonParser;
import org.ohmage.annotator.Annotator.ErrorCode;
import org.ohmage.domain.DataStream;
import org.ohmage.domain.Observer;
import org.ohmage.exception.InvalidRequestException;
import org.ohmage.exception.ServiceException;
import org.ohmage.exception.ValidationException;
import org.ohmage.request.InputKeys;
import org.ohmage.request.UserRequest;
import org.ohmage.service.ObserverServices;
import org.ohmage.validator.ObserverValidators;

/**
 * <p>Creates a new observer in the system.</p>
 * <table border="1">
 *   <tr>
 *     <td>Parameter Name</td>
 *     <td>Description</td>
 *     <td>Required</td>
 *   </tr>
 *   <tr>
 *     <td>{@value org.ohmage.request.InputKeys#CLIENT}</td>
 *     <td>A string describing the client that is making this request.</td>
 *     <td>true</td>
 *   </tr>
 *   <tr>
 *     <td>{@value org.ohmage.request.InputKeys#OBSERVER_ID}</td>
 *     <td>The unique identifier that the data points belong to.</td>
 *     <td>true</td>
 *   </tr>
 *   <tr>
 *     <td>{@value org.ohmage.request.InputKeys#OBSERVER_VERSION}</td>
 *     <td>The version of the observer to which the data applies.</td>
 *     <td>true</td>
 *   </tr>
 *   <tr>
 *     <td>{@value org.ohmage.request.InputKeys#DATA}</td>
 *     <td>The data to upload. This should be a JSON array of JSON objects. 
 *       each object may contain a "metadata" key and must contain "data",
 *       "stream_id", and "stream_version" keys. The "metadata" value must be a
 *       JSON object that may contain the allowed meta-data fields as defined 
 *       in the observer's definition. The "stream_id"'s value is the ID of the
 *       stream as defined by this observer. The "stream_version"'s value is 
 *       the numeric value of the stream as defined by this observer. The 
 *       "data" value must conform to the schema from the definition.</td>
 *     <td>true</td>
 *   </tr>
 * </table>
 * 
 * @author John Jenkins
 */
public class StreamUploadRequest extends UserRequest {
	private static final Logger LOGGER = 
		Logger.getLogger(StreamUploadRequest.class);

	private final String observerId;
	private final Long observerVersion;
	private final JsonParser data;
	
	/**
	 * Creates a stream upload request from the set of parameters.
	 * 
	 * @param httpRequest The HTTP request.
	 * 
	 * @param observerId The observer's ID.
	 * 
	 * @param observerVersion The observer's version.
	 * 
	 * @param data The data to be uploaded.
	 * 
	 * @throws InvalidRequestException Thrown if the parameters cannot be 
	 * 								   parsed.
	 * 
	 * @throws IOException There was an error reading from the request.
	 */
	public StreamUploadRequest(
			final HttpServletRequest httpRequest, 
			final Map<String, String[]> parameters,
			final String observerId,
			final long observerVersion,
			final String data)
			throws IOException, InvalidRequestException {
		
		super(httpRequest, false, TokenLocation.PARAMETER, parameters);
		
		String tObserverId = null;
		Long tObserverVersion = null;
		JsonParser tData = null;
		
		if(! isFailed()) {
			LOGGER.info("Creating a stream upload request.");
			
			if(observerId == null) {
				setFailed(
					ErrorCode.OBSERVER_INVALID_ID, 
					"The observer ID is missing.");
			}
			if(data == null) {
				setFailed(
					ErrorCode.OBSERVER_INVALID_STREAM_DATA,
					"The data is missing.");
			}
		
			try {
				tObserverId = ObserverValidators.validateObserverId(observerId);
				tObserverVersion = observerVersion;
				tData = ObserverValidators.validateData(data);
			}
			catch(ValidationException e) {
				e.failRequest(this);
				e.logException(LOGGER);
			}
		}
		
		this.observerId = tObserverId;
		this.observerVersion = tObserverVersion;
		this.data = tData;
	}
	
	/**
	 * Creates a stream upload request.
	 * 
	 * @param httpRequest The HTTP request.
	 * 
	 * @throws InvalidRequestException Thrown if the parameters cannot be 
	 * 								   parsed.
	 * 
	 * @throws IOException There was an error reading from the request.
	 */
	public StreamUploadRequest(
			final HttpServletRequest httpRequest) 
			throws IOException, InvalidRequestException {
		
		super(httpRequest, false, TokenLocation.PARAMETER, null);
		
		String tObserverId = null;
		Long tObserverVersion = null;
		JsonParser tData = null;
		
		if(! isFailed()) {
			LOGGER.info("Creating a stream upload request.");
			String[] t;
			
			try {
				t = getParameterValues(InputKeys.OBSERVER_ID);
				if(t.length > 1) {
					throw new ValidationException(
						ErrorCode.OBSERVER_INVALID_ID,
						"Multiple observer IDs were given: " +
							InputKeys.OBSERVER_ID);
				}
				else if(t.length == 1) {
					tObserverId = 
						ObserverValidators.validateObserverId(t[0]);
				}
				if(tObserverId == null) {
					throw new ValidationException(
						ErrorCode.OBSERVER_INVALID_ID,
						"The observer's ID is missing.");
				}
				
				t = getParameterValues(InputKeys.OBSERVER_VERSION);
				if(t.length > 1) {
					throw new ValidationException(
						ErrorCode.OBSERVER_INVALID_VERSION,
						"Multiple observer versions were given: " +
							InputKeys.OBSERVER_VERSION);
				}
				else if(t.length == 1) {
					tObserverVersion = 
						ObserverValidators.validateObserverVersion(t[0]);
				}
				if(tObserverVersion == null) {
					throw new ValidationException(
						ErrorCode.OBSERVER_INVALID_VERSION,
						"The observer's version is missing.");
				}
				
				t = getParameterValues(InputKeys.DATA);
				if(t.length > 1) {
					throw new ValidationException(
						ErrorCode.OBSERVER_INVALID_STREAM_DATA,
						"Multiple data streams were uploaded: " + 
							InputKeys.DATA);
				}
				else if(t.length == 1) {
					tData = ObserverValidators.validateData(t[0]);
				}
				if(tData == null) {
					throw new ValidationException(
						ErrorCode.OBSERVER_INVALID_STREAM_DATA,
						"The data was missing: " + InputKeys.DATA);
				}
			}
			catch(ValidationException e) {
				e.failRequest(this);
				e.logException(LOGGER);
			}
		}
		
		observerId = tObserverId;
		observerVersion = tObserverVersion;
		data = tData;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.ohmage.request.Request#service()
	 */
	@Override
	public void service() {
		LOGGER.info("Servicing a stream upload request.");
		
		if(! authenticate(AllowNewAccount.NEW_ACCOUNT_DISALLOWED)) {
			return;
		}
		
		try {
			LOGGER.info("Getting the observer definition.");
			Observer observer = 
				ObserverServices.instance().getObserver(
					observerId, 
					observerVersion);
			
			LOGGER.info("Validating the uploaded data.");
			Collection<DataStream> dataStreams =
				ObserverServices.instance().validateData(observer, data);
			
			try {
				data.close();
			}
			catch(IOException e) {
				LOGGER.info("Error closing the data.", e);
			}
			
			LOGGER.info("Pruning out the duplicates from previous uploads.");
			ObserverServices.instance().removeDuplicates(
				getUser().getUsername(), 
				observerId,
				dataStreams);
			
			LOGGER.info("Storing the uploaded data.");
			ObserverServices.instance().storeData(
				getUser().getUsername(), 
				observer,
				dataStreams);
		}
		catch(ServiceException e) {
			e.failRequest(this);
			e.logException(LOGGER);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.ohmage.request.Request#respond(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	@Override
	public void respond(
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		
		super.respond(httpRequest, httpResponse, null);
	}
}
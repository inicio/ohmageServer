/*******************************************************************************
 * Copyright 2012 The Regents of the University of California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.ohmage.reporting;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.json.JSONException;
import org.json.JSONObject;
import org.ohmage.cache.PreferenceCache;
import org.ohmage.domain.Audit;
import org.ohmage.exception.CacheMissException;
import org.ohmage.exception.ServiceException;
import org.ohmage.request.InputKeys;
import org.ohmage.request.RequestBuilder;
import org.ohmage.service.AuditServices;
import org.ohmage.util.TimeUtils;

/**
 * Begins on server startup and creates a daily snapshot of some information
 * from the audit table.
 * 
 * @author John Jenkins
 */
public final class AuditReporter {
	private static final Logger LOGGER = Logger.getLogger(AuditReporter.class);
	
	// Used by the timer.
	private static final int MILLIS_IN_A_DAY = 1000 * 60 * 60 * 24;
	
	// The number of idle threads ready to generate the report. Two reports 
	// should never be generated at the same time, so we only need one thread
	// to handle this.
	private static final int THREAD_POOL_SIZE = 1;
	
	// Where we will save the audit reports.
	private static String saveLocation;
	
	/**
	 * The class that runs as its own thread to generate the report.
	 * 
	 * @author John Jenkins
	 */
	private final class GenerateReport implements Runnable {
		/**
		 * Generates a report for the previous day.
		 */
		@Override
		public void run() {
			DateTime currentDate = new DateTime();
			
			DateTime endDate = 
					new DateTime(
						currentDate.getYear(), 
						currentDate.getMonthOfYear(), 
						currentDate.getDayOfMonth(), 
						0, 
						0);
			
			DateTime startDate = endDate.minusDays(1);
			
			// Use the service to aggregate the results.
			List<Audit> audits;
			try {
				audits = AuditServices.instance().getAuditInformation(
						null, 
						null, 
						null, 
						null, 
						null, 
						null, 
						startDate, 
						endDate);
			}
			catch(ServiceException e) {
				LOGGER.error("There was an error generating the audit inforamtion.", e);
				return;
			}
			
			long numberOfValidRequests = 0;
			long numberOfInvalidRequests = 0;
			long numberOfSuccessfulValidRequests = 0;
			long numberOfFailedValidRequests = 0;
			long timeToProcessValidRequests = 0;

			Map<String, Integer> numberUriRequests = new HashMap<String, Integer>();
			Map<String, Integer> numberCampaignReads = new HashMap<String, Integer>();
			Map<String, Integer> numberClassReads = new HashMap<String, Integer>();
			
			// Cycle through all of the audit entries.
			for(Audit audit : audits) {
				// First, get the URI and determine if the request is even 
				// valid.
				String uri = audit.getUri();
				
				// Either way, make a note of it in the list of URIs.
				Integer uriCount = numberUriRequests.get(uri);
				if(uriCount == null) {
					numberUriRequests.put(uri, 1);
				}
				else {
					numberUriRequests.put(uri, uriCount + 1);
				}
				
				// If the request is known, note it and continue processing.
				if(RequestBuilder.getInstance().knownUri(uri)) {
					numberOfValidRequests++;
					
					// Calculate the time it took to process the request.
					timeToProcessValidRequests += audit.getRespondedMillis() - audit.getReceivedMillis();
					
					// Get the audit's response. If there is an issue parsing
					// the response, note it and continue to the next audit.
					JSONObject response = audit.getResponse();
					String result;
					try {
						result = response.getString("result");
					}
					catch(JSONException e) {
						LOGGER.error("Error reading an audit's response.");
						continue;
					}
					
					// If the request was successful, continue evaluating the
					// audit to see if any other substantial data was returned.
					if("success".equals(result)) {
						numberOfSuccessfulValidRequests++;
						
						// Check if it's a class read request.
						if(RequestBuilder.getInstance().getApiClassRead().equals(uri) ||
								RequestBuilder.getInstance().getApiClassRosterRead().equals(uri)) {
							// Get the class ID parameter if it exists.
							Collection<String> classIdCollection = audit.getExtras(InputKeys.CLASS_URN);
							if(classIdCollection != null) {
								for(String classId : classIdCollection) {
									Integer count = numberClassReads.get(classId);
									if(count == null) {
										numberClassReads.put(classId, 1);
									}
									else {
										numberClassReads.put(classId, count + 1);
									}
								}
							}
						}
						// Check if it's a campaign read request.
						else if(RequestBuilder.getInstance().getApiCampaignRead().equals(uri)) {
							Collection<String> campaignIdCollection = audit.getExtras(InputKeys.CAMPAIGN_URN);
							if(campaignIdCollection != null) {
								for(String campaignId : campaignIdCollection) {
									Integer count = numberCampaignReads.get(campaignId);
									if(count == null) {
										numberCampaignReads.put(campaignId, 1);
									}
									else {
										numberCampaignReads.put(campaignId, count + 1);
									}
								}
							}
						}
					}
					// If the request was unsuccessful, note it and move on to
					// the next request.
					else {
						numberOfFailedValidRequests++;
					}
				}
				// If the request is unknown, note it and move on to the next
				// request.
				else {
					numberOfInvalidRequests++;
				}
			}
			
			try {
				// Retrieve the output file to write the results.
				FileWriter fileWriter = new FileWriter(saveLocation + "/" + TimeUtils.getIso8601DateString(startDate, false));
				BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
				
				bufferedWriter.write("invalid_requests=");
				bufferedWriter.write(Long.toString(numberOfInvalidRequests));
				bufferedWriter.write('\n');
				
				bufferedWriter.write("valid_requests=");
				bufferedWriter.write(Long.toString(numberOfValidRequests));
				bufferedWriter.write('\n');
				
				bufferedWriter.write("successful_valid_requests=");
				bufferedWriter.write(Long.toString(numberOfSuccessfulValidRequests));
				bufferedWriter.write('\n');
				
				bufferedWriter.write("failed_valid_requests=");
				bufferedWriter.write(Long.toString(numberOfFailedValidRequests));
				bufferedWriter.write('\n');
				
				bufferedWriter.write("average_request_time=");
				if(numberOfValidRequests == 0) {
					bufferedWriter.write('0');
				}
				else {
					bufferedWriter.write(Long.toString(timeToProcessValidRequests / numberOfValidRequests));
				}
				bufferedWriter.write('\n');
				
				bufferedWriter.write("number of requests per uri\n");
				for(String uri : numberUriRequests.keySet()) {
					bufferedWriter.write(uri);
					bufferedWriter.write('=');
					bufferedWriter.write(numberUriRequests.get(uri).toString());
					bufferedWriter.write('\n');
				}
				
				bufferedWriter.write("number of reads per campaign ID\n");
				for(String campaignId : numberCampaignReads.keySet()) {
					bufferedWriter.write(campaignId);
					bufferedWriter.write('=');
					bufferedWriter.write(numberCampaignReads.get(campaignId).toString());
					bufferedWriter.write('\n');
				}
				
				bufferedWriter.write("number of reads per class ID\n");
				for(String classId : numberClassReads.keySet()) {
					bufferedWriter.write(classId);
					bufferedWriter.write('=');
					bufferedWriter.write(numberClassReads.get(classId).toString());
					bufferedWriter.write('\n');
				}
				
				bufferedWriter.flush();
				bufferedWriter.close();
				fileWriter.close();
			}
			catch(IOException e) {
				LOGGER.error("Error while writing the audit information.", e);
			}
		}
	}
	
	/**
	 * Starts a timer task to generate a report at the beginning of every day.
	 */
	private AuditReporter() {
		try {
			// Get the location to save the audit logs.
			saveLocation = 
				PreferenceCache.instance().lookup(
					PreferenceCache.KEY_AUDIT_LOG_LOCATION);
		}
		catch(CacheMissException e) {
			throw new IllegalStateException(
				"The audit log location is missing: " + 
					PreferenceCache.KEY_AUDIT_LOG_LOCATION,
				e);
		}
		
		try {
			// If it doesn't exist, create it. If it does exist, make sure it's a
			// directory.
			File saveFolder = new File(saveLocation);
			if(! saveFolder.exists()) {
				saveFolder.mkdir();
			}
			else if(! saveFolder.isDirectory()) {
				throw new IllegalStateException(
					"The directory that is to be used for saving the audit reports exists but isn't a directory: " + 
						saveLocation);
			}
		}
		catch(SecurityException e) {
			throw new IllegalStateException(
				"We are not allowed to read or write in the specified directory.", 
				e);
		}
		
		// Generate the number of milliseconds until the first run.
		Calendar firstRun = Calendar.getInstance();
		// Fast-forward to the beginning of the next day.
		firstRun.add(Calendar.DAY_OF_YEAR, 1);
		// Reset the hours, minutes, seconds, and milliseconds.
		firstRun.set(Calendar.HOUR_OF_DAY, 0);
		firstRun.set(Calendar.MINUTE, 0);
		firstRun.set(Calendar.SECOND, 0);
		firstRun.set(Calendar.MILLISECOND, 0);
		
		// Calculate the time between now and when the task should first run.
		long initialDelay = 
			firstRun.getTimeInMillis() - 
			Calendar.getInstance().getTimeInMillis();
		
		// Begin the task.
		Executors.newScheduledThreadPool(THREAD_POOL_SIZE).scheduleAtFixedRate(
			new GenerateReport(), 
			initialDelay, 
			MILLIS_IN_A_DAY, 
			TimeUnit.MILLISECONDS);
	}
}

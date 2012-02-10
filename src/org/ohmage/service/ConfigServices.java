package org.ohmage.service;

import java.util.List;

import org.ohmage.cache.PreferenceCache;
import org.ohmage.domain.ServerConfig;
import org.ohmage.domain.campaign.SurveyResponse;
import org.ohmage.exception.CacheMissException;
import org.ohmage.exception.DomainException;
import org.ohmage.exception.ServiceException;
import org.ohmage.util.StringUtils;

/**
 * This class contains the services that pertain to the server's configuration.
 * 
 * @author John Jenkins
 */
public class ConfigServices {
	/**
	 * Default constructor. Made private so that it cannot be instantiated.
	 */
	private ConfigServices() {}

	/**
	 * Creates a server configuration object to hold the server's 
	 * configuration.
	 * 
	 * @return A ServerConfig object representing the server's configuration.
	 * 
	 * @throws ServiceException Thrown if there is an error reading from the
	 * 							cache or decoding a value from it.
	 */
	public static ServerConfig readServerConfiguration() 
			throws ServiceException {
		
		PreferenceCache instance = PreferenceCache.instance();

		String appName;
		try {
			appName = instance.lookup(PreferenceCache.KEY_APPLICATION_NAME); 
		}
		catch(CacheMissException e) {
			throw new ServiceException("The application name was unknown.", e);
		}
		
		String appVersion;
		try {
			appVersion = instance.lookup(PreferenceCache.KEY_APPLICATION_VERSION); 
		}
		catch(CacheMissException e) {
			throw new ServiceException("The application version was unknown.", e);
		}
		
		String appBuild;
		try {
			appBuild = instance.lookup(PreferenceCache.KEY_APPLICATION_BUILD); 
		}
		catch(CacheMissException e) {
			throw new ServiceException("The application build was unknown.", e);
		}
		
		SurveyResponse.PrivacyState defaultSurveyResponsePrivacyState;
		try {
			defaultSurveyResponsePrivacyState = 
				SurveyResponse.PrivacyState.getValue(
						instance.lookup(PreferenceCache.KEY_DEFAULT_SURVEY_RESPONSE_SHARING_STATE)
					); 
		}
		catch(CacheMissException e) {
			throw new ServiceException("The application name was unknown.", e);
		}
		catch(IllegalArgumentException e) {
			throw new ServiceException("The survey response privacy state is not a valid survey response privacy state.");
		}
		
		List<SurveyResponse.PrivacyState> surveyResponsePrivacyStates =
				SurveyResponseServices.instance().getSurveyResponsePrivacyStates();
		
		boolean defaultCampaignCreationPrivilege;
		try {
			defaultCampaignCreationPrivilege = 
				Boolean.valueOf(
						instance.lookup(PreferenceCache.KEY_DEFAULT_CAN_CREATE_PRIVILIEGE)
					);
		}
		catch(CacheMissException e) {
			throw new ServiceException("The default campaign creation privilege was unknown.", e);
		}
		catch(IllegalArgumentException e) {
			throw new ServiceException("The default campaign creation privilege was not a valid boolean.", e);
		}
		
		boolean mobilityEnabled;
		try {
			mobilityEnabled = 
					StringUtils.decodeBoolean(
							PreferenceCache.instance().lookup(
									PreferenceCache.KEY_MOBILITY_ENABLED));
		}
		catch(CacheMissException e) {
			throw new ServiceException("Whether or not Mobility is enabled is missing from the database.", e);
		}
		
		try {
			return new ServerConfig(appName, appVersion, appBuild,
					defaultSurveyResponsePrivacyState, surveyResponsePrivacyStates,
					defaultCampaignCreationPrivilege, mobilityEnabled
				);
		} 
		catch(DomainException e) {
			throw new ServiceException(e);
		}
	}
}
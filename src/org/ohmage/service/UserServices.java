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
package org.ohmage.service;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import jbcrypt.BCrypt;
import net.tanesha.recaptcha.ReCaptchaImpl;
import net.tanesha.recaptcha.ReCaptchaResponse;

import org.ohmage.annotator.Annotator.ErrorCode;
import org.ohmage.cache.PreferenceCache;
import org.ohmage.domain.Clazz;
import org.ohmage.domain.User;
import org.ohmage.domain.UserInformation;
import org.ohmage.domain.UserInformation.UserPersonal;
import org.ohmage.domain.UserSummary;
import org.ohmage.domain.campaign.Campaign;
import org.ohmage.exception.CacheMissException;
import org.ohmage.exception.DataAccessException;
import org.ohmage.exception.DomainException;
import org.ohmage.exception.ServiceException;
import org.ohmage.query.IImageQueries;
import org.ohmage.query.IUserCampaignQueries;
import org.ohmage.query.IUserClassQueries;
import org.ohmage.query.IUserImageQueries;
import org.ohmage.query.IUserQueries;
import org.ohmage.query.impl.QueryResultsList;
import org.ohmage.request.InputKeys;
import org.ohmage.util.StringUtils;

import com.sun.mail.smtp.SMTPTransport;

/**
 * This class contains the services for users.
 * 
 * @author John Jenkins
 */
public final class UserServices {
	private static final String MAIL_PROTOCOL = "smtp";
	private static final String MAIL_PROPERTY_HOST = 
			"mail." + MAIL_PROTOCOL + ".host";
	private static final String MAIL_PROPERTY_PORT =
			"mail." + MAIL_PROTOCOL + ".port";
	private static final String MAIL_PROPERTY_SSL_ENABLED =
			"mail." + MAIL_PROTOCOL + ".ssl.enable";
	
	private static final String MAIL_REGISTRATION_TEXT_TOS = "<_TOS_>";
	private static final String MAIL_REGISTRATION_TEXT_REGISTRATION_LINK =
			"<_REGISTRATION_LINK_>";
	
	private static final String ACTIVATION_FUNCTION = "#activate";
	
	private static final long REGISTRATION_DURATION = 1000 * 60 * 60 * 24;
	
	final static char[] CHARS_TEMPORARY_PASSWORD = 
			new char[] { 
				'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l',
				'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x',
				'y', 'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 
				'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 
				'W', 'X', 'Y', 'Z', '0', '1', '2', '3', '4', '5', '6', '7', 
				'8', '9' };
	
	private static UserServices instance;
	
	private IUserQueries userQueries;
	private IUserCampaignQueries userCampaignQueries;
	private IUserClassQueries userClassQueries;
	private IUserImageQueries userImageQueries;
	private IImageQueries imageQueries;
	
	/**
	 * Default constructor. Privately instantiated via dependency injection
	 * (reflection).
	 * 
	 * @throws IllegalStateException if an instance of this class already
	 * exists
	 * 
	 * @throws IllegalArgumentException if iUserQueries or iUserClassQueries
	 * or iUserCampaignQueries is null
	 */
	private UserServices(IUserQueries iUserQueries, 
			IUserCampaignQueries iUserCampaignQueries, IUserClassQueries iUserClassQueries,
			IUserImageQueries iUserImageQueries, IImageQueries iImageQueries) {
		
		if(instance != null) {
			throw new IllegalStateException("An instance of this class already exists.");
		}
		instance = this;
		
		if(iUserQueries == null) {
			throw new IllegalArgumentException("An instance of IUserQueries is required.");
		}
		if(iUserCampaignQueries == null) {
			throw new IllegalArgumentException("An instance of IUserCampaignQueries is required.");
		}
		if(iUserClassQueries == null) {
			throw new IllegalArgumentException("An instance of IUserClassQueries is required.");
		}
		if(iUserImageQueries == null) {
			throw new IllegalArgumentException("An instance of IUserImageQueries is required.");
		}
		if(iImageQueries == null) {
			throw new IllegalArgumentException("An instance of IIimageQueries is required.");
		}
		
		userQueries = iUserQueries;
		userCampaignQueries = iUserCampaignQueries;
		userClassQueries = iUserClassQueries;
		userImageQueries = iUserImageQueries;
		imageQueries = iImageQueries;
	}
	
	/**
	 * @return  Returns the singleton instance of this class.
	 */
	public static UserServices instance() {
		return instance;
	}

	/**
	 * Creates a new user.
	 * 
	 * @param username The username for the new user.
	 * 
	 * @param password The password for the new user.
	 * 
	 * @param emailAddress The user's email address or null.
	 * 
	 * @param admin Whether or not the user should initially be an admin.
	 * 
	 * @param enabled Whether or not the user should initially be enabled.
	 * 
	 * @param newAccount Whether or not the new user must change their password
	 * 					 before using any other APIs.
	 * 
	 * @param campaignCreationPrivilege Whether or not the new user is allowed
	 * 									to create campaigns.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public void createUser(
			final String username, 
			final String password, 
			final String emailAddress,
			final Boolean admin, 
			final Boolean enabled, 
			final Boolean newAccount, 
			final Boolean campaignCreationPrivilege)
			throws ServiceException {
		
		try {
			String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(User.BCRYPT_COMPLEXITY));
			
			userQueries.createUser(username, hashedPassword, emailAddress, admin, enabled, newAccount, campaignCreationPrivilege);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Registers the user in the system by first creating the user whose 
	 * account is disabled. It then creates an entry in the registration cache
	 * with the key for the user to activate their account. Finally, it sends 
	 * an email to the user with a link that includes the activation key.
	 * 
	 * @param username The username of the new user.
	 * 
	 * @param password The plain-text password for the new user.
	 * 
	 * @param emailAddress The email address for the user.
	 * 
	 * @throws ServiceException There was a configuration issue with the mail
	 * 							server or if there was an issue in the Query
	 * 							layer.
	 */
	public void createUserRegistration(
			final String username,
			final String password,
			final String emailAddress) 
			throws ServiceException {
		
		try {
			// Generate a random registration ID from the username and some 
			// random bits.
			MessageDigest digest = MessageDigest.getInstance("SHA-512");
			digest.update(username.getBytes());
			digest.update(UUID.randomUUID().toString().getBytes());
			byte[] digestBytes = digest.digest();
			
			StringBuffer buffer = new StringBuffer();
	        for(int i = 0; i < digestBytes.length; i++) {
	        	buffer.append(
	        			Integer.toString(
	        					(digestBytes[i] & 0xff) + 0x100, 16)
	        						.substring(1));
	        }
			String registrationId = buffer.toString();
			
			// Hash the password.
			String hashedPassword = 
					BCrypt.hashpw(
							password, 
							BCrypt.gensalt(User.BCRYPT_COMPLEXITY));
			
			// Create the user in the database with all of its connections.
			userQueries.createUserRegistration(
					username, 
					hashedPassword, 
					emailAddress, 
					registrationId.toString());

			// Build the registration text.
			String registrationText;
			try {
				registrationText = PreferenceCache.instance().lookup(
					PreferenceCache.KEY_MAIL_REGISTRATION_TEXT);
			}
			catch(CacheMissException e) {
				throw new ServiceException(
					"The mail property is not in the preference table: " +
						PreferenceCache.KEY_MAIL_REGISTRATION_TEXT,
					e);
			}

			// Compute the registration link.
			StringBuilder registrationLink = 
					new StringBuilder("<a href=\"http://");
			// Get this machine's hostname.
			try {
				registrationLink.append(
						InetAddress.getLocalHost().getHostName());
			}
			catch(UnknownHostException e) {
				throw new ServiceException(
						"The sky is falling! Oh, and our own hostname is unknown.",
						e);
			}
			registrationLink.append(ACTIVATION_FUNCTION);
			registrationLink.append('?');
			registrationLink.append(InputKeys.USER_REGISTRATION_ID);
			registrationLink.append('=');
			registrationLink.append(registrationId);
			registrationLink.append("\">I Agree</a>");
			registrationText =
					registrationText.replace(
							MAIL_REGISTRATION_TEXT_REGISTRATION_LINK, 
							registrationLink);
			
			// Split based on the TOS flag.
			String[] registrationParts = null;
			if(registrationText.contains(MAIL_REGISTRATION_TEXT_TOS)) {
				registrationParts = 
					registrationText.split(MAIL_REGISTRATION_TEXT_TOS);
			}
			
			// Get the terms of service.
			String termsOfService;
			try {
				termsOfService =
					PreferenceCache.instance().lookup(
						PreferenceCache.KEY_TERMS_OF_SERVICE);
			}
			catch(CacheMissException e) {
				throw new ServiceException(
					"The mail property is not in the preference table: " +
						PreferenceCache.KEY_TERMS_OF_SERVICE,
					e);
			}
			
			// Get the session.
			Session smtpSession = getMailSession();
			// Create the message.
			MimeMessage message = new MimeMessage(smtpSession);
			
			// Add the recipient.
			try {
				message.setRecipient(
						Message.RecipientType.TO, 
						new InternetAddress(emailAddress));
			}
			catch(AddressException e) {
				throw new ServiceException(
						"The destination address is not a valid email address.",
						e);
			}
			catch(MessagingException e) {
				throw new ServiceException(
						"Could not add the recipient to the message.",
						e);
			}
			
			// Add the sender.
			try {
				message.setFrom(
						new InternetAddress(
								PreferenceCache.instance().lookup(
									PreferenceCache.KEY_MAIL_REGISTRATION_SENDER)));
			}
			catch(CacheMissException e) {
				throw new ServiceException(
					"The mail property is not in the preference table: " +
						PreferenceCache.KEY_MAIL_REGISTRATION_SENDER,
					e);
			}
			catch(AddressException e) {
				throw new ServiceException(
						"The origin address is not a valid email address.",
						e);
			}
			catch(MessagingException e) {
				throw new ServiceException(
						"Could not update the sender's email address.",
						e);
			}
			
			// Set the subject.
			try {
				message.setSubject(
						PreferenceCache.instance().lookup(
							PreferenceCache.KEY_MAIL_REGISTRATION_SUBJECT));
			}
			catch(CacheMissException e) {
				throw new ServiceException(
					"The mail property is not in the preference table: " +
						PreferenceCache.KEY_MAIL_REGISTRATION_SUBJECT,
					e);
			}
			catch(MessagingException e) {
				throw new ServiceException(
						"Could not set the subject on the message.",
						e);
			}
			try {
				// There is no TOS.
				if(registrationParts == null) {
					try {
						message.setContent(registrationText, "text/html");
					}
					catch(MessagingException e) {
						throw new ServiceException(
								"Could not add the content to the message.",
								e);
					}
				}
				else {
					MimeMultipart multipart = new MimeMultipart();
					
					boolean firstPass = true;
					for(int i = 0; i < registrationParts.length; i++) {
						if(firstPass) {
							firstPass = false;
						}
						else {
							BodyPart plainPart = new MimeBodyPart();
							plainPart.setContent(termsOfService, "text/plain");
							multipart.addBodyPart(plainPart);
						}
						
						BodyPart htmlPart = new MimeBodyPart();
						htmlPart.setContent(registrationParts[i], "text/html");
						multipart.addBodyPart(htmlPart);
					}
					
					message.setContent(multipart);
				}
			}
			catch(MessagingException e) {
				throw new ServiceException(
						"There was an error constructing the message.",
						e);
			}
			
			try {
				message.saveChanges();
			}
			catch(MessagingException e) {
				throw new ServiceException(
						"Could not save the changes to the message.",
						e);
			}
			
			sendMailMessage(smtpSession, message);
		}
		catch(NoSuchAlgorithmException e) {
			throw new ServiceException("The hashing algorithm is unknown.", e);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		} 
	}
	
	/**
	 * Verifies that self-registration is allowed.
	 * 
	 * @throws ServiceException Self-registration is not allowed, or the 
	 * 							self-registration flag is missing or invalid.
	 */
	public void verifySelfRegistrationAllowed() throws ServiceException {
		try {
			Boolean selfRegistrationAllowed =
				StringUtils.decodeBoolean(
					PreferenceCache.instance().lookup(
						PreferenceCache.KEY_ALLOW_SELF_REGISTRATION));
				
			if(selfRegistrationAllowed == null) {
				throw new ServiceException(
					"The self-registration flag is not a valid boolean: " +
						PreferenceCache.KEY_ALLOW_SELF_REGISTRATION);	
			}
			else if(! selfRegistrationAllowed) {
				throw new ServiceException(
					ErrorCode.SERVER_SELF_REGISTRATION_NOT_ALLOWED,
					"Self-registration is not allowed.");
			}
		}
		catch(CacheMissException e) {
			throw new ServiceException(
				"Could not retrieve the 'known' key: " +
					PreferenceCache.KEY_ALLOW_SELF_REGISTRATION,
				e);
		}
	}
	
	/**
	 * Verifies that the given captcha information is valid.
	 * 
	 * @param remoteAddr The address of the remote host.
	 * 
	 * @param challenge The challenge value.
	 * 
	 * @param response The response value.
	 * 
	 * @throws ServiceException Thrown if the private key is missing or if the
	 * 							response is invalid.
	 */
	public void verifyCaptcha(
			final String remoteAddr,
			final String challenge,
			final String response)
			throws ServiceException {
		
		ReCaptchaImpl reCaptcha = new ReCaptchaImpl();
		try {
			reCaptcha.setPrivateKey(
					PreferenceCache.instance().lookup(
							PreferenceCache.KEY_RECAPTCHA_KEY_PRIVATE));
		}
		catch(CacheMissException e) {
			throw new ServiceException(
					"The ReCaptcha key is missing from the preferences: " +
						PreferenceCache.KEY_RECAPTCHA_KEY_PRIVATE,
					e);
		}
		
		ReCaptchaResponse reCaptchaResponse = 
				reCaptcha.checkAnswer(remoteAddr, challenge, response);
		
		if(! reCaptchaResponse.isValid()) {
			throw new ServiceException(
					ErrorCode.SERVER_INVALID_CAPTCHA,
					"The reCaptcha response was invalid.");
		}
	}
	
	/**
	 * Checks that a user's existence matches that of 'shouldExist'.
	 * 
	 * @param username The username of the user in question.
	 * 
	 * @param shouldExist Whether or not the user should exist.
	 * 
	 * @throws ServiceException Thrown if there was an error, if the user 
	 * 							exists but shouldn't, or if the user doesn't
	 * 							exist but should.
	 */
	public void checkUserExistance(final String username, 
			final boolean shouldExist) throws ServiceException {
		
		try {
			if(userQueries.userExists(username)) {
				if(! shouldExist) {
					throw new ServiceException(
							ErrorCode.USER_INVALID_USERNAME, 
							"The following user already exists: " + username);
				}
			}
			else {
				if(shouldExist) {
					throw new ServiceException(
							ErrorCode.USER_INVALID_USERNAME, 
							"The following user does not exist: " + username);
				}
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Checks that a Collection of users' existence matches that of 
	 * 'shouldExist'.
	 * 
	 * @param usernames A Collection of usernames to check that each exists.
	 * 
	 * @param shouldExist Whether or not all of the users should exist or not.
	 * 
	 * @throws ServiceException Thrown if there was an error, if one of the 
	 * 							users should have existed and didn't, or if one 
	 * 							of the users shouldn't exist but does.
	 */
	public void verifyUsersExist(final Collection<String> usernames, 
			final boolean shouldExist) throws ServiceException {
		
		for(String username : usernames) {
			checkUserExistance(username, shouldExist);
		}
	}
	
	/**
	 * Assumes the user exists. Checks that a parameterized email address 
	 * matches the user's actual email address. To check if a user doesn't have
	 * an email address, pass NULL in for the 'emailAddress'. Otherwise, a 
	 * string comparison is done between the two email addresses to ensure that
	 * they are identical.
	 * 
	 * @param username The user's username.
	 * 
	 * @param emailAddress The user's expected email address.
	 * 
	 * @throws ServiceException The email addresses didn't match or there was
	 * 							an error.
	 */
	public void isUserEmailCorrect(
			final String username, 
			final String emailAddress)
			throws ServiceException {
		
		try {
			String actualEmailAddress = userQueries.getEmailAddress(username);
			
			if((actualEmailAddress == null) && (emailAddress == null)) {
				// The email addresses match in that the user doesn't have one
				// and none was passed in.
			}
			else if((emailAddress == null) || (actualEmailAddress == null) ||
				(! actualEmailAddress.toLowerCase().equals(emailAddress.toLowerCase()))) {
				throw new ServiceException(
						ErrorCode.USER_INVALID_EMAIL_ADDRESS,
						"The email addresses don't match.");
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Checks if the user is an admin.
	 * 
	 * TODO Any use case that involves an API call which isn't exclusively
	 * for admins can be migrated to use the isUserAnAdmin() method.
	 * 
	 * @throws ServiceException Thrown if there was an error or if the user is
	 * 							not an admin.
	 */
	public void verifyUserIsAdmin(final String username) 
			throws ServiceException {
		
		try {
			if(! userQueries.userIsAdmin(username)) {
				throw new ServiceException(
						ErrorCode.USER_INSUFFICIENT_PERMISSIONS, 
						"The user is not an admin."
					);
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}

	/**
	 * Checks if the user is an admin.
	 * 
	 * @return true if the user has admin privileges, false otherwise
	 * 
	 * @throws ServiceException Thrown if there was an error.
	 */
	public boolean isUserAnAdmin(final String username) 
			throws ServiceException {
		
		try {
			return userQueries.userIsAdmin(username);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Verifies that the user can create campaigns.
	 * 
	 * @param username The username of the user whose campaign creation ability
	 * 				   is being checked.
	 * 
	 * @throws ServiceException Thrown if there is an error or if the user is
	 * 							not allowed to create campaigns.
	 */
	public void verifyUserCanCreateCampaigns(final String username) 
			throws ServiceException {
		
		try {
			if(! userQueries.userCanCreateCampaigns(username)) {
				throw new ServiceException(
						ErrorCode.CAMPAIGN_INSUFFICIENT_PERMISSIONS, 
						"The user does not have permission to create new campaigns.");
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Validates that a registration ID still exists, hasn't been used, and 
	 * hasn't expired.
	 * 
	 * @param registrationId The registration's unique identifier.
	 * 
	 * @throws ServiceException The registration doesn't exist or is invalid or
	 * 							there was an error.
	 */
	public void validateRegistrationId(
			final String registrationId) 
			throws ServiceException {
		
		try {
			if(! userQueries.registrationIdExists(registrationId)) {
				throw new ServiceException(
						ErrorCode.USER_INVALID_REGISTRATION_ID,
						"No such registration ID exists.");
			}
			
			if(userQueries.getRegistrationAcceptedDate(registrationId) != null) {
				throw new ServiceException(
						ErrorCode.USER_INVALID_REGISTRATION_ID,
						"This registration ID has already been used to activate an account.");
			}
			
			long earliestTime = (new Date()).getTime() - REGISTRATION_DURATION;
			
			if(userQueries.getRegistrationRequestedDate(registrationId).getTime() < earliestTime) {
				throw new ServiceException(
						ErrorCode.USER_INVALID_REGISTRATION_ID,
						"The registration ID has expired.");
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Verifies that a given user is allowed to read the personal information
	 * about a group of users.
	 * 
	 * @param username The username of the reader.
	 * 
	 * @param usernames The usernames of the readees.
	 * 
	 * @throws ServiceException There was an error or the user is not allowed 
	 * 							to read the personal information of one or more
	 * 							of the users.
	 */
	public void verifyUserCanReadUsersPersonalInfo(
			final String username, final Collection<String> usernames) 
			throws ServiceException {
		
		if((usernames == null) || (usernames.size() == 0) ||
				((usernames.size() == 1) && 
				 usernames.iterator().next().equals(username))) {
			return;
		}
		
		Set<String> supervisorCampaigns = 
			UserCampaignServices.instance().getCampaignsForUser(username, 
					null, null, null, null, null, null, 
					Campaign.Role.SUPERVISOR);
		
		Set<String> privilegedClasses = 
			UserClassServices.instance().getClassesForUser(
					username, 
					Clazz.Role.PRIVILEGED);
		
		for(String currUsername : usernames) {
			if(UserCampaignServices.instance().getCampaignsForUser( 
					currUsername, supervisorCampaigns, privilegedClasses, 
					null, null, null, null, null).size() == 0) {
				
				throw new ServiceException(
						ErrorCode.USER_INSUFFICIENT_PERMISSIONS, 
						"The user is not allowed to view personal information about a user in the list: " + 
							currUsername);
			}
		}
	}

	/**
	 * Verifies that if the user already has personal information in which it 
	 * is acceptable to update any combination of the pieces or that they 
	 * supplied all necessary pieces to update the information.
	 * 
	 * @param username The username of the user whose personal information is
	 * 				   being queried.
	 * 
	 * @param firstName The new first name of the user or null if the first 
	 * 					name is not being updated.
	 * 
	 * @param lastName The new last name of the user or null if the last name 
	 * 				   is not being updated.
	 * 
	 * @param organization The new organization of the user or null if the
	 * 					   organization is not being updated.
	 * 
	 * @param personalId The new personal ID of the user or null if the 
	 * 					 personal ID is not being updated.
	 * 
	 * @throws ServiceException The user doesn't have personal information in
	 * 							the system and is attempting to update some 
	 * 							fields but not all of them. If the user doesn't
	 * 							have personal information already, they must
	 * 							create a new one with all of the information. 
	 * 							Or there was an error.
	 */
	public void verifyUserHasOrCanCreatePersonalInfo(
			final String username, 
			final String firstName,
			final String lastName,
			final String organization,
			final String personalId) 
			throws ServiceException {
		
		// If the user already has personal information, then they are allowed
		// to edit it as they wish.
		try {
			if(userQueries.userHasPersonalInfo(username)) {
				return;
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
		
		// If they are all null and the user isn't trying to update the 
		// personal information, then that is fine.
		if((firstName == null) &&
				(lastName == null) &&
				(organization == null) &&
				(personalId == null)) {
			
			return;
		}
		
		if(firstName == null) {
			throw new ServiceException(
					ErrorCode.USER_INVALID_FIRST_NAME_VALUE, 
					"The user doesn't have personal information yet, and a first name is necessary to create one.");
		}
		else if(lastName == null) {
			throw new ServiceException(
					ErrorCode.USER_INVALID_LAST_NAME_VALUE, 
					"The user doesn't have personal information yet, and a last name is necessary to create one.");
		}
		else if(organization == null) {
			throw new ServiceException(
					ErrorCode.USER_INVALID_ORGANIZATION_VALUE, 
					"The user doesn't have personal information yet, and an organization is necessary to create one.");
		}
		else if(personalId == null) {
			throw new ServiceException(
					ErrorCode.USER_INVALID_PERSONAL_ID_VALUE, 
					"The user doesn't have personal information yet, and a personal ID is necessary to create one.");
		}
	}
	
	/**
	 * Searches through all of the users in the system and returns those that
	 * match the criteria. All Object parameters are optional except
	 * 'requesterUsername'; by passing a null value, it will be omitted from 
	 * the search.
	 * 
	 * @param requesterUsername The username of the user making this request.
	 * 
	 * @param usernames Limits the results to only those whose username is in
	 * 					this list.
	 * 
	 * @param usernameTokens Limits the results to only those whose username is
	 * 						 like any of the values in this token.
	 * 
	 * @param emailAddress Limits the results to only those users whose email
	 * 					   address matches this value.
	 * 
	 * @param admin Limits the results to only those users whose admin value
	 * 				matches this value.
	 * 
	 * @param enabled Limits the results to only those user whose enabled value
	 * 				  matches this value.
	 * 
	 * @param newAccount Limits the results to only those users whose new 
	 * 					 account value matches this value.
	 * 
	 * @param campaignCreationPrivilege Limits the results to only those 
	 * 									users whose campaign creation privilege
	 * 									matches this value.
	 * 
	 * @param firstName Limits the results to only those that have personal 
	 * 					information and their first name equals this value.
	 * 
	 * @param partialLastName Limits the results to only those users that have 
	 * 						  personal information and their last name matches 
	 * 						  this value.
	 * 
	 * @param partialOrganization Limits the results to only those users that 
	 * 							  have personal information and their 
	 * 							  organization value matches this value.
	 * 
	 * @param partialPersonalId Limits the results to only those users that 
	 * 							have personal information and their personal ID
	 * 							matches this value.
	 * 
	 * @param numToSkip The number of results to skip.
	 * 
	 * @param numToReturn The number of results to return.
	 * 
	 * @param results The user information for the users that matched the
	 * 				  criteria.
	 * 
	 * @return The number of usernames that matched the given criteria.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public long getUserInformation(
			final String requesterUsername,
			final Collection<String> usernames,
			final Collection<String> usernameTokens,
			final Collection<String> emailAddressTokens,
			final Boolean admin,
			final Boolean enabled,
			final Boolean newAccount,
			final Boolean canCreateCampaigns,
			final Collection<String> firstNameTokens,
			final Collection<String> lastNameTokens,
			final Collection<String> organizationTokens,
			final Collection<String> personalIdTokens,
			final Collection<String> campaignIds,
			final Collection<String> classIds,
			final long numToSkip,
			final long numToReturn,
			final List<UserInformation> results) 
			throws ServiceException {
		
		try {
			QueryResultsList<UserInformation> result =
					userQueries.getUserInformation(
							requesterUsername,
							usernames,
							usernameTokens, 
							emailAddressTokens, 
							admin, 
							enabled, 
							newAccount, 
							canCreateCampaigns, 
							firstNameTokens, 
							lastNameTokens, 
							organizationTokens, 
							personalIdTokens,
							campaignIds,
							classIds,
							numToSkip, 
							numToReturn);
			
			results.addAll(result.getResults());
			
			return result.getTotalNumResults();
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Searches through all of the users in the system and returns those that
	 * match the criteria. All Object parameters are optional except 
	 * 'requesterUsername'; by passing a null value, it will be omitted from 
	 * the search. 
	 * 
	 * @param requesterUsername The username of the user making this request.
	 * 
	 * @param partialUsername Limits the results to only those users whose 
	 * 						  username contain this value.
	 * 
	 * @param partialEmailAddress Limits the results to only those users whose
	 * 							  email address contains this value.
	 * 
	 * @param admin Limits the results to only those usernames that belong to
	 * 				users whose admin value matches this one.
	 * 
	 * @param enabled Limits the results to only those usernames that belong to
	 * 				  users whose enabled value matches this one.
	 * 
	 * @param newAccount Limits the results to only those usernames that belong
	 * 					 to users whose new account value matches this one.
	 * 
	 * @param campaignCreationPrivilege Limits the results to only those 
	 * 									usernames that belong to users whose	
	 * 									campaign creation privilege matches 
	 * 									this one.
	 * 
	 * @param partialFirstName Limits the results to only those usernames that
	 * 						   belong to users that have personal information
	 * 						   and their first name contains this value.
	 * 
	 * @param partialLastName Limits the results to only those usernames that
	 * 						  belong to users that have personal information 
	 * 						  and their last name contains this value.
	 * 
	 * @param partialOrganization Limits the results to only those usernames
	 * 							  that belong to users that have personal 
	 * 							  information and their organization value 
	 * 							  contains this value.
	 * 
	 * @param partialPersonalId Limits the results to only those usernames that
	 * 							belong to users that have personal information
	 * 							and their personal ID contains this value.
	 * 
	 * @param numToSkip The number of results to skip.
	 * 
	 * @param numToReturn The number of results to return.
	 * 
	 * @param results The user information for the users that matched the
	 * 				  criteria. This cannot be null and will be populated with
	 * 				  the results.
	 * 
	 * @return The number of usernames that matched the given criteria.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public long userSearch(
			final String requesterUsername,
			final String partialUsername,
			final String partialEmailAddress,
			final Boolean admin,
			final Boolean enabled,
			final Boolean newAccount,
			final Boolean campaignCreationPrivilege,
			final String partialFirstName,
			final String partialLastName,
			final String partialOrganization,
			final String partialPersonalId,
			final int numToSkip,
			final int numToReturn,
			final Collection<UserInformation> results)
			throws ServiceException {
		
		try {
			Set<String> usernameTokens = null;
			if(partialUsername != null) {
				usernameTokens = new HashSet<String>();
				usernameTokens.add(partialUsername);
			}
			
			Set<String> emailAddressTokens = null;
			if(partialEmailAddress != null) {
				emailAddressTokens = new HashSet<String>();
				emailAddressTokens.add(partialEmailAddress);
			}
			
			Set<String> firstNameTokens = null;
			if(partialFirstName != null) {
				firstNameTokens = new HashSet<String>();
				firstNameTokens.add(partialFirstName);
			}
			
			Set<String> lastNameTokens = null;
			if(partialLastName != null) {
				lastNameTokens = new HashSet<String>();
				lastNameTokens.add(partialLastName);
			}
			
			Set<String> organizationTokens = null;
			if(partialOrganization != null) {
				organizationTokens = new HashSet<String>();
				organizationTokens.add(partialOrganization);
			}
			
			Set<String> personalIdTokens = null;
			if(partialPersonalId != null) {
				personalIdTokens = new HashSet<String>();
				personalIdTokens.add(partialPersonalId);
			}
			
			QueryResultsList<UserInformation> result =
					userQueries.getUserInformation(
							requesterUsername,
							null, 
							usernameTokens, 
							emailAddressTokens, 
							admin, 
							enabled, 
							newAccount, 
							campaignCreationPrivilege, 
							firstNameTokens, 
							lastNameTokens, 
							organizationTokens, 
							personalIdTokens, 
							null,
							null,
							numToSkip, 
							numToReturn);
			
			try {
				for(UserInformation currResult : result.getResults()) {
					currResult.addCampaigns(
							userCampaignQueries.getCampaignAndRolesForUser(
									currResult.getUsername()));
				
					currResult.addClasses(
							userClassQueries.getClassAndRoleForUser(
									currResult.getUsername()));
				}
			}
			catch(DomainException e) {
				throw new ServiceException(e);
			}
			
			results.addAll(result.getResults());

			return result.getTotalNumResults();
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Gathers the summary about a user.
	 * 
	 * @param username The username of the user whose summary is being 
	 * 				   requested.
	 * 
	 * @return Returns a UserSummary object that contains the necessary
	 * 		   information about a user.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public UserSummary getUserSummary(final String username)
			throws ServiceException {
		
		try {
			// Get the campaigns and their names for the requester.
			Map<String, String> campaigns = userCampaignQueries.getCampaignIdsAndNameForUser(username);
						
			// Get the requester's campaign roles for each of the campaigns.
			Set<Campaign.Role> campaignRoles = new HashSet<Campaign.Role>();
			for(String campaignId : campaigns.keySet()) {
				campaignRoles.addAll(
						userCampaignQueries.getUserCampaignRoles(
								username, 
								campaignId));
			}

			// Get the classes and their names for the requester.
			Map<String, String> classes = userClassQueries.getClassIdsAndNameForUser(username);
			
			// Get the requester's class roles for each of the classes.
			Set<Clazz.Role> classRoles = new HashSet<Clazz.Role>();
			for(String classId : classes.keySet()) {
				classRoles.add(
						userClassQueries.getUserClassRole(classId, username));
			}
			
			// Get campaign creation privilege.
			try {
				return new UserSummary(
						userQueries.getEmailAddress(username),
						userQueries.userIsAdmin(username), 
						userQueries.userCanCreateCampaigns(username),
						campaigns,
						campaignRoles,
						classes,
						classRoles);
			} 
			catch(DomainException e) {
				throw new ServiceException(e);
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Retrieves the personal information for all of the users in the list.
	 * 
	 * @param usernames The usernames.
	 * 
	 * @return A map of usernames to personal information or null if no 
	 * 		   personal information is available.
	 * 
	 * @throws ServiceException There was an error.
	 */
	public Map<String, UserPersonal> gatherPersonalInformation(
			final Collection<String> usernames) throws ServiceException {
		
		try {
			Map<String, UserPersonal> result = 
				new HashMap<String, UserPersonal>(usernames.size());
			
			for(String username : usernames) {
				result.put(username, userQueries.getPersonalInfoForUser(username));
			}
			
			return result;
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Updates a user's account information.
	 * 
	 * @param username The username of the user whose information is to be
	 * 				   updated.
	 * 
	 * @param emailAddress The new email address for the user. A null value 
	 * 					   indicates that this field should not be updated.
	 * 
	 * @param admin Whether or not the user should be an admin. A null value
	 * 			    indicates that this field should not be updated.
	 * 
	 * @param enabled Whether or not the user's account should be enabled. A
	 * 				  null value indicates that this field should not be
	 * 				  updated.
	 * 
	 * @param newAccount Whether or not the user should be required to change
	 * 					 their password. A null value indicates that this field
	 * 					 should not be updated.
	 * 
	 * @param campaignCreationPrivilege Whether or not the user should be 
	 * 									allowed to create campaigns. A null
	 * 									Value indicates that this field should
	 * 									not be updated.
	 * 
	 * @param firstName The user's new first name. A null value indicates that
	 * 					this field should not be updated.
	 * 
	 * @param lastName The users's last name. A null value indicates that this
	 * 				   field should not be updated.
	 * 
	 * @param organization The user's new organization. A null value indicates
	 * 					   that this field should not be updated.
	 * 
	 * @param personalId The user's new personal ID. A null value indicates 
	 * 					 that this field should not be updated.
	 * 
	 * @param deletePersonalInfo Whether or not to delete the user's personal
	 * 							 information.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public void updateUser(
			final String username, 
			final String emailAddress,
			final Boolean admin, 
			final Boolean enabled, 
			final Boolean newAccount, 
			final Boolean campaignCreationPrivilege, 
			final String firstName,
			final String lastName,
			final String organization,
			final String personalId,
			final boolean deletePersonalInfo) 
			throws ServiceException {
		
		try {
			userQueries.updateUser(
					username, 
					emailAddress,
					admin, 
					enabled, 
					newAccount, 
					campaignCreationPrivilege,
					firstName,
					lastName,
					organization,
					personalId,
					deletePersonalInfo);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}

	/**
	 * Activates a user's account by updating the enabled status to true and
	 * updates the registration table's entry.
	 * 
	 * @param registrationId The registration's unique identifier.
	 * 
	 * @throws DataAccessException There was an error.
	 */
	public void activateUser(
			final String registrationId)
			throws ServiceException {
		
		try {
			userQueries.activateUser(registrationId);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Resets a user's password. This is done by first updating the user's 
	 * information and then by sending an email to the user.
	 * 
	 * @param username The user's username.
	 * 
	 * @throws ServiceException There was an error.
	 */
	public void resetPassword(final String username)
			throws ServiceException {
			
		String newPassword = generateRandomTemporaryPassword();
		
		String emailAddress;
		try {
			emailAddress = userQueries.getEmailAddress(username);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
		
		if(emailAddress == null) {
			throw new ServiceException(
					"The user no longer exists or no longer has an email address.");
		}
		
		try {
			userQueries.updateUserPassword(
					username, 
					BCrypt.hashpw(newPassword, BCrypt.gensalt(13)), 
					true);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
		
		// Get the session.
		Session smtpSession = getMailSession();
		// Create the message.
		MimeMessage message = new MimeMessage(smtpSession);
		
		// Add the recipient.
		try {
			message.setRecipient(
					Message.RecipientType.TO, 
					new InternetAddress(emailAddress));
		}
		catch(AddressException e) {
			throw new ServiceException(
					"The destination address is not a valid email address.",
					e);
		}
		catch(MessagingException e) {
			throw new ServiceException(
					"Could not add the recipient to the message.",
					e);
		}
		
		// Add the sender.
		try {
			message.setFrom(
					new InternetAddress(
							PreferenceCache.instance().lookup(
								PreferenceCache.KEY_MAIL_PASSWORD_RECOVERY_SENDER)));
		}
		catch(CacheMissException e) {
			throw new ServiceException(
				"The mail property is not in the preference table: " +
					PreferenceCache.KEY_MAIL_PASSWORD_RECOVERY_SENDER,
				e);
		}
		catch(AddressException e) {
			throw new ServiceException(
					"The origin address is not a valid email address.",
					e);
		}
		catch(MessagingException e) {
			throw new ServiceException(
					"Could not update the sender's email address.",
					e);
		}
		
		// Set the subject.
		try {
			message.setSubject(
					PreferenceCache.instance().lookup(
						PreferenceCache.KEY_MAIL_PASSWORD_RECOVERY_SUBJECT));
		}
		catch(CacheMissException e) {
			throw new ServiceException(
					"The mail property is not in the preference table: " +
						PreferenceCache.KEY_MAIL_PASSWORD_RECOVERY_SUBJECT,
					e);
		}
		catch(MessagingException e) {
			throw new ServiceException(
					"Could not set the subject on the message.",
					e);
		}
		
		try {
			message.setContent(
					PreferenceCache.instance().lookup(
						PreferenceCache.KEY_MAIL_PASSWORD_RECOVERY_TEXT) +
						"<br /><br />" +
						newPassword, 
					"text/html");
		}
		catch(CacheMissException e) {
			throw new ServiceException(
					"The mail property is not in the preference table: " +
						PreferenceCache.KEY_MAIL_PASSWORD_RECOVERY_SUBJECT,
					e);
		}
		catch(MessagingException e) {
			throw new ServiceException(
					"Could not set the HTML portion of the message.", 
					e);
		}

		try {
			message.saveChanges();
		}
		catch(MessagingException e) {
			throw new ServiceException(
					"Could not save the changes to the message.",
					e);
		}
		
		sendMailMessage(smtpSession, message);
	}
	
	/**
	 * Updates the user's password.
	 * 
	 * @param username The username of the user whose password is being 
	 * 				   updated.
	 * 
	 * @param plaintextPassword The plaintext password for the user.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public void updatePassword(final String username, 
			final String plaintextPassword) throws ServiceException {
		
		try {
			String hashedPassword = BCrypt.hashpw(plaintextPassword, BCrypt.gensalt(13));
			
			userQueries.updateUserPassword(username, hashedPassword, false);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Deletes all of the users from the Collection.
	 * 
	 * @param usernames A Collection of usernames of the users to delete.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public void deleteUser(final Collection<String> usernames) 
			throws ServiceException {
		// First, retrieve the path information for all of the images 
		// associated with each user.
		Collection<URL> imageUrls = new HashSet<URL>();
		try {
			for(String username : usernames) {
				imageUrls.addAll(
					userImageQueries.getImageUrlsFromUsername(username));
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
				
		try {
			userQueries.deleteUsers(usernames);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
		
		// If the transaction succeeded, delete all of the images from the 
		// disk.
		for(URL imageUrl : imageUrls) {
			imageQueries.deleteImageDiskOnly(imageUrl);
		}
	}
	
	/**
	 * Generates a plaintext temporary password based that does not observe our
	 * rule set.
	 * 
	 * @return The plaintext password.
	 */
	private String generateRandomTemporaryPassword() {
		Random random = new Random();
		StringBuilder passwordBuilder = new StringBuilder();
		for(int i = 0; i < 32; i++) {
			passwordBuilder.append(
				CHARS_TEMPORARY_PASSWORD[random.nextInt(
					CHARS_TEMPORARY_PASSWORD.length)]);
		}
		return passwordBuilder.toString();
	}
	
	/**
	 * Creates and returns a new mail Session.
	 * 
	 * @return The mail session based on the current state of the preferences
	 * 		   in the database.
	 * 
	 * @throws ServiceException There was a problem creating the session.
	 */
	private Session getMailSession() throws ServiceException {	
		// Get the email properties.
		Properties sessionProperties = new Properties();
		try {
			String host = 
					PreferenceCache.instance().lookup(
						PreferenceCache.KEY_MAIL_HOST);
			
			sessionProperties.put(MAIL_PROPERTY_HOST, host);
		}
		catch(CacheMissException e) {
			// This is acceptable. It simply tells JavaMail to use the
			// default.
		}
		
		try {
			sessionProperties.put(
					MAIL_PROPERTY_PORT, 
					PreferenceCache.instance().lookup(
						PreferenceCache.KEY_MAIL_PORT));
		}
		catch(CacheMissException e) {
			// This is acceptable. It simply tells JavaMail to use the
			// default.
		}
		
		try {
			sessionProperties.put(
					MAIL_PROPERTY_SSL_ENABLED, 
					PreferenceCache.instance().lookup(
						PreferenceCache.KEY_MAIL_SSL));
		}
		catch(CacheMissException e) {
			// This is acceptable. It simply tells JavaMail to use the
			// default.
		}
		
		// Create the session and return it.
		return Session.getInstance(sessionProperties);
	}
	
	/**
	 * Sends a mail message.
	 * 
	 * @param smtpSession The session used to create the message.
	 * 
	 * @param message The message to be sent.
	 * 
	 * @throws ServiceException There was a problem creating the connection to
	 * 							the mail server or sending the message.
	 */
	private void sendMailMessage(Session smtpSession, Message message) throws ServiceException {
		// Get the transport from the session.
		SMTPTransport transport;
		try {
			transport = 
					(SMTPTransport) smtpSession.getTransport(MAIL_PROTOCOL);
		}
		catch(NoSuchProviderException e) {
			throw new ServiceException(
					"There is no provider for SMTP. " +
						"This means the library has changed as it has built-in support for SMTP.",
					e);
		}

		Boolean auth = null;
		try {
			auth = StringUtils.decodeBoolean(
					PreferenceCache.instance().lookup(
						PreferenceCache.KEY_MAIL_AUTH));
		}
		catch(CacheMissException e) {
			// This is acceptable. It simply tells JavaMail to use the
			// default.
		}
		
		if((auth != null) && auth) {
			String mailUsername;
			try {
				mailUsername = 
						PreferenceCache.instance().lookup(
							PreferenceCache.KEY_MAIL_USERNAME);
			}
			catch(CacheMissException e) {
				throw new ServiceException(
					"The mail property is not in the preference table: " +
						PreferenceCache.KEY_MAIL_USERNAME,
					e);
			}
			
			String mailPassword;
			try {
				mailPassword = 
						PreferenceCache.instance().lookup(
							PreferenceCache.KEY_MAIL_PASSWORD);
			}
			catch(CacheMissException e) {
				throw new ServiceException(
					"The mail property is not in the preference table: " +
						PreferenceCache.KEY_MAIL_PASSWORD,
					e);
			}
			
			try {
				transport.connect(
						smtpSession.getProperty(MAIL_PROPERTY_HOST), 
						mailUsername, 
						mailPassword);
			}
			catch(MessagingException e) {
				throw new ServiceException(
						"Could not authenticate with or connect to the mail server.",
						e);
			}
		}
		else {
			try {
				transport.connect();
			}
			catch(MessagingException e) {
				throw new ServiceException(
						"Could not connect to the mail server.",
						e);
			}
		}
		
		try {
			transport.sendMessage(message, message.getAllRecipients());
		}
		catch(SendFailedException e) {
			throw new ServiceException(
					"Failed to send the message.",
					e);
		}
		catch(MessagingException e) {
			throw new ServiceException(
					"There was a problem while sending the message.",
					e);
		}
		
		try {
			transport.close();
		}
		catch(MessagingException e) {
			throw new ServiceException(
					"After sending the message there was an error closing the connection.",
					e);
		}
	}
}

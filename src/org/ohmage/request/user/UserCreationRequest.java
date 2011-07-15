package org.ohmage.request.user;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.ohmage.annotator.ErrorCodes;
import org.ohmage.request.InputKeys;
import org.ohmage.request.UserRequest;
import org.ohmage.service.ServiceException;
import org.ohmage.service.UserServices;
import org.ohmage.validator.UserValidators;
import org.ohmage.validator.ValidationException;

/**
 * <p>Creates a new user. The requester must be an admin.</p>
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
 *     <td>{@value org.ohmage.request.InputKeys#USER}</td>
 *     <td>The username for the new user.</td>
 *     <td>true</td>
 *   </tr>
 *   <tr>
 *     <td>{@value org.ohmage.request.InputKeys#PASSWORD}</td>
 *     <td>The password for the new user.</td>
 *     <td>true</td>
 *   </tr>
 *   <tr>
 *     <td>{@value org.ohmage.request.InputKeys#USER_ADMIN}</td>
 *     <td>Whether or not the new user should be an admin.</td>
 *     <td>true</td>
 *   </tr>
 *   <tr>
 *     <td>{@value org.ohmage.request.InputKeys#USER_ENABLED}</td>
 *     <td>Whether or not the new user's account should be enabled.</td>
 *     <td>true</td>
 *   </tr>
 *   <tr>
 *     <td>{@value org.ohmage.request.InputKeys#NEW_ACCOUNT}</td>
 *     <td>Whether or not the user must change their password before using any
 *       other APIs. The default value is "true".</td>
 *     <td>true</td>
 *   </tr>
 *   <tr>
 *     <td>{@value org.ohmage.request.InputKeys#CAMPAIGN_CREATION_PRIVILEGE}</td>
 *     <td>Whether or not the new user is allowed to create campaigns. The 
 *       default value is based on the current system and can be discovered
 *       through the /config/read API.</td>
 *     <td>true</td>
 *   </tr>
 * </table>
 * 
 * @author John Jenkins
 */
public class UserCreationRequest extends UserRequest {
	private static final Logger LOGGER = Logger.getLogger(UserCreationRequest.class);
	
	private final String newUsername;
	private final String newPassword;
	private final Boolean newIsAdmin;
	private final Boolean newIsEnabled;
	private final Boolean newIsNewAccount;
	private final Boolean newCampaignCreationPrivilege;
	
	/**
	 * Creates a user creation request.
	 * 
	 * @param httpRequest The HttpServletRequest that contains the required and
	 * 					  optional parameters for creating this request.
	 */
	public UserCreationRequest(HttpServletRequest httpRequest) {
		super(getToken(httpRequest), httpRequest.getParameter(InputKeys.CLIENT));
		
		LOGGER.info("Creating a user creation request.");
		
		String tNewUsername = null;
		String tNewPassword = null;
		Boolean tNewIsAdmin = null;
		Boolean tNewIsEnabled = null;
		Boolean tNewIsNewAccount = null;
		Boolean tNewCampaignCreationPrivilege = null;
		
		try {
			tNewUsername = UserValidators.validateUsername(this, httpRequest.getParameter(InputKeys.USERNAME));
			if(tNewUsername == null) {
				setFailed(ErrorCodes.USER_INVALID_USERNAME, "Missing the required username for the new user: " + InputKeys.USERNAME);
				throw new ValidationException("Missing the required username for the new user: " + InputKeys.USERNAME);
			}
			
			tNewPassword = UserValidators.validatePlaintextPassword(this, httpRequest.getParameter(InputKeys.PASSWORD));
			if(tNewPassword == null) {
				setFailed(ErrorCodes.USER_INVALID_PASSWORD, "Missing the required plaintext password for the user: " + InputKeys.PASSWORD);
				throw new ValidationException("Missing the required plaintext password for the user: " + InputKeys.PASSWORD);
			}
			
			tNewIsAdmin = UserValidators.validateAdminValue(this, httpRequest.getParameter(InputKeys.USER_ADMIN));
			if(tNewIsAdmin == null) {
				setFailed(ErrorCodes.USER_INVALID_ADMIN_VALUE, "Missing the required admin parameter: " + InputKeys.USER_ADMIN);
				throw new ValidationException("Missing the required admin parameter: " + InputKeys.USER_ADMIN);
			}
			
			tNewIsEnabled = UserValidators.validateEnabledValue(this, httpRequest.getParameter(InputKeys.USER_ENABLED));
			if(tNewIsEnabled == null) {
				setFailed(ErrorCodes.USER_INVALID_ADMIN_VALUE, "Missing the required enabled parameter: " + InputKeys.USER_ENABLED);
				throw new ValidationException("Missing the required enabled parameter: " + InputKeys.USER_ENABLED);
			}
			
			tNewIsNewAccount = UserValidators.validateNewAccountValue(this, httpRequest.getParameter(InputKeys.NEW_ACCOUNT));
			tNewCampaignCreationPrivilege = UserValidators.validateCampaignCreationPrivilegeValue(this, httpRequest.getParameter(InputKeys.CAMPAIGN_CREATION_PRIVILEGE));
		}
		catch(ValidationException e) {
			LOGGER.info(e.toString());
		}
		
		newUsername = tNewUsername;
		newPassword = tNewPassword;
		newIsAdmin = tNewIsAdmin;
		newIsEnabled = tNewIsEnabled;
		newIsNewAccount = tNewIsNewAccount;
		newCampaignCreationPrivilege = tNewCampaignCreationPrivilege;
	}

	/**
	 * Services this request.
	 */
	@Override
	public void service() {
		LOGGER.info("Servicing the user creation request.");
		
		if(! authenticate(false)) {
			return;
		}
		
		try {
			LOGGER.info("Verifying that the requesting user is an admin.");
			UserServices.verifyUserIsAdmin(this, user.getUsername());
			
			LOGGER.info("Verifying that a user with the username doesn't already exist.");
			UserServices.checkUserExistance(this, newUsername, false);
			
			LOGGER.info("Creating the user.");
			UserServices.createUser(this, newUsername, newPassword, newIsAdmin, newIsEnabled, newIsNewAccount, newCampaignCreationPrivilege);
		}
		catch(ServiceException e) {
			e.logException(LOGGER);
		}
	}

	/**
	 * Responds with success or failure and a message.
	 */
	@Override
	public void respond(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		super.respond(httpRequest, httpResponse, null);
	}
}
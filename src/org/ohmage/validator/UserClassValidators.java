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
package org.ohmage.validator;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.ohmage.annotator.Annotator.ErrorCode;
import org.ohmage.domain.Clazz;
import org.ohmage.exception.ValidationException;
import org.ohmage.request.InputKeys;
import org.ohmage.util.StringUtils;

/**
 * Class for validating username and class pair values.
 * 
 * @author John Jenkins
 */
public final class UserClassValidators {
	private static final Logger LOGGER = Logger.getLogger(UserClassValidators.class);
	
	/**
	 * Default constructor. Private so that it cannot be instantiated.
	 */
	private UserClassValidators() {}
	
	/**
	 * Checks that the user class-role list is syntactically valid, that all of
	 * the usernames are syntactically valid, and that all of the roles are 
	 * valid. If the list String is null or whitespace only, null is returned.
	 * If there is any error in validating the list, a ValidationException is
	 * thrown. Otherwise, a Map of usernames to class roles is returned.
	 *  
	 * @param userClassRoleList A String representing a list of username and
	 * 							class-role pairs. The pairs should be separated
	 * 							by 
	 * 							{@value org.ohmage.request.InputKeys#LIST_ITEM_SEPARATOR}
	 * 							and the username and class-role should be 
	 * 							separated by
	 * 							{@value org.ohmage.request.InputKeys#ENTITY_ROLE_SEPARATOR}.
	 * 
	 * @return Returns null if the list string is null, whitespace only, or 
	 * 		   only contains separators and no meaningful information. 
	 * 		   Otherwise, a map of username to class roles is returned with at
	 * 		   least one entry.
	 * 
	 * @throws ValidationException Thrown if the list String or any of the 
	 * 							   usernames in the list String are 
	 * 							   syntactically invalid. Also, thrown if any
	 * 							   of the roles in the list String are invalid.
	 */
	public static Map<String, Clazz.Role> validateUserAndClassRoleList(
			final String userClassRoleList) throws ValidationException {
		
		LOGGER.info("Validating the user and class role list.");
		
		if(StringUtils.isEmptyOrWhitespaceOnly(userClassRoleList)) {
			return null;
		}
		
		Map<String, Clazz.Role> result = new HashMap<String, Clazz.Role>();
		String[] userAndRoleArray = userClassRoleList.split(InputKeys.LIST_ITEM_SEPARATOR);
		for(int i = 0; i < userAndRoleArray.length; i++) {
			String currUserAndRole = userAndRoleArray[i].trim();
			
			if((! StringUtils.isEmptyOrWhitespaceOnly(currUserAndRole)) &&
					(! currUserAndRole.equals(InputKeys.ENTITY_ROLE_SEPARATOR))) {
				String[] userAndRole = currUserAndRole.split(InputKeys.ENTITY_ROLE_SEPARATOR);
				
				if(userAndRole.length != 2) {
					throw new ValidationException(
							ErrorCode.CLASS_INVALID_ROLE, 
							"The user class-role list is invalid: " + 
								currUserAndRole);
				}
				
				String username = UserValidators.validateUsername(userAndRole[0].trim());
				if(username == null) {
					throw new ValidationException(
							ErrorCode.USER_INVALID_USERNAME, 
							"The username in the username, class role list is missing: " + 
								currUserAndRole);
				}
				
				Clazz.Role role = ClassValidators.validateClassRole(userAndRole[1].trim());
				if(role == null) {
					throw new ValidationException(
							ErrorCode.CLASS_INVALID_ROLE, 
							"The class role in the username, class role list is missing: " + 
								currUserAndRole);
				}
				
				result.put(username, role);
			}
		}
		
		if(result.size() == 0) {
			return null;
		}
		else {
			return result;
		}
	}
}

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
package org.ohmage.query.impl;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.ohmage.exception.DataAccessException;
import org.ohmage.query.IImageQueries;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * This class is responsible for all operations directly pertaining to images.
 * It may read information from other entities as required but the parameters
 * to these functions, the return values from these functions, and the changes
 * made by these functions should only pertain to images.
 * 
 * @author John Jenkins
 */
public final class ImageQueries extends Query implements IImageQueries {
	private static final Logger LOGGER = Logger.getLogger(ImageQueries.class);
	
	// Checks if an image exists.
	private static final String SQL_EXISTS_IMAGE = 
		"SELECT EXISTS(" +
			"SELECT ubr.uuid " +
			"FROM url_based_resource ubr, prompt_response pr " +
			"WHERE ubr.uuid = ? " +
			"AND pr.response = ubr.uuid " +
			"AND pr.prompt_type = 'photo'" +
		")";
	
	// Retrieves the URL for an image.
	private static final String SQL_GET_IMAGE_URL =
		"SELECT DISTINCT(ubr.url) " +
		"FROM url_based_resource ubr, prompt_response pr " +
		"WHERE ubr.uuid = ? " +
		"AND pr.response = ubr.uuid " +
		"AND pr.prompt_type = 'photo'";
	
	// Deletes an image form the url_based_resource table.
	private static final String SQL_DELETE_IMAGE =
		"DELETE FROM url_based_resource " +
		"WHERE uuid = ?";
	
	public static final String IMAGE_STORE_FORMAT = "png";
	public static final String IMAGE_SCALED_EXTENSION = "-s";
	
	/**
	 * Creates this object.
	 * 
	 * @param dataSource The DataSource to use to query the database.
	 */
	private ImageQueries(DataSource dataSource) {
		super(dataSource);
	}

	/* (non-Javadoc)
	 * @see org.ohmage.query.impl.IImageQueries#getImageExists(java.lang.String)
	 */
	public Boolean getImageExists(UUID imageId) throws DataAccessException {
		try {
			return getJdbcTemplate().queryForObject(SQL_EXISTS_IMAGE, new Object[] { imageId.toString() }, Boolean.class);
		}
		catch(org.springframework.dao.DataAccessException e) {
			throw new DataAccessException("Error executing SQL '" + SQL_EXISTS_IMAGE + "' with parameter: " + imageId, e);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.ohmage.query.impl.IImageQueries#getImageUrl(java.lang.String)
	 */
	public URL getImageUrl(UUID imageId) throws DataAccessException {
		try {
			return new URL(
					getJdbcTemplate().queryForObject(
							SQL_GET_IMAGE_URL, 
							new Object[] { imageId.toString() }, 
							String.class));
		}
		catch(org.springframework.dao.IncorrectResultSizeDataAccessException e) {
			if(e.getActualSize() > 1) {
				throw new DataAccessException("Multiple images have the same unique identifier.", e);
			}
			
			return null;
		}
		catch(org.springframework.dao.DataAccessException e) {
			throw new DataAccessException("Error executing SQL '" + SQL_GET_IMAGE_URL + "' with parameter: " + imageId, e);
		}
		catch(MalformedURLException e) {
			throw new DataAccessException("The URL was not a valid URL.", e);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.ohmage.query.impl.IImageQueries#deleteImage(java.lang.String)
	 */
	public void deleteImage(UUID imageId) throws DataAccessException {
		// Create the transaction.
		DefaultTransactionDefinition def = new DefaultTransactionDefinition();
		def.setName("Deleting an image.");
		
		try {
			// Begin the transaction.
			PlatformTransactionManager transactionManager = new DataSourceTransactionManager(getDataSource());
			TransactionStatus status = transactionManager.getTransaction(def);
			
			URL imageUrl = getImageUrl(imageId);
			
			try {
				getJdbcTemplate().update(
						SQL_DELETE_IMAGE,
						new Object[] { imageId.toString() });
			}
			catch(org.springframework.dao.DataAccessException e) {
				transactionManager.rollback(status);
				throw new DataAccessException(
						"Error executing SQL '" + SQL_DELETE_IMAGE + 
						"' with parameter: " +
							imageId, 
						e);
			}
			
			if(imageUrl != null) {
				deleteImageDiskOnly(imageUrl);
			}

			// Commit the transaction.
			try {
				transactionManager.commit(status);
			}
			catch(TransactionException e) {
				transactionManager.rollback(status);
				throw new DataAccessException("Error while committing the transaction.", e);
			}
		}
		catch(TransactionException e) {
			throw new DataAccessException("Error while attempting to rollback the transaction.", e);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.ohmage.query.IImageQueries#deleteImageDiskOnly(java.net.URL)
	 */
	public void deleteImageDiskOnly(URL imageUrl) {
		try {
			// Delete the original image.
			if((new File(imageUrl.getFile())).delete()) {
				LOGGER.warn("The image no longer existed.");
			}
			
			// Delete the scaled image.
			if((new File((new URL(imageUrl + IMAGE_SCALED_EXTENSION)).getFile())).delete()) {
				LOGGER.warn("The scaled image no longer existed.");
			}
		}
		catch(MalformedURLException e) {
			LOGGER.warn("The URL was malformed, but we are deleting the image anyway.", e);
		}
		catch(SecurityException e) {
			LOGGER.error("The system would not allow us to delete the image.", e);
		}
	}
}

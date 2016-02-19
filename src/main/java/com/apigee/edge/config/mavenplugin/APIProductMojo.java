/**
 * Copyright (C) 2014 Apigee Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.apigee.edge.config.mavenplugin;

import com.apigee.edge.config.utils.ConfigReader;
import com.apigee.edge.config.rest.RestUtil;
import com.apigee.edge.config.utils.ServerProfile;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.util.Key;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.File;
import java.util.List;

import org.apache.commons.io.FileUtils;

import com.google.api.client.http.*;
import org.json.simple.JSONValue;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**                                                                                                                                     ¡¡
 * Goal to create API Product in Apigee EDGE
 * scope: org
 *
 * @author madhan.sadasivam
 * @goal apiproducts
 * @phase install
 */

public class APIProductMojo extends GatewayAbstractMojo
{
	static Logger logger = LoggerFactory.getLogger(APIProductMojo.class);
	public static final String ____ATTENTION_MARKER____ =
	"************************************************************************";

	enum OPTIONS {
		none, create, update
	}

	OPTIONS buildOption = OPTIONS.none;

	private ServerProfile serverProfile;

    public static class APIProduct {
        @Key
        public String name;
    }
	
	public APIProductMojo() {
		super();

	}
	
	public void init() throws MojoFailureException {
		try {
			logger.info(____ATTENTION_MARKER____);
			logger.info("Apigee API Product");
			logger.info(____ATTENTION_MARKER____);

			String options="";
			serverProfile = super.getProfile();			
	
			options = super.getOptions();
			if (options != null) {
				buildOption = OPTIONS.valueOf(options);
			}
			logger.debug("Build option " + buildOption.name());
			logger.debug("Base dir " + super.getBaseDirectoryPath());
		} catch (IllegalArgumentException e) {
			throw new RuntimeException("Invalid apigee.option provided");
		} catch (RuntimeException e) {
			throw e;
		}

	}

	protected String getAPIProductName(String payload) 
            throws MojoFailureException {
		Gson gson = new Gson();
		try {
			APIProduct product = gson.fromJson(payload, APIProduct.class);
			return product.name;
		} catch (JsonParseException e) {
		  throw new MojoFailureException(e.getMessage());
		}
	}

	protected void doUpdate(List<String> products) 
            throws MojoFailureException {
		try {
			List existingAPIProducts = null;
			if (buildOption != OPTIONS.update && 
				buildOption != OPTIONS.create) {
				return;
			}

			logger.info("Retrieving existing environment API Products");
			existingAPIProducts = getAPIProduct(serverProfile);

	        for (String product : products) {
	        	String productName = getAPIProductName(product);
	        	if (productName == null) {
	        		throw new IllegalArgumentException(
	        			"API Product does not have a name.\n" + product + "\n");
	        	}

        		if (existingAPIProducts.contains(productName)) {
		        	if (buildOption == OPTIONS.update) {
						logger.info("API Product \"" + productName + 
												"\" exists. Updating.");
						updateAPIProduct(serverProfile,
												productName, product);
	        		} else {
	        			logger.info("API Product \"" + productName + 
	        									"\" already exists. Skipping.");
	        		}
	        	} else {
					logger.info("Creating API Product - " + productName);
					createAPIProduct(serverProfile, product);
	        	}
			}
		
		} catch (IOException e) {
			throw new MojoFailureException("Apigee network call error " +
														 e.getMessage());
		} catch (RuntimeException e) {
			throw e;
		}
	}

	/** 
	 * Entry point for the mojo.
	 */
	public void execute() throws MojoExecutionException, MojoFailureException {

		if (super.isSkip()) {
			getLog().info("Skipping");
			return;
		}

		Logger logger = LoggerFactory.getLogger(APIProductMojo.class);
		File configFile = findConfigFile(logger);
		if (configFile == null) {
			return;
		}

		try {
			fixOSXNonProxyHosts();
			
			init();

			if (buildOption == OPTIONS.none) {
				logger.info("Skipping API Products (default action)");
				return;
			}

            if (serverProfile.getEnvironment() == null) {
                throw new MojoExecutionException(
                            "Apigee environment not found in profile");
            }

			List products = getConfig(logger, configFile);			
			if (products == null || products.size() == 0) {
				logger.info("No API Product found in edge.json.");
                return;
			}

			doUpdate(products);				
			
		} catch (MojoFailureException e) {
			throw e;
		} catch (RuntimeException e) {
			throw e;
		}
	}

	private List getConfig(Logger logger, File configFile) 
			throws MojoExecutionException {
		logger.debug("Retrieving config from edge.json");
		try {
			return ConfigReader.getConfig(serverProfile.getEnvironment(), 
    										configFile,
                                            "orgConfig",
                                            "apiProducts");
		} catch (Exception e) {
			throw new MojoExecutionException(e.getMessage());
		}
	}

	private File findConfigFile(Logger logger) throws MojoExecutionException {
		File configFile = new File(super.getBaseDirectoryPath() + 
									File.separator + "edge.json");

		if (configFile.exists()) {
			return configFile;
		}

		logger.info("No edge.json found.");
		return null;
	}

    /***************************************************************************
     * REST call wrappers
     **/
    public static String createAPIProduct(ServerProfile profile, String product)
            throws IOException {

        HttpResponse response = RestUtil.createOrgConfig(profile, 
                                                         "apiproducts",
                                                         product);
        try {

            logger.info("Response " + response.getContentType() + "\n" +
                                        response.parseAsString());
            if (response.isSuccessStatusCode())
            	logger.info("Create Success.");

        } catch (HttpResponseException e) {
            logger.error("API Product create error " + e.getMessage());
            throw new IOException(e.getMessage());
        }

        return "";
    }

    public static String updateAPIProduct(ServerProfile profile, 
                                        String productName, 
                                        String product)
            throws IOException {

        HttpResponse response = RestUtil.updateOrgConfig(profile, 
                                                        "apiproducts", 
                                                        productName,
                                                        product);
        try {
            
            logger.info("Response " + response.getContentType() + "\n" +
                                        response.parseAsString());
            if (response.isSuccessStatusCode())
            	logger.info("Update Success.");

        } catch (HttpResponseException e) {
            logger.error("API Product update error " + e.getMessage());
            throw new IOException(e.getMessage());
        }

        return "";
    }

    public static List getAPIProduct(ServerProfile profile)
            throws IOException {

        HttpResponse response = RestUtil.getOrgConfig(profile, 
                                                    "apiproducts");
        JSONArray products = null;
        try {
            logger.debug("output " + response.getContentType());
            // response can be read only once
            String payload = response.parseAsString();
            logger.debug(payload);

            /* Parsers fail to parse a string array.
             * converting it to an JSON object as a workaround */
            String obj = "{ \"products\": " + payload + "}";

            JSONParser parser = new JSONParser();                
            JSONObject obj1     = (JSONObject)parser.parse(obj);
            products    = (JSONArray)obj1.get("products");

        } catch (ParseException pe){
            logger.error("Get API Product parse error " + pe.getMessage());
            throw new IOException(pe.getMessage());
        } catch (HttpResponseException e) {
            logger.error("Get API Product error " + e.getMessage());
            throw new IOException(e.getMessage());
        }

        return products;
    }	
}





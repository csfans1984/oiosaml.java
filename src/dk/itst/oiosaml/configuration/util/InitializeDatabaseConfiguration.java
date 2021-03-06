/*
 * The contents of this file are subject to the Mozilla Public 
 * License Version 1.1 (the "License"); you may not use this 
 * file except in compliance with the License. You may obtain 
 * a copy of the License at http://www.mozilla.org/MPL/
 * 
 * Software distributed under the License is distributed on an 
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express 
 * or implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 *
 * The Original Code is OIOSAML Java Service Provider.
 * 
 * The Initial Developer of the Original Code is Trifork A/S. Portions 
 * created by Trifork A/S are Copyright (C) 2014 Danish National IT 
 * and Telecom Agency (http://www.itst.dk). All Rights Reserved.
 * 
 * Contributor(s):
 *   Mads M. Tandrup <mads@maetzke-tandrup.dk>
 *
 */
package dk.itst.oiosaml.configuration.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.opensaml.DefaultBootstrap;
import org.opensaml.saml2.metadata.EntityDescriptor;
import org.opensaml.xml.security.credential.Credential;
import org.xml.sax.SAXException;

import dk.itst.oiosaml.common.OIOSAMLConstants;
import dk.itst.oiosaml.common.SAMLUtil;
import dk.itst.oiosaml.sp.configuration.ConfigurationGenerator;
import dk.itst.oiosaml.sp.configuration.ConfigurationGenerator.KeystoreCredentialsHolder;
import dk.itst.oiosaml.sp.service.util.Constants;

public class InitializeDatabaseConfiguration {
	public static void main(String[] args) throws org.opensaml.xml.ConfigurationException, ConfigurationException, IOException, ParserConfigurationException, SAXException {
		if (args.length < 2) {
			System.out.println("File configuration to database metadata needs 6 parameters:");
			System.out.println("1) ProviderId (entityID)");
			System.out.println("2) Base URL");
			System.out.println("3) Organization Name");
			System.out.println("4) Organization Url");
			System.out.println("5) Organization E-mail");
			System.out.println("6) IdP metadata filename");
			System.out.println("7) CRL period (0 = CRL check disabled)");
			System.out.println("7) Database url");
			System.out.println("8) Database user");
			System.out.println("9) Database password");
			System.out.println("Remember to add sqldriver to classpath.");
			System.out.println("Exam: ");
			System.out.println("InitializeDatabaseConfiguration https://app.companya.com https://app.companya.com/singlesignon/saml CompanyA http://www.companya.com contact@companya.com idp-metadata.xml 0 jdbc:postgresql://localhost:5432/oiodb oiouser oiopwd");
			return;
		}

		final String entityId = args[0];
		final String baseUrl = args[1];
		final String orgName = args[2];
		final String orgUrl = args[3];
		final String email = args[4];
		final String idpFilename = args[5];
		final String crlPeriod = args[6];
		final String databaseUrl = args[7];
		final String databaseUser = args[8];
		final String databasePwd = args.length > 9 ? args[9] : null;
		final String password = "datacenter";

		System.out.println("Connecting to " + databaseUrl);
		
		DefaultBootstrap.bootstrap();

		System.out.println("Generate keystore");
		KeystoreCredentialsHolder keystoreAndCredentials = ConfigurationGenerator.generateKeystoreAndCredentials(password, entityId);
		final byte[] keystore = keystoreAndCredentials.getKeystore();
		final Credential credential = keystoreAndCredentials.getCredential();

		System.out.println("Generate SP metadata");
		final EntityDescriptor spDescriptor = ConfigurationGenerator.generateSPDescriptor(baseUrl, entityId, credential, orgName, orgUrl, email, false, true, true, true, false, OIOSAMLConstants.NAMEIDFORMAT_UNSPECIFIED);

		Connection con = null;
		try {
			con = DriverManager.getConnection(databaseUrl, databaseUser, databasePwd);
			System.out.println("(Re-)Creating database schema");
			createSchema(con);
			System.out.println("Store keystore");
			saveKeystoreToDb(keystore, entityId, con);
			System.out.println("Store SP metadata");
			saveSPToDb(spDescriptor, entityId, con);
			System.out.println("Store IdP metadata");
			saveIdPToDb(idpFilename, con);
			System.out.println("Store properties");
			saveConfigurationToDb(password, crlPeriod, con);

			System.out.println("Done!");
		} catch (SQLException e) {
			System.err.println("Unable to select or insert from database! Error: " + e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				if (con != null) con.close();
			} catch (SQLException e) {
				System.err.println("Unable to close database");
			}
		}
	}

	private static void saveSPToDb(EntityDescriptor spDescriptor, String id, Connection con) throws SQLException, ConfigurationException, IOException, ParserConfigurationException, SAXException {
		PreparedStatement insertStatement = con.prepareStatement("insert into oiosaml_serviceprovider (id,metadata) values(?,?)");
		insertStatement.setString(1, id);
		insertStatement.setString(2, SAMLUtil.getSAMLObjectAsPrettyPrintXML(spDescriptor));
		insertStatement.executeUpdate();
	}

	private static void saveKeystoreToDb(byte[] keystore, String id, Connection con) throws SQLException, ConfigurationException, IOException, ParserConfigurationException, SAXException {
		PreparedStatement insertStatement = con.prepareStatement("insert into oiosaml_java_keystore (id, keystore) values(?,?)");
		insertStatement.setString(1, id);
		insertStatement.setBytes(2, keystore);
		insertStatement.executeUpdate();
	}

	private static void saveIdPToDb(String fileName, Connection con) throws SQLException, ConfigurationException, IOException, ParserConfigurationException, SAXException {
		PreparedStatement insertStatement = con.prepareStatement("insert into oiosaml_identityproviders (id,metadata) values(?,?)");
		File file = new File(fileName);
		String idpmetadata = FileUtils.readFileToString(file);
		insertStatement.setString(1, fileName);
		insertStatement.setString(2, idpmetadata);
		insertStatement.executeUpdate();
	}

	private static void saveConfigurationToDb(String password, String crlPeriod, Connection con) throws SQLException {
		PreparedStatement stmt = con.prepareStatement("insert into oiosaml_properties (conf_key, conf_value) values(?, ?)");

		stmt.setString(1, Constants.PROP_CERTIFICATE_PASSWORD);
		stmt.setString(2, password);
		stmt.addBatch();

		stmt.setString(1, Constants.PROP_ASSURANCE_LEVEL);
		stmt.setString(2, "2");
		stmt.addBatch();

		stmt.setString(1, Constants.PROP_HOME);
		stmt.setString(2, "/singlesignon");
		stmt.addBatch();

		stmt.setString(1, Constants.PROP_CRL_CHECK_PERIOD);
		stmt.setString(2, crlPeriod);
		stmt.addBatch();

		stmt.setString(1, Constants.PROP_SESSION_HANDLER_FACTORY);
		stmt.setString(2, "dk.itst.oiosaml.sp.service.session.jdbc.JndiFactory");
		stmt.addBatch();

		stmt.setString(1, "oiosaml-sp.sessionhandler.jndi");
		stmt.setString(2, "java:jdbc/oiosaml-ds");
		stmt.addBatch();

		stmt.executeBatch();
	}

	private static void createSchema(Connection con) throws IOException, SQLException {
		InputStream inputStream = InitializeDatabaseConfiguration.class.getResourceAsStream("/dk/itst/oiosaml/configuration/jdbc/postgresql.sql");
		String sql = IOUtils.toString(inputStream);
		Statement statement = con.createStatement();
		statement.execute(sql);
		statement.close();
	}
}

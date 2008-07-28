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
 * created by Trifork A/S are Copyright (C) 2008 Danish National IT 
 * and Telecom Agency (http://www.itst.dk). All Rights Reserved.
 * 
 * Contributor(s):
 *   Joakim Recht <jre@trifork.com>
 *   Rolf Njor Jensen <rolf@trifork.com>
 *
 */
package dk.itst.oiosaml.sp.model;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;

import dk.itst.oiosaml.logging.LogUtil;
import dk.itst.oiosaml.sp.service.session.LoggedInHandler;
import dk.itst.oiosaml.sp.service.util.Constants;

public class RelayState {
	private static final Logger log = Logger.getLogger(RelayState.class);
	
	private final String relayState;

	public RelayState(String relayState) {
		if (relayState == null) throw new IllegalArgumentException(" Parameter 'RelayState' is null...");
		this.relayState = relayState;
	}

	public static RelayState fromRequest(HttpServletRequest request) {
		String relayState = request.getParameter(Constants.SAML_RELAYSTATE);
		return new RelayState(relayState);
	}
	
	public String toString() {
		return "RelayState: " + relayState;
	}

	public void finished(HttpSession session) {
		// Find the timer object stored when the <AuthnRequest> was sent
		LogUtil requestLogUtil = LoggedInHandler.getInstance().removeID(session, relayState);
		if (requestLogUtil != null) {
			requestLogUtil.afterService(Constants.SERVICE_AUTHN_REQUEST);
		} else {
			log.warn("No id found in session for relaystate: " + relayState);
		}
	}
}

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.swssf.policy.test;

import org.swssf.policy.PolicyEnforcer;
import org.swssf.policy.PolicyViolationException;
import org.swssf.wss.ext.WSSecurityException;
import org.swssf.wss.securityEvent.ContentEncryptedElementSecurityEvent;
import org.swssf.wss.securityEvent.SecurityEvent;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.xml.namespace.QName;

/**
 * @author $Author: giger $
 * @version $Revision: 1181995 $ $Date: 2011-10-11 20:03:00 +0200 (Tue, 11 Oct 2011) $
 */
public class ContentEncryptedElementsTest extends AbstractPolicyTestBase {

    @Test
    public void testPolicy() throws Exception {
        String policyString =
                "<sp:ContentEncryptedElements xmlns:sp=\"http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702\" xmlns:sp3=\"http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200802\">\n" +
                        "<sp:XPath xmlns:b=\"http://example.org\">/b:a</sp:XPath>\n" +
                        "</sp:ContentEncryptedElements>";
        PolicyEnforcer policyEnforcer = buildAndStartPolicyEngine(policyString);
        ContentEncryptedElementSecurityEvent contentEncryptedElementSecurityEvent = new ContentEncryptedElementSecurityEvent(SecurityEvent.Event.ContentEncrypted, true);
        contentEncryptedElementSecurityEvent.setElement(new QName("http://schemas.xmlsoap.org/soap/envelope/", "Body"));
        policyEnforcer.registerSecurityEvent(contentEncryptedElementSecurityEvent);
        contentEncryptedElementSecurityEvent.setElement(new QName("http://example.org", "a"));
        policyEnforcer.registerSecurityEvent(contentEncryptedElementSecurityEvent);
        //additional ContentEncryptedElements are also allowed!
        contentEncryptedElementSecurityEvent.setElement(new QName("http://example.com", "b"));
        policyEnforcer.registerSecurityEvent(contentEncryptedElementSecurityEvent);
        policyEnforcer.doFinal();
    }

    @Test
    public void testPolicyMultipleAssertionEventsNegative() throws Exception {
        String policyString =
                "<sp:ContentEncryptedElements xmlns:sp=\"http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702\" xmlns:sp3=\"http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200802\">\n" +
                        "<sp:XPath xmlns:b=\"http://example.org\">/b:a</sp:XPath>\n" +
                        "</sp:ContentEncryptedElements>";
        PolicyEnforcer policyEnforcer = buildAndStartPolicyEngine(policyString);
        ContentEncryptedElementSecurityEvent contentEncryptedElementSecurityEvent = new ContentEncryptedElementSecurityEvent(SecurityEvent.Event.ContentEncrypted, true);
        contentEncryptedElementSecurityEvent.setElement(new QName("http://schemas.xmlsoap.org/soap/envelope/", "Body"));
        policyEnforcer.registerSecurityEvent(contentEncryptedElementSecurityEvent);
        contentEncryptedElementSecurityEvent = new ContentEncryptedElementSecurityEvent(SecurityEvent.Event.ContentEncrypted, false);
        contentEncryptedElementSecurityEvent.setElement(new QName("http://example.org", "a"));
        try {
            policyEnforcer.registerSecurityEvent(contentEncryptedElementSecurityEvent);
            Assert.fail("Exception expected");
        } catch (WSSecurityException e) {
            Assert.assertTrue(e.getCause() instanceof PolicyViolationException);
        }
    }
}

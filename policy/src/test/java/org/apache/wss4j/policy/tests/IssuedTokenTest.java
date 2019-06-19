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
package org.apache.wss4j.policy.tests;

import org.apache.neethi.*;
import org.apache.wss4j.policy.SP12Constants;
import org.apache.wss4j.policy.model.IssuedToken;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IssuedTokenTest extends AbstractTestBase {

    @Test
    public void testIssuedToken() throws Exception {
        String fileName = "IssuedToken.xml";
        String policyFile = loadPolicyFile("policy/model/sp12/" + fileName);
        String serializedPolicyReferenceFile = loadPolicyFile("policy/model/sp12/serialized/" + fileName);
        String normalizedPolicyReferenceFile = loadPolicyFile("policy/model/sp12/normalized/" + fileName);
        Policy policy = loadPolicy(policyFile);
        String serializedPolicy = serializePolicy(policy);
        assertXMLisEqual(serializedPolicy, serializedPolicyReferenceFile);

        Iterator<List<Assertion>> alternativeIterator = policy.getAlternatives();
        int count = 0;
        while (alternativeIterator.hasNext()) {
            List<Assertion> alternative = alternativeIterator.next();
            assertEquals(1, alternative.size());
            assertTrue(alternative.get(0) instanceof IssuedToken);
            IssuedToken issuedToken = (IssuedToken) alternative.get(0);
            assertFalse(issuedToken.isNormalized());
            assertTrue(issuedToken.isIgnorable());
            assertTrue(issuedToken.isOptional());
            assertEquals(Constants.TYPE_ASSERTION, issuedToken.getType());
            assertEquals(SP12Constants.ISSUED_TOKEN, issuedToken.getName());
            assertNotNull(issuedToken.getRequestSecurityTokenTemplate());
            assertTrue(issuedToken.isRequireExternalReference());
            assertTrue(issuedToken.isRequireInternalReference());
            count++;
        }
        assertEquals(1, count);

        policy = policy.normalize(true);
        serializedPolicy = serializePolicy(policy);
        assertXMLisEqual(serializedPolicy, normalizedPolicyReferenceFile);

        alternativeIterator = policy.getAlternatives();
        List<Assertion> alternative = alternativeIterator.next();
        assertEquals(0, alternative.size());

        List<PolicyComponent> policyComponents = policy.getPolicyComponents();
        assertEquals(1, policyComponents.size());
        PolicyOperator policyOperator = (PolicyOperator) policyComponents.get(0);
        policyComponents = policyOperator.getPolicyComponents();
        assertEquals(2, policyComponents.size());
        All all = (All) policyComponents.get(0);
        List<PolicyComponent> policyComponentsAll = all.getAssertions();
        assertEquals(0, policyComponentsAll.size());

        all = (All) policyComponents.get(1);
        policyComponentsAll = all.getAssertions();
        assertEquals(1, policyComponentsAll.size());

        Iterator<PolicyComponent> policyComponentIterator = policyComponentsAll.iterator();
        IssuedToken issuedToken = (IssuedToken) policyComponentIterator.next();
        assertTrue(issuedToken.isNormalized());
        assertTrue(issuedToken.isIgnorable());
        assertFalse(issuedToken.isOptional());
        assertEquals(Constants.TYPE_ASSERTION, issuedToken.getType());
        assertEquals(SP12Constants.ISSUED_TOKEN, issuedToken.getName());
        assertNotNull(issuedToken.getRequestSecurityTokenTemplate());
        assertTrue(issuedToken.isRequireExternalReference());
        assertTrue(issuedToken.isRequireInternalReference());
    }
}
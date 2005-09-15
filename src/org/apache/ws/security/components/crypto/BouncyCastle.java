/*
 * Copyright  2003-2004 The Apache Software Foundation.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.ws.security.components.crypto;

import org.apache.ws.security.WSSecurityException;
import org.bouncycastle.jce.cert.CertPath;
import org.bouncycastle.jce.cert.CertificateFactory;
import org.bouncycastle.jce.cert.PKIXParameters;
import org.bouncycastle.jce.cert.CertPathValidator;
import org.bouncycastle.jce.cert.CertPathValidatorException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateEncodingException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

/**
 * JDK1.4 based implementation of Crypto (uses keystore).
 * <p/>
 *
 * @author Davanum Srinivas (dims@yahoo.com).
 */
public class BouncyCastle extends AbstractCrypto {

    /**
     * Constructor.
     * <p/>
     *
     * @param properties
     * @throws CredentialException
     * @throws java.io.IOException
     */
    public BouncyCastle(Properties properties) throws CredentialException, IOException {
        super(properties);
    }

    /**
     * Construct an array of X509Certificate's from the byte array.
     * <p/>
     *
     * @param data    The <code>byte</code> array containg the X509 data
     * @param reverse If set the first certificate in input data will
     *                the last in the array
     * @return An array of X509 certificates, ordered according to
     *         the reverse flag
     * @throws org.apache.ws.security.WSSecurityException
     *
     */
    public X509Certificate[] getX509Certificates(byte[] data, boolean reverse)
            throws WSSecurityException {
        InputStream in = new ByteArrayInputStream(data);
        CertPath path = null;
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            path = factory.generateCertPath(in);
        } catch (CertificateException e) {
            throw new WSSecurityException(WSSecurityException.SECURITY_TOKEN_UNAVAILABLE,
                    "parseError");
        }
        List l = path.getCertificates();
        X509Certificate[] certs = new X509Certificate[l.size()];
        Iterator iterator = l.iterator();
        for (int i = 0; i < l.size(); i++) {
            certs[(reverse) ? (l.size() - 1 - i) : i] = (X509Certificate) iterator.next();
        }
        return certs;
    }

    /**
     * get a byte array given an array of X509 certificates.
     * <p/>
     *
     * @param reverse If set the first certificate in the array data will
     *                the last in the byte array
     * @param certs   The certificates to convert
     * @return The byte array for the certficates ordered according
     *         to the reverse flag
     * @throws org.apache.ws.security.WSSecurityException
     *
     */
    public byte[] getCertificateData(boolean reverse, X509Certificate[] certs)
            throws WSSecurityException {
        Vector list = new Vector();
        for (int i = 0; i < certs.length; i++) {
            if (reverse) {
                list.insertElementAt(certs[i], 0);
            } else {
                list.add(certs[i]);
            }
        }
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            CertPath path = factory.generateCertPath(list);
            return path.getEncoded();
        } catch (CertificateEncodingException e) {
            throw new WSSecurityException(WSSecurityException.SECURITY_TOKEN_UNAVAILABLE,
                    "encodeError");
        } catch (CertificateException e) {
            throw new WSSecurityException(WSSecurityException.SECURITY_TOKEN_UNAVAILABLE,
                    "parseError");
        }
    }

    /**
     * Uses the CertPath API to validate a given certificate chain
     *
     * @param certs Certificate chain to validate
     * @return true if the certificate chain is valid, false otherwise
     * @throws org.apache.ws.security.WSSecurityException
     *
     */
    public boolean validateCertPath(X509Certificate[] certs) throws WSSecurityException {

        try {
            // Generate cert path
            List certList = Arrays.asList(certs);
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            CertPath path = factory.generateCertPath(certList);

            // Use the certificates in the keystore as TrustAnchors
            PKIXParameters param = new PKIXParameters(this.keystore);

            // Do not check a revocation list
            param.setRevocationEnabled(false);

            // Verify the trust path using the above settings            
            CertPathValidator certPathValidator = CertPathValidator.getInstance("PKIX");
            certPathValidator.validate(path, param);
        } catch (NoSuchAlgorithmException ex) {
            throw new WSSecurityException(WSSecurityException.FAILURE,
                    "certpath",
                    new Object[]{ex.getMessage()},
                    (Throwable) ex);
        } catch (CertificateException ex) {
            throw new WSSecurityException(WSSecurityException.FAILURE,
                    "certpath",
                    new Object[]{ex.getMessage()},
                    (Throwable) ex);
        } catch (InvalidAlgorithmParameterException ex) {
            throw new WSSecurityException(WSSecurityException.FAILURE,
                    "certpath",
                    new Object[]{ex.getMessage()},
                    (Throwable) ex);
        } catch (KeyStoreException ex) {
            throw new WSSecurityException(WSSecurityException.FAILURE,
                    "certpath",
                    new Object[]{ex.getMessage()},
                    (Throwable) ex);
        } catch (CertPathValidatorException ex) {
            throw new WSSecurityException(WSSecurityException.FAILURE,
                    "certpath",
                    new Object[]{ex.getMessage()},
                    (Throwable) ex);
        }
        return true;
    }
}



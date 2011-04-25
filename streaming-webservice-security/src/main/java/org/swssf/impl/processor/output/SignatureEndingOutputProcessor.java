/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.swssf.impl.processor.output;

import org.apache.commons.codec.binary.Base64;
import org.oasis_open.docs.wss._2004._01.oasis_200401_wss_wssecurity_secext_1_0.BinarySecurityTokenType;
import org.swssf.config.TransformerAlgorithmMapper;
import org.swssf.ext.*;
import org.swssf.impl.SignaturePartDef;
import org.swssf.impl.algorithms.SignatureAlgorithm;
import org.swssf.impl.algorithms.SignatureAlgorithmFactory;
import org.swssf.impl.transformer.canonicalizer.Canonicalizer20010315Transformer;
import org.swssf.impl.util.RFC2253Parser;
import org.swssf.impl.util.SignerOutputStream;
import org.swssf.securityEvent.SecurityEvent;
import org.swssf.securityEvent.SignatureValueSecurityEvent;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * @author $Author: giger $
 * @version $Revision: 272 $ $Date: 2010-12-23 14:30:56 +0100 (Thu, 23 Dec 2010) $
 */
public class SignatureEndingOutputProcessor extends AbstractBufferingOutputProcessor {

    private List<SignaturePartDef> signaturePartDefList;
    //todo implement or remove?:
    private boolean useSingleCert = true;

    public SignatureEndingOutputProcessor(SecurityProperties securityProperties, SignatureOutputProcessor signatureOutputProcessor) throws WSSecurityException {
        super(securityProperties);
        this.getAfterProcessors().add(SignatureOutputProcessor.class.getName());
        this.getAfterProcessors().add(UsernameTokenOutputProcessor.class.getName());
        signaturePartDefList = signatureOutputProcessor.getSignaturePartDefList();
    }

    protected void processHeaderEvent(OutputProcessorChain outputProcessorChain) throws XMLStreamException, WSSecurityException {

        OutputProcessorChain subOutputProcessorChain = outputProcessorChain.createSubChain(this);

        String alias = getSecurityProperties().getSignatureUser();
        X509Certificate[] x509Certificates = null;

        if (alias != null) {
            x509Certificates = getSecurityProperties().getSignatureCrypto().getCertificates(alias);
            if (x509Certificates == null || x509Certificates.length == 0) {
                throw new WSSecurityException(WSSecurityException.FAILED_SIGNATURE, "noUserCertsFound");
            }
        }

        String certUri = "CertId-" + UUID.randomUUID().toString();
        BinarySecurityTokenType referencedBinarySecurityTokenType = null;
        if (getSecurityProperties().getSignatureKeyIdentifierType() == Constants.KeyIdentifierType.BST_DIRECT_REFERENCE) {
            referencedBinarySecurityTokenType = new BinarySecurityTokenType();
            referencedBinarySecurityTokenType.setEncodingType(Constants.SOAPMESSAGE_NS10_BASE64_ENCODING);
            referencedBinarySecurityTokenType.setValueType(Constants.NS_X509_V3_TYPE);
            referencedBinarySecurityTokenType.setId(certUri);

            Map<QName, String> attributes = new HashMap<QName, String>();
            attributes.put(Constants.ATT_NULL_EncodingType, referencedBinarySecurityTokenType.getEncodingType());
            attributes.put(Constants.ATT_NULL_ValueType, referencedBinarySecurityTokenType.getValueType());
            attributes.put(Constants.ATT_wsu_Id, referencedBinarySecurityTokenType.getId());
            createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_wsse_BinarySecurityToken, attributes);
            try {
                createCharactersAndOutputAsEvent(subOutputProcessorChain, Base64.encodeBase64String(x509Certificates[0].getEncoded()));
            } catch (CertificateEncodingException e) {
                throw new WSSecurityException(WSSecurityException.FAILED_SIGNATURE, null, e);
            }
            createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_wsse_BinarySecurityToken);
        }

        Map<QName, String> attributes = new HashMap<QName, String>();
        attributes.put(Constants.ATT_NULL_Id, "Signature-" + UUID.randomUUID().toString());
        createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_Signature, attributes);

        WSPasswordCallback pwCb = new WSPasswordCallback(alias, WSPasswordCallback.SIGNATURE);
        Utils.doCallback(getSecurityProperties().getCallbackHandler(), pwCb);
        String password = pwCb.getPassword();
        if (password == null) {
            throw new WSSecurityException(WSSecurityException.FAILED_SIGNATURE, "noPassword", new Object[]{alias});
        }

        SignatureAlgorithm signatureAlgorithm;

        try {
            signatureAlgorithm = SignatureAlgorithmFactory.getInstance().getSignatureAlgorithm(getSecurityProperties().getSignatureAlgorithm());
        } catch (NoSuchAlgorithmException e) {
            throw new WSSecurityException(WSSecurityException.FAILED_SIGNATURE, null, e);
        } catch (NoSuchProviderException e) {
            throw new WSSecurityException(WSSecurityException.FAILURE, "noSecProvider", e);
        }

        SecurityTokenProvider securityTokenProvider = null;
        if (alias != null) {
            signatureAlgorithm.engineInitSign(getSecurityProperties().getSignatureCrypto().getPrivateKey(alias, password));
        } else if (getSecurityProperties().getTokenUser() != null) {
            securityTokenProvider = outputProcessorChain.getSecurityContext().getSecurityTokenProvider(getSecurityProperties().getTokenUser());
            if (securityTokenProvider == null) {
                throw new WSSecurityException(WSSecurityException.FAILED_SIGNATURE);
            }
            SecurityToken securityToken = securityTokenProvider.getSecurityToken(null);
            if (securityToken == null) {
                throw new WSSecurityException(WSSecurityException.FAILED_SIGNATURE);
            }
            signatureAlgorithm.engineInitSign(securityToken.getSecretKey(getSecurityProperties().getSignatureAlgorithm()));
        }

        SignedInfoProcessor signedInfoProcessor = new SignedInfoProcessor(getSecurityProperties(), signatureAlgorithm);
        subOutputProcessorChain.addProcessor(signedInfoProcessor);

        createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_SignedInfo, null);

        attributes = new HashMap<QName, String>();
        attributes.put(Constants.ATT_NULL_Algorithm, getSecurityProperties().getSignatureCanonicalizationAlgorithm());
        createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_CanonicalizationMethod, attributes);
        createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_CanonicalizationMethod);

        attributes = new HashMap<QName, String>();
        attributes.put(Constants.ATT_NULL_Algorithm, getSecurityProperties().getSignatureAlgorithm());
        createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_SignatureMethod, attributes);
        createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_SignatureMethod);

        Iterator<SignaturePartDef> signaturePartDefIterator = signaturePartDefList.iterator();
        while (signaturePartDefIterator.hasNext()) {
            SignaturePartDef signaturePartDef = signaturePartDefIterator.next();
            attributes = new HashMap<QName, String>();
            attributes.put(Constants.ATT_NULL_URI, "#" + signaturePartDef.getSigRefId());
            createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_Reference, attributes);
            createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_Transforms, null);

            attributes = new HashMap<QName, String>();
            attributes.put(Constants.ATT_NULL_Algorithm, getSecurityProperties().getSignatureCanonicalizationAlgorithm());
            createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_Transform, attributes);
            createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_Transform);

            createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_Transforms);

            attributes = new HashMap<QName, String>();
            attributes.put(Constants.ATT_NULL_Algorithm, getSecurityProperties().getSignatureDigestAlgorithm());
            createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_DigestMethod, attributes);
            createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_DigestMethod);
            createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_DigestValue, null);
            createCharactersAndOutputAsEvent(subOutputProcessorChain, signaturePartDef.getDigestValue());
            createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_DigestValue);
            createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_Reference);
        }

        createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_SignedInfo);
        subOutputProcessorChain.removeProcessor(signedInfoProcessor);

        createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_SignatureValue, null);
        final byte[] signatureValue = signedInfoProcessor.getSignatureValue();
        createCharactersAndOutputAsEvent(subOutputProcessorChain, Base64.encodeBase64String(signatureValue));
        createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_SignatureValue);

        attributes = new HashMap<QName, String>();
        attributes.put(Constants.ATT_NULL_Id, "KeyId-" + UUID.randomUUID().toString());
        createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_KeyInfo, attributes);

        attributes = new HashMap<QName, String>();
        attributes.put(Constants.ATT_wsu_Id, "STRId-" + UUID.randomUUID().toString());
        createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_wsse_SecurityTokenReference, attributes);

        if (getSecurityProperties().getSignatureKeyIdentifierType() == Constants.KeyIdentifierType.ISSUER_SERIAL) {

            createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_X509Data, null);
            createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_X509IssuerSerial, null);
            createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_X509IssuerName, null);
            createCharactersAndOutputAsEvent(subOutputProcessorChain, RFC2253Parser.normalize(x509Certificates[0].getIssuerDN().getName(), true));
            createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_X509IssuerName);
            createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_X509SerialNumber, null);
            createCharactersAndOutputAsEvent(subOutputProcessorChain, x509Certificates[0].getSerialNumber().toString());
            createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_X509SerialNumber);
            createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_X509IssuerSerial);
            createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_X509Data);
        } else if (getSecurityProperties().getSignatureKeyIdentifierType() == Constants.KeyIdentifierType.SKI_KEY_IDENTIFIER) {
            // As per the 1.1 specification, SKI can only be used for a V3 certificate
            if (x509Certificates[0].getVersion() != 3) {
                throw new WSSecurityException(WSSecurityException.FAILED_SIGNATURE, "invalidCertForSKI");
            }

            attributes = new HashMap<QName, String>();
            attributes.put(Constants.ATT_NULL_EncodingType, Constants.SOAPMESSAGE_NS10_BASE64_ENCODING);
            attributes.put(Constants.ATT_NULL_ValueType, Constants.NS_X509SubjectKeyIdentifier);
            createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_wsse_KeyIdentifier, attributes);
            byte data[] = getSecurityProperties().getSignatureCrypto().getSKIBytesFromCert(x509Certificates[0]);
            createCharactersAndOutputAsEvent(subOutputProcessorChain, Base64.encodeBase64String(data));
            createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_wsse_KeyIdentifier);
        } else if (getSecurityProperties().getSignatureKeyIdentifierType() == Constants.KeyIdentifierType.X509_KEY_IDENTIFIER) {

            attributes = new HashMap<QName, String>();
            attributes.put(Constants.ATT_NULL_EncodingType, Constants.SOAPMESSAGE_NS10_BASE64_ENCODING);
            attributes.put(Constants.ATT_NULL_ValueType, Constants.NS_X509_V3_TYPE);
            createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_wsse_KeyIdentifier, attributes);
            try {
                createCharactersAndOutputAsEvent(subOutputProcessorChain, Base64.encodeBase64String(x509Certificates[0].getEncoded()));
            } catch (CertificateEncodingException e) {
                throw new WSSecurityException(WSSecurityException.FAILED_SIGNATURE, null, e);
            }
            createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_wsse_KeyIdentifier);
        } else if (getSecurityProperties().getSignatureKeyIdentifierType() == Constants.KeyIdentifierType.THUMBPRINT_IDENTIFIER) {

            attributes = new HashMap<QName, String>();
            attributes.put(Constants.ATT_NULL_EncodingType, Constants.SOAPMESSAGE_NS10_BASE64_ENCODING);
            attributes.put(Constants.ATT_NULL_ValueType, Constants.NS_THUMBPRINT);
            createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_wsse_KeyIdentifier, attributes);
            try {
                MessageDigest sha = null;
                sha = MessageDigest.getInstance("SHA-1");
                sha.reset();
                sha.update(x509Certificates[0].getEncoded());
                byte[] data = sha.digest();

                createCharactersAndOutputAsEvent(subOutputProcessorChain, Base64.encodeBase64String(data));
            } catch (CertificateEncodingException e) {
                throw new WSSecurityException(WSSecurityException.FAILED_SIGNATURE, null, e);
            } catch (NoSuchAlgorithmException e) {
                throw new WSSecurityException(WSSecurityException.FAILED_SIGNATURE, null, e);
            }
            createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_wsse_KeyIdentifier);
        } else if (getSecurityProperties().getSignatureKeyIdentifierType() == Constants.KeyIdentifierType.BST_EMBEDDED) {

            attributes = new HashMap<QName, String>();
            attributes.put(Constants.ATT_NULL_URI, "#" + certUri);
            if (useSingleCert) {
                attributes.put(Constants.ATT_NULL_ValueType, Constants.NS_X509_V3_TYPE);
            } else {
                attributes.put(Constants.ATT_NULL_ValueType, Constants.NS_X509PKIPathv1);
            }
            createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_wsse_Reference, attributes);

            attributes = new HashMap<QName, String>();
            attributes.put(Constants.ATT_NULL_EncodingType, Constants.SOAPMESSAGE_NS10_BASE64_ENCODING);
            if (useSingleCert) {
                attributes.put(Constants.ATT_NULL_ValueType, Constants.NS_X509_V3_TYPE);
            } else {
                attributes.put(Constants.ATT_NULL_ValueType, Constants.NS_X509PKIPathv1);
            }
            attributes.put(Constants.ATT_wsu_Id, certUri);
            createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_wsse_BinarySecurityToken, attributes);
            try {
                if (useSingleCert) {
                    createCharactersAndOutputAsEvent(subOutputProcessorChain, Base64.encodeBase64String(x509Certificates[0].getEncoded()));
                } else {
                    createCharactersAndOutputAsEvent(subOutputProcessorChain, Base64.encodeBase64String(getSecurityProperties().getSignatureCrypto().getCertificateData(false, x509Certificates)));
                }
            } catch (CertificateEncodingException e) {
                throw new WSSecurityException(WSSecurityException.FAILED_SIGNATURE, null, e);
            }
            createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_wsse_BinarySecurityToken);
            createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_wsse_Reference);
        } else if (getSecurityProperties().getSignatureKeyIdentifierType() == Constants.KeyIdentifierType.BST_DIRECT_REFERENCE) {

            attributes = new HashMap<QName, String>();
            attributes.put(Constants.ATT_NULL_URI, "#" + certUri);
            attributes.put(Constants.ATT_NULL_ValueType, referencedBinarySecurityTokenType.getValueType());
            createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_wsse_Reference, attributes);
            createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_wsse_Reference);
        } else if (getSecurityProperties().getSignatureKeyIdentifierType() == Constants.KeyIdentifierType.USERNAMETOKEN_SIGNED) {
            attributes = new HashMap<QName, String>();
            attributes.put(Constants.ATT_NULL_URI, "#" + securityTokenProvider.getId());
            attributes.put(Constants.ATT_NULL_ValueType, Constants.NS_USERNAMETOKEN_PROFILE_UsernameToken);
            createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_wsse_Reference, attributes);
            createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_wsse_Reference);
        } else {
            throw new WSSecurityException(WSSecurityException.FAILED_SIGNATURE, "unsupportedSecurityToken", new Object[]{getSecurityProperties().getSignatureKeyIdentifierType().name()});
        }

        createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_wsse_SecurityTokenReference);
        createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_KeyInfo);
        createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_dsig_Signature);

        /*
            <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#" Id="Signature-1022834285">
                <ds:SignedInfo>
                    <ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#" />
                    <ds:SignatureMethod Algorithm="http://www.w3.org/2000/09/xmldsig#rsa-sha1" />
                    <ds:Reference URI="#id-1612925417">
                        <ds:Transforms>
                            <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#" />
                        </ds:Transforms>
                        <ds:DigestMethod Algorithm="http://www.w3.org/2000/09/xmldsig#sha1" />
                        <ds:DigestValue>cy/khx5N6UobCJ1EbX+qnrGID2U=</ds:DigestValue>
                    </ds:Reference>
                    <ds:Reference URI="#Timestamp-1106985890">
                        <ds:Transforms>
                            <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#" />
                        </ds:Transforms>
                        <ds:DigestMethod Algorithm="http://www.w3.org/2000/09/xmldsig#sha1" />
                        <ds:DigestValue>+p5YRII6uvUdsJ7XLKkWx1CBewE=</ds:DigestValue>
                    </ds:Reference>
                </ds:SignedInfo>
                <ds:SignatureValue>
                    Izg1FlI9oa4gOon2vTXi7V0EpiyCUazECVGYflbXq7/3GF8ThKGDMpush/fo1I2NVjEFTfmT2WP/
                    +ZG5N2jASFptrcGbsqmuLE5JbxUP1TVKb9SigKYcOQJJ8klzmVfPXnSiRZmIU+DUT2UXopWnGNFL
                    TwY0Uxja4ZuI6U8m8Tg=
                </ds:SignatureValue>
                <ds:KeyInfo Id="KeyId-1043455692">
                    <wsse:SecurityTokenReference xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd" wsu:Id="STRId-1008354042">
                        <wsse:Reference xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" URI="#CertId-3458500" ValueType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3" />
                    </wsse:SecurityTokenReference>
                </ds:KeyInfo>
            </ds:Signature>
        */

        SignatureValueSecurityEvent signatureValueSecurityEvent = new SignatureValueSecurityEvent(SecurityEvent.Event.SignatureValue);
        signatureValueSecurityEvent.setSignatureValue(signatureValue);
        outputProcessorChain.getSecurityContext().registerSecurityEvent(signatureValueSecurityEvent);
    }

    class SignedInfoProcessor extends AbstractOutputProcessor {

        private SignerOutputStream signerOutputStream;
        private OutputStream bufferedSignerOutputStream;
        private Transformer transformer;

        SignedInfoProcessor(SecurityProperties securityProperties, SignatureAlgorithm signatureAlgorithm) throws WSSecurityException {
            super(securityProperties);
            this.getAfterProcessors().add(SignatureEndingOutputProcessor.class.getName());

            signerOutputStream = new SignerOutputStream(signatureAlgorithm);
            bufferedSignerOutputStream = new BufferedOutputStream(signerOutputStream);

            Class<Transformer> transformerClass = TransformerAlgorithmMapper.getTransformerClass(getSecurityProperties().getSignatureCanonicalizationAlgorithm());
            try {
                if (Canonicalizer20010315Transformer.class.isAssignableFrom(transformerClass)) {
                    Constructor<Transformer> constructor = transformerClass.getConstructor(String.class);
                    transformer = constructor.newInstance((String) null);
                } else {
                    transformer = transformerClass.newInstance();
                }
            } catch (NoSuchMethodException e) {
                throw new WSSecurityException(WSSecurityException.FAILED_SIGNATURE, null, e);
            } catch (InstantiationException e) {
                throw new WSSecurityException(WSSecurityException.FAILED_SIGNATURE, null, e);
            } catch (IllegalAccessException e) {
                throw new WSSecurityException(WSSecurityException.FAILED_SIGNATURE, null, e);
            } catch (InvocationTargetException e) {
                throw new WSSecurityException(WSSecurityException.FAILED_SIGNATURE, null, e);
            }
        }

        public byte[] getSignatureValue() throws WSSecurityException {
            try {
                bufferedSignerOutputStream.close();
                return signerOutputStream.sign();
            } catch (IOException e) {
                throw new WSSecurityException(WSSecurityException.FAILED_SIGNATURE, null, e);
            }
        }

        @Override
        public void processEvent(XMLEvent xmlEvent, OutputProcessorChain outputProcessorChain) throws XMLStreamException, WSSecurityException {
            transformer.transform(xmlEvent, bufferedSignerOutputStream);
            outputProcessorChain.processEvent(xmlEvent);
        }
    }
}
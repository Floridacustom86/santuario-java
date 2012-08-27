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
package org.apache.xml.security.stax.ext;

import org.apache.commons.codec.binary.Base64;
import org.apache.xml.security.keys.content.x509.XMLX509SKI;
import org.apache.xml.security.stax.config.TransformerAlgorithmMapper;
import org.apache.xml.security.stax.ext.stax.XMLSecAttribute;
import org.apache.xml.security.stax.ext.stax.XMLSecEvent;
import org.apache.xml.security.stax.impl.algorithms.ECDSAUtils;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author $Author$
 * @version $Revision$ $Date$
 */
public class XMLSecurityUtils {

    protected XMLSecurityUtils() {
    }

    /**
     * Returns the Id reference without the leading #
     *
     * @param reference The reference on which to drop the #
     * @return The reference without a leading #
     */
    public static String dropReferenceMarker(String reference) {
        if (reference.startsWith("#")) {
            return reference.substring(1);
        }
        return reference;
    }

    /**
     * Returns the XMLEvent type in String form
     *
     * @param xmlSecEvent
     * @return The XMLEvent type as string representation
     */
    public static String getXMLEventAsString(XMLSecEvent xmlSecEvent) {
        int eventType = xmlSecEvent.getEventType();

        switch (eventType) {
            case XMLSecEvent.START_ELEMENT:
                return "START_ELEMENT";
            case XMLSecEvent.END_ELEMENT:
                return "END_ELEMENT";
            case XMLSecEvent.PROCESSING_INSTRUCTION:
                return "PROCESSING_INSTRUCTION";
            case XMLSecEvent.CHARACTERS:
                return "CHARACTERS";
            case XMLSecEvent.COMMENT:
                return "COMMENT";
            case XMLSecEvent.START_DOCUMENT:
                return "START_DOCUMENT";
            case XMLSecEvent.END_DOCUMENT:
                return "END_DOCUMENT";
            case XMLSecEvent.ATTRIBUTE:
                return "ATTRIBUTE";
            case XMLSecEvent.DTD:
                return "DTD";
            case XMLSecEvent.NAMESPACE:
                return "NAMESPACE";
            default:
                throw new IllegalArgumentException("Illegal XMLSecEvent received: " + eventType);
        }
    }

    /**
     * Executes the Callback handling. Typically used to fetch passwords
     *
     * @param callbackHandler
     * @param callback
     * @throws XMLSecurityException if the callback couldn't be executed
     */
    public static void doPasswordCallback(CallbackHandler callbackHandler, Callback callback) throws XMLSecurityException {
        if (callbackHandler == null) {
            throw new XMLSecurityException(XMLSecurityException.ErrorCode.FAILURE, "noCallback");
        }
        try {
            callbackHandler.handle(new Callback[]{callback});
        } catch (IOException e) {
            throw new XMLSecurityException(XMLSecurityException.ErrorCode.FAILURE, e);
        } catch (UnsupportedCallbackException e) {
            throw new XMLSecurityException(XMLSecurityException.ErrorCode.FAILURE, e);
        }
    }

    /**
     * Try to get the secret key from a CallbackHandler implementation
     *
     * @param callbackHandler a CallbackHandler implementation
     * @return An array of bytes corresponding to the secret key (can be null)
     * @throws XMLSecurityException
     */
    public static void doSecretKeyCallback(CallbackHandler callbackHandler, Callback callback, String id) throws XMLSecurityException {
        if (callbackHandler != null) {
            try {
                callbackHandler.handle(new Callback[]{callback});
            } catch (IOException e) {
                throw new XMLSecurityException(XMLSecurityException.ErrorCode.FAILURE, "noPassword", e);
            } catch (UnsupportedCallbackException e) {
                throw new XMLSecurityException(XMLSecurityException.ErrorCode.FAILURE, "noPassword", e);
            }
        }
    }

    public static Class loadClass(String className) throws ClassNotFoundException {
        return Thread.currentThread().getContextClassLoader().loadClass(className);
    }

    //todo transformer factory?
    public static Transformer getTransformer(Object methodParameter1, Object methodParameter2, String algorithm, XMLSecurityConstants.DIRECTION direction)
            throws XMLSecurityException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {

        @SuppressWarnings("unchecked")
        Class<Transformer> transformerClass = (Class<Transformer>) TransformerAlgorithmMapper.getTransformerClass(algorithm, direction);
        Transformer childTransformer = transformerClass.newInstance();
        if (methodParameter2 != null) {
            childTransformer.setList((List) methodParameter1);
            childTransformer.setOutputStream((OutputStream) methodParameter2);
        } else {
            childTransformer.setTransformer((Transformer) methodParameter1);
        }
        return childTransformer;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getType(List<Object> objects, Class<T> clazz) {
        for (int i = 0; i < objects.size(); i++) {
            Object o = objects.get(i);
            if (o instanceof JAXBElement) {
                o = ((JAXBElement) o).getValue();
            }
            if (clazz.isAssignableFrom(o.getClass())) {
                return (T) o;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getQNameType(List<Object> objects, QName qName) {
        for (int i = 0; i < objects.size(); i++) {
            Object o = objects.get(i);
            if (o instanceof JAXBElement) {
                JAXBElement jaxbElement = (JAXBElement) o;
                if (jaxbElement.getName().equals(qName)) {
                    return (T) jaxbElement.getValue();
                }
            }
        }
        return null;
    }

    public static String getQNameAttribute(Map<QName, String> attributes, QName qName) {
        return attributes.get(qName);
    }
    
    public static void createKeyValueTokenStructure(AbstractOutputProcessor abstractOutputProcessor,
                                                    OutputProcessorChain outputProcessorChain, X509Certificate[] x509Certificates)
            throws XMLStreamException, XMLSecurityException {

        X509Certificate x509Certificate = x509Certificates[0];
        PublicKey publicKey = x509Certificate.getPublicKey();
        String algorithm = publicKey.getAlgorithm();

        abstractOutputProcessor.createStartElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_KeyValue, true, null);

        if ("RSA".equals(algorithm)) {
            RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;
            abstractOutputProcessor.createStartElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_RSAKeyValue, false, null);
            abstractOutputProcessor.createStartElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_Modulus, false, null);
            abstractOutputProcessor.createCharactersAndOutputAsEvent(outputProcessorChain, new Base64(76, new byte[]{'\n'}).encodeToString(rsaPublicKey.getModulus().toByteArray()));
            abstractOutputProcessor.createEndElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_Modulus);
            abstractOutputProcessor.createStartElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_Exponent, false, null);
            abstractOutputProcessor.createCharactersAndOutputAsEvent(outputProcessorChain, new Base64(76, new byte[]{'\n'}).encodeToString(rsaPublicKey.getPublicExponent().toByteArray()));
            abstractOutputProcessor.createEndElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_Exponent);
            abstractOutputProcessor.createEndElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_RSAKeyValue);
        } else if ("DSA".equals(algorithm)) {
            DSAPublicKey dsaPublicKey = (DSAPublicKey) publicKey;
            BigInteger j = dsaPublicKey.getParams().getP().subtract(BigInteger.ONE).divide(dsaPublicKey.getParams().getQ());
            abstractOutputProcessor.createStartElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_DSAKeyValue, false, null);
            abstractOutputProcessor.createStartElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_P, false, null);
            abstractOutputProcessor.createCharactersAndOutputAsEvent(outputProcessorChain, new Base64(76, new byte[]{'\n'}).encodeToString(dsaPublicKey.getParams().getP().toByteArray()));
            abstractOutputProcessor.createEndElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_P);
            abstractOutputProcessor.createStartElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_Q, false, null);
            abstractOutputProcessor.createCharactersAndOutputAsEvent(outputProcessorChain, new Base64(76, new byte[]{'\n'}).encodeToString(dsaPublicKey.getParams().getQ().toByteArray()));
            abstractOutputProcessor.createEndElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_Q);
            abstractOutputProcessor.createStartElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_G, false, null);
            abstractOutputProcessor.createCharactersAndOutputAsEvent(outputProcessorChain, new Base64(76, new byte[]{'\n'}).encodeToString(dsaPublicKey.getParams().getG().toByteArray()));
            abstractOutputProcessor.createEndElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_G);
            abstractOutputProcessor.createStartElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_Y, false, null);
            abstractOutputProcessor.createCharactersAndOutputAsEvent(outputProcessorChain, new Base64(76, new byte[]{'\n'}).encodeToString(dsaPublicKey.getY().toByteArray()));
            abstractOutputProcessor.createEndElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_Y);
            abstractOutputProcessor.createStartElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_J, false, null);
            abstractOutputProcessor.createCharactersAndOutputAsEvent(outputProcessorChain, new Base64(76, new byte[]{'\n'}).encodeToString(j.toByteArray()));
            abstractOutputProcessor.createEndElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_J);
            abstractOutputProcessor.createEndElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_DSAKeyValue);
        } else if ("EC".equals(algorithm)) {
            ECPublicKey ecPublicKey = (ECPublicKey) publicKey;

            List<XMLSecAttribute> attributes = new ArrayList<XMLSecAttribute>(1);
            attributes.add(abstractOutputProcessor.createAttribute(XMLSecurityConstants.ATT_NULL_URI, "urn:oid:" + ECDSAUtils.getOIDFromPublicKey(ecPublicKey)));
            abstractOutputProcessor.createStartElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig11_ECKeyValue, true, null);
            abstractOutputProcessor.createStartElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig11_NamedCurve, false, attributes);
            abstractOutputProcessor.createEndElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig11_NamedCurve);
            abstractOutputProcessor.createStartElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig11_PublicKey, false, null);
            abstractOutputProcessor.createCharactersAndOutputAsEvent(outputProcessorChain, new Base64(76, new byte[]{'\n'}).encodeToString(ECDSAUtils.encodePoint(ecPublicKey.getW(), ecPublicKey.getParams().getCurve())));
            abstractOutputProcessor.createEndElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig11_PublicKey);
            abstractOutputProcessor.createEndElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig11_ECKeyValue);
        }

        abstractOutputProcessor.createEndElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_KeyValue);
    }
    
    public static void createX509SubjectKeyIdentifierStructure(AbstractOutputProcessor abstractOutputProcessor,
            OutputProcessorChain outputProcessorChain,
            X509Certificate[] x509Certificates)
        throws XMLSecurityException, XMLStreamException {
        // SKI can only be used for a V3 certificate
        if (x509Certificates[0].getVersion() != 3) {
            throw new XMLSecurityException(XMLSecurityException.ErrorCode.FAILED_SIGNATURE, "invalidCertForSKI", x509Certificates[0].getVersion());
        }
        
        abstractOutputProcessor.createStartElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_X509Data, true, null);

        abstractOutputProcessor.createStartElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_X509SKI, false, null);
        byte[] data = null;
        try {
            data = XMLX509SKI.getSKIBytesFromCert(x509Certificates[0]);
        } catch (org.apache.xml.security.exceptions.XMLSecurityException e) {
            throw new XMLSecurityException(XMLSecurityException.ErrorCode.FAILED_SIGNATURE, "invalidCertForSKI", e);
        }
        abstractOutputProcessor.createCharactersAndOutputAsEvent(outputProcessorChain, new Base64(76, new byte[]{'\n'}).encodeToString(data));
        abstractOutputProcessor.createEndElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_X509SKI);
        
        abstractOutputProcessor.createEndElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_X509Data);
    }
    
    public static void createX509CertificateStructure(AbstractOutputProcessor abstractOutputProcessor,
            OutputProcessorChain outputProcessorChain,
            X509Certificate[] x509Certificates)
        throws XMLSecurityException, XMLStreamException {
        
        abstractOutputProcessor.createStartElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_X509Data, true, null);

        abstractOutputProcessor.createStartElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_X509Certificate, false, null);
        byte[] data;
        try {
            data = x509Certificates[0].getEncoded();
        } catch (CertificateEncodingException e) {
            throw new XMLSecurityException(XMLSecurityException.ErrorCode.FAILED_SIGNATURE, "invalidCertForSKI", e);
        }
        abstractOutputProcessor.createCharactersAndOutputAsEvent(outputProcessorChain, new Base64(76, new byte[]{'\n'}).encodeToString(data));
        abstractOutputProcessor.createEndElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_X509Certificate);
        
        abstractOutputProcessor.createEndElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_X509Data);
    }
    
    public static void createX509SubjectNameStructure(AbstractOutputProcessor abstractOutputProcessor,
            OutputProcessorChain outputProcessorChain,
            X509Certificate[] x509Certificates)
        throws XMLSecurityException, XMLStreamException {
        
        abstractOutputProcessor.createStartElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_X509Data, true, null);

        abstractOutputProcessor.createStartElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_X509SubjectName, false, null);
        String subjectName = x509Certificates[0].getSubjectX500Principal().getName();
        abstractOutputProcessor.createCharactersAndOutputAsEvent(outputProcessorChain, subjectName);
        abstractOutputProcessor.createEndElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_X509SubjectName);
        
        abstractOutputProcessor.createEndElementAndOutputAsEvent(outputProcessorChain, XMLSecurityConstants.TAG_dsig_X509Data);
    }
}
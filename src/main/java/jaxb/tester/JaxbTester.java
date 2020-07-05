/*
 * Copyright 2020 salesforce.com, inc.
 * All Rights Reserved
 * Company Confidential
 */

package jaxb.tester;

import java.io.*;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.bind.*;
import javax.xml.bind.annotation.*;
import javax.xml.bind.util.ValidationEventCollector;
import javax.xml.parsers.*;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.sun.org.apache.xerces.internal.util.NamespaceSupport;


public class JaxbTester {
    public static void main(String[] args) throws JAXBException, SAXException, FileNotFoundException, ParserConfigurationException {
        JAXBContext jaxbContext = JAXBContext.newInstance(Flexipage.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

        ValidationEventCollector validator = setupSchemaValidation(unmarshaller);
        Object obj = unmarshaller.unmarshal(getSaxSource(JaxbTester.class.getResourceAsStream("/sample.xml")));
        Flexipage flexipage = (Flexipage) obj;
        System.out.println("Unmarshalled object toString(): " + flexipage);

        if (validator.hasEvents()) {
            StringBuilder message = new StringBuilder();
            for (ValidationEvent evt : validator.getEvents()) {
                message.append(String.format("[%d:%d] %s\n", evt.getLocator().getLineNumber(), evt.getLocator().getColumnNumber(), evt.getMessage()));
            }
            System.out.println("Validation error: " + message.toString());;
        }

        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.marshal(flexipage, System.out);
    }

    private static ValidationEventCollector setupSchemaValidation(Unmarshaller unmarshaller) throws SAXException,
            JAXBException, ParserConfigurationException {
        final InputStream xsdStream = JaxbTester.class.getResourceAsStream("/schema.xsd");
        final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        final Schema schema = factory.newSchema(getSaxSource(xsdStream));
        unmarshaller.setSchema(schema);
        ValidationEventCollector validator = new ValidationEventCollector() {
            @Override
            public boolean handleEvent(ValidationEvent event) {
                super.handleEvent(event);
                return true;
            };
        };
        unmarshaller.setEventHandler(validator);
        return validator;
    }

    private static SAXSource getSaxSource(InputStream inputStream) throws ParserConfigurationException,
            SAXException {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        spf.setValidating(false);

        // Protect against XXE attacks
        // Which is useless because these XML files are all internal, customers can't create them
        spf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        spf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        SAXParser parser = spf.newSAXParser();

        setupNamespaceHandling(parser);

        SAXSource source = new SAXSource(parser.getXMLReader(), new InputSource(inputStream));
        return source;
    }

    private static void setupNamespaceHandling(SAXParser parser) throws SAXException {
        // The "XML" files we parse for flexipages are not fully well formed XML
        // because we do not require that component namespaces be declared within
        // the XML.  For example, this is valid:
        //
        // <region ...>
        //   <sfa:someComponent .../>
        // </region>
        //
        // However, to be well formed XML, an xmlns declaration would have to be present for the "sfa"
        // prefix.  As of now, we choose not to force this since it doesn't really add anything but
        // visual clutter.
        //
        // To deal with this, we make use of a construct that is particular to the Xerces XML parsing
        // engine.  This construct allows me to deal with this scenario by always being able to provide
        // a namespace URI even though it's just made up.
        parser.setProperty("http://apache.org/xml/properties/internal/namespace-context", new NamespaceSupport() {
            @Override
            public String getURI(String prefix) {
                String result = super.getURI(prefix);
                if (null != prefix && prefix.length() > 0 && null == result) {
                    // XML Parsers can be sensitive about un-intern()-ed strings.
                    result = ("urn:salesforce:" + prefix).intern();
                    super.declarePrefix(prefix, result);
                }
                return result;
            }
        });
    }

    @XmlRootElement
    static class Flexipage {
        private PageProperties pageProperties;

        @XmlElement(name = "pageProperties")
        public PageProperties getPageProperties() {
            return pageProperties;
        }

        public void setPageProperties(PageProperties pageProperties) {
            this.pageProperties = pageProperties;
        }

        @Override
        public String toString() {
            return "Flexipage{" + pageProperties +
                   '}';
        }

        private static class PageProperties {
            private List<PageProperty> pageProperties;

//            @XmlElementWrapper(name = "pageProperties")
            @XmlElement(name = "pageProperty")
            public List<PageProperty> getPageProperties() {
                return pageProperties;
            }

            public void setPageProperties(List<PageProperty> pageProperties) {
                this.pageProperties = pageProperties;
            }

            @Override
            public String toString() {
                return "PageProperties{" + pageProperties +
                       '}';
            }

            private static class PageProperty {
                private String name;
                private String value;

                @XmlAttribute(name = "name")
                public String getName() {
                    return name;
                }

                public void setName(String name) {
                    this.name = name;
                }

                @XmlAttribute(name = "value")
                public String getValue() {
                    return value;
                }

                public void setValue(String value) {
                    this.value = value;
                }

                @Override
                public String toString() {
                    return "PageProperty{" +
                           "name='" + name + '\'' +
                           ", value='" + value + '\'' +
                           '}';
                }
            }
        }
    }


}

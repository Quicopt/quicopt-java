// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.io.jaxb;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

import com.quicopt.planner.config.solver.SolverConfig;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.SchemaOutputResolver;
import jakarta.xml.bind.ValidationEvent;
import jakarta.xml.bind.ValidationEventHandler;
import jakarta.xml.bind.ValidationEventLocator;

/**
 * Reads a solver configuration from XML, as {@code SolverConfig.createFromXml*} asks. The elements are the
 * configuration classes' XML names, in their order; the document's namespace, if any, is ignored, so a
 * configuration file written for another namespace reads as it stands. The document is validated against the
 * schema of the configuration classes, so an element they do not know, or a value they cannot read, is refused by
 * name with its line and column.
 */
public final class SolverConfigIO {

    /**
     * The binding of the configuration classes, built once: building it reads every class's annotations and
     * generates their schema.
     *
     * @param context the classes' binding
     * @param schema their schema, generated from the binding
     */
    private record Binding(JAXBContext context, Schema schema) {
    }

    private static volatile Binding binding;

    /**
     * @param reader the XML document
     * @return the configuration it describes
     * @throws XmlSerializationException when the document is not XML, or not a valid solver configuration
     */
    public SolverConfig read(Reader reader) {
        var problems = new FirstProblem();
        try {
            var binding = binding();
            var unmarshaller = binding.context().createUnmarshaller();
            unmarshaller.setSchema(binding.schema());
            unmarshaller.setEventHandler(problems);
            var source = new SAXSource(new NamespaceOverride(secureParser()), new InputSource(reader));
            return unmarshaller.unmarshal(source, SolverConfig.class).getValue();
        } catch (JAXBException | SAXException | ParserConfigurationException | IOException e) {
            throw new XmlSerializationException(problems.describe("Reading the solver configuration failed", e), e);
        }
    }

    /**
     * @return the binding of the configuration classes
     */
    private static Binding binding() throws JAXBException, SAXException, IOException {
        var built = binding;
        if (built == null) {
            synchronized (SolverConfigIO.class) {
                if (binding == null) {
                    var context = JAXBContext.newInstance(SolverConfig.class);
                    binding = new Binding(context, schemaOf(context));
                }
                built = binding;
            }
        }
        return built;
    }

    /**
     * @return the schema that the binding generates from the configuration classes' annotations
     */
    private static Schema schemaOf(JAXBContext context) throws IOException, SAXException {
        var documents = new ArrayList<StringWriter>();
        var names = new ArrayList<String>();
        context.generateSchema(new SchemaOutputResolver() {
            @Override
            public Result createOutput(String namespaceUri, String suggestedFileName) {
                var document = new StringWriter();
                documents.add(document);
                names.add(suggestedFileName);
                var result = new StreamResult(document);
                result.setSystemId(suggestedFileName);
                return result;
            }
        });
        var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newSchema(sources(documents, names));
    }

    /**
     * @return the generated schema documents as sources, each under its suggested name
     */
    private static Source[] sources(List<StringWriter> documents, List<String> names) {
        var sources = new Source[documents.size()];
        for (int i = 0; i < sources.length; i++) {
            sources[i] = new StreamSource(new StringReader(documents.get(i).toString()), names.get(i));
        }
        return sources;
    }

    /**
     * @return a namespace-aware parser that reads no external entities or document type definitions
     */
    private static XMLReader secureParser() throws SAXException, ParserConfigurationException {
        var factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newSAXParser().getXMLReader();
    }

    /**
     * Puts every element into the configuration classes' namespace, whatever namespace the document gives it.
     */
    private static final class NamespaceOverride extends XMLFilterImpl {

        /**
         * @param parent the parser whose events are rewritten
         */
        NamespaceOverride(XMLReader parent) {
            super(parent);
        }

        @Override
        public void startElement(String uri, String localName, String qualifiedName, Attributes attributes)
                throws SAXException {
            super.startElement(SolverConfig.XML_NAMESPACE, localName, localName, attributes);
        }

        @Override
        public void endElement(String uri, String localName, String qualifiedName) throws SAXException {
            super.endElement(SolverConfig.XML_NAMESPACE, localName, localName);
        }
    }

    /**
     * Keeps the first problem the binding reports, such as an unknown element, and stops the reading there.
     */
    private static final class FirstProblem implements ValidationEventHandler {

        private ValidationEvent first;

        @Override
        public boolean handleEvent(ValidationEvent event) {
            if (first == null) {
                first = event;
            }
            return false;
        }

        /**
         * @param failure the exception the reading ended with
         * @return the message with the first problem's place and text appended: the binding's, else the parser's
         */
        String describe(String message, Throwable failure) {
            if (first != null) {
                return message + " at " + place(first.getLocator()) + ": " + first.getMessage();
            }
            for (var cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof SAXParseException parse) {
                    return message + " at line " + parse.getLineNumber() + ", column " + parse.getColumnNumber()
                            + ": " + parse.getMessage();
                }
            }
            return message + ": " + failure.getMessage();
        }

        /**
         * @return where the problem was found, as a line and column when the parser reported them
         */
        private static String place(ValidationEventLocator locator) {
            if (locator == null || locator.getLineNumber() < 0) {
                return "an unknown place";
            }
            return "line " + locator.getLineNumber() + ", column " + locator.getColumnNumber();
        }
    }
}

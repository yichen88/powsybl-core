/**
 * Copyright (c) 2016, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.xml;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.IdentifiableAdder;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.Properties;

/**
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
abstract class AbstractIdentifiableXml<T extends Identifiable, A extends IdentifiableAdder<A>, P extends Identifiable> implements XmlConstants {

    protected abstract String getRootElementName();

    protected abstract boolean hasSubElements(T identifiable);

    protected abstract void writeRootElementAttributes(T identifiable, P parent, XmlWriterContext context) throws XMLStreamException;

    protected abstract void writeSubElements(T identifiable, P parent, XmlWriterContext context) throws XMLStreamException;

    final public void write(T identifiable, P parent, XmlWriterContext context) throws XMLStreamException {
        boolean hasSubElements = hasSubElements(identifiable);
        if (hasSubElements || identifiable.hasProperty()) {
            context.getWriter().writeStartElement(IIDM_URI, getRootElementName());
        } else {
            context.getWriter().writeEmptyElement(IIDM_URI, getRootElementName());
        }
        context.getWriter().writeAttribute("id", context.getAnonymizer().anonymizeString(identifiable.getId()));
        if (!identifiable.getId().equals(identifiable.getName())) {
            context.getWriter().writeAttribute("name", context.getAnonymizer().anonymizeString(identifiable.getName()));
        }
        writeRootElementAttributes(identifiable, parent, context);
        if (identifiable.hasProperty()) {
            Properties props = identifiable.getProperties();
            for (String name : props.stringPropertyNames()) {
                String value = props.getProperty(name);
                context.getWriter().writeEmptyElement(IIDM_URI, "property");
                context.getWriter().writeAttribute("name", name);
                context.getWriter().writeAttribute("value", value);
            }
        }
        writeSubElements(identifiable, parent, context);
        if (hasSubElements || identifiable.hasProperty()) {
            context.getWriter().writeEndElement();
        }
    }

    protected void readUntilEndRootElement(XMLStreamReader reader, XmlUtil.XmlEventHandler eventHandler) throws XMLStreamException {
        XmlUtil.readUntilEndElement(getRootElementName(), reader, eventHandler);
    }

    protected abstract A createAdder(P parent);

    protected abstract T readRootElementAttributes(A adder, XmlReaderContext context);

    protected void readSubElements(T identifiable, XmlReaderContext context) throws XMLStreamException {
        if (context.getReader().getLocalName().equals("property")) {
            String name = context.getReader().getAttributeValue(null, "name");
            String value = context.getReader().getAttributeValue(null, "value");
            identifiable.getProperties().put(name, value);
        } else {
            throw new RuntimeException("Unknown element name <" + context.getReader().getLocalName() + "> in <" + identifiable.getId() + ">");
        }
    }

    final public void read(P parent, XmlReaderContext context) throws XMLStreamException {
        A adder = createAdder(parent);
        String id = context.getAnonymizer().deanonymizeString(context.getReader().getAttributeValue(null, "id"));
        String name = context.getAnonymizer().deanonymizeString(context.getReader().getAttributeValue(null, "name"));
        adder.setId(id)
                .setName(name);
        T identifiable = readRootElementAttributes(adder, context);
        readSubElements(identifiable, context);
    }
}

/**
 * Copyright (c) 2016, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.xml;

import com.powsybl.iidm.network.LccConverterStation;
import com.powsybl.iidm.network.LccConverterStationAdder;
import com.powsybl.iidm.network.VoltageLevel;

import javax.xml.stream.XMLStreamException;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Mathieu Bague <mathieu.bague at rte-france.com>
 */
public class LccConverterStationXml extends AbstractConnectableXml<LccConverterStation, LccConverterStationAdder, VoltageLevel> {

    static final LccConverterStationXml INSTANCE = new LccConverterStationXml();

    static final String ROOT_ELEMENT_NAME = "lccConverterStation";

    @Override
    protected String getRootElementName() {
        return ROOT_ELEMENT_NAME;
    }

    @Override
    protected boolean hasSubElements(LccConverterStation cs) {
        return false;
    }

    @Override
    protected void writeRootElementAttributes(LccConverterStation cs, VoltageLevel vl, XmlWriterContext context) throws XMLStreamException {
        XmlUtil.writeFloat("lossFactor", cs.getLossFactor(), context.getWriter());
        XmlUtil.writeFloat("powerFactor", cs.getPowerFactor(), context.getWriter());
        writeNodeOrBus(null, cs.getTerminal(), context);
        writePQ(null, cs.getTerminal(), context.getWriter());
    }

    @Override
    protected void writeSubElements(LccConverterStation cs, VoltageLevel vl, XmlWriterContext context) throws XMLStreamException {
    }

    @Override
    protected LccConverterStationAdder createAdder(VoltageLevel vl) {
        return vl.newLccConverterStation();
    }

    @Override
    protected LccConverterStation readRootElementAttributes(LccConverterStationAdder adder, XmlReaderContext context) {
        float lossFactor = XmlUtil.readFloatAttribute(context.getReader(), "lossFactor");
        float powerFactor = XmlUtil.readOptionalFloatAttribute(context.getReader(), "powerFactor");
        readNodeOrBus(adder, context);
        LccConverterStation cs = adder
                .setLossFactor(lossFactor)
                .setPowerFactor(powerFactor)
                .add();
        readPQ(null, cs.getTerminal(), context.getReader());
        return cs;
    }

    @Override
    protected void readSubElements(LccConverterStation cs, XmlReaderContext context) throws XMLStreamException {
        readUntilEndRootElement(context.getReader(), () -> LccConverterStationXml.super.readSubElements(cs, context));
    }
}

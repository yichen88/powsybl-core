/**
 * Copyright (c) 2016, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.network.impl;

import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.iidm.network.StaticVarCompensatorAdder;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class StaticVarCompensatorAdderImpl extends AbstractInjectionAdder<StaticVarCompensatorAdderImpl> implements StaticVarCompensatorAdder {

    private final VoltageLevelExt vl;

    private float bMin = Float.NaN;

    private float bMax = Float.NaN;

    private float voltageSetPoint = Float.NaN;

    private float reactivePowerSetPoint = Float.NaN;

    private StaticVarCompensator.RegulationMode regulationMode;

    StaticVarCompensatorAdderImpl(VoltageLevelExt vl) {
        this.vl = Objects.requireNonNull(vl);
    }

    @Override
    protected NetworkImpl getNetwork() {
        return vl.getNetwork();
    }

    @Override
    protected String getTypeDescription() {
        return StaticVarCompensatorImpl.TYPE_DESCRIPTION;
    }

    @Override
    public StaticVarCompensatorAdderImpl setBmin(float bMin) {
        this.bMin = bMin;
        return this;
    }

    @Override
    public StaticVarCompensatorAdderImpl setBmax(float bMax) {
        this.bMax = bMax;
        return this;
    }

    @Override
    public StaticVarCompensatorAdderImpl setVoltageSetPoint(float voltageSetPoint) {
        this.voltageSetPoint = voltageSetPoint;
        return this;
    }

    @Override
    public StaticVarCompensatorAdderImpl setReactivePowerSetPoint(float reactivePowerSetPoint) {
        this.reactivePowerSetPoint = reactivePowerSetPoint;
        return this;
    }

    @Override
    public StaticVarCompensatorAdderImpl setRegulationMode(StaticVarCompensator.RegulationMode regulationMode) {
        this.regulationMode = regulationMode;
        return this;
    }

    @Override
    public StaticVarCompensatorImpl add() {
        String id = checkAndGetUniqueId();
        String name = getName();
        TerminalExt terminal = checkAndGetTerminal(id);
        ValidationUtil.checkBmin(this, bMin);
        ValidationUtil.checkBmax(this, bMax);
        ValidationUtil.checkSvcRegulator(this, voltageSetPoint, reactivePowerSetPoint, regulationMode);
        StaticVarCompensatorImpl svc = new StaticVarCompensatorImpl(id, name, bMin, bMax, voltageSetPoint, reactivePowerSetPoint,
                                                                    regulationMode, getNetwork().getRef());
        svc.addTerminal(terminal);
        vl.attach(terminal, false);
        getNetwork().getObjectStore().checkAndAdd(svc);
        getNetwork().getListeners().notifyCreation(svc);
        return svc;
    }

}

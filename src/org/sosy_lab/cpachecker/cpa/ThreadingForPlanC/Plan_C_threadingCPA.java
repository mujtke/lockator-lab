// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.ThreadingForPlanC;

import com.google.common.base.Preconditions;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;

public class Plan_C_threadingCPA extends AbstractCPA {

    @Option(name= "Plan_C_threadingStopOperator", secure = true, description = "used for stop operation for Plan_C_threadingState")
    private StopOperator stopOperator;


    public static CPAFactory factory() {
        return AutomaticCPAFactory.forType(org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingCPA.class);
    }

    public Plan_C_threadingCPA(Configuration config, LogManager pLogger, CFA pCfa) throws InvalidConfigurationException {
        super("sep", "sep", new Plan_C_threadingTransferRelation(config, pCfa, pLogger));
        stopOperator = new Plan_C_threadingStopOperator();
    }

    @Override
    public AbstractState getInitialState(CFANode pNode, StateSpacePartition pPartition) throws InterruptedException {
        Preconditions.checkNotNull(pNode);
        // We create an empty ThreadingState and enter the main function with the first thread.
        // We use the main function's name as thread identifier.
        String mainThread = pNode.getFunctionName();
        return ((Plan_C_threadingTransferRelation) getTransferRelation())
                .addNewThread(new Plan_C_threadingState(), mainThread, Plan_C_threadingState.MIN_THREAD_NUM, mainThread);
    }

    @Override
    public StopOperator getStopOperator() {
        return stopOperator;
    }
}

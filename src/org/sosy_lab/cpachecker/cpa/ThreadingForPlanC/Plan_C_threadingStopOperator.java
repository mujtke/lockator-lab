// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.ThreadingForPlanC;

import my_lab.usage.Plan_C_UsageReachedSet;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.exceptions.CPAException;

import java.util.Collection;
import java.util.Iterator;

public class Plan_C_threadingStopOperator implements StopOperator {

    /**
     * 根据位置覆盖来判断是否需要暂停该位置处的探索
     * @param state
     * @param reached
     * @param precision
     * @return
     * @throws CPAException
     * @throws InterruptedException
     */
    @Override
    public boolean stop(AbstractState state, Collection<AbstractState> reached, Precision precision) throws CPAException, InterruptedException {

        assert state instanceof Plan_C_threadingState;

        boolean shouldStop = true;
        Plan_C_threadingState pState = (Plan_C_threadingState) state;
        Iterable<CFANode> Locs = pState.getLocationNodes();
        Iterator<CFANode> it = Locs.iterator();
        while (it.hasNext()) {
            CFANode p = it.next();
            if (!Plan_C_UsageReachedSet.visitedLocations.contains(p)) {
                shouldStop = false;
                break;
            }
        }
        if (shouldStop) {   // 如果Location覆盖，则将该usageState的locationCovered设置为true
            pState.locationCovered = true;
        }

        return shouldStop;
    }
}

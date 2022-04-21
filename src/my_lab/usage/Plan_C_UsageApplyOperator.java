// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package my_lab.usage;
import org.sosy_lab.cpachecker.core.interfaces.AbstractEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ApplyOperator;
import org.sosy_lab.cpachecker.cpa.usage.UsageState;

public class Plan_C_UsageApplyOperator implements ApplyOperator {

    private final ApplyOperator wrappedApply;

    Plan_C_UsageApplyOperator(ApplyOperator innerOperator) {
        wrappedApply = innerOperator;
    }

    @Override
    public AbstractState apply(AbstractState pState1, AbstractState pState2) {
        UsageState state1 = (UsageState) pState1;
        UsageState state2 = (UsageState) pState2;
        AbstractState wrappedState =
                wrappedApply.apply(state1.getWrappedState(), state2.getWrappedState());

        if (wrappedState == null) {
            return null;
        }

        return state1.copy(wrappedState);
    }

    @Override
    public boolean compatible(AbstractState pState1, AbstractState pState2) {
        return true;
    }

    @Override
    public AbstractState project(AbstractState pParent, AbstractState pChild) {
        UsageState state1 = (UsageState) pParent;
        UsageState state2 = (UsageState) pChild;
        AbstractState wrappedState =
                wrappedApply.project(state1.getWrappedState(), state2.getWrappedState());

        if (wrappedState == null) {
            return null;
        }

        return state1.copy(wrappedState);
    }

    @Override
    public AbstractState project(AbstractState pParent, AbstractState pChild, AbstractEdge pEdge) {
        UsageState state1 = (UsageState) pParent;
        UsageState state2 = (UsageState) pChild;
        AbstractState wrappedState =
                wrappedApply.project(state1.getWrappedState(), state2.getWrappedState(), pEdge);

        if (wrappedState == null) {
            return null;
        }

        return state1.copy(wrappedState);
    }

    @Override
    public boolean isInvariantToEffects(AbstractState pState) {
        UsageState state = (UsageState) pState;
        return wrappedApply.isInvariantToEffects(state.getWrappedState());
    }

    @Override
    public boolean canBeAnythingApplied(AbstractState pState) {
        UsageState state = (UsageState) pState;
        return wrappedApply.canBeAnythingApplied(state.getWrappedState());
    }

}

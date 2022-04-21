// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.ThreadingForPlanC;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingTransferRelatoin.THREAD_JOIN;
import static org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingTransferRelatoin.extractParamName;
import static org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingTransferRelatoin.getLockId;
import static org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingTransferRelatoin.isLastNodeOfThread;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;

import java.util.*;

import com.google.common.collect.Iterables;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentMap;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.AIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithLocations;
import org.sosy_lab.cpachecker.core.interfaces.Graphable;
import org.sosy_lab.cpachecker.core.interfaces.Partitionable;
import org.sosy_lab.cpachecker.core.interfaces.ThreadIdProvider;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackStateEqualsWrapper;
import org.sosy_lab.cpachecker.cpa.location.LocationState;
import org.sosy_lab.cpachecker.cpa.thread.ThreadState;
import org.sosy_lab.cpachecker.cpa.threading.ThreadingTransferRelation;
import org.sosy_lab.cpachecker.cpa.usage.CompatibleNode;
import org.sosy_lab.cpachecker.cpa.usage.CompatibleState;
import org.sosy_lab.cpachecker.exceptions.InvalidQueryException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCodeException;
import org.sosy_lab.cpachecker.util.Pair;

/** This immutable state represents a location state combined with a callstack state. */
public class Plan_C_threadingState implements AbstractState, AbstractStateWithLocations, Graphable,
        Partitionable, AbstractQueryableState, ThreadIdProvider, CompatibleNode {

    private static final String PROPERTY_DEADLOCK = "deadlock";

    final static int MIN_THREAD_NUM = 0;

    // String :: identifier for the thread TODO change to object or memory-location
    // CallstackState +  LocationState :: thread-position
    private final PersistentMap<String, org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState.ThreadState> threads;

    // String :: lock-id  -->  String :: thread-id
    private final PersistentMap<String, String> locks;

    /**
     * 参照ThreadState的内容
     * 用于MHP分析
     */
    public enum ThreadStatus {
        PARENT_THREAD,
        CREATED_THREAD,
        SELF_PARALLEL_THREAD;
    }

    public Map<String, ThreadStatus> threadSet = new HashMap<>();

    public boolean locationCovered = false;

    public String getCurrentThread() {
        return currentThread;
    }

    public  String currentThread = "";
    public final String mainThread = "main";

    public Map<String, ThreadStatus> getThreadSet() {
        return threadSet;
    }

    public Plan_C_threadingState copyWith(String current, Map<String, ThreadStatus> newSet) {
        Plan_C_threadingState state = new Plan_C_threadingState(this.threads, this.locks, this.activeThread, this.threadIdsForWitness);
        state.currentThread = current;
        state.threadSet = newSet;
        return state;
    }

    /**
     * Thread-id of last active thread that produced this exact {@link org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState}. This value
     * should only be set in {@link Plan_C_threadingTransferRelatoin#getAbstractSuccessorsForEdge} and must
     * be deleted in {@link Plan_C_threadingTransferRelatoin#strengthen}, e.g. set to {@code null}. It is not
     * considered to be part of any 'full' abstract state, but serves as intermediate flag to have
     * information for the strengthening process.
     */
    @Nullable private final String activeThread;

    /**
     * This map contains the mapping of threadIds to the unique identifier used for witness
     * validation. Without a witness, it should always be empty.
     */
    private final PersistentMap<String, Integer> threadIdsForWitness;

    public Plan_C_threadingState() {
        this.threads = PathCopyingPersistentTreeMap.of();
        this.locks = PathCopyingPersistentTreeMap.of();
        this.activeThread = null;
        this.threadIdsForWitness = PathCopyingPersistentTreeMap.of();
    }

    private Plan_C_threadingState(
            PersistentMap<String, org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState.ThreadState> pThreads,
            PersistentMap<String, String> pLocks,
            String pActiveThread,
            PersistentMap<String, Integer> pThreadIdsForWitness) {
        this.threads = pThreads;
        this.locks = pLocks;
        this.activeThread = pActiveThread;
        this.threadIdsForWitness = pThreadIdsForWitness;
        // 0419 将currentThread设置为当前边所在的线程
        currentThread = activeThread;
    }

    private org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState withThreads(PersistentMap<String, org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState.ThreadState> pThreads) {
        return new org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState(pThreads, locks, activeThread, threadIdsForWitness);
    }

    private org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState withLocks(PersistentMap<String, String> pLocks) {
        return new org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState(threads, pLocks, activeThread, threadIdsForWitness);
    }

    private org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState withThreadIdsForWitness(
            PersistentMap<String, Integer> pThreadIdsForWitness) {
        return new org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState(threads, locks, activeThread, pThreadIdsForWitness);
    }

    public org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState addThreadAndCopy(String id, int num, AbstractState stack, AbstractState loc) {
        Preconditions.checkNotNull(id);
        Preconditions.checkArgument(!threads.containsKey(id), "thread already exists");
        return withThreads(threads.putAndCopy(id, new org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState.ThreadState(loc, stack, num)));
    }

    public org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState updateLocationAndCopy(String id, AbstractState stack, AbstractState loc) {
        Preconditions.checkNotNull(id);
        Preconditions.checkArgument(threads.containsKey(id), "updating non-existing thread");
        return withThreads(
                threads.putAndCopy(id, new org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState.ThreadState(loc, stack, threads.get(id).getNum())));
    }

    public org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState removeThreadAndCopy(String id) {
        Preconditions.checkNotNull(id);
        checkState(threads.containsKey(id), "leaving non-existing thread: %s", id);
        return withThreads(threads.removeAndCopy(id));
    }

    public Set<String> getThreadIds() {
        return threads.keySet();
    }

    public AbstractState getThreadCallstack(String id) {
        return Preconditions.checkNotNull(threads.get(id).getCallstack());
    }

    public LocationState getThreadLocation(String id) {
        return (LocationState) Preconditions.checkNotNull(threads.get(id).getLocation());
    }

    Set<Integer> getThreadNums() {
        Set<Integer> result = new LinkedHashSet<>();
        for (org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState.ThreadState ts : threads.values()) {
            result.add(ts.getNum());
        }
        Preconditions.checkState(result.size() == threads.size());
        return result;
    }

    int getSmallestMissingThreadNum() {
        int num = MIN_THREAD_NUM;
        // TODO loop is not efficient for big number of threads
        final Set<Integer> threadNums = getThreadNums();
        while(threadNums.contains(num)) {
            num++;
        }
        return num;
    }

    public org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState addLockAndCopy(String threadId, String lockId) {
        Preconditions.checkNotNull(lockId);
        Preconditions.checkNotNull(threadId);
        checkArgument(
                threads.containsKey(threadId),
                "blocking non-existant thread: %s with lock: %s",
                threadId,
                lockId);
        return withLocks(locks.putAndCopy(lockId, threadId));
    }

    public org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState removeLockAndCopy(String threadId, String lockId) {
        Preconditions.checkNotNull(threadId);
        Preconditions.checkNotNull(lockId);
        checkArgument(
                threads.containsKey(threadId),
                "unblocking non-existant thread: %s with lock: %s",
                threadId,
                lockId);
        return withLocks(locks.removeAndCopy(lockId));
    }

    /** returns whether any of the threads has the lock */
    public boolean hasLock(String lockId) {
        return locks.containsKey(lockId); // TODO threadId needed?
    }

    /** returns whether the given thread has the lock */
    public boolean hasLock(String threadId, String lockId) {
        return locks.containsKey(lockId) && threadId.equals(locks.get(lockId));
    }

    /** returns whether there is any lock registered for the thread. */
    public boolean hasLockForThread(String threadId) {
        return locks.containsValue(threadId);
    }

    @Override
    public String toString() {
        return "( threads={\n"
                + Joiner.on(",\n ").withKeyValueSeparator("=").join(threads)
                + "}\n and locks={"
                + Joiner.on(",\n ").withKeyValueSeparator("=").join(locks)
                + "}"
                + (activeThread == null ? "" : ("\n produced from thread " + activeThread))
                + " \n"
                + Joiner.on(",\n ").withKeyValueSeparator("=").join(threadIdsForWitness)
                + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState)) {
            return false;
        }
        org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState ts = (org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState)other;
        return threads.equals(ts.threads)
                && locks.equals(ts.locks)
                && Objects.equals(activeThread, ts.activeThread)
                && threadIdsForWitness.equals(ts.threadIdsForWitness);
    }

    @Override
    public int hashCode() {
        return Objects.hash(threads, locks, activeThread, threadIdsForWitness);
    }

    private FluentIterable<AbstractStateWithLocations> getLocations() {
        return FluentIterable.from(threads.values()).transform(
                s -> (AbstractStateWithLocations) s.getLocation());
    }

    @Override
    public Iterable<CFANode> getLocationNodes() {
        return getLocations().transformAndConcat(AbstractStateWithLocations::getLocationNodes);
    }

    @Override
    public Iterable<CFAEdge> getOutgoingEdges() {
        return getLocations().transformAndConcat(AbstractStateWithLocations::getOutgoingEdges);
    }

    @Override
    public Iterable<CFAEdge> getIngoingEdges() {
        return getLocations().transformAndConcat(AbstractStateWithLocations::getIngoingEdges);
    }

    @Override
    public String toDOTLabel() {
        StringBuilder sb = new StringBuilder();

        sb.append("[");
        Joiner.on(",\n ").withKeyValueSeparator("=").appendTo(sb, threads);
        sb.append("]");

        return sb.toString();
    }

    @Override
    public boolean shouldBeHighlighted() {
        return false;
    }

    @Override
    public Object getPartitionKey() {
        return threads;
    }


    @Override
    public String getCPAName() {
        return "ThreadingCPA";
    }

    @Override
    public boolean checkProperty(String pProperty) throws InvalidQueryException {
        if (PROPERTY_DEADLOCK.equals(pProperty)) {
            try {
                return hasDeadlock();
            } catch (UnrecognizedCodeException e) {
                throw new InvalidQueryException("deadlock-check had a problem", e);
            }
        }
        throw new InvalidQueryException("Query '" + pProperty + "' is invalid.");
    }

    /**
     * check, whether one of the outgoing edges can be visited
     * without requiring a already used lock.
     */
    private boolean hasDeadlock() throws UnrecognizedCodeException {
        FluentIterable<CFAEdge> edges = FluentIterable.from(getOutgoingEdges());

        // no need to check for existing locks after program termination -> ok

        // no need to check for existing locks after thread termination
        // -> TODO what about a missing ATOMIC_LOCK_RELEASE?

        // no need to check VERIFIER_ATOMIC, ATOMIC_LOCK or LOCAL_ACCESS_LOCK,
        // because they cannot cause deadlocks, as there is always one thread to go
        // (=> the thread that has the lock).
        // -> TODO what about a missing ATOMIC_LOCK_RELEASE?

        // no outgoing edges, i.e. program terminates -> no deadlock possible
        if (edges.isEmpty()) {
            return false;
        }

        for (CFAEdge edge : edges) {
            if (!needsAlreadyUsedLock(edge) && !isWaitingForOtherThread(edge)) {
                // edge can be visited, thus there is no deadlock
                return false;
            }
        }

        // if no edge can be visited, there is a deadlock
        return true;
    }

    /** check, if the edge required a lock, that is already used. This might cause a deadlock. */
    private boolean needsAlreadyUsedLock(CFAEdge edge) throws UnrecognizedCodeException {
        final String newLock = getLockId(edge);
        return newLock != null && hasLock(newLock);
    }

    /** A thread might need to wait for another thread, if the other thread joins at
     * the current edge. If the other thread never exits, we have found a deadlock. */
    private boolean isWaitingForOtherThread(CFAEdge edge) throws UnrecognizedCodeException {
        if (edge.getEdgeType() == CFAEdgeType.StatementEdge) {
            AStatement statement = ((AStatementEdge)edge).getStatement();
            if (statement instanceof AFunctionCall) {
                AExpression functionNameExp = ((AFunctionCall)statement).getFunctionCallExpression().getFunctionNameExpression();
                if (functionNameExp instanceof AIdExpression) {
                    final String functionName = ((AIdExpression)functionNameExp).getName();
                    if (THREAD_JOIN.equals(functionName)) {
                        final String joiningThread = extractParamName(statement, 0);
                        // check whether other thread is running and has at least one outgoing edge,
                        // then we have to wait for it.
                        if (threads.containsKey(joiningThread)
                                && !isLastNodeOfThread(getThreadLocation(joiningThread).getLocationNode())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    // 下面两个方法因为继承了CompatibleNode而添加
    @Override
    public boolean cover(CompatibleNode node) {
        return false;
    }

    /**
     * 这个方法暂时按照ThreadState(不是Threading中的ThreadState)中的进行改写
     * 似乎是用于判断是否“相同”
     * @param o
     * @return 如果“相同”则返回0
     */
    @Override
    public int compareTo(CompatibleState o) {

        int result = 0;

        PersistentMap<String, Plan_C_threadingState.ThreadState> thisState = this.threads;
        PersistentMap<String, Plan_C_threadingState.ThreadState> otherState = ((Plan_C_threadingState)o).threads;

        Iterator<Map.Entry<String, Plan_C_threadingState.ThreadState>> thisIt = thisState.entrySet().iterator();
        Iterator<Map.Entry<String, Plan_C_threadingState.ThreadState>> otherIt = otherState.entrySet().iterator();

        while (thisIt.hasNext() && otherIt.hasNext()) {
            Map.Entry<String, Plan_C_threadingState.ThreadState> thisEntry = thisIt.next();
            Map.Entry<String, Plan_C_threadingState.ThreadState> otherEntry = otherIt.next();
            String thisString = thisEntry.getKey();
            String otherString = otherEntry.getKey();
            result = thisString.equals(otherString) ? 0 : 1;
            if (result != 0) {
                return result;
            }
            Plan_C_threadingState.ThreadState thisThreadState = thisEntry.getValue();
            Plan_C_threadingState.ThreadState otherThreadState = otherEntry.getValue();
            result = thisThreadState.callstack.equals(otherThreadState.callstack) ? 0 : 1;
            if (result != 1) {
                return result;
            }

        }

        return result;  // result == 0;
    }

    // 决定两个ThreadingState是否相容，即是否有可能形成竞争 ---> 先采用锁集法

    /**
     *
     * @param state 另一个Plan_C_threadingState
     * @return 返回是否相容，这里是否相容的算法不确定
     */
    @Override
    public boolean isCompatibleWith(CompatibleState otherState) {

        assert otherState instanceof Plan_C_threadingState;
        Plan_C_threadingState other = (Plan_C_threadingState) otherState;
        for (Map.Entry<String, ThreadStatus> entry : threadSet.entrySet()) {
            String thisThread = entry.getKey();
            ThreadStatus thisStatus = entry.getValue();

            if (other.threadSet.containsKey(thisThread)) { // 如果otherState的线程集合中也包含thisThread，这是happen in parallel的前提
                ThreadStatus otherStatus = other.threadSet.get(thisThread);

                // TODO:这里的情况不包含self_parallel_thread
                if ((thisStatus == ThreadStatus.PARENT_THREAD && otherStatus == ThreadStatus.CREATED_THREAD)
                || thisStatus == ThreadStatus.CREATED_THREAD && otherStatus == ThreadStatus.PARENT_THREAD) {
                    return true;
                }
            }
        }

        return false;
    }

    /** 0419 获取当前边所在的函数
     * @param pEdge 当前边
     * @return 当前边所在所在的函数
     * this.getThreadLocation(id).getIngoingEdges()中，如果换成OutgoingEdges会报错
     */
    public String getCurrentFunction(CFAEdge pEdge) {
        final Set<String> activeThreads = new HashSet<>();
        for (String id : this.getThreadIds()) {
            if (Iterables.contains(this.getThreadLocation(id).getIngoingEdges(), pEdge)) {
                activeThreads.add(id);
            }
        }

        assert activeThreads.size() <= 1 : "multiple active threads are not allowed: " + activeThreads;

        return activeThreads.isEmpty() ? null : Iterables.getOnlyElement(activeThreads);
    }

    /** A ThreadState describes the state of a single thread. */
    private static class ThreadState {

        // String :: identifier for the thread TODO change to object or memory-location
        // CallstackState +  LocationState :: thread-position
        private final AbstractState location;
        private final CallstackStateEqualsWrapper callstack;

        // Each thread is assigned to an Integer
        // TODO do we really need this? -> needed for identification of cloned functions.
        private final int num;

        ThreadState(AbstractState pLocation, AbstractState pCallstack, int  pNum) {
            location = pLocation;
            callstack = new CallstackStateEqualsWrapper((CallstackState)pCallstack);
            num= pNum;
        }

        public AbstractState getLocation() {
            return location;
        }

        public AbstractState getCallstack() {
            return callstack.getState();
        }

        public int getNum() {
            return num;
        }

        @Override
        public String toString() {
            return location + " " + callstack + " @@ " + num;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState.ThreadState)) {
                return false;
            }
            org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState.ThreadState other = (org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState.ThreadState)o;
            return location.equals(other.location) && callstack.equals(other.callstack) && num == other.num;
        }

        @Override
        public int hashCode() {
            return Objects.hash(location, callstack, num);
        }
    }

    /** See {@link #activeThread}. */
    public org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState withActiveThread(String pActiveThread) {
        return new org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState(threads, locks, pActiveThread, threadIdsForWitness);
    }

    String getActiveThread() {
        return activeThread;
    }

    @Nullable
    Integer getThreadIdForWitness(String threadId) {
        Preconditions.checkNotNull(threadId);
        return threadIdsForWitness.get(threadId);
    }

    boolean hasWitnessIdForThread(int witnessId) {
        return threadIdsForWitness.containsValue(witnessId);
    }

    org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState setThreadIdForWitness(String threadId, int witnessId) {
        Preconditions.checkNotNull(threadId);
        Preconditions.checkArgument(
                !threadIdsForWitness.containsKey(threadId), "threadId already exists");
        Preconditions.checkArgument(
                !threadIdsForWitness.containsValue(witnessId), "witnessId already exists");
        return withThreadIdsForWitness(threadIdsForWitness.putAndCopy(threadId, witnessId));
    }

    org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState removeThreadIdForWitness(String threadId) {
        Preconditions.checkNotNull(threadId);
        checkArgument(
                threadIdsForWitness.containsKey(threadId), "removing non-existant thread: %s", threadId);
        return withThreadIdsForWitness(threadIdsForWitness.removeAndCopy(threadId));
    }

    @Override
    public String getThreadIdForEdge(CFAEdge pEdge) {
        for (String threadId : getThreadIds()) {
            if (getThreadLocation(threadId).getLocationNode().equals(pEdge.getPredecessor())) {
                return threadId;
            }
        }
        return "";
    }

    @Override
    public Optional<Pair<String, String>>
    getSpawnedThreadIdByEdge(CFAEdge pEdge, ThreadIdProvider pSuccessor) {
        org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState succThreadingState = (org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState) pSuccessor;
        String calledFunctionName = null;

        if (pEdge.getEdgeType() == CFAEdgeType.StatementEdge) {
            AStatement statement = ((AStatementEdge) pEdge).getStatement();
            if (statement instanceof AFunctionCall) {
                AExpression functionNameExp =
                        ((AFunctionCall) statement).getFunctionCallExpression().getFunctionNameExpression();
                if (functionNameExp instanceof AIdExpression) {
                    final String functionName = ((AIdExpression) functionNameExp).getName();
                    switch (functionName) {
                        case ThreadingTransferRelation.THREAD_START: {
                            for (String threadId : succThreadingState.getThreadIds()) {
                                if (!getThreadIds().contains(threadId)) {
                                    // we found the new created thread-id. we assume there is only 'one' match
                                    calledFunctionName =
                                            succThreadingState.getThreadLocation(threadId)
                                                    .getLocationNode()
                                                    .getFunctionName();
                                    return Optional.of(Pair.of(threadId, calledFunctionName));
                                }
                            }
                            break;
                        }
                        default:
                            // nothing to do
                    }
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public CompatibleNode getCompatibleNode() {
        return CompatibleNode.super.getCompatibleNode();
    }
}

/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2019  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.sosy_lab.cpachecker.cpa.threadmodular;

import com.google.common.collect.Iterables;

import java.util.*;
import java.util.Map.Entry;

import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.defaults.EmptyEdge;
import org.sosy_lab.cpachecker.core.defaults.WrapperCFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithLocations;
import org.sosy_lab.cpachecker.core.interfaces.ApplyOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.reachedset.ThreadModularReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;

@Options(prefix = "cpa.threadmodular")
public class ThreadModularTransferRelation implements TransferRelation {

  @Option(secure = true, description = "apply projections only if the state is relevant")
  private boolean relevanceOptimization = true;

  private final TransferRelation wrappedTransfer;
  private final ThreadModularStatistics stats;
  private final ShutdownNotifier shutdownNotifier;
  private final ApplyOperator applyOperator;

  public ThreadModularTransferRelation(
      TransferRelation pTransferRelation,
      ThreadModularStatistics pStats,
      ShutdownNotifier pShutdownNotifier,
      ApplyOperator pApplyOperator,
      Configuration pConfig)
      throws InvalidConfigurationException {

    pConfig.inject(this);
    wrappedTransfer = pTransferRelation;
    stats = pStats;
    shutdownNotifier = pShutdownNotifier;
    applyOperator = pApplyOperator;
  }

  @Override
  public Collection<? extends AbstractState>
      getAbstractSuccessors(
          AbstractState pState,
          UnmodifiableReachedSet pReached,
          Precision pPrecision)
          throws CPATransferException, InterruptedException {

    stats.totalTransfer.start();
//    if(!pState.toString().contains("main:{}")){
//      System.out.println(pState + "\n");
//    }

    /**
     * ---------  Part I  -----------
     * 根据pState的具体情况进行相应的apply操作
     * ------------------------------
     * 可达集reached中，有一部分是transition，有一部分是projection（两者在组成上是一样的，都是ARGState，不过不同之处在于transition或者projection中的LocationState含有edge）
     * 即transition中的edge是通过LocationStateWithEdge来体现的
     * 因此需要判断pState是transition还是projection，来进行之后的apply操作，apply操作之后得到新的transition集合toAdd，相当于这是线程之间相互影响的产物，并不是严格意义上的后继获取
     * 注意apply操作之后得到的transition的父节点是当前状态pState
     * 2019文献中是先计算后继，这里先计算的是线程之间的影响，即apply操作
     * 或许当获取到第一个state的时候，还没有投影，所以就自动跳过这一部分的操作，之后扩展后继，下一次运行到此处时才开始进行apply操作
     * 所以还是先计算的后继？
     */
    Map<AbstractState, Precision> toAdd = new TreeMap<>();        // transitions

    boolean isProjection = ((AbstractStateWithEdge) pState).isProjection();       //可达集中有一部分transition是投影，这些transition不会出现在ARG中
    // do not need stop and merge as they has been already performed on projections

    if (isProjection || !relevanceOptimization || isRelevant(pState)) {

      //打印一下获取到的投影
//      System.out.println(pState.toString());
      stats.allApplyActions.start();
      Collection<AbstractState> toApply =
          ((ThreadModularReachedSet) pReached).getStatesForApply(pState); //如果pState是投影，则返回状态（这里是transitons）集合，若pState不是投影，则返回投影集合projections

      for (AbstractState oldState : toApply) {
        AbstractState appliedState = null;      //应用apply操作之后得到的新状态
        Precision appliedPrecision = null;      //apply的格式：apply（transition，projection）
        if (isProjection) {
             if (!relevanceOptimization || isRelevant(oldState)) {       //isRelevant判断oldState是否可以被投影作用，以及oldState被作用后是否有效果？
              stats.innerApply.start();
              appliedState = applyOperator.apply(oldState, pState);     //当前状态pState为projection，appliedState是投影组合之后的结果
              stats.applyCounter.inc();
              stats.innerApply.stop();
              appliedPrecision = pReached.getPrecision(oldState);
          }
        }
        else {                                                      //当前状态pState不是投影，oldState是投影
          stats.innerApply.start();
          appliedState = applyOperator.apply(pState, oldState);
          stats.applyCounter.inc();
          stats.innerApply.stop();
          appliedPrecision = pPrecision;
        }
        if (appliedState != null) {
          stats.relevantApplyCounter.inc();
          toAdd.put(appliedState, appliedPrecision);
        }
      }
      stats.allApplyActions.stop();
    }

    // Just to statistics
    AbstractStateWithLocations loc =
        AbstractStates.extractStateByType(pState, AbstractStateWithLocations.class);
    if (loc instanceof AbstractStateWithEdge) {
      AbstractEdge edge = ((AbstractStateWithEdge) loc).getAbstractEdge();
      if (edge instanceof WrapperCFAEdge) {
        stats.numberOfTransitionsInThreadConsidered.inc();
      } else if (edge == EmptyEdge.getInstance()) {
        stats.numberOfTransitionsInEnvironmentConsidered.inc();
      }
    }
    ARGState argState = AbstractStates.extractStateByType(pState, ARGState.class);
    if (argState != null) {
      for (ARGState parent : argState.getParents()) {
        if (parent.getAppliedFrom() != null) {
          stats.numberOfValuableTransitionsInEnvironement.inc();
          break;
        }
      }
    }

    List<AbstractState> result = new ArrayList<>();
    Collection<? extends AbstractState> successors;

    /**
     * ---------  Part II  -----------
     * 这一部分计算pState的投影，如果pState不是投影的话
     * ------------------------------
     * 这样一来，2019的文献中Algorithm2是先将后继添加入reached集合中（也就是transitions），然后再计算线程之间的相互影响
     * 而这里的代码似乎是先计算线程之间的相互影响，然后才获取后继并将其加入到reached集合中
     * **/
    if (!isProjection) {                                                                    //这一部分是投影的计算，apply操作中的两个参数，一个是transition一个是projection
      stats.wrappedTransfer.start();
       successors = wrappedTransfer.getAbstractSuccessors(pState, pReached, pPrecision);    //这里实际上没有用到pReached，这里是获取ARG后继
      stats.wrappedTransfer.stop();

      shutdownNotifier.shutdownIfNecessary();

      if (!successors.isEmpty()) {
        for (int i = 0; i < successors.size(); i++) {
          stats.numberOfTransitionsInThreadProduced.inc();
        }
        result.addAll(successors);                                                              //result = result U {e_hat}，A类后继transition，当前PState的直接后继

        stats.projectOperator.start();                                                          //
        // Projection must be independent from child edge, so we may get only one               //
        AbstractState projection =                                                              //emptyEdge的情况下不会产生投影，emptyEdge？
            applyOperator.project(pState, Iterables.getFirst(successors, null));    //pState与后继节点，计算产生投影（与后继节点的原因？）
        if (projection != null) {
          result.add(projection);                                                              // result = result U {e_hat|p}
          stats.numberOfProjectionsProduced.inc();
        }
        stats.projectOperator.stop();
      }
    }

    /**
     * ---------  Part III  -----------
     * 根据apply操作之后更新的transition来产生后继，更新的transition实际上是已有transition的受环境影响版本？
     * --------------------------------
     * toAdd集合中的transition，是线程之间相互影响之后产生的
     * 在计算后继transition时，一部分是pState直接产生的后继，另一部分则是线程间相互作用之后得到的pState的环境影响版本来产生的后继
     * 如果当前transition是projection，那么这里应该是计算该projection对其他transition产生影响之后，得到的受环境影响版本transition的后继
     * 即如果当前状态是projection，那么获取的后继方式是通过其他transition，也就是说获取后继始终只能通过transition
     * 也就是ARG中对应的节点？ ----- 不确定
     */
    stats.envTransfer.start();
    for (Entry<AbstractState, Precision> applied : toAdd.entrySet()) {                         // B类transition？
      successors =
          wrappedTransfer.getAbstractSuccessors(applied.getKey(), pReached, applied.getValue());    //
      result.addAll(successors);                                                                    //
    }
    stats.envTransfer.stop();

    stats.totalTransfer.stop();

    /**
     * ---------  Part IV  -----------
     * my implements
     * 这里在返回result之前，计算一下是否存在潜在的竞争
     * result中的transition两两对比，然后result中的每一个transition和reached中的每一个transition进行对比
     * O(n^2)
     */
    /*
    //result部分
    for(Iterator<AbstractState> it1 = result.iterator(); it1.hasNext(); ){
      AbstractState transitionA = it1.next();
      if(((AbstractStateWithEdge)transitionA).isProjection()) {
        continue;
      }
      for(Iterator<AbstractState> it2 = it1; it2.hasNext(); ){
        AbstractState transitionB = it2.next();
        if(((AbstractStateWithEdge)transitionB).isProjection()) {
          continue;
        }
        //判断transitionA和transitionB是否相容，不相容则报相应的race，不一定真实
        //返回结果也已经对锁集的相交情况进行了处理，认为相容的状态交集应该为空
        //这里的compatible实际上是存在race的情况了，将相容和锁的判断进行了合并
        if(applyOperator.compatible(transitionA, transitionB)) {
          //相容的情况，打印出transitionA和transitionB
          System.out.println("**********************************************************************");
          System.out.println("probably a race:\n");
          System.out.println(transitionA.toString() + "\n" + transitionB.toString());
          System.out.println("**********************************************************************");
        }
        else
          continue;
      }
    }

    //result和reached之间的比较
    for(Iterator<AbstractState> it1 = result.iterator(); it1.hasNext(); ){
      AbstractState transitionA = it1.next();
      if(((AbstractStateWithEdge)transitionA).isProjection()) {
        continue;
      }
      for(Iterator<AbstractState> it2 = ((ThreadModularReachedSet)pReached).getTransitions().iterator(); it2.hasNext(); ){
        AbstractState transitionB = it2.next();
        if(((AbstractStateWithEdge)transitionB).isProjection()) {
          continue;
        }
        //判断transitionA和transitionB是否相容，不相容则报相应的race，不一定真实
        if(applyOperator.compatible(transitionA, transitionB)) {
          //相容的情况，打印出transitionA和transitionB
          System.out.println("**********************************************************************");
          System.out.println("probably a race:\n");
          System.out.println(transitionA.toString() + "\n" + transitionB.toString());
          System.out.println("**********************************************************************");
        }
        else
          continue;
      }
    }
    */

    return result;
  }

  @Override
  public Collection<? extends AbstractState>
      getAbstractSuccessorsForEdge(AbstractState pState, Precision pPrecision, CFAEdge pCfaEdge)
          throws CPATransferException, InterruptedException {
    throw new UnsupportedOperationException(
        "Thread Modular CPA does not support direct transitions with CFA edges");
  }

  @Override
  public Collection<? extends AbstractState>
      getAbstractSuccessors(AbstractState pState, Precision pPrecision)
          throws CPATransferException, InterruptedException {
    throw new UnsupportedOperationException(
        "Thread Modular CPA does not support transitions without reached set");
  }

  private boolean isRelevant(AbstractState pState) {
    return !applyOperator.isInvariantToEffects(pState)
        && applyOperator.canBeAnythingApplied(pState);
  }
}

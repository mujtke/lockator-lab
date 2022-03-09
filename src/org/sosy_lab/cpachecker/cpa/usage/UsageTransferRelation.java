// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage;

import static com.google.common.collect.FluentIterable.from;
import static org.sosy_lab.cpachecker.util.AbstractStates.extractLocation;
import static org.sosy_lab.cpachecker.util.AbstractStates.extractStateByType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperTransferRelation;
import org.sosy_lab.cpachecker.core.defaults.WrapperCFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithLocations;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.WrapperTransferRelation;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackTransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.IdentifierCreator;

@Options(prefix = "cpa.usage")
public class UsageTransferRelation extends AbstractSingleWrapperTransferRelation {

  private final UsageCPAStatistics statistics;

  @Option(description = "functions, which we don't analize", secure = true)
  private Set<String> skippedfunctions = ImmutableSet.of(); //不识别的函数

  @Option(
    description =
        "functions, which are used to bind variables (like list elements are binded to list variable)",
    secure = true
  )
  private Set<String> binderFunctions = ImmutableSet.of();  // 用于将列表元素绑定到列表变量等的函数

  @Option(description = "functions, which are marked as write access",
      secure = true)
  private Set<String> writeAccessFunctions = ImmutableSet.of(); //标记写操作

  @Option(name = "", description = "functions, which stops analysis", secure = true)
  private Set<String> abortFunctions = ImmutableSet.of(); //标志停止分析

  private final CallstackTransferRelation callstackTransfer;

  private final Map<String, BinderFunctionInfo> binderFunctionInfo;
  private final IdentifierCreator creator;

  private final LogManager logger;

  private final boolean bindArgsFunctions;

  public UsageTransferRelation(
      TransferRelation pWrappedTransfer,
      Configuration config,
      LogManager pLogger,
      UsageCPAStatistics s,
      IdentifierCreator c,
      boolean bindArgs)
      throws InvalidConfigurationException {
    super(pWrappedTransfer);
    config.inject(this, UsageTransferRelation.class);
    callstackTransfer =
        ((WrapperTransferRelation) transferRelation)
            .retrieveWrappedTransferRelation(CallstackTransferRelation.class);
    statistics = s;
    logger = pLogger;

    ImmutableMap.Builder<String, BinderFunctionInfo> binderFunctionInfoBuilder =
        ImmutableMap.builder();

    from(binderFunctions)
        .forEach(
            name ->
                binderFunctionInfoBuilder.put(name, new BinderFunctionInfo(name, config, logger)));

    BinderFunctionInfo dummy = new BinderFunctionInfo();
    from(writeAccessFunctions).forEach(name -> binderFunctionInfoBuilder.put(name, dummy));
    binderFunctionInfo = binderFunctionInfoBuilder.build();

    // BindedFunctions should not be analysed
    skippedfunctions = new TreeSet<>(Sets.union(skippedfunctions, binderFunctions));
    creator = c;
    bindArgsFunctions = bindArgs;
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessors(
      AbstractState pElement, Precision pPrecision)
      throws InterruptedException, CPATransferException {

    Collection<AbstractState> results;

    statistics.transferRelationTimer.start();
    CFANode node = extractLocation(pElement);
    results = new ArrayList<>(node.getNumLeavingEdges());

    AbstractStateWithLocations locState =
        extractStateByType(pElement, AbstractStateWithLocations.class);

    if (locState instanceof AbstractStateWithEdge) {      // 对含有边的状态的处理 -----> 这是考虑的
      AbstractEdge edge = ((AbstractStateWithEdge) locState).getAbstractEdge();
      if (edge instanceof WrapperCFAEdge) {               //如果Edge是经过封装的  -----> 这是考虑的
        results.addAll(
            getAbstractSuccessorsForEdge(                 // ---->
                pElement,
                pPrecision,
                ((WrapperCFAEdge) edge).getCFAEdge()));
      } else {
        results.addAll(getAbstractSuccessorForAbstractEdge(pElement, pPrecision));
      }
    } else {                                              //对不含边的状态的处理
      for (int edgeIdx = 0; edgeIdx < node.getNumLeavingEdges(); edgeIdx++) {
        CFAEdge edge = node.getLeavingEdge(edgeIdx);
        results.addAll(getAbstractSuccessorsForEdge(pElement, pPrecision, edge));
      }
    }

    //这里添加了两次？
//    for (int edgeIdx = 0; edgeIdx < node.getNumLeavingEdges(); edgeIdx++) {
//      CFAEdge edge = node.getLeavingEdge(edgeIdx);
//      results.addAll(getAbstractSuccessorsForEdge(pElement, pPrecision, edge));
//    }

    statistics.transferRelationTimer.stop();
    return results;
  }

  private Collection<? extends AbstractState>
      getAbstractSuccessorForAbstractEdge(AbstractState pElement, Precision pPrecision)
          throws CPATransferException, InterruptedException {

    UsageState oldState = (UsageState) pElement;
    AbstractState oldWrappedState = oldState.getWrappedState();
    statistics.innerAnalysisTimer.start();
    Collection<? extends AbstractState> newWrappedStates =
        transferRelation.getAbstractSuccessors(oldWrappedState, pPrecision);
    statistics.innerAnalysisTimer.stop();

    Collection<AbstractState> result = new ArrayList<>();
    for (AbstractState newWrappedState : newWrappedStates) {
      UsageState newState = oldState.copy(newWrappedState);
      result.add(newState);
    }
    return result;
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(        //     ------->
      AbstractState pState, Precision pPrecision, CFAEdge pCfaEdge)
      throws CPATransferException, InterruptedException {

    statistics.transferForEdgeTimer.start();

    UsageState oldState = (UsageState) pState;

    statistics.checkForSkipTimer.start();
    CFAEdge currentEdge = changeIfNeccessary(pCfaEdge); //如果pCfaEdge的调用了abort函数或者skipped函数，则返回空边或者summary边（相当于跳过函数分析）
    statistics.checkForSkipTimer.stop();

    if (currentEdge == null) {  //return <1> -------->如果调用了abort函数，则后继状态为空集
      // Abort function
      statistics.transferForEdgeTimer.stop();
      return ImmutableSet.of();
    }

    statistics.innerAnalysisTimer.start();
    //先根据composite的transferRelation来获取传入的WrappedStates的后继WrappedStates
    Collection<? extends AbstractState> newWrappedStates =
        transferRelation
            .getAbstractSuccessorsForEdge(oldState.getWrappedState(), pPrecision, currentEdge);   //这里的transferRelation是传入的封装的transferRelation，即composite的transferRelation
    statistics.innerAnalysisTimer.stop();

    statistics.bindingTimer.start();
    creator.setCurrentFunction(getCurrentFunction(oldState));   //获取当前的函数调用，oldState的函数调用可能在处理函数调用表达式之后发生了改变（跳过的函数只是分析上跳过，调用栈还是要正常计算？）
    // Function in creator could be changed after handleFunctionCallExpression call

    Collection<? extends AbstractState> result =
        handleEdge(currentEdge, newWrappedStates, oldState);      // handleEdge -----> 根据边的类型来进行不同的处理，重要
    statistics.bindingTimer.stop();

    if (currentEdge != pCfaEdge) {
      callstackTransfer.disableRecursiveContext();
    }
    statistics.transferForEdgeTimer.stop();
    return ImmutableList.copyOf(result);
  }

  private CFAEdge changeIfNeccessary(CFAEdge pCfaEdge) {
    /**
     * 获取边的后继，如果后继是函数调用边
     * case1：调用的函数是abortFunction，则返回NULL
     * case2：...的函数是skippedFunction，则返回summary边，相当于函数被跳过
     * 不过不是函数调用边，则返回传入的边，相当于该边会被用户后面的分析
     */
    if (pCfaEdge.getEdgeType() == CFAEdgeType.FunctionCallEdge) {
      String functionName = pCfaEdge.getSuccessor().getFunctionName();

      if (abortFunctions.contains(functionName)) {
        return null;
      } else if (skippedfunctions.contains(functionName)) {
        CFAEdge newEdge = ((FunctionCallEdge) pCfaEdge).getSummaryEdge();
        logger.log(Level.FINEST, functionName + " will be skipped");
        callstackTransfer.enableRecursiveContext();
        return newEdge;
      }
    }
    return pCfaEdge;
  }


  private Collection<? extends AbstractState>
      handleEdge(
          CFAEdge pCfaEdge,
          Collection<? extends AbstractState> newWrappedStates,
          UsageState oldState)
          throws CPATransferException {

    Collection<AbstractState> result = new ArrayList<>();

    switch (pCfaEdge.getEdgeType()) {
      case StatementEdge:
      case FunctionCallEdge:
        {     //这里应该是对应没有skipped的函数调用

        CStatement stmt;
        if (pCfaEdge.getEdgeType() == CFAEdgeType.StatementEdge) {  //表达式边，应该是类似"a = foo()"之类的
          CStatementEdge statementEdge = (CStatementEdge) pCfaEdge;
          stmt = statementEdge.getStatement();
        } else if (pCfaEdge.getEdgeType() == CFAEdgeType.FunctionCallEdge) {   //函数调用边，应该类似"func()"之类的
          stmt = ((CFunctionCallEdge) pCfaEdge).getRawAST().get();
        } else {
          // Not sure what is it
          break;
        }

        Collection<Pair<AbstractIdentifier, AbstractIdentifier>> newLinks = ImmutableSet.of();

        if (stmt instanceof CFunctionCallAssignmentStatement) {     // 函数调用赋值表达式
          // assignment like "a = b" or "a = foo()"
          CAssignment assignment = (CAssignment) stmt;
          CFunctionCallExpression right =
              ((CFunctionCallAssignmentStatement) stmt).getRightHandSide();   // RHS
          CExpression left = assignment.getLeftHandSide();      // LHS
          newLinks =
              handleFunctionCallExpression(
                  left,
                  right,
                  (pCfaEdge.getEdgeType() == CFAEdgeType.FunctionCallEdge && bindArgsFunctions));   //进行绑定分析
        } else if (stmt instanceof CFunctionCallStatement) {
          /*
           * Body of the function called in StatementEdge will not be analyzed and thus there is no
           * need binding its local variables with its arguments.
           * 如果只是函数调用而没有LSH，则不用进行绑定分析
           */
          newLinks =
              handleFunctionCallExpression(
                  null,
                  ((CFunctionCallStatement) stmt).getFunctionCallExpression(),
                  (pCfaEdge.getEdgeType() == CFAEdgeType.FunctionCallEdge && bindArgsFunctions));
        }

        // Do not know why, but replacing the loop into lambda greatly decreases the speed
        for (AbstractState newWrappedState : newWrappedStates) {  //这里的WrappedState是compositeState，WrappedStates是WrappedStates的数组
          UsageState newState = oldState.copy(newWrappedState);   //其实是求WrappedStates的后继状态（有多个）？

          if (!newLinks.isEmpty()) {
            newState = newState.put(newLinks);
          }

          result.add(newState);
        }
        return ImmutableList.copyOf(result);      //UsageState的内容：stats（数据相关）、variableBindingRelation、WrappedStates（compositeState）
      }

      case FunctionReturnEdge: {
        // Data race detection in recursive calls does not work because of this optimisation
        CFunctionReturnEdge returnEdge = (CFunctionReturnEdge) pCfaEdge;
        String functionName =
            returnEdge.getSummaryEdge()
                .getExpression()
                .getFunctionCallExpression()
                .getDeclaration()
                .getName();
        for (AbstractState newWrappedState : newWrappedStates) {
          UsageState newState = oldState.copy(newWrappedState);
          result.add(newState.removeInternalLinks(functionName));   //去除与局部变量相关的变量绑定
        }
        return ImmutableList.copyOf(result);
      }

      case AssumeEdge:
      case DeclarationEdge:
      case ReturnStatementEdge:
      case BlankEdge:
      case CallToReturnEdge:
        {
          break;
        }

      default:
        throw new UnrecognizedCFAEdgeException(pCfaEdge);
    }
    for (AbstractState newWrappedState : newWrappedStates) {
      result.add(oldState.copy(newWrappedState));
    }
    return ImmutableList.copyOf(result);
  }

  private Collection<Pair<AbstractIdentifier, AbstractIdentifier>> handleFunctionCallExpression(
      final CExpression left,
      final CFunctionCallExpression fcExpression,
      boolean bindArgs) {                                                               // 变量绑定分析

    String functionCallName = fcExpression.getFunctionNameExpression().toASTString();

    if (binderFunctionInfo.containsKey(functionCallName)) {                             //
      BinderFunctionInfo bInfo = binderFunctionInfo.get(functionCallName);

      if (bInfo.shouldBeLinked()) {
        List<CExpression> params = fcExpression.getParameterExpressions();
        // Sometimes these functions are used not only for linkings.
        // For example, getListElement also deletes element.
        // So, if we can't link (no left side), we skip it
        //例如获取list的元素且不会删除元素时，就会执行linked操作，应该是将元素和list名字联系起来（应该对应了LHS存在的情况）。如果不存在LHS，则没有必要执行linked操作
        return bInfo.constructIdentifiers(left, params, creator);
      }
    }

    if (bindArgs) {         //如果不是变量绑定的函数
      if (fcExpression.getDeclaration() == null) {
        logger.log(Level.FINE, "No declaration.");
      } else {
        Collection<Pair<AbstractIdentifier, AbstractIdentifier>> newLinks = new ArrayList<>();
        for (int i = 0; i < fcExpression.getDeclaration().getParameters().size(); i++) {
          if (i >= fcExpression.getParameterExpressions().size()) {
            logger.log(Level.FINE, "More parameters in declaration than in expression.");
            break;
          }

          CSimpleDeclaration exprIn = fcExpression.getDeclaration().getParameters().get(i);
          CExpression exprFrom = fcExpression.getParameterExpressions().get(i);
          if (exprFrom.getExpressionType() instanceof CPointerType) {
            AbstractIdentifier idIn, idFrom;
            idFrom = creator.createIdentifier(exprFrom, 0);
            creator.setCurrentFunction(fcExpression.getFunctionNameExpression().toString());
            idIn = creator.createIdentifier(exprIn, 0);
            newLinks.add(Pair.of(idIn, idFrom));
          }
        }
        return ImmutableSet.copyOf(newLinks);
      }
    }
    return ImmutableSet.of();
  }

  private String getCurrentFunction(UsageState newState) {
    return AbstractStates.extractStateByType(newState, CallstackState.class).getCurrentFunction();
  }

  Map<String, BinderFunctionInfo> getBinderFunctionInfo() {
    return binderFunctionInfo;
  }
}

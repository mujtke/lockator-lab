// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cfa.ast.c;

public interface CLeftHandSideVisitor<R, X extends Exception> {

  R visit(CArraySubscriptExpression pIastArraySubscriptExpression) throws X;    // 数组下标

  R visit(CFieldReference pIastFieldReference) throws X;                        // 字段引用

  R visit(CIdExpression pIastIdExpression) throws X;                            // 标识符表达式？

  R visit(CPointerExpression pointerExpression) throws X;                       // 指针表达式

  R visit(CComplexCastExpression complexCastExpression) throws X;               // 复杂转换表达式
}

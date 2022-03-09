// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cfa.ast.c;


public interface CExpressionVisitor<R, X extends Exception> extends CLeftHandSideVisitor<R, X> {

  R visit(CBinaryExpression pIastBinaryExpression) throws X;                      // 二元表达式

  R visit(CCastExpression pIastCastExpression) throws X;                          // 转换表达式

  R visit(CCharLiteralExpression pIastCharLiteralExpression) throws X;            // 字符

  R visit(CFloatLiteralExpression pIastFloatLiteralExpression) throws X;          // 浮点

  R     visit(CIntegerLiteralExpression pIastIntegerLiteralExpression) throws X;  // 整数

  R visit(CStringLiteralExpression pIastStringLiteralExpression) throws X;        // 字符串

  R visit(CTypeIdExpression pIastTypeIdExpression) throws X;                      // 标识符

  R visit(CUnaryExpression pIastUnaryExpression) throws X;                        // 一元表达式

  R visit (CImaginaryLiteralExpression PIastLiteralExpression) throws X;          //

  R visit(CAddressOfLabelExpression pAddressOfLabelExpression) throws X;          //
}

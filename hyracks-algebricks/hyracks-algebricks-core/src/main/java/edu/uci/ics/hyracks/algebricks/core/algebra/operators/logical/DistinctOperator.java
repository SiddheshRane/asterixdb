/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.mutable.Mutable;

import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalExpressionTag;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalOperatorTag;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalVariable;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.IVariableTypeEnvironment;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.VariableReferenceExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.properties.VariablePropagationPolicy;
import edu.uci.ics.hyracks.algebricks.core.algebra.typing.ITypingContext;
import edu.uci.ics.hyracks.algebricks.core.algebra.typing.NonPropagatingTypeEnvironment;
import edu.uci.ics.hyracks.algebricks.core.algebra.visitors.ILogicalExpressionReferenceTransform;
import edu.uci.ics.hyracks.algebricks.core.algebra.visitors.ILogicalOperatorVisitor;

public class DistinctOperator extends AbstractLogicalOperator {
    private final List<Mutable<ILogicalExpression>> expressions;

    public DistinctOperator(List<Mutable<ILogicalExpression>> expressions) {
        this.expressions = expressions;
    }

    @Override
    public LogicalOperatorTag getOperatorTag() {
        return LogicalOperatorTag.DISTINCT;
    }

    public List<Mutable<ILogicalExpression>> getExpressions() {
        return expressions;
    }

    @Override
    public void recomputeSchema() {
        if (schema == null) {
            schema = new ArrayList<LogicalVariable>();
        }
        schema.clear();
        for (Mutable<ILogicalExpression> eRef : expressions) {
            ILogicalExpression e = eRef.getValue();
            if (e.getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                VariableReferenceExpression v = (VariableReferenceExpression) e;
                schema.add(v.getVariableReference());
            }
        }
    }

    @Override
    public VariablePropagationPolicy getVariablePropagationPolicy() {
        return new VariablePropagationPolicy() {
            @Override
            public void propagateVariables(IOperatorSchema target, IOperatorSchema... sources)
                    throws AlgebricksException {
                for (Mutable<ILogicalExpression> eRef : expressions) {
                    ILogicalExpression e = eRef.getValue();
                    if (e.getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                        VariableReferenceExpression v = (VariableReferenceExpression) e;
                        target.addVariable(v.getVariableReference());
                    }
                }
            }
        };
    }

    @Override
    public boolean acceptExpressionTransform(ILogicalExpressionReferenceTransform visitor) throws AlgebricksException {
        boolean changed = false;
        for (Mutable<ILogicalExpression> e : expressions) {
            if (visitor.transform(e)) {
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public <R, T> R accept(ILogicalOperatorVisitor<R, T> visitor, T arg) throws AlgebricksException {
        return visitor.visitDistinctOperator(this, arg);
    }

    @Override
    public boolean isMap() {
        return false;
    }

    public List<LogicalVariable> getDistinctByVarList() {
        List<LogicalVariable> varList = new ArrayList<LogicalVariable>(expressions.size());
        for (Mutable<ILogicalExpression> eRef : expressions) {
            ILogicalExpression e = eRef.getValue();
            if (e.getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                VariableReferenceExpression v = (VariableReferenceExpression) e;
                varList.add(v.getVariableReference());
            }
        }
        return varList;
    }

    public boolean isDistinctByVar(LogicalVariable var) {
        for (Mutable<ILogicalExpression> eRef : expressions) {
            ILogicalExpression e = eRef.getValue();
            if (e.getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                VariableReferenceExpression v = (VariableReferenceExpression) e;
                if (v.getVariableReference() == var) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public IVariableTypeEnvironment computeOutputTypeEnvironment(ITypingContext ctx) throws AlgebricksException {
        IVariableTypeEnvironment env = new NonPropagatingTypeEnvironment(ctx.getExpressionTypeComputer(), ctx.getMetadataProvider());
        IVariableTypeEnvironment childEnv = ctx.getOutputTypeEnvironment(inputs.get(0).getValue());
        for (Mutable<ILogicalExpression> exprRef : expressions) {
            ILogicalExpression expr = exprRef.getValue();
            if (expr.getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                VariableReferenceExpression varRefExpr = (VariableReferenceExpression) expr;
                env.setVarType(varRefExpr.getVariableReference(), childEnv.getType(expr));
            }
        }
        return env;
    }

}
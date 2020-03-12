/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: Stefan Irimescu, Can Berker Cikis
 *
 */

package org.rumbledb.expressions.postfix;

import org.rumbledb.exceptions.ExceptionMetadata;
import org.rumbledb.exceptions.OurBadException;
import org.rumbledb.expressions.AbstractNodeVisitor;
import org.rumbledb.expressions.Expression;
import org.rumbledb.expressions.Node;

import java.util.ArrayList;
import java.util.List;


public class ArrayLookupExpression extends Expression {

    private Expression mainExpression;
    private Expression lookupExpression;

    public ArrayLookupExpression(Expression mainExpression, Expression lookupExpression, ExceptionMetadata metadata) {
        super(metadata);
        if (mainExpression == null) {
            throw new OurBadException("Main expression cannot be null in a postfix expression.");
        }
        this.mainExpression = mainExpression;
        this.lookupExpression = lookupExpression;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.add(this.mainExpression);
        result.add(this.lookupExpression);
        return result;
    }

    public Expression getLookupExpression() {
        return this.lookupExpression;
    }

    public Expression getMainExpression() {
        return this.mainExpression;
    }

    @Override
    public void initHighestExecutionMode() {
        this.highestExecutionMode = this.mainExpression.getHighestExecutionMode();
    }

    @Override
    public String serializationString(boolean prefix) {
        String result = "(arrayLookup "
            + this.mainExpression.serializationString(false)
            + " [["
            + this.lookupExpression.serializationString(false)
            + "]])";
        return result;
    }

    @Override
    public <T> T accept(AbstractNodeVisitor<T> visitor, T argument) {
        return visitor.visitArrayLookupExpression(this, argument);
    }
}

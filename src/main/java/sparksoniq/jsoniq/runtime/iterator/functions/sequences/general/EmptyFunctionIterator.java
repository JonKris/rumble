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

package sparksoniq.jsoniq.runtime.iterator.functions.sequences.general;

import org.rumbledb.api.Item;
import org.rumbledb.exceptions.ExceptionMetadata;
import org.rumbledb.exceptions.IteratorFlowException;
import sparksoniq.jsoniq.ExecutionMode;
import sparksoniq.jsoniq.item.ItemFactory;
import sparksoniq.jsoniq.runtime.iterator.RuntimeIterator;
import sparksoniq.jsoniq.runtime.iterator.functions.base.LocalFunctionCallIterator;

import java.util.List;

public class EmptyFunctionIterator extends LocalFunctionCallIterator {


    private static final long serialVersionUID = 1L;
    private RuntimeIterator _sequenceIterator;

    public EmptyFunctionIterator(
            List<RuntimeIterator> parameters,
            ExecutionMode executionMode,
            ExceptionMetadata iteratorMetadata
    ) {
        super(parameters, executionMode, iteratorMetadata);
        this._sequenceIterator = this._children.get(0);
    }

    @Override
    public Item next() {
        if (this.hasNext()) {
            this._hasNext = false;
            if (this._sequenceIterator.isRDD()) {
                List<Item> i = this._sequenceIterator.getRDD(this._currentDynamicContextForLocalExecution).take(1);
                return ItemFactory.getInstance().createBooleanItem(i.isEmpty());
            }
            this._sequenceIterator.open(this._currentDynamicContextForLocalExecution);
            if (this._sequenceIterator.hasNext()) {
                this._sequenceIterator.close();
                return ItemFactory.getInstance().createBooleanItem(false);
            } else {
                return ItemFactory.getInstance().createBooleanItem(true);
            }
        }
        throw new IteratorFlowException(FLOW_EXCEPTION_MESSAGE + "empty function", getMetadata());
    }
}

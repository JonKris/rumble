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

package sparksoniq.jsoniq.runtime.iterator.functions;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.rumbledb.api.Item;
import org.rumbledb.exceptions.IteratorFlowException;
import org.rumbledb.exceptions.OurBadException;
import org.rumbledb.exceptions.UnexpectedTypeException;

import sparksoniq.jsoniq.ExecutionMode;
import sparksoniq.jsoniq.item.FunctionItem;
import sparksoniq.jsoniq.runtime.iterator.HybridRuntimeIterator;
import sparksoniq.jsoniq.runtime.iterator.RuntimeIterator;
import sparksoniq.jsoniq.runtime.iterator.functions.base.FunctionIdentifier;
import sparksoniq.jsoniq.runtime.iterator.functions.base.FunctionSignature;
import sparksoniq.jsoniq.runtime.iterator.operational.TypePromotionIterator;
import org.rumbledb.exceptions.ExceptionMetadata;
import sparksoniq.semantics.DynamicContext;
import sparksoniq.semantics.types.SequenceType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static sparksoniq.semantics.types.SequenceType.mostGeneralSequenceType;

public class FunctionItemCallIterator extends HybridRuntimeIterator {

    private static final long serialVersionUID = 1L;
    // parametrized fields
    private FunctionItem _functionItem;
    private List<RuntimeIterator> _functionArguments;

    // calculated fields
    private boolean _isPartialApplication;
    private RuntimeIterator _functionBodyIterator;
    private Item _nextResult;


    public FunctionItemCallIterator(
            FunctionItem functionItem,
            List<RuntimeIterator> functionArguments,
            ExecutionMode executionMode,
            ExceptionMetadata iteratorMetadata
    ) {
        super(null, executionMode, iteratorMetadata);
        for (RuntimeIterator arg : functionArguments) {
            if (arg == null) {
                _isPartialApplication = true;
            } else {
                _children.add(arg);
            }
        }
        _functionItem = functionItem;
        _functionArguments = functionArguments;

    }

    @Override
    public void openLocal() {
        if (_isPartialApplication) {
            _functionBodyIterator = generatePartiallyAppliedFunction(_currentDynamicContextForLocalExecution);
        } else {
            _functionBodyIterator = _functionItem.getBodyIterator();
            _currentDynamicContextForLocalExecution = this.createNewDynamicContextWithArguments(
                _currentDynamicContextForLocalExecution
            );
        }

        _functionBodyIterator.open(_currentDynamicContextForLocalExecution);
        setNextResult();
    }

    private void validateAndReadArguments() {
        String formattedName = (!_functionItem.getIdentifier().getName().equals(""))
            ? _functionItem.getIdentifier().getName() + " "
            : "";
        if (_functionItem.getParameterNames().size() != _functionArguments.size()) {
            throw new UnexpectedTypeException(
                    "Dynamic function "
                        + formattedName
                        + "invoked with incorrect number of arguments. Expected: "
                        + _functionItem.getParameterNames().size()
                        + ", Found: "
                        + _functionArguments.size(),
                    getMetadata()
            );
        }

        if (_functionItem.getSignature().getParameterTypes() != null) {
            for (int i = 0; i < _functionArguments.size(); i++) {
                if (
                    _functionArguments.get(i) != null
                        && !_functionItem.getSignature().getParameterTypes().get(i).equals(mostGeneralSequenceType)
                ) {
                    TypePromotionIterator typePromotionIterator = new TypePromotionIterator(
                            _functionArguments.get(i),
                            _functionItem.getSignature().getParameterTypes().get(i),
                            "Invalid argument for " + formattedName + "function. ",
                            _functionArguments.get(i).getHighestExecutionMode(),
                            getMetadata()
                    );
                    _functionArguments.set(i, typePromotionIterator);
                }
            }
        }
    }

    /**
     * Partial application generates a new function:
     * - Supplied parameters are set as NonLocalVariables
     * - Argument placeholders form the parameters
     *
     * @return FunctionRuntimeIterator that contains the newly generated FunctionItem
     */
    private FunctionRuntimeIterator generatePartiallyAppliedFunction(DynamicContext context) {
        this.validateAndReadArguments();

        String argName;
        RuntimeIterator argIterator;

        Map<String, List<Item>> localArgumentValues = new LinkedHashMap<>(_functionItem.getLocalVariablesInClosure());
        Map<String, JavaRDD<Item>> RDDArgumentValues = new LinkedHashMap<>(_functionItem.getRDDVariablesInClosure());
        Map<String, Dataset<Row>> DFArgumentValues = new LinkedHashMap<>(_functionItem.getDFVariablesInClosure());

        List<String> partialApplicationParamNames = new ArrayList<>();
        List<SequenceType> partialApplicationParamTypes = new ArrayList<>();

        for (int i = 0; i < _functionArguments.size(); i++) {
            argName = _functionItem.getParameterNames().get(i);
            argIterator = _functionArguments.get(i);

            if (argIterator == null) { // == ArgumentPlaceholder
                partialApplicationParamNames.add(argName);
                partialApplicationParamTypes.add(_functionItem.getSignature().getParameterTypes().get(i));
            } else {
                if (argIterator.isDataFrame()) {
                    DFArgumentValues.put(argName, argIterator.getDataFrame(context));
                } else if (argIterator.isRDD()) {
                    RDDArgumentValues.put(argName, argIterator.getRDD(context));
                } else {
                    localArgumentValues.put(argName, argIterator.materialize(context));
                }
            }
        }

        FunctionItem partiallyAppliedFunction = new FunctionItem(
                new FunctionIdentifier("", partialApplicationParamNames.size()),
                partialApplicationParamNames,
                new FunctionSignature(
                        partialApplicationParamTypes,
                        _functionItem.getSignature().getReturnType()
                ),
                _functionItem.getBodyIterator(),
                localArgumentValues,
                RDDArgumentValues,
                DFArgumentValues
        );
        return new FunctionRuntimeIterator(partiallyAppliedFunction, ExecutionMode.LOCAL, getMetadata());
    }

    private DynamicContext createNewDynamicContextWithArguments(DynamicContext context) {
        this.validateAndReadArguments();

        String argName;
        RuntimeIterator argIterator;

        Map<String, List<Item>> localArgumentValues = new LinkedHashMap<>(_functionItem.getLocalVariablesInClosure());
        Map<String, JavaRDD<Item>> RDDArgumentValues = new LinkedHashMap<>(_functionItem.getRDDVariablesInClosure());
        Map<String, Dataset<Row>> DFArgumentValues = new LinkedHashMap<>(_functionItem.getDFVariablesInClosure());

        for (int i = 0; i < _functionArguments.size(); i++) {
            argName = _functionItem.getParameterNames().get(i);
            argIterator = _functionArguments.get(i);

            if (argIterator.isDataFrame()) {
                DFArgumentValues.put(argName, argIterator.getDataFrame(context));
            } else if (argIterator.isRDD()) {
                RDDArgumentValues.put(argName, argIterator.getRDD(context));
            } else {
                localArgumentValues.put(argName, argIterator.materialize(context));
            }
        }
        return new DynamicContext(context, localArgumentValues, RDDArgumentValues, DFArgumentValues);
    }

    @Override
    public Item nextLocal() {
        if (this._hasNext) {
            Item result = _nextResult;
            setNextResult();
            return result;
        }
        throw new IteratorFlowException(
                RuntimeIterator.FLOW_EXCEPTION_MESSAGE
                    + " in "
                    + _functionItem.getIdentifier().getName()
                    + "  function",
                getMetadata()
        );
    }

    @Override
    protected boolean hasNextLocal() {
        return _hasNext;
    }

    @Override
    protected void resetLocal(DynamicContext context) {
        _functionBodyIterator.reset(_currentDynamicContextForLocalExecution);
        setNextResult();
    }

    @Override
    protected void closeLocal() {
        // ensure that recursive function calls terminate gracefully
        // the function call in the body of the deepest recursion call is never visited, never opened and never closed
        if (this.isOpen()) {
            _functionBodyIterator.close();
        }
    }

    public void setNextResult() {
        _nextResult = null;
        if (_functionBodyIterator.hasNext()) {
            _nextResult = _functionBodyIterator.next();
        }

        if (_nextResult == null) {
            this._hasNext = false;
            _functionBodyIterator.close();
        } else {
            this._hasNext = true;
        }
    }

    @Override
    public JavaRDD<Item> getRDDAux(DynamicContext dynamicContext) {
        if (_isPartialApplication) {
            throw new OurBadException(
                    "Unexpected program state reached. Partially applied function calls must be evaluated locally."
            );
        }
        DynamicContext contextWithArguments = dynamicContext;
        _functionBodyIterator = _functionItem.getBodyIterator();
        contextWithArguments = this.createNewDynamicContextWithArguments(contextWithArguments);
        return _functionBodyIterator.getRDD(contextWithArguments);
    }
}
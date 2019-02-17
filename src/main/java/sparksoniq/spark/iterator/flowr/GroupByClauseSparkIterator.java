/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author: Stefan Irimescu
 *
 */
package sparksoniq.spark.iterator.flowr;

import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import sparksoniq.exceptions.IteratorFlowException;
import sparksoniq.exceptions.SparksoniqRuntimeException;
import sparksoniq.jsoniq.compiler.translator.expr.flowr.FLWOR_CLAUSES;
import sparksoniq.jsoniq.runtime.metadata.IteratorMetadata;
import sparksoniq.jsoniq.runtime.tupleiterator.RuntimeTupleIterator;
import sparksoniq.jsoniq.runtime.tupleiterator.SparkRuntimeTupleIterator;
import sparksoniq.jsoniq.tuple.FlworKey;
import sparksoniq.jsoniq.tuple.FlworTuple;
import sparksoniq.semantics.DynamicContext;
import sparksoniq.spark.closures.GroupByLinearizeTupleClosure;
import sparksoniq.spark.closures.GroupByToPairMapClosure;
import sparksoniq.spark.iterator.flowr.base.FlowrClauseSparkIterator;
import sparksoniq.spark.iterator.flowr.expression.GroupByClauseSparkIteratorExpression;

import java.util.List;

public class GroupByClauseSparkIterator extends SparkRuntimeTupleIterator {
    private final List<GroupByClauseSparkIteratorExpression> _variables;

    public GroupByClauseSparkIterator(RuntimeTupleIterator child, List<GroupByClauseSparkIteratorExpression> variables,
                                      IteratorMetadata iteratorMetadata) {
        super(child, iteratorMetadata);
        this._variables = variables;
    }

    @Override
    public JavaRDD<FlworTuple> getRDD(DynamicContext context) {
        if (_rdd == null) {
            _rdd = this._child.getRDD(context);
            //map to pairs - ArrayItem [sort keys] , tuples
            JavaPairRDD<FlworKey, FlworTuple> keyTuplePair = this._rdd
                    .mapToPair(new GroupByToPairMapClosure(_variables));
            //group by key
            JavaPairRDD<FlworKey, Iterable<FlworTuple>> groupedPair =
                    keyTuplePair.groupByKey();
            //linearize iterable tuples into arrays
            this._rdd = groupedPair.map(new GroupByLinearizeTupleClosure(_variables));
        }
        return _rdd;
    }
}

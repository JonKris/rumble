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

package sparksoniq.spark.iterator.function;

import org.apache.spark.api.java.JavaRDD;
import org.rumbledb.api.Item;
import org.rumbledb.items.parsing.JSONSyntaxToItemMapper;
import org.rumbledb.exceptions.CannotRetrieveResourceException;

import sparksoniq.jsoniq.ExecutionMode;
import sparksoniq.jsoniq.runtime.iterator.RDDRuntimeIterator;
import sparksoniq.jsoniq.runtime.iterator.RuntimeIterator;
import org.rumbledb.exceptions.ExceptionMetadata;
import sparksoniq.semantics.DynamicContext;
import sparksoniq.spark.SparkSessionManager;

import java.util.List;

public class JsonFileFunctionIterator extends RDDRuntimeIterator {

    private static final long serialVersionUID = 1L;

    public JsonFileFunctionIterator(
            List<RuntimeIterator> arguments,
            ExecutionMode executionMode,
            ExceptionMetadata iteratorMetadata
    ) {
        super(arguments, executionMode, iteratorMetadata);
    }

    @Override
    public JavaRDD<Item> getRDDAux(DynamicContext context) {
        JavaRDD<String> strings;
        RuntimeIterator urlIterator = this._children.get(0);
        urlIterator.open(context);
        String url = urlIterator.next().getStringValue();
        urlIterator.close();
        if (!UrlValidator.isValid(url)) {
            throw new CannotRetrieveResourceException("File " + url + " not found.", getMetadata());
        }

        if (this._children.size() == 1) {
            strings = SparkSessionManager.getInstance()
                .getJavaSparkContext()
                .textFile(url);
        } else {
            RuntimeIterator partitionsIterator = this._children.get(1);
            partitionsIterator.open(_currentDynamicContextForLocalExecution);
            strings = SparkSessionManager.getInstance()
                .getJavaSparkContext()
                .textFile(
                    url,
                    partitionsIterator.next().getIntegerValue()
                );
            partitionsIterator.close();
        }
        return strings.mapPartitions(new JSONSyntaxToItemMapper(getMetadata()));
    }
}
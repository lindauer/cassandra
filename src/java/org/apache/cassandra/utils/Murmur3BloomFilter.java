/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cassandra.utils;

import java.nio.ByteBuffer;
import java.io.DataOutput;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.io.ICompactSerializer2;
import org.apache.cassandra.utils.obs.OpenBitSet;

public class Murmur3BloomFilter extends BloomFilter
{
    static ICompactSerializer2<BloomFilter> serializer_ = new Murmur3BloomFilterSerializer();
    private static final Logger logger = LoggerFactory.getLogger(Murmur3BloomFilter.class);

    private static final int EXCESS = 20;

    Murmur3BloomFilter(int hashes, OpenBitSet bs)
    {
        super(hashes, bs);
    }

    public static ICompactSerializer2<BloomFilter> serializer()
    {
        return serializer_;
    }

    public void serialize(DataOutput dos) throws IOException
    {
        serializer().serialize(this, dos);
    }

    protected long[] hash(ByteBuffer b, int position, int remaining, long seed)
    {
        return MurmurHash.hash3_x64_128(b, b.position(), b.remaining(), 0L);
    }

    private static OpenBitSet bucketsFor(long numElements, int bucketsPer)
    {
        return new OpenBitSet(numElements * bucketsPer + EXCESS);
    }

    /**
    * @return A BloomFilter with the lowest practical false positive probability
    * for the given number of elements.
    */
    public static BloomFilter getFilter(long numElements, int targetBucketsPerElem)
    {
        int maxBucketsPerElement = Math.max(1, BloomCalculations.maxBucketsPerElement(numElements));
        int bucketsPerElement = Math.min(targetBucketsPerElem, maxBucketsPerElement);
        if (bucketsPerElement < targetBucketsPerElem)
        {
            logger.warn(String.format("Cannot provide an optimal BloomFilter for %d elements (%d/%d buckets per element).",
                                      numElements, bucketsPerElement, targetBucketsPerElem));
        }
        BloomCalculations.BloomSpecification spec = BloomCalculations.computeBloomSpec(bucketsPerElement);
        return new Murmur3BloomFilter(spec.K, bucketsFor(numElements, spec.bucketsPerElement));
    }

    /**
    * @return The smallest BloomFilter that can provide the given false positive
    * probability rate for the given number of elements.
    *
    * Asserts that the given probability can be satisfied using this filter.
    */
    public static BloomFilter getFilter(long numElements, double maxFalsePosProbability)
    {
        assert maxFalsePosProbability <= 1.0 : "Invalid probability";
        int bucketsPerElement = BloomCalculations.maxBucketsPerElement(numElements);
        BloomCalculations.BloomSpecification spec = BloomCalculations.computeBloomSpec(bucketsPerElement, maxFalsePosProbability);
        return new Murmur3BloomFilter(spec.K, bucketsFor(numElements, spec.bucketsPerElement));
    }
}
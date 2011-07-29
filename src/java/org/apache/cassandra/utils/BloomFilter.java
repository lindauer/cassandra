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
import java.util.List;

import org.apache.cassandra.io.ICompactSerializer2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.utils.obs.OpenBitSet;

public class BloomFilter extends Filter
{

    private static final Logger logger = LoggerFactory.getLogger(BloomFilter.class);
    private static final int EXCESS = 20;
    static ICompactSerializer2<BloomFilter> serializer_ = new BloomFilterSerializer();

    public OpenBitSet bitset;

    BloomFilter(int hashes, OpenBitSet bs)
    {
        hashCount = hashes;
        bitset = bs;
    }

    public static BloomFilter emptyFilter()
    {
        return new BloomFilter(0, bucketsFor(0, 0));
    }

    public static ICompactSerializer2<BloomFilter> serializer()
    {
        return serializer_;
    }

    private static OpenBitSet bucketsFor(long numElements, int bucketsPer)
    {
        return new OpenBitSet(numElements * bucketsPer + EXCESS);
    }

    /**
    * @return a bloom filter with the specified number of hashes and bits.
    */
    public static BloomFilter getFilterWithSpec(int hashes, long buckets)
    {
        assert hashes > 0 : "Invalid hash count";
        assert buckets > 0: "Invalid bucket count";
        return new BloomFilter(hashes, new OpenBitSet(buckets));
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
        return new BloomFilter(spec.K, bucketsFor(numElements, spec.bucketsPerElement));
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
        return new BloomFilter(spec.K, bucketsFor(numElements, spec.bucketsPerElement));
    }

    private long buckets()
    {
      return bitset.size();
    }

    private long[] getHashBuckets(ByteBuffer key)
    {
        return BloomFilter.getHashBuckets(key, hashCount, buckets());
    }

    // Murmur is faster than an SHA-based approach and provides as-good collision
    // resistance.  The combinatorial generation approach described in
    // http://www.eecs.harvard.edu/~kirsch/pubs/bbbf/esa06.pdf
    // does prove to work in actual tests, and is obviously faster
    // than performing further iterations of murmur.
    static long[] getHashBuckets(ByteBuffer b, int hashCount, long max)
    {
        long[] result = new long[hashCount];
        long[] hash = MurmurHash.hash3_x64_128(b, b.position(), b.remaining(), 0L);
        long hash1 = hash[0];
        long hash2 = hash[1];
        for (int i = 0; i < hashCount; ++i)
        {
            result[i] = Math.abs((hash1 + (long)i * hash2) % max);
        }
        return result;
    }

    public void add(ByteBuffer key)
    {
        for (long bucketIndex : getHashBuckets(key))
        {
            bitset.set(bucketIndex);
        }
    }

    /**
    * Add a key to the bloom filter and set any changed bits in diff.
    *
    * @return true if there were changes, false otherwise
    */
    public boolean addAndDiff(ByteBuffer key, OpenBitSet diff)
    {
        boolean changed = false;
        for (long bucketIndex : getHashBuckets(key))
        {
            if (!bitset.get(bucketIndex))
            {
                changed = true;
                diff.set(bucketIndex);
                bitset.set(bucketIndex);
            }
        }
        return changed;
    }


    /**
    * Add a key to the bloom filter and add any changed bits to the list.
    *
    * @return true if there were changes, false otherwise
    */
    public boolean addAndDiff(ByteBuffer key, List<Long> changes)
    {
        boolean changed = false;
        for (long bucketIndex : getHashBuckets(key))
        {
            if (!bitset.get(bucketIndex))
            {
                changed = true;
                changes.add(bucketIndex);
                bitset.set(bucketIndex);
            }
        }
        return changed;
    }


    public boolean isPresent(ByteBuffer key)
    {
      for (long bucketIndex : getHashBuckets(key))
      {
          if (!bitset.get(bucketIndex))
          {
              return false;
          }
      }
      return true;
    }

    public void clear()
    {
        bitset.clear(0, bitset.size());
    }

    public int serializedSize()
    {
        return BloomFilterSerializer.serializedSize(this);
    }
}

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

import org.apache.cassandra.utils.obs.OpenBitSet;

public abstract class BloomFilter extends Filter
{

    public OpenBitSet bitset;

    BloomFilter(int hashes, OpenBitSet bs)
    {
        hashCount = hashes;
        bitset = bs;
    }

    public static BloomFilter emptyFilter()
    {
        return Murmur3BloomFilter.emptyFilter();
    }

    public abstract void serialize(DataOutput dos) throws IOException;

    /**
    * @return A BloomFilter with the lowest practical false positive probability
    * for the given number of elements.
    */
    public static BloomFilter getFilter(long numElements, int targetBucketsPerElem)
    {
        logger.debug("Creating bloom filter for {} elements and spec {}", numElements, spec);
        return Murmur3BloomFilter.getFilter(numElements, targetBucketsPerElem);
    }

    /**
    * @return The smallest BloomFilter that can provide the given false positive
    * probability rate for the given number of elements.
    *
    * Asserts that the given probability can be satisfied using this filter.
    */
    public static BloomFilter getFilter(long numElements, double maxFalsePosProbability)
    {
        return Murmur3BloomFilter.getFilter(numElements, maxFalsePosProbability);
    }

    private long buckets()
    {
      return bitset.size();
    }

    private long[] getHashBuckets(ByteBuffer key)
    {
        return getHashBuckets(key, hashCount, buckets());
    }

    protected abstract long[] hash(ByteBuffer b, int position, int remaining, long seed);

    // Murmur is faster than an SHA-based approach and provides as-good collision
    // resistance.  The combinatorial generation approach described in
    // http://www.eecs.harvard.edu/~kirsch/pubs/bbbf/esa06.pdf
    // does prove to work in actual tests, and is obviously faster
    // than performing further iterations of murmur.
    long[] getHashBuckets(ByteBuffer b, int hashCount, long max)
    {
        long[] result = new long[hashCount];
        long[] hash = this.hash(b, b.position(), b.remaining(), 0L);

        for (int i = 0; i < hashCount; ++i)
        {
            result[i] = Math.abs((hash[0] + (long)i * hash[1]) % max);
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

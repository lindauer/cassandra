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

import org.apache.cassandra.io.ICompactSerializer2;
import org.apache.cassandra.utils.obs.OpenBitSet;

public class Murmur2BloomFilter extends BloomFilter
{
    static ICompactSerializer2<BloomFilter> serializer_ = new Murmur2BloomFilterSerializer();

    Murmur2BloomFilter(int hashes, OpenBitSet bs)
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
        long hash1 = MurmurHash.hash64(b, b.position(), b.remaining(), 0L);
        long hash2 = MurmurHash.hash64(b, b.position(), b.remaining(), hash1);
        return(new long[] {hash1, hash2});
    }
}
/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright (c) 1997-2010 Oracle and/or its affiliates. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * 
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 * 
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 * 
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package com.sun.corba.ee.impl.encoding.fast.bytebuffer ;

import java.io.Closeable ;

/** Factory for Allocators (pooled and non-pooled) and SlabPools.
 */
public class AllocatorFactory {
    /** makeAllocator and makePoolAllocator must always have headerSize at least MIN_HEADER_SIZE.
     */
    int MIN_HEADER_SIZE = 8 ;

    private AllocatorFactory() {}

    /** Return an Allocator that allocates from one slab at a time, creating a new slab
     * as needed.  Space is recovered by the garbage collector.
     * This version will create a new slab allocator when available space is exhausted.
     * It will only return null if an attempt is made to allocate a BufferWrapper larger
     * than allocatorSize - headerSize bytes.
     * trim is supported, but only for the last BufferWrapper returned from an allocate call.
     * @param headerSize The size of the reserved header space in an allocated BufferWraper.
     * @param allocatorSize The total size available for all allocate calls in a single Allocator.
     * @param bt The buffer type of the slabs created for this allocator.
     */
    public static Allocator makeAllocator( int headerSize, int allocatorSize, 
        Allocator.BufferType bt ) {
        return new AllocatorImpl( headerSize, allocatorSize, bt ) ;
    }

    /** Create an Allocator that allocates from a pool of slabs.  All space is managed by
     * the Slabs and SlabPool, and Slabs are recycled once they have been completely freed.
     * @param headerSize The size of the reserved header space in an allocated BufferWraper.
     * @param pool The SlabPool to use for Slabs for this Allocator.
     */
    public static Allocator makePoolAllocator( int headerSize, SlabPool pool ) {
        return new AllocatorPoolImpl( headerSize, pool ) ;
    }

    /** Obtain useful statistics about the SlabPool.
     */
    public interface SlabPool extends Closeable {
        /** The maxAllocationSize of every Slab in this pool.
         */
        int maxAllocationSize() ;

        /** The BufferType of every Slab in this pool.
         */
        Allocator.BufferType bufferType() ;

        /** Number of free slabs available for use in Allocators using this SlabPool.
         */
        int numFreeSlabs() ;

        /** Number of slabs currently in use by Allocators.
         */
        int numPartialSlabs() ;

        /** Number of full slabs which still have allocations in use.
         * Full slabs become empty as soon as all of their allocations have
         * been disposed.
         */
        int numFullSlabs() ;

        /** Minimum size of pool, as specified when the SlabPool was created.  
         */
        long minSize() ;

        /** Maximum size to which pool may expand as specified when the SlabPool was created.  
         */
        long maxSize() ;

        /** Current free space in pool.
         * freeSpace() + allocatedSpace() is the total size of the pool.  This may at times
         * temporarily exceed maxSize, but excess space will be released from the pool
         * when it is freed and the total size is bigger than the maximum size.
         */
        long freeSpace() ;

        /** Total bytes disposed but not yet available for use.  This is always less
         * than allocatedSpace.
         */
        long unavailableSpace() ;

        /** Total bytes allocated.  This includes unavailableSpace().  
         * allocatedSpace() - unavailableSpace() is the space still actively in use
         * by clients of the SlabPool.
         */
        long allocatedSpace() ;
    }

    /** Create a SlabPool from which Allocators may be created.
     * @param maxAllocationSize The maximum size that can be satisfied by a call to Allocator.allocate().
     * @param minSize The minimum size of the pool in bytes.
     * @param maxSize The maximum size to which a pool may grow.
     * @param by The buffer type used in this pool.
     */
    public SlabPool makeSlabPool( int maxAllocationSize, long minSize, long maxSize, 
        Allocator.BufferType bt ) {

        return new SlabPoolImpl( maxAllocationSize, minSize, maxSize, bt ) ;
    }
}

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
package com.sun.corba.se.impl.encoding.fast.bytebuffer ;

import java.nio.ByteBuffer ;

public class Slab {
    private static final boolean DEBUG = true;
    private static long totalAllocated = 0 ;
    private static long reclaimed = 0 ;

    protected void finalize() {
        reclaimed += space.capacity() ;
    }

    private void msg( String msg ) {
        System.out.println( "Slab: " + msg ) ;
    }

    // A Slab is used only by an Allocator, so questions of concurrent 
    // access should be handled at the Allocator level.
    public enum State { EMPTY, PARTIAL, FULL } ;

    private ByteBuffer space ;
    private int sizeDisposed ;

    // Only for use in BufferWrapper.
    // Note that the implementation is exactly the same as
    // sizeAllocated.  However, the semantics are different:
    // currentPosition is the allocation pointer at the time of
    // an allocate call, which sizeAllocated is the total space
    // allocated.  If the implementation changes, we still want
    // to preserve the semantics.
    public int currentPosition() {
        return space.position() ;
    }

    public State getState() {
        if (sizeAllocated() == 0) {
            return State.EMPTY ;
        } else if (sizeAvailable() == 0) {
            return State.FULL ;
        } else {
            return State.PARTIAL ;
        }
    }

    public int totalSize() {
        return space.capacity() ;
    }

    public int sizeAvailable() {
        return space.remaining() ;
    }

    public int sizeAllocated() {
        return space.position() ;
    }

    public int sizeDisposed() {
        return sizeDisposed ;
    }

    public void markFull() {
        ByteBuffer result = allocate( sizeAvailable() ) ;
        dispose( result ) ;
    }

    public void markEmpty() {
        space.position(0) ;
    }

    public Slab( int size, Allocator.BufferType bt ) {
        if (false) {
            msg( "Constructor: allocating " + size + " for BufferType " + bt ) ;
        }

        totalAllocated += size ;
        try {
            if (bt == Allocator.BufferType.DIRECT) {
                space = ByteBuffer.allocateDirect( size ) ;
            } else {
                space = ByteBuffer.allocate( size ) ;
            }

            initialize() ;
        } catch (Error err ) {
            if (DEBUG) 
                msg( "Error " + err + " in constructor: size = " + size 
                    + " BufferType = " + bt 
                    + " totalAllocated = " + totalAllocated
                    + " reclaimed = " + reclaimed ) ;
            throw err ;
        } catch (RuntimeException exc) {
            if (DEBUG) 
                msg( "RuntimeException " + exc + " in constructor: size = " + size 
                    + " BufferType = " + bt 
                    + " totalAllocated = " + totalAllocated
                    + " reclaimed = " + reclaimed ) ;
            throw exc ;
        }
    }

    private void initialize() {
        space.limit( space.capacity() ) ;
        space.position( 0 ) ;
        sizeDisposed = 0 ;
    }

    public ByteBuffer allocate( int size ) {
        ByteBuffer result = null ;
        if (size <= space.remaining()) {
            space.limit( space.position() + size ) ;
            result = space.slice() ;
            space.position( space.limit() ) ;
            space.limit( space.capacity() ) ;
        }

        return result ;
    }

    public void dispose( ByteBuffer buffer ) {
        if (getState() == State.EMPTY)
            throw new IllegalStateException( 
                "Attempt to disposed of a buffer to an empty Slab!" ) ;

        sizeDisposed += buffer.capacity() ;
        if (getState() == State.FULL) {
            if (sizeDisposed >= sizeAllocated()) {
                // XXX if sizeDisposed > sizeAllocated, an error was made somewhere.
                // Should log this.
                initialize() ;
            }
        }
    }

    public ByteBuffer trim( int bufferStartPosition, ByteBuffer buffer, int sizeNeeded ) {
        ByteBuffer result = buffer ;

        // Check to see if buffer is the last buffer allocated
        if ((bufferStartPosition + buffer.capacity()) == space.position()) {
            space.position( bufferStartPosition ) ;
            buffer = allocate( sizeNeeded ) ;
        }

        return buffer ;
    }
}

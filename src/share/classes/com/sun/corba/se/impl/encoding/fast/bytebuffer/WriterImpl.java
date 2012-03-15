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

import java.nio.ByteBuffer ;
import java.nio.ByteOrder ;
import java.nio.ByteOrder ;
import java.nio.CharBuffer ;
import java.nio.IntBuffer ;
import java.nio.ShortBuffer ;
import java.nio.LongBuffer ;
import java.nio.FloatBuffer ;
import java.nio.DoubleBuffer ;

/** Write the primitive Java type to the current ByteBuffer.  If it doesn't
 * fit, call the BufferHandler, and write to the result, which becomes the
 * new current ByteBuffer.  Arrays will be written across multiple ByteBuffers
 * if necessary, but all primitives will be written to a single ByteBuffer.
 */
public class WriterImpl implements Writer {
    private BufferHandler handler ;
    private volatile BufferWrapper buffer ;

    private boolean isClosed = false ;
    private ByteOrder byteOrder ;

    /** Create a new ByteBufferWriter.  An instance maintains a current buffer
     * for use in writing.  Whenever the current buffer is insufficient to hold
     * the required data, the BufferHandler is called, and the result of the
     * handler is the new current buffer. The handler is responsible for 
     * the disposition of the contents of the old buffer.
     */
    public WriterImpl( BufferHandler handler ) {
        this.handler = handler ;
        byteOrder = ByteOrder.BIG_ENDIAN ;
    }

    public BufferHandler bufferHandler() {
        return handler ;
    }

    public ByteOrder order() {
        return byteOrder ;
    }

    public void order( ByteOrder byteOrder ) {
        this.byteOrder = byteOrder ;
    }
    
    private void overflow() {
        // Why was this here: buffer.buffer().limit( buffer.buffer().position() ) ;
        initBuffer( handler.overflow( buffer ) ) ;
    }

    private void initBuffer( BufferWrapper buffer ) {
        this.buffer = buffer ;
        buffer.buffer().clear() ;
        buffer.buffer().order( byteOrder ) ;
    }

    /** Cause the overflow handler to be called even if buffer is not full.
     */
    public synchronized void flush() {
        overflow() ;
    }

    public synchronized void close() {
        handler.close( buffer ) ;
        buffer = null ;
        isClosed = true ;
    }

    /** Ensure that the requested amount of space is available
     */
    public synchronized void ensure( int size ) {
        if (isClosed)
            throw new IllegalStateException( 
                "ByteBufferWriter is closed" ) ;

        if ((buffer == null) || (buffer.remaining() < size)) {
            overflow() ;
        }

        if (buffer.remaining() < size) {
            throw new RuntimeException( "Newly allocated buffer is too small" ) ;
        }
    }

    public void putBoolean( boolean data ) {
        ensure( 1 ) ;
        byte value = data ? (byte)1 : (byte)0 ;
        buffer.buffer().put( value ) ;
    }

    public void putByte( byte data ) {
        ensure( 1 ) ;
        buffer.buffer().put( data ) ;
    }

    public void putChar( char data ) {
        ensure( 2 ) ;
        buffer.buffer().putChar( data ) ;
    }

    public void putShort( short data ) {
        ensure( 2 ) ;
        buffer.buffer().putShort( data ) ;
    }

    public void putInt( int data ) {
        ensure( 4 ) ;
        buffer.buffer().putInt( data ) ;
    }

    public void putLong( long data ) {
        ensure( 8 ) ;
        buffer.buffer().putLong( data ) ;
    }

    public void putFloat( float data ) {
        ensure( 4 ) ;
        buffer.buffer().putFloat( data ) ;
    }

    public void putDouble( double data ) {
        ensure( 8 ) ;
        buffer.buffer().putDouble( data ) ;
    }

    public void putBooleanArray( boolean[] data ) {
        ensure(1) ;
        int ctr = 0 ;
        while (ctr < data.length) {
            int dataSizeToWrite = Math.min( data.length - ctr, 
                buffer.buffer().remaining()) ;

            for (int ctr2 = ctr; ctr2<ctr+dataSizeToWrite; ctr2++) {
                buffer.buffer().put( (byte)(data[ctr2] ? 1 : 0) ) ;
            }
            ctr += dataSizeToWrite ;

            if (ctr == data.length)
                break ;

            overflow() ;
        }
    }

    public void putByteArray( byte[] data ) {
        ensure(1) ;
        int ctr = 0 ;
        while (true) {
            int dataSizeToWrite = Math.min( data.length - ctr, 
                buffer.buffer().remaining()) ;

            buffer.buffer().put( data, ctr, dataSizeToWrite ) ;
            ctr += dataSizeToWrite ;
            
            if (ctr == data.length)
                break ;

            overflow() ;
        }
    }

    public void putCharArray( final char[] data ) {
        ensure(2) ;
        final CharBuffer typedBuffer = buffer.buffer().asCharBuffer() ;
        int ctr = 0 ;
        while (true) {
            int dataSizeToWrite = Math.min( data.length - ctr, 
                typedBuffer.remaining()) ;

            typedBuffer.put( data, ctr, dataSizeToWrite ) ;
            ctr += dataSizeToWrite ;
            
            if (ctr == data.length)
                break ;

            overflow() ;
        }
    }

    public void putShortArray( short[] data ) {
        ensure(2) ;
        final ShortBuffer typedBuffer = buffer.buffer().asShortBuffer() ;
        int ctr = 0 ;
        while (true) {
            int dataSizeToWrite = Math.min( data.length - ctr, 
                typedBuffer.limit() - typedBuffer.position() ) ;
            typedBuffer.put( data, ctr, dataSizeToWrite ) ;
            ctr += dataSizeToWrite ;
            
            if (ctr == data.length)
                break ;

            overflow() ;
        }
    }

    public void putIntArray( int[] data ) {
        ensure(4) ;
        final IntBuffer typedBuffer = buffer.buffer().asIntBuffer() ;
        int ctr = 0 ;
        while (true) {
            int dataSizeToWrite = Math.min( data.length - ctr, 
                typedBuffer.limit() - typedBuffer.position() ) ;
            typedBuffer.put( data, ctr, dataSizeToWrite ) ;
            ctr += dataSizeToWrite ;
            
            if (ctr == data.length)
                break ;

            overflow() ;
        }
    }

    public void putLongArray( long[] data ) {
        ensure(8) ;
        final LongBuffer typedBuffer = buffer.buffer().asLongBuffer() ;
        int ctr = 0 ;
        while (true) {
            int dataSizeToWrite = Math.min( data.length - ctr, 
                typedBuffer.limit() - typedBuffer.position() ) ;
            typedBuffer.put( data, ctr, dataSizeToWrite ) ;
            ctr += dataSizeToWrite ;
            
            if (ctr == data.length)
                break ;

            overflow() ;
        }
    }

    public void putFloatArray( float[] data ) {
        ensure(4) ;
        final FloatBuffer typedBuffer = buffer.buffer().asFloatBuffer() ;
        int ctr = 0 ;
        while (true) {
            int dataSizeToWrite = Math.min( data.length - ctr, 
                typedBuffer.limit() - typedBuffer.position() ) ;
            typedBuffer.put( data, ctr, dataSizeToWrite ) ;
            ctr += dataSizeToWrite ;
            
            if (ctr == data.length)
                break ;

            overflow() ;
        }
    }

    public void putDoubleArray( double[] data ) {
        ensure(8) ;
        final DoubleBuffer typedBuffer = buffer.buffer().asDoubleBuffer() ;
        int ctr = 0 ;
        while (true) {
            int dataSizeToWrite = Math.min( data.length - ctr, 
                typedBuffer.limit() - typedBuffer.position() ) ;
            typedBuffer.put( data, ctr, dataSizeToWrite ) ;
            ctr += dataSizeToWrite ;
            
            if (ctr == data.length)
                break ;

            overflow() ;
        }
    }
}


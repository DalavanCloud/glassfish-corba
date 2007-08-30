/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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

package com.sun.corba.se.impl.encoding.fast ;

import java.util.List ;
import java.util.ArrayList ;
import java.util.Map ;
import java.util.HashMap ;
import java.util.IdentityHashMap ;
import java.util.Iterator ;

import java.nio.ByteBuffer ;
import java.nio.ByteOrder ;
import java.nio.BufferOverflowException ;

import static com.sun.corba.se.impl.encoding.fast.Codes.* ;

/** A prototype of an OutputStream design to be very fast, and used
 * from generated code.  This stream avoids most lookups, except for 
 * a String lookup to support String indirections.
 */
public class FastOutputStream {
    private List<ByteBuffer> data ;
    private ByteBuffer current ;

    // indirections are negative ints, starting with -1.
    // The absolute value is the index into this array.
    private List<Object> indirections ;
    private int currentIndirection ;

    // Maps objects to their indirections.  Only used for
    // objects (like Arrays, possibly things built into rt.jar)
    // that cannot contain additional fields.
    private IdentityHashMap<Object,Integer> indirMap ;

    private static final int DATA_SIZE = 4096 ;

    private void allocate() {
	ByteBuffer buff = ByteBuffer.allocate( DATA_SIZE ) ;
	buff.order( ByteOrder.nativeOrder() ) ;
	data.add( buff ) ;
	current = buff ;
    }

    public FastOutputStream() {
	data = new ArrayList<ByteBuffer>() ;
	allocate() ;

	indirections = new ArrayList<Object>( 100 ) ;
	currentIndirection = -1 ;
	indirMap = new IdentityHashMap<Object,Integer>() ;
    }
  
    /** Return a FastInputStream that contains all of the data written to this
     * FastOutputStream.
     */
    public FastInputStream getInputStream() {
	// Prepare buffers for reading.
	for (ByteBuffer buf : data)
	    buf.flip() ;

	return new FastInputStream( data ) ;
    }

    public void close() {
	data = null ;
	current = null ;
	indirections = null ;
	currentIndirection = -1 ;
	indirMap = null ;
    }

    private void put( Codes code ) {
	put( (byte)code.ordinal() ) ;
    }

    private void put( boolean value ) {
	try {
	    current.put( 
		(byte)(value ? BOOL_TRUE.ordinal() : BOOL_FALSE.ordinal() ) ) ;
	} catch (BufferOverflowException exc) {
	    allocate() ;
	    current.put( 
		(byte)(value ? BOOL_TRUE.ordinal() : BOOL_FALSE.ordinal() ) ) ;
	}
    }

    private void put( byte value ) {
	try {
	    current.put( value ) ;
	} catch (BufferOverflowException exc) {
	    allocate() ;
	    current.put( value ) ;
	}
    }

    private void put( char value ) {
	try {
	    current.putChar( value ) ;
	} catch (BufferOverflowException exc) {
	    allocate() ;
	    current.putChar( value ) ;
	}
    }

    private void put( short value ) {
	try {
	    current.putShort( value ) ;
	} catch (BufferOverflowException exc) {
	    allocate() ;
	    current.putShort( value ) ;
	}
    }

    private void put( int value ) {
	try {
	    current.putInt( value ) ;
	} catch (BufferOverflowException exc) {
	    allocate() ;
	    current.putInt( value ) ;
	}
    }

    private void put( long value ) {
	try {
	    current.putLong( value ) ;
	} catch (BufferOverflowException exc) {
	    allocate() ;
	    current.putLong( value ) ;
	}
    }

    private void put( float value ) {
	try {
	    current.putFloat( value ) ;
	} catch (BufferOverflowException exc) {
	    allocate() ;
	    current.putFloat( value ) ;
	}
    }

    private void put( double value ) {
	try {
	    current.putDouble( value ) ;
	} catch (BufferOverflowException exc) {
	    allocate() ;
	    current.putDouble( value ) ;
	}
    }

    public void writeBoolean(boolean value) {
	put( value ) ;
    }

    public void writeChar(char value) {
	put( CHAR ) ;
	put( value ) ;
    }

    public void writeByte(byte value) {
	put( BYTE ) ;
	put( value ) ;
    }

    public void writeShort(short value) {
	put( SHORT ) ;
	put( value ) ;
    }

    public void writeInt(int value) {
	put( INT ) ;
	put( value ) ;
    }

    public void writeLong(long value) {
	put( LONG ) ;
	put( value ) ;
    }

    public void writeFloat(float value) {
	put( FLOAT ) ;
	put( value ) ;
    }

    public void writeDouble(double value) {
	put( DOUBLE ) ;
	put( value ) ;
    }

    /** Write out an indirect reference to the value with the given label.
     */
    public void indirectReference( int label ) {
	put( REF ) ;
	put( label ) ;
    }

    public void writeBooleanArray( boolean[] value ) {
	if (!primArrayHeader( BOOL_ARR, value, value.length )) {
	    for (int ctr=0; ctr<value.length; ctr++) 
		put( value[ctr] ) ;
	}
    }

    public void writeCharArray( char[] value ) {
	if (!primArrayHeader( CHAR_ARR, value, value.length )) {
	    for (int ctr=0; ctr<value.length; ctr++) 
		put( value[ctr] ) ;
	}
    }

    public void writeByteArray( byte[] value) {
	if (!primArrayHeader( BYTE_ARR, value, value.length )) {
	    for (int ctr=0; ctr<value.length; ctr++) 
		put( value[ctr] ) ;
	}
    }

    public void writeShortArray( short[] value) {
	if (!primArrayHeader( SHORT_ARR, value, value.length )) {
	    for (int ctr=0; ctr<value.length; ctr++) 
		put( value[ctr] ) ;
	}
    }

    public void writeIntArray( int[] value) {
	if (!primArrayHeader( INT_ARR, value, value.length )) {
	    for (int ctr=0; ctr<value.length; ctr++) 
		put( value[ctr] ) ;
	}
    }

    public void writeLongArray( long[] value) {
	if (!primArrayHeader( LONG_ARR, value, value.length )) {
	    for (int ctr=0; ctr<value.length; ctr++) 
		put( value[ctr] ) ;
	}
    }

    public void writeFloatArray( float[] value) {
	if (!primArrayHeader( FLOAT_ARR, value, value.length )) {
	    for (int ctr=0; ctr<value.length; ctr++) 
		put( value[ctr] ) ;
	}
    }

    public void writeDoubleArray( double[] value) {
	if (!primArrayHeader( DOUBLE_ARR, value, value.length )) {
	    for (int ctr=0; ctr<value.length; ctr++) 
		put( value[ctr] ) ;
	}
    }

    /** Start writing a reference.  This must be followed by any required calls
     * to write the fields of the class, and closed by an endReference call.
     * The label of the reference is returned, for later use in an indirectReference call.
     */
    public int startReference( String className ) {
	put( REF ) ;
	writeCharArray( className.toCharArray() ) ;
	int label = currentIndirection-- ;
	put( label ) ;
	return label ; 
    }

    public void endReference() {
	put( END ) ;
    }

    private Integer handleIndirection( Object value ) {
	if (value == null) {
	    put( NULL ) ;
	    return null ;
	} else {
	    Integer label = indirMap.get( value ) ;
	    if (label == null) {
		label = currentIndirection-- ;
		indirMap.put( value, label ) ;
		return label ;
	    } else {
		indirectReference( label ) ;
		return null ;
	    }
	}
    }

    /* Return true if write was completed here by writing out an
     * indirection, otherwise write the code followed by the 
     * indirection value and return false.
     */
    private boolean primArrayHeader( Codes code, Object value, int size ) {
	Integer label = handleIndirection( value ) ;
	if (label != null) {
	    put( code ) ;
	    put( label ) ;
	    put( size ) ;
	    return true ;
	} else
	    return false ;
    }

    /** Write out a reference.  This will look in the indirection map
     * to see if this instance has been previously written.  This is used for
     * classes that are not or cannot be modified.
     */
    public void writeReference( Object value ) {
	Integer label = handleIndirection( value ) ;
	if (label != null) {
	    put( REF ) ;
	    writeCharArray( value.getClass().getName().toCharArray() ) ;
	    put( label) ;
	}
	
	// XXX write out fields? not in the prototype: proto only handles
	// bytecode modified classes and arrays.  Required code is similar
	// to what is present in the object copier, but moving data between
	// object and ByteBuffer.
	// error( "writeReference not supported" ) ;

	put( END ) ;
    }

    /** Start writing an array of references of the given type.
     * Must be followed by size calls to 
     * startReference/endReference or indirectReference.
     * value must be an array.
     */
    public void startReferenceArray( Object value, String className, int size ) {
	Integer label = handleIndirection( value ) ;
	if (label != null) {
	    put( REF_ARR ) ;
	    writeCharArray( value.getClass().getComponentType().getName().toCharArray() ) ;
	    put( label ) ;
	    put( size ) ;
	}
    }

    // XXX We do not have support for multi-dimensional arrays in this prototype.
}

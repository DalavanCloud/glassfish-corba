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
import java.util.Iterator ;

import java.nio.ByteBuffer ;
import java.nio.BufferUnderflowException ;

import com.sun.corba.se.spi.orbutil.generic.Pair ;

import static com.sun.corba.se.impl.encoding.fast.Codes.* ;

/** A prototype of an OutputStream design to be very fast, and used
 * from generated code.  This stream avoids most lookups, except for 
 * a String lookup to support String indirections.
 */
public class FastInputStream {
    private List<ByteBuffer> data ;
    private Iterator<ByteBuffer> iter ;
    private ByteBuffer current ;

    // indirections are negative ints, starting with -1.
    // The absolute value is the index into this array.
    private List<Object> indirections ;

    private static final int DATA_SIZE = 4096 ;

    private void error( String msg ) {
	throw new IllegalStateException( msg ) ;
    }

    private void allocate() {
	if (iter.hasNext()) {
	    current = iter.next() ;
	} else {
	    error( "Tried to read past end of stream" ) ;
	}
    }

    public FastInputStream( List<ByteBuffer> data ) {
	this.data = data ;
	this.iter = data.iterator() ;
	allocate() ;

	indirections = new ArrayList<Object>( 100 ) ;
    }

    public void close() {
	data = null ;
	current = null ;
	indirections = null ;
    }

    private String code( byte b ) {
	Codes[] codes = Codes.class.getEnumConstants() ;
	if ((b>=0) && (b<codes.length))
	    return codes[b].toString() ;
	else 
	    return "invalid Code(" + b + ")" ;
    }

    private Codes getCode() {
	byte b = getByte() ;
	Codes[] codes = Codes.class.getEnumConstants() ;
	if ((b>=0) && (b<codes.length))
	    return codes[b] ;
	else 
	    error( "Invalid code " + b ) ;

	return null ;
    }

    private void expect( Codes code ) {
	byte b = getByte() ;
	if (code.ordinal() != b)
	    error( "Error in reading stream: expected " + code
		+ " but read " + code(b) ) ;
    }

    private boolean getBoolean() {
	byte b ;
	try {
	    b = current.get() ;
	} catch (BufferUnderflowException exc) {
	    allocate() ;
	    b = current.get() ;
	}

	if (b==0)
	    return false ;
	else if (b==1)
	    return true ;
	else
	    error( "Invalid boolean in stream: value was " + b ) ;

	return false ;
    }

    private byte getByte() {
	byte b ;
	try {
	    return current.get() ;
	} catch (BufferUnderflowException exc) {
	    allocate() ;
	    return current.get() ;
	}
    }

    private short getShort() {
	short b ;
	try {
	    return current.getShort() ;
	} catch (BufferUnderflowException exc) {
	    allocate() ;
	    return current.getShort() ;
	}
    }

    private char getChar() {
	char b ;
	try {
	    return current.getChar() ;
	} catch (BufferUnderflowException exc) {
	    allocate() ;
	    return current.getChar() ;
	}
    }

    private int getInt() {
	int b ;
	try {
	    return current.getInt() ;
	} catch (BufferUnderflowException exc) {
	    allocate() ;
	    return current.getInt() ;
	}
    }

    private long getLong() {
	long b ;
	try {
	    return current.getLong() ;
	} catch (BufferUnderflowException exc) {
	    allocate() ;
	    return current.getLong() ;
	}
    }

    private float getFloat() {
	float b ;
	try {
	    return current.getFloat() ;
	} catch (BufferUnderflowException exc) {
	    allocate() ;
	    return current.getFloat() ;
	}
    }

    private double getDouble() {
	double b ;
	try {
	    return current.getDouble() ;
	} catch (BufferUnderflowException exc) {
	    allocate() ;
	    return current.getDouble() ;
	}
    }

    public boolean readBoolean() {
	expect( BOOL ) ;
	return getBoolean() ;
    }

    public char readChar() {
	expect( CHAR ) ;
	return getChar() ;
    }

    public byte readByte() {
	expect( BYTE ) ;
	return getByte() ;
    }

    public short readShort() {
	expect( SHORT ) ;
	return getShort() ;
    }

    public int readInt() {
	expect( INT ) ;
	return getInt() ;
    }

    public long readLong() {
	expect( LONG ) ;
	return getLong() ;
    }

    public float readFloat() {
	expect( FLOAT ) ;
	return getFloat() ;
    }

    public double readDouble() {
	expect( DOUBLE ) ;
	return getDouble() ;
    }

    private <T> T handleIndirection( Class<T> cls ) {
	int indir = -getInt() ;
	if ((indir > 0) && (indir < indirections.size())) {
	    Object val = indirections.get(indir) ;
	    if (val == null) {
		error( "Indirection to a NULL value" ) ;
	    } else if (cls.isInstance( val )) {
		return cls.cast( val ) ;
	    } else {
		error( "Indirection to value " + val 
		    + " of incompatible type. Expected type " + cls.getName() ) ;
	    }
	} else {
	    error( "Indirection value " + indir + " is out of range" ) ;
	}

	return null ;
    }

    public boolean[] readBooleanArray() {
	Codes code = getCode() ;
	if (code == REF) {
	    int indir = -getInt() ;
	    if ((indir > 0) && (indir < indirections.size())) {
		Object val = indirections.get(indir) ;
		if (val == null) {
		    error( "Indirection to a NULL value" ) ;
		} else if (val instanceof boolean[]) {
		    return (boolean[])val ;
		} else {
		    error( "Indirection to non-String value " + val ) ;
		}
	    } else {
		error( "Indirection value " + indir + " is out of range" ) ;
	    }
	} else if (code == BOOL_ARR) {
	    int indir1 = -getInt() ;
	    int len = getInt() ;
	    boolean[] result = new boolean[len] ;
	    for (int ctr=0; ctr<len; ctr++)
		result[ctr] = getBoolean() ;	
	    indirections.set( indir1, result ) ;
	    return result ;
	} else
	    error( "Expected INDIR or ARRAY BOOL, but read " + code ) ;

	return null ;
    }

    public char[] readCharArray() {
	return null ;
    }

    public byte[] readByteArray() {
	return null ;
    }

    public short[] readShortArray() {
	return null ;
    }

    public int[] readIntArray() {
	return null ;
    }

    public long[] readLongArray() {
	return null ;
    }

    public float[] readFloatArray() {
	return null ;
    }

    public double[] readDoubleArray() {
	return null ;
    }

    public void readNull() {
    }

    /** Read back a reference.  There are several cases:
     * <ol>
     * <li>NULL.  In this case, we get back a null object reference.
     * <li>REF STRING value LABEL.  In this case, we get back a reference to a previously
     * seen object.
     * <li>REF STRING value START LABEL. In this case, we get back a reference to a 
     * newly created object, which must be initialized from other read calls.
     * </ol>
     */
    public Object startReference() {
	return null ;
    }

    /** Skip the end marker at the end of a reference, or throw an error.
     */
    public void endReference() {
    }

    /** Start reading an array of references of the given type.
     * Returns a pair containing an empty array instance of the appropriate type,
     * and the size of the array.  Must be followed by size calls to 
     * startReference/endReference.
     */
    public Pair<Object,Integer> startReferenceArray( String className, int size ) {
	return null ;
    }

    // XXX We do not have support for multi-dimensional arrays in this prototype.
}


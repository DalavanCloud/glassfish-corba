/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2002-2007 Sun Microsystems, Inc. All rights reserved.
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
/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 * US Government Users Restricted Rights - Use, duplication or
 * disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

package com.sun.corba.se.impl.io;

import java.io.IOException;
import java.io.StreamCorruptedException;
import java.io.ObjectInputValidation;
import java.io.NotActiveException;
import java.io.InvalidObjectException;
import java.io.InvalidClassException;
import java.io.OptionalDataException;
import java.io.Externalizable;
import java.io.EOFException;
import java.lang.reflect.*;

import sun.corba.Bridge ;

import org.omg.CORBA.portable.ValueInputStream;

import org.omg.CORBA.SystemException;
import org.omg.CORBA.TCKind;
import org.omg.CORBA.ORB; 
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.portable.IndirectionException;
import org.omg.CORBA.MARSHAL;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.ValueMember;

import com.sun.org.omg.CORBA.ValueDefPackage.FullValueDescription;
import com.sun.org.omg.CORBA.AttributeDescription ;
import com.sun.org.omg.CORBA.OperationDescription ;
import com.sun.org.omg.CORBA.ParameterDescription ;
import com.sun.org.omg.CORBA.ExceptionDescription ;

import com.sun.org.omg.SendingContext.CodeBase;  

import javax.rmi.CORBA.ValueHandler;

import java.security.*;
import java.util.*;

import com.sun.corba.se.spi.orbutil.misc.ObjectUtility ;

import com.sun.corba.se.spi.orbutil.misc.OperationTracer;

import com.sun.corba.se.impl.logging.OMGSystemException ;

import com.sun.corba.se.impl.javax.rmi.CORBA.Util;

import com.sun.corba.se.impl.orbutil.ClassInfoCache ;

import com.sun.corba.se.impl.util.Utility ;

import com.sun.corba.se.spi.btrace.* ;

/**
 * IIOPInputStream is used by the ValueHandlerImpl to handle Java serialization
 * input semantics.
 *
 * @author  Stephen Lewallen
 * @since   JDK1.1.6
 */

@Traceable
public class IIOPInputStream
    extends com.sun.corba.se.impl.io.InputStreamHook
{
    private static Bridge bridge = AccessController.doPrivileged( 
	new PrivilegedAction<Bridge>() {
	    public Bridge run() {
		return Bridge.get() ;
	    }
	} 
    ) ;

    // Necessary to pass the appropriate fields into the
    // defaultReadObjectDelegate method (which takes no
    // parameters since it's called from 
    // java.io.ObjectInpuStream defaultReadObject()
    // which we can't change).
    //
    // This is only used in the case where the fields had 
    // to be obtained remotely because of a serializable
    // version difference.  Set in inputObjectUsingFVD.
    // Part of serialization evolution fixes for Ladybird,
    // bug 4365188.
    private ValueMember defaultReadObjectFVDMembers[] = null;

    private org.omg.CORBA_2_3.portable.InputStream orbStream;

    private CodeBase cbSender;  

    private ValueHandlerImpl vhandler;  //d4365188

    private Object currentObject = null;

    private ObjectStreamClass currentClassDesc = null;

    private Class currentClass = null;

    private int recursionDepth = 0;

    private int simpleReadDepth = 0;

    // The ActiveRecursionManager replaces the old RecursionManager which
    // used to record how many recursions were made, and resolve them after
    // an object was completely deserialized.
    //
    // That created problems (as in bug 4414154) because when custom
    // unmarshaling in readObject, there can be recursive references
    // to one of the objects currently being unmarshaled, and the
    // passive recursion system failed.
    ActiveRecursionManager activeRecursionMgr = new ActiveRecursionManager();

    private IOException abortIOException = null;

    /* Remember the first exception that stopped this stream. */
    private ClassNotFoundException abortClassNotFoundException = null;

    /* List of validation callback objects
     * The vector is created as needed. The vector is maintained in
     * order of highest (first) priority to lowest
     */
    private List callbacks;

    // Serialization machinery fields
    /* Arrays used to keep track of classes and ObjectStreamClasses
     * as they are being merged; used in inputObject.
     * spClass is the stack pointer for both.  */
    ObjectStreamClass[] classdesc;
    Class[] classes;
    int spClass;
    // MS: MarkStack<Pair<Class,ObjectStreamClass>> cstack ;

    private static final String kEmptyStr = "";

    public static final TypeCode kRemoteTypeCode = ORB.init().get_primitive_tc(TCKind.tk_objref);
    public static final TypeCode kValueTypeCode =  ORB.init().get_primitive_tc(TCKind.tk_value);

    // TESTING CODE - useFVDOnly should be made final before FCS in order to
    // optimize out the check.
    private static final boolean useFVDOnly = false;

    private byte streamFormatVersion;

    // Since java.io.OptionalDataException's constructors are
    // package private, but we need to throw it in some special
    // cases, we try to do it by reflection.
    private static final Constructor OPT_DATA_EXCEPTION_CTOR;
   
    static {
        OPT_DATA_EXCEPTION_CTOR = getOptDataExceptionCtor();
    }

    // Grab the OptionalDataException boolean ctor and make
    // it accessible.  Note that any exceptions 
    // will be wrapped in ExceptionInInitializerErrors.
    private static Constructor getOptDataExceptionCtor() {
        try {
            Constructor result = AccessController.doPrivileged(
		new PrivilegedExceptionAction<Constructor>() {
                    public Constructor run() 
                        throws NoSuchMethodException, SecurityException {
                        
                        Constructor boolCtor 
                            = OptionalDataException.class.getDeclaredConstructor(
                                Boolean.TYPE );
                        
                        boolCtor.setAccessible(true);
                        
                        return Return.value(boolCtor);
                    }
		}
	    );
            
            if (result == null)
		// XXX I18N, logging needed.
                throw new Error("Unable to find OptionalDataException constructor");
        
            return Return.value(result);

        } catch (Exception ex) {
	    // XXX I18N, logging needed.
            throw new ExceptionInInitializerError(ex);
        }
    }

    // Create a new OptionalDataException with the EOF marker
    // set to true.  See handleOptionalDataMarshalException.
    private OptionalDataException createOptionalDataException() {
        try {
            OptionalDataException result
                = (OptionalDataException)
                   OPT_DATA_EXCEPTION_CTOR.newInstance(new Object[] { 
                       Boolean.TRUE });

            if (result == null)
		// XXX I18N, logging needed.
                throw new Error("Created null OptionalDataException");

            return Return.value(result);

        } catch (Exception ex) {
	    // XXX I18N, logging needed.
            throw new Error("Couldn't create OptionalDataException", ex);
        }
    }

    // Return the stream format version currently being used
    // to deserialize an object
    protected byte getStreamFormatVersion() {
        return streamFormatVersion;
    }

    // At the beginning of data sent by a writeObject or
    // writeExternal method there is a byte telling the
    // reader the stream format version.
    @TraceValueHandler
    private void readFormatVersion() throws IOException {
        if (valueHandlerDebug()) {
            dputil.enter( "readFormatVersion" ) ;
        }

        try {
            streamFormatVersion = orbStream.read_octet();

            if (valueHandlerDebug())
                dputil.info( "streamFormatVersion", streamFormatVersion ) ;

            if (streamFormatVersion < 1 || 
                streamFormatVersion > vhandler.getMaximumStreamFormatVersion()) {
                SystemException sysex = omgWrapper.unsupportedFormatVersion(
                        CompletionStatus.COMPLETED_MAYBE);
                // XXX I18N?  Logging for IOException?
                IOException result = new IOException("Unsupported format version: "
                                                     + streamFormatVersion);
                result.initCause( sysex ) ;
                throw result ;
            }

            if (streamFormatVersion == 2) {
                if (!(orbStream instanceof ValueInputStream)) {
                    SystemException sysex = omgWrapper.notAValueinputstream( 
                        CompletionStatus.COMPLETED_MAYBE);
                    // XXX I18N?  Logging for IOException?
                    IOException result = new IOException("Not a ValueInputStream");
                    result.initCause( sysex ) ;
                    throw result;
                }
            }
        } finally {
            if (valueHandlerDebug()) {
                dputil.exit() ;
            }
        }
    }

    public static void setTestFVDFlag(boolean val){
	//  useFVDOnly = val;
    }

    /**
     * Dummy constructor; passes upper stream a dummy stream;
     **/
    public IIOPInputStream()
    	throws java.io.IOException {
    	super();
	resetStream();
    }
	
    public final void setOrbStream(org.omg.CORBA_2_3.portable.InputStream os) {
    	orbStream = os;
        setORB( os.orb() ) ;
    }

    public final org.omg.CORBA_2_3.portable.InputStream getOrbStream() {
    	return orbStream;
    }

    //added setSender and getSender
    public final void setSender(CodeBase cb) {
        cbSender = cb;
    }

    public final CodeBase getSender() {
        return cbSender;
    }

    // 4365188 this is added to enable backward compatability w/ wrong
    // rep-ids
    public final void setValueHandler(ValueHandler vh) {
        vhandler = (com.sun.corba.se.impl.io.ValueHandlerImpl) vh;
    }

    public final ValueHandler getValueHandler() {
	return (javax.rmi.CORBA.ValueHandler) vhandler;
    }
	
    @TraceValueHandler
    public final void increaseRecursionDepth(){
	recursionDepth++;
        if (valueHandlerDebug())
            dputil.dprint( "Incremented recursionDepth to " + recursionDepth ) ;
    }

    @TraceValueHandler
    public final int decreaseRecursionDepth(){
	--recursionDepth;
        if (valueHandlerDebug())
            dputil.dprint( "Decremented recursionDepth to " + recursionDepth ) ;
        return recursionDepth ;
    }

    /**
     * Override the actions of the final method "readObject()"
     * in ObjectInputStream.
     * @since     JDK1.1.6
     *
     * Read an object from the ObjectInputStream.
     * The class of the object, the signature of the class, and the values
     * of the non-transient and non-static fields of the class and all
     * of its supertypes are read.  Default deserializing for a class can be
     * overriden using the writeObject and readObject methods.
     * Objects referenced by this object are read transitively so
     * that a complete equivalent graph of objects is reconstructed by readObject. <p>
     *
     * The root object is completly restored when all of its fields
     * and the objects it references are completely restored.  At this
     * point the object validation callbacks are executed in order
     * based on their registered priorities. The callbacks are
     * registered by objects (in the readObject special methods)
     * as they are individually restored.
     *
     * Exceptions are thrown for problems with the InputStream and for classes
     * that should not be deserialized.  All exceptions are fatal to the
     * InputStream and leave it in an indeterminate state; it is up to the caller
     * to ignore or recover the stream state.
     * @exception java.lang.ClassNotFoundException Class of a serialized object
     *      cannot be found.
     * @exception InvalidClassException Something is wrong with a class used by
     *     serialization.
     * @exception StreamCorruptedException Control information in the
     *     stream is inconsistent.
     * @exception OptionalDataException Primitive data was found in the
     * stream instead of objects.
     * @exception IOException Any of the usual Input/Output related exceptions.
     * @since     JDK1.1
     */
    @TraceValueHandler
    @ValueHandlerRead
    public final Object readObjectDelegate() throws IOException
    {
        if (valueHandlerDebug())
            dputil.enter( "readObjectDelegate" ) ;

	try {

            readObjectState.readData(this);

            return Return.value(orbStream.read_abstract_interface());
        } catch (MARSHAL marshalException) {
            handleOptionalDataMarshalException(marshalException, true);
            throw marshalException;
	} catch(IndirectionException cdrie) {
            // The CDR stream had never seen the given offset before,
            // so check the recursion manager (it will throw an
            // IOException if it doesn't have a reference, either).
            return activeRecursionMgr.getObject(cdrie.offset);
        } finally {
            if (valueHandlerDebug())
                dputil.exit() ;
        }
    }

    @TraceValueHandler
    @ValueHandlerRead
    final Object simpleReadObject(Class clz,
				  ClassInfoCache.ClassInfo cinfo,
                                  String repositoryID,
                                  com.sun.org.omg.SendingContext.CodeBase sender,
                                  int offset)
    {
        if (valueHandlerDebug())
            dputil.enter( "simpleReadObject", "clz", clz,
                "repositoryID", repositoryID, "offset", offset ) ;

        try {
            /* Save the current state and get ready to read an object. */
            Object prevObject = currentObject;
            ObjectStreamClass prevClassDesc = currentClassDesc;
            Class prevClass = currentClass;
            byte oldStreamFormatVersion = streamFormatVersion;

            simpleReadDepth++;	// Entering
            Object obj = null;

            OperationTracer.startReadValue( clz.getName() ) ;

            /*
             * Check for reset, handle it before reading an object.
             */
            try {
                // d4365188: backward compatability
                if (vhandler.useFullValueDescription(clz, repositoryID)) {
                    obj = inputObjectUsingFVD(clz, cinfo, repositoryID, sender, offset);
                } else {
                    obj = inputObject(clz, cinfo, repositoryID, sender, offset);
                }

                obj = currentClassDesc.readResolve(obj);
            } catch(ClassNotFoundException cnfe) {
                bridge.throwException( cnfe ) ;
                return Return.value(null);
            } catch(IOException ioe) {
                // System.out.println("CLZ = " + clz + "; " + ioe.toString());
                bridge.throwException(ioe) ;
                return Return.value(null);
            } finally {
                simpleReadDepth --;
                currentObject = prevObject;
                currentClassDesc = prevClassDesc;
                currentClass = prevClass;
                streamFormatVersion = oldStreamFormatVersion;
                OperationTracer.endReadValue() ;
            }

            /* Check for thrown exceptions and re-throw them, clearing them if
             * this is the last recursive call .
             */
            IOException exIOE = abortIOException;
            if (simpleReadDepth == 0)
                abortIOException = null;
            if (exIOE != null){
                bridge.throwException( exIOE ) ;
                return Return.value(null);
            }


            ClassNotFoundException exCNF = abortClassNotFoundException;
            if (simpleReadDepth == 0)
                abortClassNotFoundException = null;
            if (exCNF != null) {
                bridge.throwException( exCNF ) ;
                return Return.value(null);
            }

            return Return.value(obj);
        } finally {
            if (valueHandlerDebug()) 
                dputil.exit() ;
        }
    }

    @TraceValueHandler
    @ValueHandlerRead
    public final void simpleSkipObject( String repositoryID, 
        com.sun.org.omg.SendingContext.CodeBase sender)
    {
        if (valueHandlerDebug()) 
            dputil.enter( "simpleSkipObject", "repositoryID", repositoryID ) ;

        try {
            /* Save the current state and get ready to read an object. */
            Object prevObject = currentObject;
            ObjectStreamClass prevClassDesc = currentClassDesc;
            Class prevClass = currentClass;
            byte oldStreamFormatVersion = streamFormatVersion;

            simpleReadDepth++;	// Entering
            Object obj = null;

            /*
             * Check for reset, handle it before reading an object.
             */
            try {
                skipObjectUsingFVD(repositoryID, sender);
            } catch(ClassNotFoundException cnfe) {
                bridge.throwException( cnfe ) ;
                return;
            } catch(IOException ioe) {
                bridge.throwException( ioe ) ;
                return;
            } finally {
                simpleReadDepth --;
                streamFormatVersion = oldStreamFormatVersion;
                currentObject = prevObject;
                currentClassDesc = prevClassDesc;
                currentClass = prevClass;
            }


            /* Check for thrown exceptions and re-throw them, clearing them if
             * this is the last recursive call .
             */
            IOException exIOE = abortIOException;
            if (simpleReadDepth == 0)
                abortIOException = null;
            if (exIOE != null){
                bridge.throwException( exIOE ) ;
                return;
            }


            ClassNotFoundException exCNF = abortClassNotFoundException;
            if (simpleReadDepth == 0)
                abortClassNotFoundException = null;
            if (exCNF != null) {
                bridge.throwException( exCNF ) ;
                return;
            }

            return;
        } finally {
            if (valueHandlerDebug()) 
                dputil.exit() ;
        }
    }

    /**
     * This method is called by trusted subclasses of ObjectOutputStream
     * that constructed ObjectOutputStream using the
     * protected no-arg constructor. The subclass is expected to provide
     * an override method with the modifier "final".
     *
     * @return the Object read from the stream.
     *
     * @see #ObjectInputStream()
     * @see #readObject
     * @since JDK 1.2
     */
    @Override
    protected final Object readObjectOverride()
 	throws OptionalDataException, ClassNotFoundException, IOException
    {
        return Return.value(readObjectDelegate());
    }

    /**
     * Override the actions of the final method "defaultReadObject()"
     * in ObjectInputStream.
     * @since     JDK1.1.6
     *
     * Read the non-static and non-transient fields of the current class
     * from this stream.  This may only be called from the readObject method
     * of the class being deserialized. It will throw the NotActiveException
     * if it is called otherwise.
     *
     * @exception java.lang.ClassNotFoundException if the class of a serialized
     *              object could not be found.
     * @exception IOException        if an I/O error occurs.
     * @exception NotActiveException if the stream is not currently reading
     *              objects.
     * @since     JDK1.1
     */
    @TraceValueHandler
    public final void defaultReadObjectDelegate() {
        if (valueHandlerDebug())
            dputil.enter( "defaultReadObjectDelegate" ) ;

        try {
	    if (currentObject == null || currentClassDesc == null)
		// XXX I18N, logging needed.
		throw new NotActiveException("defaultReadObjectDelegate");

            if (defaultReadObjectFVDMembers != null ) {
                // Clear this here so that a recursion back to another
                // defaultReadObjectDelegate call from inputClassFields
                // does NOT pick up inapplicable defaultReadObjectFVDMembers
                // (see bug 6614558).
                ValueMember[] valueMembers = defaultReadObjectFVDMembers ;
                defaultReadObjectFVDMembers = null ;

                if (valueHandlerDebug()) {
                    dputil.info( "Using FVD to read:" ) ;
                    for (ValueMember vm : valueMembers) {
                        dputil.info( "\t", displayValueMember( vm ) ) ;
                    }
                }

                inputClassFields(currentObject, 
                                 currentClass, 
                                 currentClassDesc,
                                 valueMembers,
                                 cbSender);
            } else {
                if (valueHandlerDebug()) {
                    dputil.info( "Using local fields to read" ) ;
                }
                
                // Use the local fields to unmarshal.
                ObjectStreamField[] fields =
                    currentClassDesc.getFieldsNoCopy();
                if (fields.length > 0) {
                    inputClassFields(currentObject, currentClass, fields, cbSender); 
                }
            }
        } catch(NotActiveException nae) {
	    bridge.throwException( nae ) ;
	} catch(IOException ioe) {
	    bridge.throwException( ioe ) ;
	} catch(ClassNotFoundException cnfe) {
	    bridge.throwException( cnfe ) ;
	} finally {
            if (valueHandlerDebug())
                dputil.exit() ;
        }
    }

    /**
     * Override the actions of the final method "enableResolveObject()"
     * in ObjectInputStream.
     * @since     JDK1.1.6
     *
     * Enable the stream to allow objects read from the stream to be replaced.
     * If the stream is a trusted class it is allowed to enable replacment.
     * Trusted classes are those classes with a classLoader equals null. <p>
     *
     * When enabled the resolveObject method is called for every object
     * being deserialized.
     *
     * @exception SecurityException The classloader of this stream object is non-null.
     * @since     JDK1.1
     */
    public final boolean enableResolveObjectDelegate(boolean enable)
    /* throws SecurityException */
    {
	return false;
    }

    // The following three methods allow the implementing orbStream
    // to provide mark/reset behavior as defined in java.io.InputStream.

    @Override
    public final void mark(int readAheadLimit) {
        orbStream.mark(readAheadLimit);
    }
    
    @Override
    public final boolean markSupported() {
        return orbStream.markSupported();
    }
    
    @Override
    public final void reset() throws IOException {
        try {
            orbStream.reset();
        } catch (Error e) {
            IOException err = new IOException(e.getMessage());
	    err.initCause(e) ;
	    throw err ;
        }
    }

    @Override
    public final int available() throws IOException{
        return 0; // unreliable
    }

    @Override
    public final void close() throws IOException{
        // no op
    }

    @TraceValueHandler
    @ValueHandlerRead
    @Override
    public final int read() throws IOException{
        try{
            readObjectState.readData(this);

            return Return.value((orbStream.read_octet() << 0) & 0x000000FF);
        } catch (MARSHAL marshalException) {
            if (marshalException.minor 
                == OMGSystemException.RMIIIOP_OPTIONAL_DATA_INCOMPATIBLE1) {
                setState(IN_READ_OBJECT_NO_MORE_OPT_DATA);
                return Return.value(-1);
            }

            throw marshalException;
        } catch(Error e) {
	    IOException exc = new IOException(e.getMessage());
	    exc.initCause(e) ;
	    throw exc ;
	}
    }

    @TraceValueHandler
    @ValueHandlerRead
    @Override
    public final int read(byte data[], int offset, int length) throws IOException{
        try{
            readObjectState.readData(this);

            orbStream.read_octet_array(data, offset, length);
            return Return.value(length);
        } catch (MARSHAL marshalException) {
            if (marshalException.minor 
                == OMGSystemException.RMIIIOP_OPTIONAL_DATA_INCOMPATIBLE1) {
                setState(IN_READ_OBJECT_NO_MORE_OPT_DATA);
                return Return.value(-1);
            }

            throw marshalException;
        } catch(Error e) {
	    IOException exc = new IOException(e.getMessage());
	    exc.initCause(e) ;
	    throw exc ;
	}

    }

    @TraceValueHandler
    @ValueHandlerRead
    @Override
    public final boolean readBoolean() throws IOException{
        try{
            readObjectState.readData(this);

            return Return.value(orbStream.read_boolean());
        } catch (MARSHAL marshalException) {
            handleOptionalDataMarshalException(marshalException, false);
            throw marshalException;

        } catch(Error e) {
	    IOException exc = new IOException(e.getMessage());
	    exc.initCause(e);
	    throw exc ;
	}
    }

    @TraceValueHandler
    @ValueHandlerRead
    @Override
    public final byte readByte() throws IOException{
        try{
            readObjectState.readData(this);

            return Return.value(orbStream.read_octet());
        } catch (MARSHAL marshalException) {
            handleOptionalDataMarshalException(marshalException, false);
            throw marshalException;

        } catch(Error e) {
	    IOException exc = new IOException(e.getMessage());
	    exc.initCause(e);
	    throw exc ;
	}
    }

    @TraceValueHandler
    @ValueHandlerRead
    @Override
    public final char readChar() throws IOException{
        try{
            readObjectState.readData(this);

            return Return.value(orbStream.read_wchar());
        } catch (MARSHAL marshalException) {
            handleOptionalDataMarshalException(marshalException, false);
            throw marshalException;

        } catch(Error e) {
	    IOException exc = new IOException(e.getMessage());
	    exc.initCause(e);
	    throw exc ;
	}
    }

    @TraceValueHandler
    @ValueHandlerRead
    @Override
    public final double readDouble() throws IOException{
        try{
            readObjectState.readData(this);

            return Return.value(orbStream.read_double());
        } catch (MARSHAL marshalException) {
            handleOptionalDataMarshalException(marshalException, false);
            throw marshalException;
        } catch(Error e) {
	    IOException exc = new IOException(e.getMessage());
	    exc.initCause(e);
	    throw exc ;
	}
    }

    @TraceValueHandler
    @ValueHandlerRead
    @Override
    public final float readFloat() throws IOException{
        try{
            readObjectState.readData(this);

            return Return.value(orbStream.read_float());
        } catch (MARSHAL marshalException) {
            handleOptionalDataMarshalException(marshalException, false);
            throw marshalException;
        } catch(Error e) {
	    IOException exc = new IOException(e.getMessage());
	    exc.initCause(e);
	    throw exc ;
	}
    }

    @TraceValueHandler
    @ValueHandlerRead
    @Override
    public final void readFully(byte data[]) throws IOException{
// d11623 : implement readFully, required for serializing some core classes

        readFully(data, 0, data.length);
    }

    @TraceValueHandler
    @ValueHandlerRead
    @Override
    public final void readFully(byte data[],  int offset,  int size) throws IOException{
// d11623 : implement readFully, required for serializing some core classes
        try{
            readObjectState.readData(this);

            orbStream.read_octet_array(data, offset, size);
        } catch (MARSHAL marshalException) {
            handleOptionalDataMarshalException(marshalException, false);
            
            throw marshalException;
        } catch(Error e) {
	    IOException exc = new IOException(e.getMessage());
	    exc.initCause(e);
	    throw exc ;
	}
    }

    @TraceValueHandler
    @ValueHandlerRead
    @Override
    public final int readInt() throws IOException{
        try{
            readObjectState.readData(this);

            return Return.value(orbStream.read_long());
        } catch (MARSHAL marshalException) {
            handleOptionalDataMarshalException(marshalException, false);
            throw marshalException;
        } catch(Error e) {
	    IOException exc = new IOException(e.getMessage());
	    exc.initCause(e);
	    throw exc ;
	}
    }

    @Override
    public final String readLine() throws IOException{
	// XXX I18N, logging needed.
        throw new IOException("Method readLine not supported");
    }

    @TraceValueHandler
    @ValueHandlerRead
    @Override
    public final long readLong() throws IOException{
        try{
            readObjectState.readData(this);

            return Return.value(orbStream.read_longlong());
        } catch (MARSHAL marshalException) {
            handleOptionalDataMarshalException(marshalException, false);
            throw marshalException;
        } catch(Error e) {
	    IOException exc = new IOException(e.getMessage());
	    exc.initCause(e);
	    throw exc ;
	}
    }

    @TraceValueHandler
    @ValueHandlerRead
    @Override
    public final short readShort() throws IOException{
        try{
            readObjectState.readData(this);

            return Return.value(orbStream.read_short());
        } catch (MARSHAL marshalException) {
            handleOptionalDataMarshalException(marshalException, false);
            throw marshalException;
        } catch(Error e) {
	    IOException exc = new IOException(e.getMessage());
	    exc.initCause(e);
	    throw exc ;
	}
    }

    @Override
    protected final void readStreamHeader() throws IOException, StreamCorruptedException{
        // no op
    }

    @TraceValueHandler
    @ValueHandlerRead
    @Override
    public final int readUnsignedByte() throws IOException{
        try{
            readObjectState.readData(this);

    	    return Return.value((orbStream.read_octet() << 0) & 0x000000FF);
        } catch (MARSHAL marshalException) {
            handleOptionalDataMarshalException(marshalException, false);
            throw marshalException;
	} catch(Error e) {
	    IOException exc = new IOException(e.getMessage());
	    exc.initCause(e);
	    throw exc ;
	}
    }

    @TraceValueHandler
    @ValueHandlerRead
    @Override
    public final int readUnsignedShort() throws IOException{
        try{
            readObjectState.readData(this);

    	    return Return.value((orbStream.read_ushort() << 0) & 0x0000FFFF);
        } catch (MARSHAL marshalException) {
            handleOptionalDataMarshalException(marshalException, false);
            throw marshalException;
        } catch(Error e) {
	    IOException exc = new IOException(e.getMessage());
	    exc.initCause(e);
	    throw exc ;
	}
    }

    /**
     * Helper method for correcting the Kestrel bug 4367783 (dealing
     * with larger than 8-bit chars).  The old behavior was preserved
     * in orbutil.IIOPInputStream_1_3 in order to interoperate with
     * our legacy ORBs.
     */
    @TraceValueHandler
    @ValueHandlerRead
    protected String internalReadUTF(org.omg.CORBA.portable.InputStream stream)
    {
        return Return.value(stream.read_wstring());
    }

    @TraceValueHandler
    @ValueHandlerRead
    @Override
    public final String readUTF() throws IOException{
        try{
            readObjectState.readData(this);

            return Return.value(internalReadUTF(orbStream));
        } catch (MARSHAL marshalException) {
            handleOptionalDataMarshalException(marshalException, false);
            throw marshalException;
        } catch(Error e) {
	    IOException exc = new IOException(e.getMessage());
	    exc.initCause(e);
	    throw exc ;
	}
    }

    // If the ORB stream detects an incompatibility between what's
    // on the wire and what our Serializable's readObject wants,
    // it throws a MARSHAL exception with a specific minor code.
    // This is rethrown to the readObject as an OptionalDataException.
    // So far in RMI-IIOP, this process isn't specific enough to
    // tell the readObject how much data is available, so we always
    // set the OptionalDataException's EOF marker to true.
    private void handleOptionalDataMarshalException(MARSHAL marshalException,
                                                    boolean objectRead)
        throws IOException {

        // Java Object Serialization spec 3.4: "If the readObject method
        // of the class attempts to read more data than is present in the
        // optional part of the stream for this class, the stream will
        // return -1 for bytewise reads, throw an EOFException for
        // primitive data reads, or throw an OptionalDataException
        // with the eof field set to true for object reads."
        if (marshalException.minor 
            == OMGSystemException.RMIIIOP_OPTIONAL_DATA_INCOMPATIBLE1) {

            IOException result;

            if (!objectRead)
                result = new EOFException("No more optional data");
            else
                result = createOptionalDataException();

            result.initCause(marshalException);

            setState(IN_READ_OBJECT_NO_MORE_OPT_DATA);

            throw result;
        }
    }

    @Override
    public final synchronized void registerValidation(ObjectInputValidation obj,
						      int prio)
	throws NotActiveException, InvalidObjectException{
	// XXX I18N, logging needed.
        throw new Error("Method registerValidation not supported");
    }

    @Override
    protected final Class resolveClass(java.io.ObjectStreamClass v)
	throws IOException, ClassNotFoundException{
	// XXX I18N, logging needed.
        throw new IOException("Method resolveClass not supported");
    }

    @Override
    protected final Object resolveObject(Object obj) throws IOException{
	// XXX I18N, logging needed.
        throw new IOException("Method resolveObject not supported");
    }

    @TraceValueHandler
    @ValueHandlerRead
    @Override
    public final int skipBytes(int len) throws IOException{
        try{
            readObjectState.readData(this);

            byte buf[] = new byte[len];
            orbStream.read_octet_array(buf, 0, len);
            return Return.value(len);
        } catch (MARSHAL marshalException) {
            handleOptionalDataMarshalException(marshalException, false);
            
            throw marshalException;
        } catch(Error e) {
            IOException exc = new IOException(e.getMessage());
	    exc.initCause(e) ;
	    throw exc ;
        }
    }

    @TraceValueHandler
    @ValueHandlerRead
    private Object inputObject(Class clz, ClassInfoCache.ClassInfo cinfo,
        String repositoryID, com.sun.org.omg.SendingContext.CodeBase sender, 
	int offset) throws IOException, ClassNotFoundException {

        if (valueHandlerDebug()) {
            dputil.enter( "inputObject", "clz", clz, 
                "repositoryID", repositoryID, "offset", offset ) ;
        }

        int spBase = spClass ;
        try {
            currentClassDesc = ObjectStreamClass.lookup(clz);
            currentClass = currentClassDesc.forClass();
            
            if (valueHandlerDebug()) {
                dputil.info( "ObjectStreamClass.lookup called for " + clz ) ;
            }
            
            // KMC start of enum receiver-makes-right changes
            if (cinfo.isEnum()) {
                if (valueHandlerDebug()) {
                    dputil.info( "reading Enum" ) ;
                }

                // Only for backwards compatibility with JDK: 
                // int ordinal = orbStream.read_long() ;
                String value = (String)orbStream.read_value( String.class ) ;
                // Need to skip any other data marshaled from the enum, 
                // if the enum type has non-static non-transient state.
                return Return.value(Enum.valueOf( clz, value ) );
            } else if (currentClassDesc.isExternalizable()) {
                if (valueHandlerDebug()) {
                    dputil.info( "reading Externalizable object" ) ;
                }

                try {
                    if (valueHandlerDebug()) {
                        dputil.info( "Creating new instance of ", currentClass ) ;
                    }

                    currentObject = (currentClass == null) ?
                        null : currentClassDesc.newInstance();

                    if (currentObject != null) {
                        // Store this object and its beginning position
                        // since there might be indirections to it while
                        // it is being unmarshalled.
                        activeRecursionMgr.addObject(offset, currentObject);

                        // Read format version
                        readFormatVersion();

                        Externalizable ext = (Externalizable)currentObject;

                        // KMC issue 5161: just as in the IIOPOutputStream, we must
                        // save and restore the state for reading as well!
                        ReadObjectState oldState = readObjectState;
                        setState(DEFAULT_STATE);
                        if (valueHandlerDebug()) 
                            dputil.info( "Calling readExternal" ) ;

                        try {
                            ext.readExternal(this);
                        } finally {
                            setState(oldState) ;
                            if (valueHandlerDebug()) 
                                dputil.info( "Returned from readExternal" ) ;
                        }
                    }
                } catch (InvocationTargetException e) {
                    InvalidClassException exc = new InvalidClassException(
                        currentClass.getName(), 
                        "InvocationTargetException accessing no-arg constructor");
                    exc.initCause( e ) ;
                    throw exc ;
                } catch (UnsupportedOperationException e) {
                    InvalidClassException exc = new InvalidClassException(
                        currentClass.getName(), 
                        "UnsupportedOperationException accessing no-arg constructor");
                    exc.initCause( e ) ;
                    throw exc ;
                } catch (InstantiationException e) {
                    InvalidClassException exc = new InvalidClassException(
                        currentClass.getName(), 
                        "InstantiationException accessing no-arg constructor");
                    exc.initCause( e ) ;
                    throw exc ;
                }
            } else {
                if (valueHandlerDebug())
                    dputil.info( "reading Serializable object" ) ;

                ObjectStreamClass currdesc = currentClassDesc;
                Class currclass = currentClass;

                // MS: cstack.mark() ;
                spBase = spClass;	// current top of stack

                for (currdesc = currentClassDesc, currclass = currentClass;
                     currdesc != null && currdesc.isSerializable();   
                     currdesc = currdesc.getSuperclass()) {

                    if (valueHandlerDebug())
                        dputil.info( "currentClassDesc", currentClassDesc, "currentClass", 
                            currentClass ) ;

                    Class cc = currdesc.forClass();
                    Class cl;
                    for (cl = currclass; cl != null; cl = cl.getSuperclass()) {
                        if (cc == cl) {
                            if (valueHandlerDebug()) {
                                dputil.info( "Matching superclass", cl ) ;
                            }

                            break;
                        } 
                    } 

                    // MS: cstack.push( new Pair<Class,ObjectStreamClass>( cl, currdesc ) ) ;
                    // if (cl != null)
                    //      currclass = cl.getSuperclass() ;
                    spClass++;
                    if (spClass >= classes.length) {
                        int newlen = classes.length * 2;
                        Class[] newclasses = new Class[newlen];
                        ObjectStreamClass[] newclassdesc = new ObjectStreamClass[newlen];

                        System.arraycopy(classes, 0, newclasses, 0, classes.length);
                        System.arraycopy(classdesc, 0, newclassdesc, 0, classes.length);

                        classes = newclasses;
                        classdesc = newclassdesc;
                    }

                    if (cl == null) {
                        classdesc[spClass] = currdesc;
                        classes[spClass] = null;
                    } else {
                        classdesc[spClass] = currdesc;
                        classes[spClass] = cl;
                        currclass = cl.getSuperclass();
                    }
                } // end : for (currdesc = currentClassDesc, currclass = currentClass;

                try {
                    if (valueHandlerDebug()) {
                        dputil.info( "Creating new instance of ", currentClass ) ;
                    }

                    currentObject = (currentClass == null) ?
                        null : currentClassDesc.newInstance() ;

                    // Store this object and its beginning position
                    // since there might be indirections to it while
                    // it's been unmarshalled.
                    activeRecursionMgr.addObject(offset, currentObject);
                } catch (InvocationTargetException e) {
                    InvalidClassException exc = new InvalidClassException(
                        currentClass.getName(), 
                        "InvocationTargetException accessing no-arg constructor");
                    exc.initCause( e ) ;
                    throw exc ;
                } catch (UnsupportedOperationException e) {
                    InvalidClassException exc = new InvalidClassException(
                        currentClass.getName(), 
                        "UnsupportedOperationException accessing no-arg constructor");
                    exc.initCause( e ) ;
                    throw exc ;
                } catch (InstantiationException e) {
                    InvalidClassException exc = new InvalidClassException(
                        currentClass.getName(), 
                        "InstantiationException accessing no-arg constructor");
                    exc.initCause( e ) ;
                    throw exc ;
                }

                // MS: while (!cstack.isEmpty) 
                //      Pair<Class,ObjectStreamClass> pair = cstack.pop() ;
                for (spClass = spClass; spClass > spBase; spClass--) {
                    // MS: currentClassDesc = pair.second() ;
                    // currentClass = pair.first() ;
                    currentClassDesc = classdesc[spClass];
                    currentClass = classes[spClass];

                    if (valueHandlerDebug()) 
                        dputil.info( "Reading data for class", currentClass ) ;
                    
                    // MS: if (currentClass != null) 
                    if (classes[spClass] != null) {
                        ReadObjectState oldState = readObjectState;
                        setState(DEFAULT_STATE);

                        try {
                            if (currentClassDesc.hasWriteObject()) {
                                if (valueHandlerDebug())
                                    dputil.info( "Class has writeObject" ) ;

                                readFormatVersion();

                                // Read defaultWriteObject indicator
                                boolean calledDefaultWriteObject = readBoolean();

                                readObjectState.beginUnmarshalCustomValue( this, 
                                    calledDefaultWriteObject, 
                                    (currentClassDesc.readObjectMethod != null));
                            } else {
                                if (valueHandlerDebug())
                                    dputil.info( "Class does not have writeObject" ) ;

                                if (currentClassDesc.hasReadObject())
                                    setState(IN_READ_OBJECT_REMOTE_NOT_CUSTOM_MARSHALED);
                            }

                            if (!invokeObjectReader(currentClassDesc, currentObject, 
                                currentClass, null) ||
                                readObjectState == IN_READ_OBJECT_DEFAULTS_SENT) {

                                // Error case of no readObject and didn't call
                                // defaultWriteObject handled in default state
                                ObjectStreamField[] fields =
                                    currentClassDesc.getFieldsNoCopy();

                                if (fields.length > 0) {
                                    inputClassFields(currentObject, currentClass, fields, sender);
                                }
                            }

                            if (currentClassDesc.hasWriteObject())
                                readObjectState.endUnmarshalCustomValue(this);
                        } finally {
                            setState(oldState);
                        }
                    } else {
                        // _REVISIT_ : Can we ever get here?
                        /* No local class for this descriptor,
                         * Skip over the data for this class.
                         * like defaultReadObject with a null currentObject.
                         * The code will read the values but discard them.
                         */
                        ObjectStreamField[] fields = 
                            currentClassDesc.getFieldsNoCopy();

                        if (fields.length > 0) {
                            inputClassFields(null, currentClass, fields, sender);
                        }
                    }
                }
            }
        } finally {
            // Make sure we exit at the same stack level as when we started.
            // MS: cstack.popMark() ;
            spClass = spBase;
            
            // We've completed deserializing this object.  Any
            // future indirections will be handled correctly at the
            // CDR level.  The ActiveRecursionManager only deals with
            // objects currently being deserialized.
            activeRecursionMgr.removeObject(offset);

            if (valueHandlerDebug())
                dputil.exit() ;
        }
        return Return.value(currentObject);
    }

    // This retrieves a vector of FVD's for the hierarchy of serializable 
    // classes stemming from repositoryID.  It is assumed that the sender 
    // will not provide base_value id's for non-serializable classes!
    @TraceValueHandler
    @ValueHandlerRead
    private List<FullValueDescription> getOrderedDescriptions(
	String repositoryID, com.sun.org.omg.SendingContext.CodeBase sender) {

        if (valueHandlerDebug()) {
            dputil.enter( "getOrderedDescriptions" ) ;
        }

        try {
            List<FullValueDescription> descs = 
                new ArrayList<FullValueDescription>();

            if (sender == null) {
                return Return.value(descs);
            }
            
            FullValueDescription aFVD = sender.meta(repositoryID);
            while (aFVD != null) {
                descs.add(0, aFVD);
                if ((aFVD.base_value != null) && !kEmptyStr.equals(aFVD.base_value)) {
                    aFVD = sender.meta(aFVD.base_value);
                } else 
                    return Return.value(descs);
            }

            if (valueHandlerDebug()) {
                dputil.info( "result:" ) ;
                for (FullValueDescription fvd : descs)  {
                    dputil.info( "\t", displayFVD( fvd ) ) ;
                }
            }

            return Return.value(descs);
        } finally {
            if (valueHandlerDebug()) {
                dputil.exit() ;
            }
        }
    }

    private String displayFVD( FullValueDescription fvd ) {
        StringBuilder sb = new StringBuilder() ;
        sb.append( "FVD(" ) ;
        sb.append( "\n\tname=" ) ;
        sb.append( fvd.name ) ;
        sb.append( "\n\tid=" ) ;
        sb.append( fvd.id ) ;
        sb.append( "\n\tis_abstract=" ) ;
        sb.append( fvd.is_abstract ) ;
        sb.append( "\n\tis_custom=" ) ;
        sb.append( fvd.is_custom ) ;
        sb.append( "\n\tdefined_in=" ) ;
        sb.append( fvd.defined_in ) ;
        sb.append( "\n\tversion=" ) ;
        sb.append( fvd.version ) ;
        sb.append( "\n\tis_truncatable=" ) ;
        sb.append( fvd.is_truncatable ) ;
        sb.append( "\n\tbase_value=" ) ;
        sb.append( fvd.base_value ) ;

        int ctr = 0 ;
        sb.append( "\n\toperations:" ) ;
        for (OperationDescription opdesc : fvd.operations) {
            sb.append( "\n\t    [" ) ;
            sb.append( ctr++ ) ;
            sb.append( "]" ) ;
            sb.append( "\n\t\tname=" ) ;
            sb.append( opdesc.name ) ;
            sb.append( "\n\t\tid=" ) ;
            sb.append( opdesc.id ) ;
            sb.append( "\n\t\tdefined_in=" ) ;
            sb.append( opdesc.defined_in ) ;
            sb.append( "\n\t\tversion=" ) ;
            sb.append( opdesc.version ) ;
            sb.append( "\n\t\tmode=" ) ;
            sb.append( opdesc.mode ) ;

            int ctr2 = 0 ;
            sb.append( "\n\t\tcontexts=" ) ;
            for (String str : opdesc.contexts) {
                sb.append( "\n\t\t    [" ) ;
                sb.append( ctr2++ ) ;
                sb.append( "]" ) ;
                sb.append( "\n\t\t" ) ;
                sb.append( str ) ;
            }

            ctr2 = 0 ;
            sb.append( "\n\t\tparameters" ) ;
            for (ParameterDescription pdesc : opdesc.parameters) {
                sb.append( "\n\t\t    [" ) ;
                sb.append( ctr2++ ) ;
                sb.append( "]" ) ;
                sb.append( "\n\t\t\tname=" ) ;
                sb.append( pdesc.name ) ;
                sb.append( "\n\t\t\tmode=" ) ;
                sb.append( pdesc.mode ) ;
            }

            ctr2 = 0 ;
            sb.append( "\n\t\texceptions" ) ;
            for (ExceptionDescription edesc : opdesc.exceptions) {
                sb.append( "\n\t\t    [" ) ;
                sb.append( ctr2++ ) ;
                sb.append( "]" ) ;
                sb.append( "\n\t\t\tname=" ) ;
                sb.append( edesc.name ) ;
                sb.append( "\n\t\t\tid=" ) ;
                sb.append( edesc.id ) ;
                sb.append( "\n\t\t\tdefined_in=" ) ;
                sb.append( edesc.defined_in ) ;
                sb.append( "\n\t\t\tversion=" ) ;
                sb.append( edesc.version ) ;
            }
        }

        ctr = 0 ;
        sb.append( "\n\tattributes:" ) ;
        for (AttributeDescription atdesc : fvd.attributes) {
            sb.append( "\n\t    [" ) ;
            sb.append( ctr++ ) ;
            sb.append( "]" ) ;
            sb.append( "\n\t\t\tname=" ) ;
            sb.append( atdesc.name ) ;
            sb.append( "\n\t\t\tid=" ) ;
            sb.append( atdesc.id ) ;
            sb.append( "\n\t\t\tdefined_in=" ) ;
            sb.append( atdesc.defined_in ) ;
            sb.append( "\n\t\t\tversion=" ) ;
            sb.append( atdesc.version ) ;
            sb.append( "\n\t\t\tmode=" ) ;
            sb.append( atdesc.mode ) ;
        }

        ctr = 0 ;
        sb.append( "\n\tmembers:" ) ;
        for (ValueMember vm : fvd.members) {
            sb.append( "\n\t    [" ) ;
            sb.append( ctr++ ) ;
            sb.append( "]" ) ;
            sb.append( "\n\t\tname=" ) ;
            sb.append( vm.name ) ;
            sb.append( "\n\t\tid=" ) ;
            sb.append( vm.id ) ;
            sb.append( "\n\t\tdefined_in=" ) ;
            sb.append( vm.defined_in ) ;
            sb.append( "\n\t\tversion=" ) ;
            sb.append( vm.version ) ;
            sb.append( "\n\t\taccess=" ) ;
            sb.append( vm.access ) ;
        }

        // Ignore for now
        // for (Initializer init : fvd.initializers) {
        // }

        ctr = 0 ;
        sb.append( "\n\tsupported_interfaces:" ) ;
        for (String str : fvd.supported_interfaces) {
            sb.append( "\n\t    [" ) ;
            sb.append( ctr++ ) ;
            sb.append( "]" ) ;
            sb.append( "\n\t\t" ) ;
            sb.append( str ) ;
        }

        ctr = 0 ;
        sb.append( "\n\tabstract_base_values:" ) ;
        for (String str : fvd.abstract_base_values) {
            sb.append( "\n\t    [" ) ;
            sb.append( ctr++ ) ;
            sb.append( "]" ) ;
            sb.append( "\n\t\t" ) ;
            sb.append( str ) ;
        }

        sb.append( "\n)" ) ;
        return sb.toString() ;
    }

    /**
     * This input method uses FullValueDescriptions retrieved from the sender's runtime to 
     * read in the data.  This method is capable of throwing out data not applicable to client's fields.
     * This method handles instances where the reader has a class not sent by the sender, the sender sent
     * a class not present on the reader, and/or the reader's class does not match the sender's class.
     *
     * NOTE : If the local description indicates custom marshaling and the remote type's FVD also
     * indicates custom marsahling than the local type is used to read the data off the wire.  However,
     * if either says custom while the other does not, a MARSHAL error is thrown.  Externalizable is 
     * a form of custom marshaling.
     *
     */
    @TraceValueHandler
    @ValueHandlerRead
    private Object inputObjectUsingFVD(final Class clz, 
        final ClassInfoCache.ClassInfo cinfo,
        final String repositoryID, final com.sun.org.omg.SendingContext.CodeBase sender, 
        final int offset) throws IOException, ClassNotFoundException {

        if (valueHandlerDebug()) {
            dputil.enter( "inputObjectUsingFVD", "clz", clz, 
                "repositoryID", repositoryID, "offset", offset ) ;
        }

        int spBase  = spClass ; 
	try {
	    currentClassDesc = ObjectStreamClass.lookup(clz);
	    currentClass = currentClassDesc.forClass();

            if (valueHandlerDebug()) {
                dputil.info( "ObjectStreamClass.lookup called for " + clz ) ;
            }

            // KMC start of enum receiver-makes-right changes
            if (cinfo.isEnum()) {
                if (valueHandlerDebug()) {
                    dputil.info( "reading Enum" ) ;
                }

                // Only for backwards compatibility with JDK: int ordinal = orbStream.read_long() ;
                String value = (String)orbStream.read_value( String.class ) ;
                // Need to skip any other data marshaled from the enum, if the enum type has non-static non-transient state.
                return Return.value(Enum.valueOf( clz, value ) );
            } else if (currentClassDesc.isExternalizable()) {
                if (valueHandlerDebug()) {
                    dputil.info( "reading Externalizable object" ) ;
                }

		try {
                    if (valueHandlerDebug()) {
                        dputil.info( "Creating instance of ", currentClassDesc ) ;
                    }

		    currentObject = (currentClass == null) ?
			null : currentClassDesc.newInstance();

		    if (currentObject != null) {
                        // Store this object and its beginning position
                        // since there might be indirections to it while
                        // it's been unmarshalled.
                        activeRecursionMgr.addObject(offset, currentObject);

			// Read format version
			readFormatVersion();
						
			Externalizable ext = (Externalizable)currentObject;

                        // KMC issue 5161: just as in the IIOPOutputStream, we must
                        // save and restore the state for reading as well!
                        ReadObjectState oldState = readObjectState;
                        setState(DEFAULT_STATE);
                        if (valueHandlerDebug()) 
                            dputil.info( "Calling readExternal" ) ;

                        try {
                            ext.readExternal(this);
                        } finally {
                            setState(oldState) ;
                            if (valueHandlerDebug()) 
                                dputil.info( "Returned from readExternal" ) ;
                        }
		    }
		} catch (InvocationTargetException e) {
		    InvalidClassException exc = new InvalidClassException(
			currentClass.getName(), 
			"InvocationTargetException accessing no-arg constructor");
		    exc.initCause( e ) ;
		    throw exc ;
		} catch (UnsupportedOperationException e) {
		    InvalidClassException exc = new InvalidClassException(
			currentClass.getName(), 
			"UnsupportedOperationException accessing no-arg constructor");
		    exc.initCause( e ) ;
		    throw exc ;
		} catch (InstantiationException e) {
		    InvalidClassException exc = new InvalidClassException(
			currentClass.getName(), 
			"InstantiationException accessing no-arg constructor");
		    exc.initCause( e ) ;
		    throw exc ;
		}
	    } else {
                if (valueHandlerDebug())
                    dputil.info( "reading Serializable object" ) ;

                ObjectStreamClass currdesc = currentClassDesc ;
                Class currclass = currentClass = clz;
                
                // MS: cstack.mark() ;
                spBase = spClass;	// current top of stack

		for (currdesc = currentClassDesc, currclass = currentClass;
		     currdesc != null && currdesc.isSerializable();   
		     currdesc = currdesc.getSuperclass()) {

                    if (valueHandlerDebug()) {
                        dputil.info( "Processing class desc" + currdesc ) ;
                    }
					
		    Class cc = currdesc.forClass();
		    Class cl;
		    for (cl = currclass; cl != null; cl = cl.getSuperclass()) {
			if (cc == cl) {
                            if (valueHandlerDebug()) {
                                dputil.info( "Matching superclass is " + cl ) ;
                            }
                    
			    break;
                        }
		    } 

                    // MS: cstack.push( new Pair<Class,ObjectStreamClass>( cl, currdesc ) ) ;
                    // if (cl != null)
                    //      currclass = cl.getSuperclass() ;
		    spClass++;
		    if (spClass >= classes.length) {
			int newlen = classes.length * 2;
			Class[] newclasses = new Class[newlen];
			ObjectStreamClass[] newclassdesc = new ObjectStreamClass[newlen];
				
			System.arraycopy(classes, 0, newclasses, 0, classes.length);
			System.arraycopy(classdesc, 0, newclassdesc, 0, classes.length);
						
			classes = newclasses;
			classdesc = newclassdesc;
    		    }

		    if (cl == null) {
			classdesc[spClass] = currdesc;
			classes[spClass] = null;
		    } else {
			classdesc[spClass] = currdesc;
			classes[spClass] = cl;
			currclass = cl.getSuperclass();
		    }
		} // end : for (currdesc = currentClassDesc, currclass = currentClass;
				
		try {
                    if (valueHandlerDebug()) {
                        dputil.info( "Creating instance of " + currentClassDesc ) ;
                    }

		    currentObject = (currentClass == null) ?
			null : currentClassDesc.newInstance();

                    // Store this object and its beginning position
                    // since there might be indirections to it while
                    // it's been unmarshalled.
                    activeRecursionMgr.addObject(offset, currentObject);
		} catch (InvocationTargetException e) {
		    InvalidClassException exc = new InvalidClassException(
			currentClass.getName(), 
			"InvocationTargetException accessing no-arg constructor");
		    exc.initCause( e ) ;
		    throw exc ;
		} catch (UnsupportedOperationException e) {
		    InvalidClassException exc = new InvalidClassException(
			currentClass.getName(), 
			"UnsupportedOperationException accessing no-arg constructor");
		    exc.initCause( e ) ;
		    throw exc ;
		} catch (InstantiationException e) {
		    InvalidClassException exc = new InvalidClassException(
			currentClass.getName(), 
			"InstantiationException accessing no-arg constructor");
		    exc.initCause( e ) ;
		    throw exc ;
		}
				
		Iterator<FullValueDescription> fvdsList = 
		    getOrderedDescriptions(repositoryID, sender).iterator();
				
                // MS: while (fvdsList.hasNext() && !cstack.isEmpty())
		while((fvdsList.hasNext()) && (spClass > spBase)) {
		    FullValueDescription fvd = fvdsList.next();
                    if (valueHandlerDebug()) {
                        dputil.info( "fvd = " + displayFVD( fvd ) ) ;
                    }

		    String repIDForFVD = vhandler.getClassName( fvd.id);
		    String repIDForClass = vhandler.getClassName(
			vhandler.getRMIRepositoryID(currentClass));
					
                    // MS: while (!cstack.isEmpty() &&
		    while ((spClass > spBase) &&
			   (!repIDForFVD.equals(repIDForClass))) {
                        // MS: while (!cstack.isEmpty() && !cstack.peek().first().getName.equals( repIDForFVD ))
                        //      cstack.pop() ;
			int pos = findNextClass(repIDForFVD, classes, spClass, spBase);
                        // if (!cstack.isEmpty()) 
                        //     currClass = currentClass = cstack.peek.first() ;
			if (pos != -1) {
			    spClass = pos;
			    currclass = currentClass = classes[spClass];
			    repIDForClass = vhandler.getClassName(
				vhandler.getRMIRepositoryID(currentClass));
			} else { 
                            // Read and throw away one level of the fvdslist
                            // This seems to mean that the sender had a superclass that
                            // we don't have

			    if (fvd.is_custom) {
                                readFormatVersion();
                                boolean calledDefaultWriteObject = readBoolean();

                                if (calledDefaultWriteObject)
                                    inputClassFields(null, null, null, fvd.members, sender);

                                if (getStreamFormatVersion() == 2) {

                                    ((ValueInputStream)getOrbStream()).start_value();
                                    ((ValueInputStream)getOrbStream()).end_value();
                                }

                                // WARNING: If stream format version is 1 and there's
                                // optional data, we'll get some form of exception down
                                // the line or data corruption.
			    } else {
				inputClassFields(null, currentClass, null, fvd.members, sender);
			    }

			    if (fvdsList.hasNext()){
				fvd = fvdsList.next();
				repIDForFVD = vhandler.getClassName(fvd.id);
			    } else {
                                return Return.value(currentObject);
                            }
			}
		    }

		    currdesc = currentClassDesc = ObjectStreamClass.lookup(currentClass);

                    if (valueHandlerDebug()) {
                        dputil.info( "ObjectStreamClass.lookup called for " + clz ) ;
                    }

		    if (!repIDForClass.equals("java.lang.Object")) {

                        // If the sender used custom marshaling, then it should have put
                        // the two bytes on the wire indicating stream format version
                        // and whether or not the writeObject method called 
                        // defaultWriteObject/writeFields.

                        ReadObjectState oldState = readObjectState;
                        setState(DEFAULT_STATE);

                        try {
                            if (fvd.is_custom) {
                                readFormatVersion();
                                boolean calledDefaultWriteObject = readBoolean();
                                readObjectState.beginUnmarshalCustomValue( this, 
				    calledDefaultWriteObject, 
				    (currentClassDesc.readObjectMethod != null));
                            }

                            boolean usedReadObject = false;

                            // Always use readObject if it exists, and fall back to default
                            // unmarshaling if it doesn't.
                            if (!fvd.is_custom && currentClassDesc.hasReadObject())
                                setState(IN_READ_OBJECT_REMOTE_NOT_CUSTOM_MARSHALED);

                            usedReadObject = invokeObjectReader(currentClassDesc, 
                                currentObject, currentClass, fvd.members );

                            // Note that the !usedReadObject !calledDefaultWriteObject
                            // case is handled by the beginUnmarshalCustomValue method
                            // of the default state
                            if (!usedReadObject || readObjectState == IN_READ_OBJECT_DEFAULTS_SENT)
                                inputClassFields(currentObject, currentClass, currdesc, 
				    fvd.members, sender);

                            if (fvd.is_custom)
                                readObjectState.endUnmarshalCustomValue(this);
                        } finally {
                            setState(oldState);
                        }
                           
                        currclass = currentClass = classes[--spClass];
		    } else { 
			// The remaining hierarchy of the local class does not match the sender's FVD.
			// So, use remaining FVDs to read data off wire.  If any remaining FVDs indicate
			// custom marshaling, throw MARSHAL error.
			inputClassFields(null, currentClass, null, fvd.members, sender);
						
			while (fvdsList.hasNext()){
			    fvd = fvdsList.next();

                            if (fvd.is_custom)
                                skipCustomUsingFVD(fvd.members, sender);
			    else
                                inputClassFields(null, currentClass, null, fvd.members, sender);
			}
		    }
		} // end : while(fvdsList.hasNext()) 

		while (fvdsList.hasNext()){
		    FullValueDescription fvd = fvdsList.next();
		    if (fvd.is_custom)
                        skipCustomUsingFVD(fvd.members, sender);
		    else 
                        throwAwayData(fvd.members, sender);			
		}
	    }
	} finally {
            // Make sure we exit at the same stack level as when we started.
            // MS: cstack.popMark() ;
            spClass = spBase;

            // We've completed deserializing this object.  Any
            // future indirections will be handled correctly at the
            // CDR level.  The ActiveRecursionManager only deals with
            // objects currently being deserialized.
            activeRecursionMgr.removeObject(offset);

            if (valueHandlerDebug())
                dputil.exit() ;
        }

        return Return.value(currentObject);
    }

    /**
     * This input method uses FullValueDescriptions retrieved from the sender's runtime to 
     * read in the data.  This method is capable of throwing out data not applicable to client's fields.
     *
     * NOTE : If the local description indicates custom marshaling and the remote type's FVD also
     * indicates custom marsahling than the local type is used to read the data off the wire.  However,
     * if either says custom while the other does not, a MARSHAL error is thrown.  Externalizable is 
     * a form of custom marshaling.
     *
     */
    @TraceValueHandler
    @ValueHandlerRead
    private Object skipObjectUsingFVD(String repositoryID,
	com.sun.org.omg.SendingContext.CodeBase sender)
	throws IOException, ClassNotFoundException {

	for (FullValueDescription fvd : 
	    getOrderedDescriptions( repositoryID, sender )) {

	    String repIDForFVD = vhandler.getClassName(fvd.id);
			
	    if (!repIDForFVD.equals("java.lang.Object")) {
		if (fvd.is_custom) {
                    readFormatVersion();
                        
                    boolean calledDefaultWriteObject = readBoolean();
                        
                    if (calledDefaultWriteObject)
                        inputClassFields(null, null, null, fvd.members, sender);

                    if (getStreamFormatVersion() == 2) {
                        ((ValueInputStream)getOrbStream()).start_value();
                        ((ValueInputStream)getOrbStream()).end_value();
                    }

                    // WARNING: If stream format version is 1 and there's
                    // optional data, we'll get some form of exception down
                    // the line.
		} else { 
		    // Use default marshaling
		    inputClassFields(null, null, null, fvd.members, sender);
		}
	    }

	} 

	return Return.value(null);
    }

    ///////////////////

    @TraceValueHandler
    @ValueHandlerRead
    private int findNextClass(String classname, Class classes[], int _spClass, int _spBase){

	for (int i = _spClass; i > _spBase; i--){
	    if (classname.equals(classes[i].getName())) {
		return Return.value(i);
	    }
	}

	return Return.value(-1);
    }

    /*
     * Invoke the readObject method if present.  Assumes that in the case of custom
     * marshaling, the format version and defaultWriteObject indicator were already
     * removed.
     */
    @TraceValueHandler
    @ValueHandlerRead
    private boolean invokeObjectReader(ObjectStreamClass osc, Object obj, Class aclass,
        ValueMember[] valueMembers )
	throws InvalidClassException, StreamCorruptedException,
	       ClassNotFoundException, IOException
    {
        OperationTracer.readingField( "<<readObject>>" ) ;

        if (valueHandlerDebug())
            dputil.enter( "invokeObjectReader" ) ;

        boolean result = false ;

        try {
            if (osc.readObjectMethod != null) {
                try {
                    defaultReadObjectFVDMembers = valueMembers ;
                    osc.readObjectMethod.invoke( obj, this ) ;
                    result = true ;
                } catch (InvocationTargetException e) {
                    Throwable t = e.getTargetException();
                    if (t instanceof ClassNotFoundException)
                        throw (ClassNotFoundException)t;
                    else if (t instanceof IOException)
                        throw (IOException)t;
                    else if (t instanceof RuntimeException)
                        throw (RuntimeException) t;
                    else if (t instanceof Error)
                        throw (Error) t;
                    else
                        // XXX logging needed.
                        throw new Error("internal error");
                } catch (IllegalAccessException e) {
                    // XXX logging needed.
                } finally {
                    // Make sure this is cleared no matter what
                    // the readObject method does.  If the readObject
                    // method calls either defaultReadObject or
                    // getFields, this will be cleared.  Any other
                    // behavior is an error, but we want to protect
                    // ourselves from a bad readObject method.
                    defaultReadObjectFVDMembers = null ;
                }
            }

            return Return.value( result ) ;
        } finally {
            if (valueHandlerDebug())
                dputil.exit( result ) ;
        }
    }

    /*
     * Reset the stream to be just like it was after the constructor.
     */
    @TraceValueHandler
    @ValueHandlerRead
    private void resetStream() throws IOException {

	if (classes == null)
	    classes = new Class[20];
	else {
	    for (int i = 0; i < classes.length; i++)
		classes[i] = null;
	}
	if (classdesc == null)
	    classdesc = new ObjectStreamClass[20];
	else {
	    for (int i = 0; i < classdesc.length; i++)
		classdesc[i] = null;
	}
	spClass = 0;

	if (callbacks != null)
	    callbacks.clear(); // discard any pending callbacks
    }

    /**
     * Factored out of inputClassFields  This reads a primitive value and sets it 
     * in the field of o described by the ObjectStreamField field.
     * 
     * Note that reflection cannot be used here, because reflection cannot be used
     * to set final fields. 
     */
    @TraceValueHandler
    @ValueHandlerRead
    private void inputPrimitiveField(Object o, Class cl, ObjectStreamField field)
        throws InvalidClassException, IOException {

        try {
            switch (field.getTypeCode()) {
                case 'B':
                    byte byteValue = orbStream.read_octet();
		    bridge.putByte( o, field.getFieldID(), byteValue ) ;
		    //reflective code: field.getField().setByte( o, byteValue ) ;
                    break;
                case 'Z':
                    boolean booleanValue = orbStream.read_boolean();
		    bridge.putBoolean( o, field.getFieldID(), booleanValue ) ;
		    //reflective code: field.getField().setBoolean( o, booleanValue ) ;
                    break;
		case 'C':
                    char charValue = orbStream.read_wchar();
		    bridge.putChar( o, field.getFieldID(), charValue ) ;
		    //reflective code: field.getField().setChar( o, charValue ) ;
                    break;
		case 'S':
                    short shortValue = orbStream.read_short();
		    bridge.putShort( o, field.getFieldID(), shortValue ) ;
		    //reflective code: field.getField().setShort( o, shortValue ) ;
                    break;
		case 'I':
                    int intValue = orbStream.read_long();
		    bridge.putInt( o, field.getFieldID(), intValue ) ;
		    //reflective code: field.getField().setInt( o, intValue ) ;
                    break;
		case 'J':
                    long longValue = orbStream.read_longlong();
		    bridge.putLong( o, field.getFieldID(), longValue ) ;
		    //reflective code: field.getField().setLong( o, longValue ) ;
                    break;
		case 'F' :
                    float floatValue = orbStream.read_float();
		    bridge.putFloat( o, field.getFieldID(), floatValue ) ;
		    //reflective code: field.getField().setFloat( o, floatValue ) ;
                    break;
		case 'D' :
                    double doubleValue = orbStream.read_double();
		    bridge.putDouble( o, field.getFieldID(), doubleValue ) ;
		    //reflective code: field.getField().setDouble( o, doubleValue ) ;
                    break;
		default:
		    // XXX I18N, logging needed.
                    throw new InvalidClassException(cl.getName());
            }
        } catch (IllegalArgumentException e) {
            /* This case should never happen. If the field types
               are not the same, InvalidClassException is raised when
               matching the local class to the serialized ObjectStreamClass. */
            ClassCastException cce = new ClassCastException(
		"Assigning instance of class " 
		+ field.getType().getName() + " to field " 
		+ currentClassDesc.getName() + '#' 
		+ field.getField().getName());
	    cce.initCause( e ) ;
	    throw cce ;
	}
    }

    @TraceValueHandler
    @ValueHandlerRead
    private Object inputObjectField(org.omg.CORBA.ValueMember field,
	com.sun.org.omg.SendingContext.CodeBase sender)
        throws IndirectionException, ClassNotFoundException, IOException,
               StreamCorruptedException {

        if (valueHandlerDebug()) {
            dputil.enter( "inputObjectField(ValueMember)" ) ;
        }

        try {
            Object objectValue = null;
            Class type = null;
            String id = field.id;
                                                            
            try {
                type = vhandler.getClassFromType(id);
            } catch(ClassNotFoundException cnfe) {
                // Make sure type = null
                type = null;
            }

            if (valueHandlerDebug()) {
                dputil.info( "type =", type ) ;
            }

            String signature = null;
            if (type != null)
                signature = ValueUtility.getSignature(field);
                                                                    
            if (signature != null && (signature.equals("Ljava/lang/Object;") 
                || signature.equals("Ljava/io/Serializable;") 
                || signature.equals("Ljava/io/Externalizable;"))) {
                objectValue = Util.getInstance().readAny(orbStream);
            } else {
                // Decide what method call to make based on the type. If
                // it is a type for which we need to load a stub, convert
                // the type to the correct stub type.
                //
                // NOTE : Since FullValueDescription does not allow us
                // to ask whether something is an interface we do not
                // have the ability to optimize this check.
                
                int callType = ValueHandlerImpl.kValueType;
                
                if (!vhandler.isSequence(id)) {

                    if (field.type.kind().value() == 
                        kRemoteTypeCode.kind().value()) {

                        // RMI Object reference...
                        callType = ValueHandlerImpl.kRemoteType;
                    } else {
                        // REVISIT.  If we don't have the local class,
                        // we should probably verify that it's an RMI type, 
                        // query the remote FVD, and use is_abstract.
                        // Our FVD seems to get NullPointerExceptions for any
                        // non-RMI types.

                        // This uses the local class in the same way as
                        // inputObjectField(ObjectStreamField) does.  REVISIT
                        // inputObjectField(ObjectStreamField)'s loadStubClass
                        // logic.  Assumption is that the given type cannot
                        // evolve to become a CORBA abstract interface or
                        // a RMI abstract interface.

                        ClassInfoCache.ClassInfo cinfo =
                            ClassInfoCache.get( type ) ;
                        if (type != null && cinfo.isInterface() &&
                            (vhandler.isAbstractBase(type) ||
                             ObjectStreamClassCorbaExt.isAbstractInterface(type))) {
                    
                            callType = ValueHandlerImpl.kAbstractType;
                        }
                    }
                }
                    
                if (valueHandlerDebug()) {
                    dputil.info( "callType", callType ) ;
                }

                // Now that we have used the FVD of the field to determine the proper course
                // of action, it is ok to use the type (Class) from this point forward since 
                // the rep. id for this read will also follow on the wire.

                switch (callType) {
                    case ValueHandlerImpl.kRemoteType: 
                        if (type != null)
                            objectValue = Utility.readObjectAndNarrow(orbStream, type);
                        else
                            objectValue = orbStream.read_Object();
                        break;
                    case ValueHandlerImpl.kAbstractType: 
                        if (type != null)
                            objectValue = Utility.readAbstractAndNarrow(orbStream, type);
                        else
                            objectValue = orbStream.read_abstract_interface();
                        break;
                    case ValueHandlerImpl.kValueType:
                        if (type != null)
                            objectValue = orbStream.read_value(type);
                        else
                            objectValue = orbStream.read_value();
                        break;
                    default:
                        // XXX I18N, logging needed.
                        throw new StreamCorruptedException("Unknown callType: " + callType);
                }
            }

            return Return.value(objectValue);
        } finally {
            if (valueHandlerDebug()) {
                dputil.exit() ;
            }
        }
    }

    /**
     * Factored out of inputClassFields and reused in 
     * inputCurrentClassFieldsForReadFields.
     *
     * Reads the field (which is of an Object type as opposed to a primitive) 
     * described by ObjectStreamField field and returns Return.value(it.
     */
    @TraceValueHandler
    @ValueHandlerRead
    private Object inputObjectField(ObjectStreamField field) 
        throws InvalidClassException, StreamCorruptedException,
               ClassNotFoundException, IndirectionException, IOException {

        if (ObjectStreamClassCorbaExt.isAny(field.getTypeString())) {
            return Return.value(Util.getInstance().readAny(orbStream));
        }

        Object objectValue = null;

        // fields have an API to provide the actual class
        // corresponding to the data type
        // Class type = osc.forClass();
        Class fieldType = field.getType();
	Class actualType = fieldType; // This may change if stub loaded.
				
        // Decide what method call to make based on the fieldType. If
        // it is a type for which we need to load a stub, convert
        // the type to the correct stub type.
        
        int callType = ValueHandlerImpl.kValueType;
        boolean narrow = false;
        
	ClassInfoCache.ClassInfo cinfo = field.getClassInfo() ;
        if (cinfo.isInterface()) { 
            boolean loadStubClass = false;
            
	    if (cinfo.isARemote(fieldType)) {
                // RMI Object reference...
                callType = ValueHandlerImpl.kRemoteType;
	    } else if (cinfo.isACORBAObject(fieldType)) {
                // IDL Object reference...
                callType = ValueHandlerImpl.kRemoteType;
                loadStubClass = true;
            } else if (vhandler.isAbstractBase(fieldType)) {
                // IDL Abstract Object reference...
                callType = ValueHandlerImpl.kAbstractType;
                loadStubClass = true;
            } else if (ObjectStreamClassCorbaExt.isAbstractInterface(
		fieldType)) {
                // RMI Abstract Object reference...
                callType = ValueHandlerImpl.kAbstractType;
            }
            
            if (loadStubClass) {
                try {
                    String codebase = Util.getInstance().getCodebase(fieldType);
                    String repID = vhandler.createForAnyType(fieldType);
                    Class stubType =
			Utility.loadStubClass(repID, codebase, fieldType); 
		    actualType = stubType;
                } catch (ClassNotFoundException e) {
                    narrow = true;
                }
            } else {
                narrow = true;
            }
        }			

        switch (callType) {
            case ValueHandlerImpl.kRemoteType: 
                if (!narrow) 
                    objectValue = (Object)orbStream.read_Object(actualType);
                else
                    objectValue = Utility.readObjectAndNarrow(orbStream, actualType);
                break;
            case ValueHandlerImpl.kAbstractType: 
                if (!narrow)
                    objectValue = (Object)orbStream.read_abstract_interface(actualType); 
                else
                    objectValue = Utility.readAbstractAndNarrow(orbStream, actualType);
                break;
            case ValueHandlerImpl.kValueType:
                objectValue = (Object)orbStream.read_value(actualType);
                break;
            default:
		// XXX I18N, logging needed.
                throw new StreamCorruptedException("Unknown callType: " + callType);
        }

        return Return.value(objectValue);
    }

    // Note that this is need for getFields support.
    void readFields(Map<String,Object> fieldToValueMap)
        throws InvalidClassException, StreamCorruptedException,
               ClassNotFoundException, IOException {

        if (defaultReadObjectFVDMembers != null) {
            inputRemoteMembersForReadFields(fieldToValueMap);
        } else
            inputCurrentClassFieldsForReadFields(fieldToValueMap);
    }

    @TraceValueHandler
    @ValueHandlerRead
    private final void inputRemoteMembersForReadFields(
	Map<String,Object> fieldToValueMap)
        throws InvalidClassException, StreamCorruptedException,
               ClassNotFoundException, IOException {

        if (valueHandlerDebug()) {
            dputil.enter( "inputRemoteMembersForReadFields" ) ;
        }

        // Must have this local variable since defaultReadObjectFVDMembers
        // may get mangled by recursion.
        ValueMember fields[] = defaultReadObjectFVDMembers;
        defaultReadObjectFVDMembers = null ;

	try {
	    for (int i = 0; i < fields.length; i++) {
                OperationTracer.readingField( fields[i].name ) ;
                if (valueHandlerDebug()) {
                    dputil.info( "Field", i, displayValueMember( fields[i] ) ) ;
                }

                switch (fields[i].type.kind().value()) {

                case TCKind._tk_octet:
                    byte byteValue = orbStream.read_octet();
                    fieldToValueMap.put(fields[i].name, Byte.valueOf(byteValue));
                    break;
                case TCKind._tk_boolean:
                    boolean booleanValue = orbStream.read_boolean();
                    fieldToValueMap.put(fields[i].name, Boolean.valueOf(booleanValue));
                    break;
                case TCKind._tk_char:
                    // Backwards compatibility.  Older Sun ORBs sent
                    // _tk_char even though they read and wrote wchars
                    // correctly.
                    //
                    // Fall through to the _tk_wchar case.
                case TCKind._tk_wchar:
                    char charValue = orbStream.read_wchar();
                    fieldToValueMap.put(fields[i].name, Character.valueOf(charValue));
                    break;
                case TCKind._tk_short:
                    short shortValue = orbStream.read_short();
                    fieldToValueMap.put(fields[i].name, Short.valueOf(shortValue));
                    break;
                case TCKind._tk_long:
                    int intValue = orbStream.read_long();
                    fieldToValueMap.put(fields[i].name, Integer.valueOf(intValue));
                    break;
                case TCKind._tk_longlong:
                    long longValue = orbStream.read_longlong();
                    fieldToValueMap.put(fields[i].name, Long.valueOf(longValue));
                    break;
                case TCKind._tk_float:
                    float floatValue = orbStream.read_float();
                    fieldToValueMap.put(fields[i].name, Float.valueOf(floatValue));
                    break;
                case TCKind._tk_double:
                    double doubleValue = orbStream.read_double();
                    fieldToValueMap.put(fields[i].name, Double.valueOf(doubleValue));
                    break;
                case TCKind._tk_value:
                case TCKind._tk_objref:
                case TCKind._tk_value_box:
                    Object objectValue = null;
                    try {
                        objectValue = inputObjectField(fields[i],
                                                       cbSender);

                    } catch (IndirectionException cdrie) {
                        // The CDR stream had never seen the given offset before,
                        // so check the recursion manager (it will throw an
                        // IOException if it doesn't have a reference, either).
                        objectValue = activeRecursionMgr.getObject(cdrie.offset);
                    }

                    fieldToValueMap.put(fields[i].name, objectValue);
                    break;
                default:
		    // XXX I18N, logging needed.
                    throw new StreamCorruptedException("Unknown kind: "
                                                       + fields[i].type.kind().value());
                }
            }
        } catch (Throwable t) {
            StreamCorruptedException result = new StreamCorruptedException(t.getMessage());
            result.initCause(t);
            throw result;
	}
    }

    /**
     * Called from InputStreamHook.
     *
     * Reads the fields of the current class (could be the ones
     * queried from the remote FVD) and puts them in
     * the given Map, name to value.  Wraps primitives in the
     * corresponding java.lang Objects.
     */
    @TraceValueHandler
    @ValueHandlerRead
    private final void inputCurrentClassFieldsForReadFields(
	Map<String,Object> fieldToValueMap) throws InvalidClassException, 
	    StreamCorruptedException, ClassNotFoundException, IOException {

        ObjectStreamField[] fields = currentClassDesc.getFieldsNoCopy();

	int primFields = fields.length - currentClassDesc.objFields;

        // Handle the primitives first
        for (int i = 0; i < primFields; ++i) {
            switch (fields[i].getTypeCode()) {
                case 'B':
                    byte byteValue = orbStream.read_octet();
                    fieldToValueMap.put(fields[i].getName(),
                                        Byte.valueOf(byteValue));
                    break;
                case 'Z':
                   boolean booleanValue = orbStream.read_boolean();
                   fieldToValueMap.put(fields[i].getName(),
                                       Boolean.valueOf(booleanValue));
                   break;
		case 'C':
                    char charValue = orbStream.read_wchar();
                    fieldToValueMap.put(fields[i].getName(),
                                        Character.valueOf(charValue));
                    break;
		case 'S':
                    short shortValue = orbStream.read_short();
                    fieldToValueMap.put(fields[i].getName(),
                                        Short.valueOf(shortValue));
                    break;
		case 'I':
                    int intValue = orbStream.read_long();
                    fieldToValueMap.put(fields[i].getName(),
                                        Integer.valueOf(intValue));
                    break;
		case 'J':
                    long longValue = orbStream.read_longlong();
                    fieldToValueMap.put(fields[i].getName(),
                                        Long.valueOf(longValue));
                    break;
		case 'F' :
                    float floatValue = orbStream.read_float();
                    fieldToValueMap.put(fields[i].getName(),
                                        Float.valueOf(floatValue));
                    break;
		case 'D' :
                    double doubleValue = orbStream.read_double();
                    fieldToValueMap.put(fields[i].getName(),
                                        Double.valueOf(doubleValue));
                    break;
		default:
		    // XXX I18N, logging needed.
                    throw new InvalidClassException(currentClassDesc.getName());
	    }
	}

	/* Read and set object fields from the input stream. */
	if (currentClassDesc.objFields > 0) {
	    for (int i = primFields; i < fields.length; i++) {
                OperationTracer.readingField( fields[i].getName() ) ;

                Object objectValue = null;
                try {
                    objectValue = inputObjectField(fields[i]);
                } catch(IndirectionException cdrie) {
                    // The CDR stream had never seen the given offset before,
                    // so check the recursion manager (it will throw an
                    // IOException if it doesn't have a reference, either).
                    objectValue = activeRecursionMgr.getObject(cdrie.offset);
                }

                fieldToValueMap.put(fields[i].getName(), objectValue);
            }
        }
    }

    /*
     * Read the fields of the specified class from the input stream and set
     * the values of the fields in the specified object. If the specified
     * object is null, just consume the fields without setting any values. If
     * any ObjectStreamField does not have a reflected Field, don't try to set
     * that field in the object.
     *
     * REVISIT -- This code doesn't do what the comment says to when
     * getField() is null!
     */
    @TraceValueHandler
    @ValueHandlerRead
    private void inputClassFields(Object o, Class cl,
				  ObjectStreamField[] fields, 
				  com.sun.org.omg.SendingContext.CodeBase sender)
	throws InvalidClassException, StreamCorruptedException,
	       ClassNotFoundException, IOException
    {
        if (valueHandlerDebug()) {
            dputil.enter( "inputClassFields(ObjectStreamFields)" ) ;
            dputil.info( "fields" ) ;
            for (ObjectStreamField field : fields) {
                dputil.info( "\t", field ) ;
            }
        }

        try {
            int primFields = fields.length - currentClassDesc.objFields;
            if (valueHandlerDebug()) {
                dputil.info( "reading", primFields, "primitive fields" ) ;
            }

            if (o != null) {
                for (int i = 0; i < primFields; ++i) {
  		    OperationTracer.readingField( fields[i].getName() ) ;
                    if (fields[i].getField() == null)
                        continue;

                    inputPrimitiveField(o, cl, fields[i]);
                }
            }

            /* Read and set object fields from the input stream. */
            if (currentClassDesc.objFields > 0) {
                for (int i = primFields; i < fields.length; i++) {
		    OperationTracer.readingField( fields[i].getName() ) ;
                    Object objectValue = null;

                    try {
                        if (valueHandlerDebug()) {
                            dputil.info( "Reading field", i, fields[i] ) ;
                        }

                        objectValue = inputObjectField(fields[i]);
                    } catch(IndirectionException cdrie) {
                        // The CDR stream had never seen the given offset before,
                        // so check the recursion manager (it will throw an
                        // IOException if it doesn't have a reference, either).
                        objectValue = activeRecursionMgr.getObject(cdrie.offset);
                    }

                    if ((o == null) || (fields[i].getField() == null)) {
                        continue;
                    }

                    try {
                        bridge.putObject( o, fields[i].getFieldID(), objectValue ) ;
                        // reflective code: fields[i].getField().set( o, objectValue ) ;
                    } catch (IllegalArgumentException e) {
                        ClassCastException exc = new ClassCastException("Assigning instance of class " +
                                                     objectValue.getClass().getName() +
                                                     " to field " +
                                                     currentClassDesc.getName() +
                                                     '#' +
                                                     fields[i].getField().getName());
                        exc.initCause( e ) ;
                        throw exc ;
                    }
                } // end : for loop
            }
        } finally {
            if (valueHandlerDebug()) {
                dputil.exit() ;
            }
        }
    }

    private String displayValueMember( ValueMember member ) {
        StringBuilder sb = new StringBuilder() ;
        sb.append( "ValueMember(" ) ;
        sb.append( "name=" ) ;
        sb.append( member.name ) ;
        sb.append( ",id=" ) ;
        sb.append( member.id ) ;
        sb.append( ",defined_in=" ) ;
        sb.append( member.defined_in ) ;
        sb.append( ",version=" ) ;
        sb.append( member.version ) ;
        sb.append( ",access=" ) ;
        sb.append( member.access ) ;
        sb.append( ")" ) ;
        return sb.toString() ;
    }

    /*
     * Read the fields of the specified class from the input stream and set
     * the values of the fields in the specified object. If the specified
     * object is null, just consume the fields without setting any values. If
     * any ObjectStreamField does not have a reflected Field, don't try to set
     * that field in the object.
     */
    @TraceValueHandler
    @ValueHandlerRead
    private void inputClassFields(Object o, Class cl, 
				  ObjectStreamClass osc,
				  ValueMember[] fields,
				  com.sun.org.omg.SendingContext.CodeBase sender)
	throws InvalidClassException, StreamCorruptedException,
	       ClassNotFoundException, IOException
    {
        if (valueHandlerDebug()) {
            dputil.enter( "inputClassFields(ValueMember)" ) ;
            dputil.info( "fields" ) ;
            for (ValueMember vm : fields) {
                dputil.info( "\t", displayValueMember( vm ) ) ;
            }
        }

	try {
	    for (int i = 0; i < fields.length; ++i) {
                OperationTracer.readingField( fields[i].name ) ;

                if (valueHandlerDebug()) {
                    dputil.info( "Reading field", i, "type is", 
                        displayValueMember( fields[i] ) ) ;
                }

		try {
		    switch (fields[i].type.kind().value()) {
		    case TCKind._tk_octet:
			byte byteValue = orbStream.read_octet();
			if ((o != null) && osc.hasField(fields[i]))
			setByteField(o, cl, fields[i].name, byteValue);
			break;
		    case TCKind._tk_boolean:
			boolean booleanValue = orbStream.read_boolean();
			if ((o != null) && osc.hasField(fields[i]))
			setBooleanField(o, cl, fields[i].name, booleanValue);
			break;
		    case TCKind._tk_char:
                        // Backwards compatibility.  Older Sun ORBs sent
                        // _tk_char even though they read and wrote wchars
                        // correctly.
                        //
                        // Fall through to the _tk_wchar case.
                    case TCKind._tk_wchar:
			char charValue = orbStream.read_wchar();
			if ((o != null) && osc.hasField(fields[i]))
			setCharField(o, cl, fields[i].name, charValue);
			break;
		    case TCKind._tk_short:
			short shortValue = orbStream.read_short();
			if ((o != null) && osc.hasField(fields[i]))
			setShortField(o, cl, fields[i].name, shortValue);
			break;
		    case TCKind._tk_long:
			int intValue = orbStream.read_long();
			if ((o != null) && osc.hasField(fields[i]))
			setIntField(o, cl, fields[i].name, intValue);
			break;
		    case TCKind._tk_longlong:
			long longValue = orbStream.read_longlong();
			if ((o != null) && osc.hasField(fields[i]))
			setLongField(o, cl, fields[i].name, longValue);
			break;
		    case TCKind._tk_float:
			float floatValue = orbStream.read_float();
			if ((o != null) && osc.hasField(fields[i]))
			setFloatField(o, cl, fields[i].name, floatValue);
			break;
		    case TCKind._tk_double:
			double doubleValue = orbStream.read_double();
			if ((o != null) && osc.hasField(fields[i]))
			setDoubleField(o, cl, fields[i].name, doubleValue);
			break;
                    case TCKind._tk_value:
		    case TCKind._tk_objref:
		    case TCKind._tk_value_box:
                        Object objectValue = null;
                        try {
                            objectValue = inputObjectField(fields[i], sender);
                        } catch (IndirectionException cdrie) {
                            // The CDR stream had never seen the given offset before,
                            // so check the recursion manager (it will throw an
                            // IOException if it doesn't have a reference, either).
                            objectValue = activeRecursionMgr.getObject(cdrie.offset);
                        }
								
			if (o == null)
			    continue;
			try {
			    if (osc.hasField(fields[i])){
                                setObjectField(o, 
                                               cl, 
                                               fields[i].name, 
                                               objectValue);
			    } else {
                                // REVISIT.  Convert to a log message.
                                // This is a normal case when fields have
                                // been added as part of evolution, but
                                // silently skipping can make it hard to
                                // debug if there's an error
//                                 System.out.println("**** warning, not setting field: "
//                                                    + fields[i].name
//                                                    + " since not on class "
//                                                    + osc.getName());

                            }
			} catch (IllegalArgumentException e) {
			    // XXX I18N, logging needed.
			    ClassCastException cce = new ClassCastException("Assigning instance of class " + 
				objectValue.getClass().getName() + " to field " + fields[i].name);
			    cce.initCause(e) ;
			    throw cce ;
			}		
			break;
                    default:
			// XXX I18N, logging needed.
                        throw new StreamCorruptedException("Unknown kind: "
                                                           + fields[i].type.kind().value());
		    }
		} catch (IllegalArgumentException e) {
		    /* This case should never happen. If the field types
		       are not the same, InvalidClassException is raised when
		       matching the local class to the serialized ObjectStreamClass. */
		    // XXX I18N, logging needed.
		    ClassCastException cce = new ClassCastException(
			"Assigning instance of class " + fields[i].id + 
			" to field " + currentClassDesc.getName() + '#' + fields[i].name);
		    cce.initCause( e ) ;
		    throw cce ;
		}
	    }
	} catch(Throwable t){
	    // XXX I18N, logging needed.
	    StreamCorruptedException sce = new StreamCorruptedException(t.getMessage());
	    sce.initCause(t) ;
	    throw sce ;
	} finally {
            if (valueHandlerDebug()) {
                dputil.exit() ;
            }
        }
    }

    @TraceValueHandler
    @ValueHandlerRead
    private void skipCustomUsingFVD(ValueMember[] fields,
	com.sun.org.omg.SendingContext.CodeBase sender
    ) throws InvalidClassException, StreamCorruptedException, 
	ClassNotFoundException, IOException {

        readFormatVersion();
        boolean calledDefaultWriteObject = readBoolean();

        if (calledDefaultWriteObject)
            throwAwayData(fields, sender);

        if (getStreamFormatVersion() == 2) {
            
            ((ValueInputStream)getOrbStream()).start_value();
            ((ValueInputStream)getOrbStream()).end_value();
        }
    }
	
    /*
     * Read the fields of the specified class from the input stream throw data 
     * away.  This must handle same switch logic as above.
     */
    @TraceValueHandler
    @ValueHandlerRead
    private void throwAwayData(ValueMember[] fields, 
	com.sun.org.omg.SendingContext.CodeBase sender
    ) throws InvalidClassException, StreamCorruptedException, 
	ClassNotFoundException, IOException {

	for (int i = 0; i < fields.length; ++i) {
            OperationTracer.readingField( fields[i].name ) ;	
	    try {
		switch (fields[i].type.kind().value()) {
		case TCKind._tk_octet:
		    orbStream.read_octet();
		    break;
		case TCKind._tk_boolean:
		    orbStream.read_boolean();
		    break;
		case TCKind._tk_char:
                    // Backwards compatibility.  Older Sun ORBs sent
                    // _tk_char even though they read and wrote wchars
                    // correctly.
                    //
                    // Fall through to the _tk_wchar case.
                case TCKind._tk_wchar:
		    orbStream.read_wchar();
		    break;
		case TCKind._tk_short:
		    orbStream.read_short();
		    break;
		case TCKind._tk_long:
		    orbStream.read_long();
		    break;
		case TCKind._tk_longlong:
		    orbStream.read_longlong();
		    break;
		case TCKind._tk_float:
		    orbStream.read_float();
		    break;
		case TCKind._tk_double:
		    orbStream.read_double();
		    break;
                case TCKind._tk_value:
		case TCKind._tk_objref:
		case TCKind._tk_value_box:
		    Class type = null;
	            String id = fields[i].id;

		    try {
			type = vhandler.getClassFromType(id);
		    }
		    catch(ClassNotFoundException cnfe){
			// Make sure type = null
			type = null;
		    }
		    String signature = null;
		    if (type != null)
			signature = ValueUtility.getSignature(fields[i]);
								
		    // Read value
		    try {
			if ((signature != null) && ( 
			    signature.equals("Ljava/lang/Object;") 
			    || signature.equals("Ljava/io/Serializable;") 
			    || signature.equals("Ljava/io/Externalizable;")) ) {
			    Util.getInstance().readAny(orbStream);
			} else {
			    // Decide what method call to make based on the type.
			    //
			    // NOTE : Since FullValueDescription does not allow 
			    // us to ask whether something is an interface we 
			    // do not have the ability to optimize this check.
			    int callType = ValueHandlerImpl.kValueType;

			    if (!vhandler.isSequence(id)) {
				FullValueDescription fieldFVD = 
				    sender.meta(fields[i].id);
				if (kRemoteTypeCode == fields[i].type) {

				    // RMI Object reference...
				    callType = ValueHandlerImpl.kRemoteType;
				} else if (fieldFVD.is_abstract) {
				    // RMI Abstract Object reference...

				    callType = ValueHandlerImpl.kAbstractType;
				}
			    }
										
			    // Now that we have used the FVD of the field to 
			    // determine the proper course
			    // of action, it is ok to use the type (Class) 
			    // from this point forward since 
			    // the rep. id for this read will also follow on 
			    // the wire.
			    switch (callType) {
			    case ValueHandlerImpl.kRemoteType: 
				orbStream.read_Object();
				break;
			    case ValueHandlerImpl.kAbstractType: 
				orbStream.read_abstract_interface(); 
				break;
			    case ValueHandlerImpl.kValueType:
				if (type != null) {
				    orbStream.read_value(type);
				} else {
				    orbStream.read_value();
				}
				break;
                            default:
				// XXX I18N, logging needed.
                                throw new StreamCorruptedException(
				    "Unknown callType: " + callType);
			    }
			}
		    } catch(IndirectionException cdrie) {
			// Since we are throwing this away, don't bother 
			// handling recursion.
			continue;
		    }
									
		    break;
                default:
		    // XXX I18N, logging needed.
                    throw new StreamCorruptedException(
			"Unknown kind: " + fields[i].type.kind().value());
		}
	    } catch (IllegalArgumentException e) {
		/* This case should never happen. If the field types
		   are not the same, InvalidClassException is raised when
		   matching the local class to the serialized ObjectStreamClass.
		*/
		// XXX I18N, logging needed.
		ClassCastException cce = new ClassCastException(
		    "Assigning instance of class " + 
		    fields[i].id + " to field " + currentClassDesc.getName() + 
		    '#' + fields[i].name);
		cce.initCause(e) ;
		throw cce ;
	    }
	}
		
    }

    @TraceValueHandler
    @ValueHandlerRead
    private static void setObjectField(Object o, Class c, String fieldName, 
	Object v) {

	try {
	    Field fld = c.getDeclaredField( fieldName ) ;
	    long key = bridge.objectFieldOffset( fld ) ;
	    bridge.putObject( o, key, v ) ;
	} catch (Exception e) {
	    throw utilWrapper.errorSetObjectField( e, fieldName, 
		ObjectUtility.compactObjectToString( o ),
		ObjectUtility.compactObjectToString( v )) ;
	}
    }

    @TraceValueHandler
    @ValueHandlerRead
    private static void setBooleanField(Object o, Class c, String fieldName, boolean v)
    {
	try {
	    Field fld = c.getDeclaredField( fieldName ) ;
	    long key = bridge.objectFieldOffset( fld ) ;
	    bridge.putBoolean( o, key, v ) ;
	} catch (Exception e) {
	    throw utilWrapper.errorSetBooleanField( e, fieldName, 
		ObjectUtility.compactObjectToString( o ), v ) ;
	}
    }

    @TraceValueHandler
    @ValueHandlerRead
    private static void setByteField(Object o, Class c, String fieldName, byte v)
    {
	try {
	    Field fld = c.getDeclaredField( fieldName ) ;
	    long key = bridge.objectFieldOffset( fld ) ;
	    bridge.putByte( o, key, v ) ;
	} catch (Exception e) {
	    throw utilWrapper.errorSetByteField( e, fieldName, 
		ObjectUtility.compactObjectToString( o ), v ) ;
	}
    }

    @TraceValueHandler
    @ValueHandlerRead
    private static void setCharField(Object o, Class c, String fieldName, char v)
    {
	try {
	    Field fld = c.getDeclaredField( fieldName ) ;
	    long key = bridge.objectFieldOffset( fld ) ;
	    bridge.putChar( o, key, v ) ;
	} catch (Exception e) {
	    throw utilWrapper.errorSetCharField( e, fieldName, 
		ObjectUtility.compactObjectToString( o ), v ) ;
	}
    }

    @TraceValueHandler
    @ValueHandlerRead
    private static void setShortField(Object o, Class c, String fieldName, short v)
    {
	try {
	    Field fld = c.getDeclaredField( fieldName ) ;
	    long key = bridge.objectFieldOffset( fld ) ;
	    bridge.putShort( o, key, v ) ;
	} catch (Exception e) {
	    throw utilWrapper.errorSetShortField( e, fieldName, 
		ObjectUtility.compactObjectToString( o ), v ) ;
	}
    }

    @TraceValueHandler
    @ValueHandlerRead
    private static void setIntField(Object o, Class c, String fieldName, int v)
    {
	try {
	    Field fld = c.getDeclaredField( fieldName ) ;
	    long key = bridge.objectFieldOffset( fld ) ;
	    bridge.putInt( o, key, v ) ;
	} catch (Exception e) {
	    throw utilWrapper.errorSetIntField( e, fieldName, 
		ObjectUtility.compactObjectToString( o ), v ) ;
	}
    }

    @TraceValueHandler
    @ValueHandlerRead
    private static void setLongField(Object o, Class c, String fieldName, long v)
    {
	try {
	    Field fld = c.getDeclaredField( fieldName ) ;
	    long key = bridge.objectFieldOffset( fld ) ;
	    bridge.putLong( o, key, v ) ;
	} catch (Exception e) {
	    throw utilWrapper.errorSetLongField( e, fieldName, 
		ObjectUtility.compactObjectToString( o ), v ) ;
	}
    }

    @TraceValueHandler
    @ValueHandlerRead
    private static void setFloatField(Object o, Class c, String fieldName, float v)
    {
	try {
	    Field fld = c.getDeclaredField( fieldName ) ;
	    long key = bridge.objectFieldOffset( fld ) ;
	    bridge.putFloat( o, key, v ) ;
	} catch (Exception e) {
	    throw utilWrapper.errorSetFloatField( e, fieldName, 
		ObjectUtility.compactObjectToString( o ), v ) ;
	}
    }

    @TraceValueHandler
    @ValueHandlerRead
    private static void setDoubleField(Object o, Class c, String fieldName, double v)
    {
	try {
	    Field fld = c.getDeclaredField( fieldName ) ;
	    long key = bridge.objectFieldOffset( fld ) ;
	    bridge.putDouble( o, key, v ) ;
	} catch (Exception e) {
	    throw utilWrapper.errorSetDoubleField( e, fieldName, 
		ObjectUtility.compactObjectToString( o ), v ) ;
	}
    }

    /**
     * This class maintains a map of stream position to
     * an Object currently being deserialized.  It is used
     * to handle the cases where the are indirections to
     * an object on the recursion stack.  The CDR level
     * handles indirections to objects previously seen
     * (and completely deserialized) in the stream.
     */
    static class ActiveRecursionManager
    {
        private Map<Integer,Object> offsetToObjectMap;
        
        public ActiveRecursionManager() {
            // A hash map is unsynchronized and allows
            // null values
            offsetToObjectMap = new HashMap<Integer,Object>();
        }

        // Called right after allocating a new object.
        // Offset is the starting position in the stream
        // of the object.
        public void addObject(int offset, Object value) {
            offsetToObjectMap.put(offset, value);
        }

        // If the given starting position doesn't refer
        // to the beginning of an object currently being
        // deserialized, this throws an IOException.
        // Otherwise, it returns a reference to the
        // object.
        public Object getObject(int offset) throws IOException {
            Integer position = Integer.valueOf(offset);

            if (!offsetToObjectMap.containsKey(position))
		// XXX I18N, logging needed.
                throw new IOException(
		    "Invalid indirection to offset " + offset);

            return offsetToObjectMap.get(position);
        }
        
        // Called when an object has been completely
        // deserialized, so it should no longer be in
        // this mapping.  The CDR level can handle
        // further indirections.
        public void removeObject(int offset) {
            offsetToObjectMap.remove(offset);
        }

        // If the given offset doesn't map to an Object,
        // then it isn't an indirection to an object
        // currently being deserialized.
        public boolean containsObject(int offset) {
            return offsetToObjectMap.containsKey(offset);
        }
    }
}

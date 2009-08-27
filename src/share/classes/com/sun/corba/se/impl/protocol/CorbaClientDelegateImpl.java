/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1996-2007 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.corba.se.impl.protocol;

import java.util.Iterator;
import java.util.HashMap;

import javax.rmi.CORBA.Tie;

import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.Context;
import org.omg.CORBA.ContextList;
import org.omg.CORBA.ExceptionList;
import org.omg.CORBA.NamedValue;
import org.omg.CORBA.NVList;
import org.omg.CORBA.Request;
import org.omg.CORBA.TypeCode;

import org.omg.CORBA.portable.ApplicationException;
import org.omg.CORBA.portable.Delegate;
import org.omg.CORBA.portable.InputStream;
import org.omg.CORBA.portable.OutputStream;
import org.omg.CORBA.portable.RemarshalException;
import org.omg.CORBA.portable.ServantObject;

import com.sun.corba.se.impl.encoding.CDRInputObject;
import com.sun.corba.se.impl.encoding.CDROutputObject;
import com.sun.corba.se.spi.protocol.ClientInvocationInfo;
import com.sun.corba.se.spi.protocol.CorbaClientRequestDispatcher;
import com.sun.corba.se.spi.transport.CorbaContactInfo;
import com.sun.corba.se.spi.transport.CorbaContactInfoList;
import com.sun.corba.se.spi.transport.CorbaContactInfoListIterator;

import com.sun.corba.se.spi.presentation.rmi.StubAdapter;
import com.sun.corba.se.spi.ior.IOR;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.protocol.CorbaClientDelegate ;
import com.sun.corba.se.spi.transport.CorbaContactInfo;
import com.sun.corba.se.spi.transport.CorbaContactInfoList;
import com.sun.corba.se.spi.transport.CorbaContactInfoListIterator;
import com.sun.corba.se.spi.orbutil.ORBConstants;

import com.sun.corba.se.impl.corba.RequestImpl;
import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.protocol.CorbaInvocationInfo;
import com.sun.corba.se.impl.transport.CorbaContactInfoListImpl;
import com.sun.corba.se.impl.util.JDKBridge;

import com.sun.corba.se.impl.orbutil.newtimer.generated.TimingPoints;

import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.spi.orbutil.misc.OperationTracer ;

// implements com.sun.corba.se.impl.core.ClientRequestDispatcher
// so RMI-IIOP Util.isLocal can call ClientRequestDispatcher.useLocalInvocation.

/**
 * @author Harold Carr
 */
public class CorbaClientDelegateImpl extends CorbaClientDelegate 
{
    private ORB orb;
    private ORBUtilSystemException wrapper ;
    private TimingPoints tp ;

    private CorbaContactInfoList contactInfoList;

    public CorbaClientDelegateImpl(ORB orb, 
				   CorbaContactInfoList contactInfoList)
    {
	this.orb = orb;
	this.tp = orb.getTimerManager().points() ;
	this.wrapper = orb.getLogWrapperTable().get_RPC_PROTOCOL_ORBUtil() ;
	this.contactInfoList = contactInfoList;
    }
    
    //
    // framework.subcontract.Delegate
    //

    public ORB getBroker()
    {
	return orb;
    }

    public CorbaContactInfoList getContactInfoList()
    {
	return contactInfoList;
    }

    //
    // CORBA_2_3.portable.Delegate
    //
    
    public OutputStream request(org.omg.CORBA.Object self, 
				String operation, 
				boolean responseExpected) 
    {
	tp.enter_totalInvocation() ;
	try {
       	    if (orb.subcontractDebugFlag) {
	        dprint(".request->: op/" + operation);
	    }

	    OutputStream result = null;
	    boolean retry;
	    do {
		retry = false;

		Iterator contactInfoListIterator = null;
		CorbaContactInfo contactInfo = null;
		ClientInvocationInfo invocationInfo = null;

		try {
		    invocationInfo = orb.createOrIncrementInvocationInfo();
		    contactInfoListIterator =
			invocationInfo.getContactInfoListIterator();
		    if (contactInfoListIterator == null) {
			contactInfoListIterator = contactInfoList.iterator();
			invocationInfo.setContactInfoListIterator(contactInfoListIterator);
		    }

		    try {
			tp.enter_hasNextNext() ;

			if (! contactInfoListIterator.hasNext()) {
			    // REVISIT: When we unwind the retry stack
			    // these are necessary.
			    orb.getPIHandler().initiateClientPIRequest(false);
                            ORBUtility.pushEncVersionToThreadLocalState(
                                                 ORBConstants.JAVA_ENC_VERSION);
			    throw ((CorbaContactInfoListIterator)contactInfoListIterator)
				.getFailureException();
			}
			contactInfo = (CorbaContactInfo)
			    contactInfoListIterator.next();
                        if (orb.folbDebugFlag) {
                            dprint( ".request: op/" + operation + " contactInfo/" + contactInfo ) ;
                        }
		    } finally {
			tp.exit_hasNextNext() ;
		    }

		    CorbaClientRequestDispatcher subcontract =
		        contactInfo.getClientRequestDispatcher();
		    // Remember chosen subcontract for invoke and releaseReply.
		    // 
		    // NOTE: This is necessary since a stream is not available
		    // in releaseReply if there is a client marshaling error
		    // or an error in _invoke.
		    invocationInfo.setClientRequestDispatcher(subcontract);
		    result = (OutputStream)
			subcontract.beginRequest(self, operation,
						 !responseExpected,
						 contactInfo);
		} catch (RuntimeException e) {
		    if (orb.subcontractDebugFlag) {
			dprint(".request: op/" + operation 
			       + ": caught RuntimeException: " + e);
		    }
		    // REVISIT: 
		    // this part similar to BufferManagerWriteStream.overflow()
		    retry = ((CorbaContactInfoListIterator) 
			     contactInfoListIterator).reportException(contactInfo, e);
		    if (retry) {
			if (orb.subcontractDebugFlag) {
			    dprint(".request: op/" + operation 
				   + ": retry as a result of : " + e);
			}
			invocationInfo.setIsRetryInvocation(true);
		    } else {
			throw e;
		    }
		}
	    } while (retry);
	    return result;
	} finally {
       	    if (orb.subcontractDebugFlag) {
	        dprint(".request<- op/" + operation);
	    }

            // Enable operation tracing for argument marshaling
            OperationTracer.enable() ;
            OperationTracer.begin( "client argument marshaling:op=" + operation ) ;
	}
    }
    
    public InputStream invoke(org.omg.CORBA.Object self, OutputStream output)
	throws
	    ApplicationException,
	    RemarshalException 
    {
        // Disable operation tracing for argment marshaling
        OperationTracer.disable() ;
        OperationTracer.finish() ;

	CorbaClientRequestDispatcher subcontract = getClientRequestDispatcher();
        try {
	    return (InputStream)
	        subcontract.marshalingComplete((Object)self, (CDROutputObject)output);
        } finally {
            // Enable operation tracing for result unmarshaling
            OperationTracer.enable() ;
            OperationTracer.begin( "client result unmarshaling" ) ;
        }
    }
    
    public void releaseReply(org.omg.CORBA.Object self, InputStream input) 
    {
	try {
	    // NOTE: InputStream may be null (e.g., exception request from PI).
	    CorbaClientRequestDispatcher subcontract = getClientRequestDispatcher();
	    if (subcontract != null) {
		// Important: avoid an NPE.
		// ie: Certain errors may happen before a subcontract is selected.
		subcontract.endRequest(orb, self, (CDRInputObject)input);
	    }
	    orb.releaseOrDecrementInvocationInfo();
	} finally {
	    tp.exit_totalInvocation() ;
        
            // Disable operation tracing for result unmarshaling
            OperationTracer.disable() ;
            OperationTracer.finish() ;
	}
    }

    private CorbaClientRequestDispatcher getClientRequestDispatcher()
    {
        return (CorbaClientRequestDispatcher)
            ((CorbaInvocationInfo)orb.getInvocationInfo())
	    .getClientRequestDispatcher();
    }

    public org.omg.CORBA.Object get_interface_def(org.omg.CORBA.Object obj) 
    {
	InputStream is = null;
	// instantiate the stub
	org.omg.CORBA.Object stub = null ;

        try {
	    OutputStream os = request(null, "_interface", true);
	    is = (InputStream) invoke((org.omg.CORBA.Object)null, os);

	    org.omg.CORBA.Object objimpl = 
		(org.omg.CORBA.Object) is.read_Object();

	    // check if returned object is of correct type
	    if ( !objimpl._is_a("IDL:omg.org/CORBA/InterfaceDef:1.0") )
		throw wrapper.wrongInterfaceDef(CompletionStatus.COMPLETED_MAYBE);

	    try {
                stub = (org.omg.CORBA.Object)
                    JDKBridge.loadClass("org.omg.CORBA._InterfaceDefStub").
		        newInstance();
	    } catch (Exception ex) {
		throw wrapper.noInterfaceDefStub( ex ) ;
	    }

	    org.omg.CORBA.portable.Delegate del = 
		StubAdapter.getDelegate( objimpl ) ;
	    StubAdapter.setDelegate( stub, del ) ;
	} catch (ApplicationException e) {
	    // This cannot happen.
	    throw wrapper.applicationExceptionInSpecialMethod( e ) ;
	} catch (RemarshalException e) {
	    return get_interface_def(obj);
	} finally {
	    releaseReply((org.omg.CORBA.Object)null, (InputStream)is);
        }

	return stub;
    }

    public boolean is_a(org.omg.CORBA.Object obj, String dest) 
    {
	if (orb.subcontractDebugFlag) {
	    dprint( ".is_a->: type = " + dest ) ;
	}

	try {
	    while (true) {
		// dest is the typeId of the interface to compare against.
		// repositoryIds is the list of typeIds that the stub knows about.

		// First we look for an answer using local information.

		String [] repositoryIds = StubAdapter.getTypeIds( obj ) ;
		String myid = contactInfoList.getTargetIOR().getTypeId();
		if ( dest.equals(myid) ) {
		    if (orb.subcontractDebugFlag) {
			dprint( ".is_a: found myid" ) ;
		    }
		    return true;
		}
		for ( int i=0; i<repositoryIds.length; i++ ) {
		    if ( dest.equals(repositoryIds[i]) ) {
			if (orb.subcontractDebugFlag) {
			    dprint( ".is_a: found id in repository IDs" ) ;
			}
			return true;
		    }
		}

		// But repositoryIds may not be complete, so it may be necessary to
		// go to server.

		InputStream is = null;
		try {
		    if (orb.subcontractDebugFlag) {
			dprint( ".is_a: calling server for is_a" ) ;
		    }

		    OutputStream os = request(null, "_is_a", true);
		    os.write_string(dest);
		    is = (InputStream) invoke((org.omg.CORBA.Object) null, os);

		    boolean result = is.read_boolean();
		    if (orb.subcontractDebugFlag) {
			dprint( ".is_a: server returned " + result ) ;
		    }

		    return result ;
		} catch (ApplicationException e) {
		    // This cannot happen.
		    throw wrapper.applicationExceptionInSpecialMethod( e ) ;
		} catch (RemarshalException e) {
		    // Fall through and retry the operation after a short
		    // pause
		    if (orb.subcontractDebugFlag) {
			dprint( ".is_a: retrying" ) ;
		    }
		    try {
			Thread.sleep( 5 ) ;
		    } catch (Exception exc) {
			// ignore the exception
		    }
		} finally {
		    releaseReply((org.omg.CORBA.Object)null, (InputStream)is);
		}
	    }
	} finally {
	    if (orb.subcontractDebugFlag) {
		dprint( ".is_a<-:" ) ;
	    }
	}
    }
    
    public boolean non_existent(org.omg.CORBA.Object obj) 
    {
	InputStream is = null;
        try {
            OutputStream os = request(null, "_non_existent", true);
            is = (InputStream) invoke((org.omg.CORBA.Object)null, os);

	    return is.read_boolean();

	} catch (ApplicationException e) {
	    // This cannot happen.
	    throw wrapper.applicationExceptionInSpecialMethod( e ) ;
	} catch (RemarshalException e) {
	    return non_existent(obj);
	} finally {
	    releaseReply((org.omg.CORBA.Object)null, (InputStream)is);
        }
    }
    
    public org.omg.CORBA.Object duplicate(org.omg.CORBA.Object obj) 
    {
	return obj;
    }
    
    public void release(org.omg.CORBA.Object obj) 
    {
	// DO NOT clear out internal variables to release memory
	// This delegate may be pointed-to by other objrefs.
    }

    // obj._get_delegate() == this due to the argument passing conventions in
    // portable.ObjectImpl, so we just ignore obj here.
    public boolean is_equivalent(org.omg.CORBA.Object obj,
				 org.omg.CORBA.Object ref)
    {
	if ( ref == null )
	    return false;

	// If ref is a local object, it is not a Stub!
	if (!StubAdapter.isStub(ref))
	    return false ;

	Delegate del = StubAdapter.getDelegate(ref) ;
	if (del == null)
	    return false ;

	// Optimize the x.is_equivalent( x ) case
	if (del == this)
	    return true;

	// If delegate was created by a different ORB, return false
	if (!(del instanceof CorbaClientDelegateImpl))
	    return false ;

	CorbaClientDelegateImpl corbaDelegate = (CorbaClientDelegateImpl)del ;
	CorbaContactInfoList ccil = 
	    (CorbaContactInfoList)corbaDelegate.getContactInfoList() ;
	return this.contactInfoList.getTargetIOR().isEquivalent( 
	    ccil.getTargetIOR() );
    }

    /**
     * This method overrides the org.omg.CORBA.portable.Delegate.equals method,
     * and does the equality check based on IOR equality.
     */
    public boolean equals(org.omg.CORBA.Object self, java.lang.Object other) 
    {
	if (other == null)
	    return false ;

        if (!StubAdapter.isStub(other)) {
            return false;   
        }
        
	Delegate delegate = StubAdapter.getDelegate( other ) ;
	if (delegate == null)
	    return false ;

        if (delegate instanceof CorbaClientDelegateImpl) {
            CorbaClientDelegateImpl otherDel = (CorbaClientDelegateImpl)
		delegate ;
            IOR otherIor = otherDel.contactInfoList.getTargetIOR();
            return this.contactInfoList.getTargetIOR().equals(otherIor);
        } 

	// Come here if other is not implemented by our ORB.
        return false;
    }

    public int hashCode(org.omg.CORBA.Object obj)
    {
	return this.hashCode() ;
    }

    public int hash(org.omg.CORBA.Object obj, int maximum) 
    {
	int h = this.hashCode();
	if ( h > maximum )
	    return 0;
	return h;
    }
    
    public Request request(org.omg.CORBA.Object obj, String operation) 
    {
	return new RequestImpl(orb, obj, null, operation, null, null, null,
			       null);
    }
    
    public Request create_request(org.omg.CORBA.Object obj,
				  Context ctx,
				  String operation,
				  NVList arg_list,
				  NamedValue result) 
    {
	return new RequestImpl(orb, obj, ctx, operation, arg_list,
			       result, null, null);
    }
    
    public Request create_request(org.omg.CORBA.Object obj,
				  Context ctx,
				  String operation,
				  NVList arg_list,
				  NamedValue result,
				  ExceptionList exclist, 
				  ContextList ctxlist) 
    {
	return new RequestImpl(orb, obj, ctx, operation, arg_list, result,
			       exclist, ctxlist);
    }
    
    public org.omg.CORBA.ORB orb(org.omg.CORBA.Object obj) 
    {
	return this.orb;
    }
    
    /**
     * Returns true if this object is implemented by a local servant.
     *
     * REVISIT: locatedIOR should be replaced with a method call that
     *	    returns the current IOR for this request (e.g. ContactInfoChooser).
     *
     * @param self The object reference which delegated to this delegate.
     * @return true only if the servant incarnating this object is located in
     * this ORB. 
     */
    public boolean is_local(org.omg.CORBA.Object self) 
    {
	// XXX this needs to check isNextCallValid
        return contactInfoList.getEffectiveTargetIOR().getProfile().
	    isLocal();
    }
    
    public ServantObject servant_preinvoke(org.omg.CORBA.Object self,
					   String operation,
					   Class expectedType) 
    {
	return
	    contactInfoList.getLocalClientRequestDispatcher()
	    .servant_preinvoke(self, operation, expectedType);
    }
    
    public void servant_postinvoke(org.omg.CORBA.Object self,
				   ServantObject servant) 
    {
	contactInfoList.getLocalClientRequestDispatcher()
	    .servant_postinvoke(self, servant);
    }
    
    // XXX Should this be public?
    /* Returns the codebase for object reference provided.
     * @param self the object reference whose codebase needs to be returned.
     * @return the codebase as a space delimited list of url strings or
     * null if none.
     */
    public String get_codebase(org.omg.CORBA.Object self) 
    {
	if (contactInfoList.getTargetIOR() != null) {
	    return contactInfoList.getTargetIOR().getProfile().getCodebase();
	}
	return null;
    }

    public String toString(org.omg.CORBA.Object self) 
    {
	return contactInfoList.getTargetIOR().stringify();
    }
    
    ////////////////////////////////////////////////////
    //
    // java.lang.Object
    //

    public int hashCode()
    {
	return this.contactInfoList.hashCode();
    }

    protected void dprint(String msg)
    {
	ORBUtility.dprint("CorbaClientDelegateImpl", msg);
    }

}

// End of file.


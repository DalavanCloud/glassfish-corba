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

package com.sun.corba.se.impl.oa.poa ;

import java.util.Set ;
import java.util.HashSet ;
import java.util.Collections ;
import java.util.Iterator ;
import java.util.Map ;
import java.util.WeakHashMap ;

import org.omg.CORBA.OBJECT_NOT_EXIST ;
import org.omg.CORBA.TRANSIENT ;

import org.omg.CORBA.ORBPackage.InvalidName ;

import org.omg.PortableServer.Servant ;
import org.omg.PortableServer.POA ;
import org.omg.PortableServer.POAManager ;

import com.sun.corba.se.spi.oa.ObjectAdapter ;
import com.sun.corba.se.spi.oa.ObjectAdapterFactory ;

import com.sun.corba.se.spi.ior.ObjectAdapterId ;

import com.sun.corba.se.spi.orb.ORB ;

import com.sun.corba.se.spi.orbutil.closure.Closure ;
import com.sun.corba.se.spi.orbutil.closure.ClosureFactory ;

import com.sun.corba.se.spi.protocol.PIHandler ;

import com.sun.corba.se.impl.logging.POASystemException ;
import com.sun.corba.se.impl.logging.OMGSystemException ;

import com.sun.corba.se.spi.orbutil.ORBConstants ;

import com.sun.corba.se.impl.oa.poa.POAManagerImpl ;

public class POAFactory implements ObjectAdapterFactory 
{
    // Maps servants to POAs for deactivating servants when unexportObject is called.
    // Maintained by POAs activate_object and deactivate_object.
    private Map exportedServantsToPOA = new WeakHashMap();

    private Set poaManagers ;
    private int poaManagerId ;
    private int poaId ;
    private POAImpl rootPOA ;
    private DelegateImpl delegateImpl;
    private ORB orb ;
    private POASystemException wrapper ;
    private OMGSystemException omgWrapper ;
    private boolean isShuttingDown = false ;

    public POASystemException getWrapper() 
    {
	return wrapper ;
    }

    /** All object adapter factories must have a no-arg constructor.
    */
    public POAFactory() 
    {
	poaManagers = Collections.synchronizedSet(new HashSet(4));
	poaManagerId = 0 ;
	poaId = 0 ;
	rootPOA = null ;
	delegateImpl = null ;
	orb = null ;
    }

    public synchronized POA lookupPOA (Servant servant) 
    {
        return (POA)exportedServantsToPOA.get(servant);
    }

    public synchronized void registerPOAForServant(POA poa, Servant servant) 
    {
        exportedServantsToPOA.put(servant, poa);
    }

    public synchronized void unregisterPOAForServant(POA poa, Servant servant) 
    {
        exportedServantsToPOA.remove(servant);
    }

// Implementation of ObjectAdapterFactory interface

    public void init( ORB orb ) 
    {
	this.orb = orb ;
	wrapper = orb.getLogWrapperTable().get_OA_LIFECYCLE_POA() ;
	omgWrapper = orb.getLogWrapperTable().get_OA_LIFECYCLE_OMG() ;
	delegateImpl = new DelegateImpl( orb, this ) ;
	registerRootPOA() ;

	POACurrent poaCurrent = new POACurrent(orb);
	orb.getLocalResolver().register( ORBConstants.POA_CURRENT_NAME, 
	    ClosureFactory.makeConstant( poaCurrent ) ) ;
    }

    public ObjectAdapter find( ObjectAdapterId oaid )
    {
	POA poa=null;
	try {
	    boolean first = true ;
	    Iterator iter = oaid.iterator() ;
	    poa = getRootPOA();
	    while (iter.hasNext()) {
		String name = (String)(iter.next()) ;

		if (first) {
		    if (!name.equals( ORBConstants.ROOT_POA_NAME ))
			throw wrapper.makeFactoryNotPoa( name ) ;
		    first = false ;
		} else {
		    poa = poa.find_POA( name, true ) ;
		}
	    }
	} catch ( org.omg.PortableServer.POAPackage.AdapterNonExistent ex ){
	    throw omgWrapper.noObjectAdaptor( ex ) ;
	} catch ( OBJECT_NOT_EXIST ex ) {
	    throw ex;
	} catch ( TRANSIENT ex ) {
	    throw ex;
	} catch ( Exception ex ) {
	    throw wrapper.poaLookupError( ex ) ;
	}

	if ( poa == null )
	    throw wrapper.poaLookupError() ;

	return (ObjectAdapter)poa;
    }

    public void shutdown( boolean waitForCompletion )
    {
    	// It is important to copy the list of POAManagers first because 
	// pm.deactivate removes itself from poaManagers!
	Iterator managers = null ;
	synchronized (this) {
            isShuttingDown = true ;
	    managers = (new HashSet(poaManagers)).iterator();
	}

	while ( managers.hasNext() ) {
	    try {
	        ((POAManager)managers.next()).deactivate(true, waitForCompletion);
	    } catch ( org.omg.PortableServer.POAManagerPackage.AdapterInactive e ) {}
	}
    }

// Special methods used to manipulate global POA related state

    public synchronized void removePoaManager( POAManager manager ) 
    {
        poaManagers.remove(manager);
    }

    public synchronized void addPoaManager( POAManager manager ) 
    {
        poaManagers.add(manager);
    }

    synchronized public int newPOAManagerId()
    {
	return poaManagerId++ ;
    }

    public void registerRootPOA()
    {
	// We delay the evaluation of makeRootPOA until
	// a call to resolve_initial_references( "RootPOA" ).
	// The Future guarantees that makeRootPOA is only called once.
	Closure rpClosure = new Closure() {
	    public Object evaluate() {
		return POAImpl.makeRootPOA( orb ) ;
	    }
	} ;

	orb.getLocalResolver().register( ORBConstants.ROOT_POA_NAME, 
	    ClosureFactory.makeFuture( rpClosure ) ) ;
    }

    public synchronized POA getRootPOA()
    {
	if (rootPOA == null) {
            if (isShuttingDown) {
                throw omgWrapper.noObjectAdaptor() ;
            }

	    try {
		Object obj = orb.resolve_initial_references(
		    ORBConstants.ROOT_POA_NAME ) ;
		rootPOA = (POAImpl)obj ;
	    } catch (InvalidName inv) {
		throw wrapper.cantResolveRootPoa( inv ) ;
	    } 
	}

	return rootPOA;
    }

    public org.omg.PortableServer.portable.Delegate getDelegateImpl() 
    {
	return delegateImpl ;
    }

    synchronized public int newPOAId()
    {
	return poaId++ ;
    }

    public ORB getORB() 
    {
	return orb ;
    }
} 

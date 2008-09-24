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

package com.sun.corba.se.impl.naming.cosnaming;

// Get CORBA type
import org.omg.CORBA.INITIALIZE;
import org.omg.CORBA.ORB;
import org.omg.CORBA.CompletionStatus;

import org.omg.CORBA.Policy;
import org.omg.CORBA.INTERNAL;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.LifespanPolicyValue;
import org.omg.PortableServer.RequestProcessingPolicyValue;
import org.omg.PortableServer.IdAssignmentPolicyValue;
import org.omg.PortableServer.ServantRetentionPolicyValue;

// Get org.omg.CosNaming types
import org.omg.CosNaming.NamingContext;

// Import transient naming context
import com.sun.corba.se.impl.naming.cosnaming.TransientNamingContext;
import com.sun.corba.se.spi.orbutil.ORBConstants;


import com.sun.corba.se.impl.logging.NamingSystemException;

/**
 * Class TransientNameService implements a transient name service
 * using TransientNamingContexts and TransientBindingIterators, which
 * implement the org.omg.CosNaming::NamingContext and org.omg.CosNaming::BindingIterator
 * interfaces specfied by the OMG Common Object Services Specification.
 * <p>
 * The TransientNameService creates the initial NamingContext object.
 * @see NamingContextImpl
 * @see BindingIteratorImpl
 * @see TransientNamingContext
 * @see TransientBindingIterator
 */
public class TransientNameService
{
    /**
     * Constructs a new TransientNameService, and creates an initial
     * NamingContext, whose object
     * reference can be obtained by the initialNamingContext method.
     * @param orb The ORB object
     * @exception org.omg.CORBA.INITIALIZE Thrown if
     * the TransientNameService cannot initialize.
     */
    public TransientNameService(com.sun.corba.se.spi.orb.ORB orb )
        throws org.omg.CORBA.INITIALIZE
    {
        // Default constructor uses "NameService" as the key for the Root Naming
        // Context. If default constructor is used then INS's object key for
        // Transient Name Service is "NameService"
        initialize( orb, "NameService" );
    }

    /**
     * Constructs a new TransientNameService, and creates an initial
     * NamingContext, whose object
     * reference can be obtained by the initialNamingContext method.
     * @param orb The ORB object
     * @param nameserviceName Stringified key used for INS Service registry
     * @exception org.omg.CORBA.INITIALIZE Thrown if
     * the TransientNameService cannot initialize.
     */
    public TransientNameService(com.sun.corba.se.spi.orb.ORB orb,
        String serviceName ) throws org.omg.CORBA.INITIALIZE
    {
        // This constructor gives the flexibility of providing the Object Key
        // for the Root Naming Context that is registered with INS.
        initialize( orb, serviceName );
    }


    /** 
     * This method initializes Transient Name Service by associating Root 
     * context with POA and registering the root context with INS Object Keymap.
     */ 
    private void initialize( com.sun.corba.se.spi.orb.ORB orb,
        String nameServiceName )
        throws org.omg.CORBA.INITIALIZE
    {
	NamingSystemException wrapper = orb.getLogWrapperTable().get_NAMING_Naming() ;

        try {
            POA rootPOA = (POA) orb.resolve_initial_references( 
		ORBConstants.ROOT_POA_NAME );
            rootPOA.the_POAManager().activate();

            int i = 0;
            Policy[] poaPolicy = new Policy[3];
            poaPolicy[i++] = rootPOA.create_lifespan_policy(
                LifespanPolicyValue.TRANSIENT);
            poaPolicy[i++] = rootPOA.create_id_assignment_policy(
                IdAssignmentPolicyValue.SYSTEM_ID);
            poaPolicy[i++] = rootPOA.create_servant_retention_policy(
                ServantRetentionPolicyValue.RETAIN);

            POA nsPOA = rootPOA.create_POA( "TNameService", null, poaPolicy );
            nsPOA.the_POAManager().activate();

            // Create an initial context
            TransientNamingContext initialContext =
                new TransientNamingContext(orb, null, nsPOA);
            byte[] rootContextId = nsPOA.activate_object( initialContext );
            initialContext.localRoot =
                nsPOA.id_to_reference( rootContextId );
            theInitialNamingContext = initialContext.localRoot;
            orb.register_initial_reference( nameServiceName, 
                theInitialNamingContext );
        } catch (org.omg.CORBA.SystemException e) {
	    throw wrapper.transNsCannotCreateInitialNcSys( e ) ;
        } catch (Exception e) {
	    throw wrapper.transNsCannotCreateInitialNc( e ) ;
        } 
    }


    /**
     * Return the initial NamingContext.
     * @return the object reference for the initial NamingContext.
     */
    public org.omg.CORBA.Object initialNamingContext()
    {
	return theInitialNamingContext;
    }


    // The initial naming context for this name service
    private org.omg.CORBA.Object theInitialNamingContext;
}

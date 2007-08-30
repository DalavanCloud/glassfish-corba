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

import org.omg.PortableServer.Servant ;
import org.omg.PortableServer.ServantManager ;
import org.omg.PortableServer.ForwardRequest ;

import org.omg.PortableServer.POAPackage.ObjectAlreadyActive ;
import org.omg.PortableServer.POAPackage.ServantAlreadyActive ;
import org.omg.PortableServer.POAPackage.ServantNotActive ;
import org.omg.PortableServer.POAPackage.NoServant ;
import org.omg.PortableServer.POAPackage.WrongPolicy ;
import org.omg.PortableServer.POAPackage.ObjectNotActive ;

/** POAPolicyMediator defines an interface to which the POA delegates all
 * policy specific operations.  This permits code paths for different
 * policies to be optimized by creating the correct code at POA creation 
 * time.  Also note that as much as possible, this interface does not 
 * do any concurrency control, except as noted.  The POA is responsible
 * for concurrency control.
 */
public interface POAPolicyMediator {
    /** Return the policies object that was used to create this
    * POAPolicyMediator.
    */
    Policies getPolicies() ;

    /** Return the subcontract ID to use in the IIOP profile in IORs
    * created by this POAPolicyMediator's POA.  This is initialized
    * according to the policies and the POA used to construct this
    * POAPolicyMediator in the POAPolicyMediatorFactory.
    */
    int getScid() ;

    /** Return the server ID to use in the IIOP profile in IORs
    * created by this POAPolicyMediator's POA.  This is initialized
    * according to the policies and the POA used to construct this
    * POAPolicyMediator in the POAPolicyMediatorFactory.
    */
    int getServerId() ;

    /** Get the servant to use for an invocation with the 
    * given id and operation.
    * @param id the object ID for which we are requesting a servant
    * @param operation the name of the operation to be performed on 
    * the servant
    * @return the resulting Servant.
    */
    java.lang.Object getInvocationServant( byte[] id, 
	String operation ) throws ForwardRequest ;

    /** Release a servant that was obtained from getInvocationServant.
    */
    void returnServant() ;

    /** Etherealize all servants associated with this POAPolicyMediator.
    * Does nothing if the retention policy is non-retain.
    */
    void etherealizeAll() ;

    /** Delete everything in the active object map.
    */
    void clearAOM() ;

    /** Return the servant manager.  Will throw WrongPolicy
    * if the request processing policy is not USE_SERVANT_MANAGER.
    */
    ServantManager getServantManager() throws WrongPolicy ;

    /** Set the servant manager.  Will throw WrongPolicy
    * if the request processing policy is not USE_SERVANT_MANAGER.
    */
    void setServantManager( ServantManager servantManager ) throws WrongPolicy ;

    /** Return the default servant.   Will throw WrongPolicy
    * if the request processing policy is not USE_DEFAULT_SERVANT.
    */
    Servant getDefaultServant() throws NoServant, WrongPolicy ;

    /** Set the default servant.   Will throw WrongPolicy
    * if the request processing policy is not USE_DEFAULT_SERVANT.
    */
    void setDefaultServant( Servant servant ) throws WrongPolicy ;

    void activateObject( byte[] id, Servant servant ) 
	throws ObjectAlreadyActive, ServantAlreadyActive, WrongPolicy ;

    /** Deactivate the object that is associated with the given id.
    * Returns the servant for id.
    */
    Servant deactivateObject( byte[] id ) throws ObjectNotActive, WrongPolicy ;

    /** Allocate a new, unique system ID.  Requires the ID assignment policy
    * to be SYSTEM.
    */
    byte[] newSystemId() throws WrongPolicy ;

    byte[] servantToId( Servant servant ) throws ServantNotActive, WrongPolicy ;

    Servant idToServant( byte[] id ) throws ObjectNotActive, WrongPolicy ;
}

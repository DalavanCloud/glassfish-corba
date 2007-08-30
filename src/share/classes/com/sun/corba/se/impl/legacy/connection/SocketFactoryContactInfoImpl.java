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

package com.sun.corba.se.impl.legacy.connection;

import com.sun.corba.se.pept.transport.Connection;

import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.ior.IOR;
import com.sun.corba.se.spi.transport.CorbaContactInfoList;
import com.sun.corba.se.spi.transport.SocketInfo;

import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.transport.SocketOrChannelContactInfoImpl;


/**
 * @author Harold Carr
 */
public class SocketFactoryContactInfoImpl 
    extends
	SocketOrChannelContactInfoImpl
{
    protected ORBUtilSystemException wrapper;
    protected SocketInfo socketInfo;

    // XREVISIT 
    // See SocketOrChannelAcceptorImpl.createMessageMediator
    // See SocketFactoryContactInfoImpl.constructor()
    // See SocketOrChannelContactInfoImpl.constructor()
    public SocketFactoryContactInfoImpl()
    {
    }

    public SocketFactoryContactInfoImpl(
        ORB orb,
	CorbaContactInfoList contactInfoList,
	IOR effectiveTargetIOR,
	short addressingDisposition,
	SocketInfo cookie)
    {
	super(orb, contactInfoList);
	this.effectiveTargetIOR = effectiveTargetIOR;
        this.addressingDisposition = addressingDisposition;

	wrapper =  orb.getLogWrapperTable().get_RPC_TRANSPORT_ORBUtil() ;

	socketInfo = 
	    orb.getORBData().getLegacySocketFactory()
	        .getEndPointInfo(orb, effectiveTargetIOR, cookie);

	socketType = socketInfo.getType();
	hostname = socketInfo.getHost();
	port = socketInfo.getPort();
    }

    ////////////////////////////////////////////////////
    //
    // pept.transport.ContactInfo
    //

    public Connection createConnection() 
    {
	Connection connection =
	    new SocketFactoryConnectionImpl(
                orb, this,
		orb.getORBData().connectionSocketUseSelectThreadToWait(),
		orb.getORBData().connectionSocketUseWorkerThreadForEvent());
	return connection;
    }

    ////////////////////////////////////////////////////
    //
    // java.lang.Object
    //

    public String toString()
    {
	return
	    "SocketFactoryContactInfoImpl[" 
	    + socketType + " "
	    + hostname + " "
	    + port
	    + "]";
    }
}

// End of file.

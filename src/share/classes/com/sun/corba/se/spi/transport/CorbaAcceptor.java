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

package com.sun.corba.se.spi.transport;

import com.sun.corba.se.impl.encoding.CDRInputObject;
import com.sun.corba.se.impl.encoding.CDROutputObject;
import com.sun.corba.se.spi.protocol.CorbaMessageMediator;
import com.sun.corba.se.spi.ior.IORTemplate;

// REVISIT - impl/poa specific:
import com.sun.corba.se.impl.oa.poa.Policies;
import com.sun.corba.se.spi.orb.ORB;
import java.net.ServerSocket;

/**
 * @author Harold Carr
 */
public abstract interface CorbaAcceptor
{
    public String getObjectAdapterId();
    public String getObjectAdapterManagerId();
    public void addToIORTemplate(IORTemplate iorTemplate, Policies policies,
				 String codebase);
    public String getMonitoringName();

    /**
     * Used to initialize an <code>Acceptor</code>.
     *
     * For example, initialization may mean to create a
     * {@link java.nio.channels.ServerSocketChannel ServerSocketChannel}.
     *
     * Note: this must be prepared to be be called multiple times.
     *
     * @return <code>true</code> when it performs initializatin
     * actions (typically the first call.
     */
    public boolean initialize();

    /**
     * Used to determine if an <code>Acceptor</code> has been initialized.
     *
     * @return <code>true</code. if the <code>Acceptor</code> has been
     * initialized.
     */
    public boolean initialized();

    public String getConnectionCacheType();

    public void setConnectionCache(CorbaInboundConnectionCache connectionCache);

    public CorbaInboundConnectionCache getConnectionCache();

    /**
     * Used to determine if the <code>Acceptor</code> should register
     * with a Selector to handle accept events.
     *
     * For example, this may be <em>false</em> in the case of Solaris Doors
     * which do not actively listen.
     *
     * @return <code>true</code> if the <code>Acceptor</code> should be
     * registered with a Selector.
     */
    public boolean shouldRegisterAcceptEvent();

    /**
     * Accept a connection request.
     *
     * This is called either when the selector gets an accept event
     * for this <code>Acceptor</code> or by a ListenerThread.
     *
     * It results in a CorbaConnection being created.
     */
    public void accept();

    /**
     * Close the <code>Acceptor</code>.
     */
    public void close();

    public EventHandler getEventHandler();

    public CorbaMessageMediator createMessageMediator(ORB xbroker, CorbaConnection xconnection);

    public CDRInputObject createInputObject(ORB broker, CorbaMessageMediator messageMediator);

    public CDROutputObject createOutputObject(ORB broker, CorbaMessageMediator messageMediator);
    
    public ServerSocket getServerSocket();
}

// End of file.

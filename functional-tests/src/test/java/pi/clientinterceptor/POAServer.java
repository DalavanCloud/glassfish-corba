/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2018 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

package pi.clientinterceptor;

import org.omg.CORBA.*;
import org.omg.CosNaming.*;
import org.omg.CORBA.ORBPackage.*;
import org.omg.PortableServer.*;
import org.omg.PortableServer.POAPackage.*;
import org.omg.PortableInterceptor.*;
import corba.framework.*;
import com.sun.corba.ee.spi.misc.ORBConstants;
import com.sun.corba.ee.impl.interceptors.*;

import java.util.*;
import java.io.*;

import ClientRequestInterceptor.*; // hello interface

public class POAServer 
    implements InternalProcess 
{
    // Set from run()
    private PrintStream out;
    
    private static final String ROOT_POA = "RootPOA";
    
    private POA rootPOA;
    
    private com.sun.corba.ee.spi.orb.ORB orb;

    public static void main(String args[]) {
        try {
            (new POAServer()).run( System.getProperties(),
                                args, System.out, System.err, null );
        }
        catch( Exception e ) {
            e.printStackTrace( System.err );
            System.exit( 1 );
        }
    }

    public void run( Properties environment, String args[], PrintStream out,
                     PrintStream err, Hashtable extra) 
        throws Exception
    {
        this.out = out;

        out.println( "Invoking ORB" );
        out.println( "============" );

        // create and initialize the ORB
        Properties props = new Properties() ;
        props.put( "org.omg.CORBA.ORBClass", 
                   System.getProperty("org.omg.CORBA.ORBClass"));
        ORB orb = ORB.init(args, props);
        this.orb = (com.sun.corba.ee.spi.orb.ORB)orb;

        // Get the root POA:
        rootPOA = null;
        out.println( "Obtaining handle to root POA and activating..." );
        try {
            rootPOA = (POA)orb.resolve_initial_references( ROOT_POA );
        }
        catch( InvalidName e ) {
            err.println( ROOT_POA + " is an invalid name." );
            throw e;
        }
        rootPOA.the_POAManager().activate();
        
        // Set up hello object and helloForward object for POA remote case:
        createAndBind( "Hello1" );
        createAndBind( "Hello1Forward" );
        
        //handshake:
        out.println("Server is ready.");
        out.flush();

        // wait for invocations from clients
        java.lang.Object sync = new java.lang.Object();
        synchronized (sync) {
            sync.wait();
        }

    }
    
    /**
     * Implementation borrowed from corba.socket.HelloServer test
     */
    public void createAndBind (String name)
        throws Exception
    {
        // create servant and register it with the ORB
        helloServant helloRef = new helloServant( out );
      
        byte[] id = rootPOA.activate_object(helloRef);
        org.omg.CORBA.Object ref = rootPOA.id_to_reference(id);
      
        // get the root naming context
        org.omg.CORBA.Object objRef = 
            orb.resolve_initial_references("NameService");
        NamingContext ncRef = NamingContextHelper.narrow(objRef);
      
        // bind the Object Reference in Naming
        NameComponent nc = new NameComponent(name, "");
        NameComponent path[] = {nc};
            
        ncRef.rebind(path, ref);
    }

}

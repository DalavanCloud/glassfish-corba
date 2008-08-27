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
package naming.rinameservice;

import com.sun.corba.se.impl.naming.cosnaming.TransientNameService;
import com.sun.corba.se.spi.orbutil.ORBConstants;
import org.omg.CORBA.ORB;
import corba.framework.*;
import java.util.*;
import java.io.*;

/** 
 * This is a simple test to demonstrate the NameService that we ship with RI
 * works. It
 * 1. Instantiates ORB by passing Persistent Port property so that there is
 *    is a listener on port 1050
 * 2. Instantiates TransientNameService by passing the ORB
 */
public class NameServer implements InternalProcess
{

    public static void main( String args[] ) {
        try {
            (new NameServer()).run( System.getProperties(),
                                args, System.out, System.err, null );
        } catch( Exception e ) {
            e.printStackTrace( System.err );
            System.exit( 1 );
        }
    }

    public void run(Properties environment,
                    String args[],
                    PrintStream out,
                    PrintStream err,
                    Hashtable extra) throws Exception
    {
        try {
            Properties orbProperties = new Properties( );
            orbProperties.put( ORBConstants.PERSISTENT_SERVER_PORT_PROPERTY,
                TestConstants.RI_NAMESERVICE_PORT );
            orbProperties.put( "org.omg.CORBA.ORBClass",
                       "com.sun.corba.se.impl.orb.ORBImpl" );
	    orbProperties.setProperty( ORBConstants.DEBUG_PROPERTY, "subcontract,giop,transport" ) ;
            ORB orb = ORB.init( args, orbProperties );
            TransientNameService standaloneNameService = 
                new TransientNameService( 
                    (com.sun.corba.se.spi.orb.ORB)orb );
	    System.out.println( "Server is ready." ) ;
            orb.run( );
        } catch( Exception e ) {
            System.err.println( "Exception In NameServer " + e );
            e.printStackTrace( );
            System.exit( 1 );
        }
    }
}

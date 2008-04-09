/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2000-2007 Sun Microsystems, Inc. All rights reserved.
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
package hopper.h4670827;

import org.omg.CORBA.ORB;
import org.omg.CORBA.Object;
import java.util.*;
import corba.framework.JUnitReportHelper ;

public class Client implements Runnable {
    private JUnitReportHelper helper ;
    private boolean failed = false ;

    public static void main(String args[]) {
        new Client().run();
    }

    private ORB orb;

    public void run() {
        helper = new JUnitReportHelper( Client.class.getName() ) ;
        try {
            orb = ORB.init( (String[]) null, null );

            for (Object[] arr : TestConstants.data) {
                String name = arr[0] ;
                String url = arr[1] ;
                boolean shouldSucceed = arr[2] ;
                helper.start( name ) ;
                try {
                    if (testURL( url, shouldSucceed )) {
                        System.out.println( "Passed test " + name ) ;
                        helper.pass() ;
                    } else {
                        System.out.println( "Test " + name + " failed" ) ;
                        helper.fail( "failed" ) ;
                        failed = true ;
                    }
                } catch (Exception exc) {
                    helper.fail( exc ) ;
                    failed = true ;
                }
            }

        System.out.println("Thread "+ Thread.currentThread()+" done.");
        if (failed)
            System.exit(1) ;
    }

    private boolean testURL ( String url, boolean shouldPass ) {
        if (shouldPass) {
            org.omg.CORBA.Object obj = orb.string_to_object( url );
            if( obj == null ) {
                System.err.println( url + " lookup failed.." );
                return false;
            }
            Hello helloRef = HelloHelper.narrow( obj );
            String returnString = helloRef.sayHello( );
            if( !returnString.equals( TestConstants.returnString ) ) {
                System.err.println( " hello.sayHello() did not return.." +
                    TestConstants.returnString );
                System.err.flush( );
                return false;
            }
        } else {
            try {
                org.omg.CORBA.Object obj = orb.string_to_object( url );
                Hello helloRef = HelloHelper.narrow( obj );
                String returnString = helloRef.sayHello( );
                // Shouldn't be here
                return false;
            } catch( Exception e ) {
                System.out.println( "Caught Exception " + e + " as expected " );
            }
        }
        
        // If we are here then we passed the test
        return true;
    }
}

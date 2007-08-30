/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2003-2007 Sun Microsystems, Inc. All rights reserved.
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

//
// Created       : 2003 Apr 09 (Wed) 16:54:21 by Harold Carr.
// Last Modified : 2003 Sep 02 (Tue) 10:47:19 by Harold Carr.
//

package corba.tcpreadtimeout;

import org.omg.CORBA.COMM_FAILURE;
import org.omg.CORBA.ORB;

import corba.framework.Controller;
import corba.hcks.C;
import corba.hcks.U;

public class Client 
{
    public static final String baseMsg = Client.class.getName();
    public static final String main = baseMsg + ".main";
    
    public static ORB orb;
    public static idlI idlIPOA;
    public static String idlIPOAArgO = Server.idlIPOA + "O";
    public static String idlIPOAArgN = Server.idlIPOA + "N";

    public static void main(String[] av)
    {
        try {
	    U.sop(main + " starting");

	    orb = ORB.init(av, null);
	    idlIPOA     = idlIHelper.narrow(U.resolve(Server.idlIPOA,    orb));

	    U.sop(idlIPOA.o(idlIPOAArgO));
	    U.sop(idlIPOA.return_after_client_gives_up(idlIPOAArgN));

	    orb.shutdown(true);
        } catch (Exception e) {
            U.sopUnexpectedException(main + " : ", e);
	    System.exit(1);
        }
	U.sop(main + " ending successfully");
	System.exit(Controller.SUCCESS);
    }
}

// End of file.


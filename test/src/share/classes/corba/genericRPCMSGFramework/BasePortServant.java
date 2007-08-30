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
//
// Created       : 2001 Nov 27 (Tue) 11:25:06 by Harold Carr.
// Last Modified : 2002 Apr 24 (Wed) 15:35:10 by Harold Carr.
//

package corba.genericRPCMSGFramework;

import corba.hcks.U;

import java.rmi.RemoteException;
import javax.rmi.PortableRemoteObject;

public class BasePortServant
    extends 
	PortableRemoteObject
    implements
	BasePort
{
    public static final String baseMsg = BasePortServant.class.getName();

    public BasePortServant()
	throws
	    RemoteException
    { 
	// DO NOT CALL SUPER - that would connect the object.
	super(); // REVISIT
    }

    public void echoVoid() throws 
         java.rmi.RemoteException 
    {
	return; 
    }
    public float[] echoFloatArray(float[] inputFloatArray) throws 
         java.rmi.RemoteException { return null; }
    public int[] echoIntegerArray(int[] inputIntegerArray) throws 
         java.rmi.RemoteException { return null; }
    public java.lang.String[] echoStringArray(java.lang.String[] inputStringArray) throws 
         java.rmi.RemoteException { return null; }
    public float echoFloat(float inputFloat) throws 
         java.rmi.RemoteException 
    {
	return inputFloat; 
    }
    public boolean echoBoolean(boolean inputBoolean) throws 
         java.rmi.RemoteException 
    { 
	return inputBoolean;
    }
    public java.math.BigDecimal echoDecimal(java.math.BigDecimal inputDecimal) throws 
         java.rmi.RemoteException { return null; }
    public int echoInteger(int inputInteger) throws 
         java.rmi.RemoteException 
    {
	return inputInteger;
    }
    public java.lang.String echoString(java.lang.String inputString) throws 
         java.rmi.RemoteException 
    { 
	U.sop("---> Server received: " + inputString);
	inputString = inputString + " (echo from server)";
	U.sop("<--- Server sending: " + inputString);
	return inputString;
    }
    public java.util.Calendar echoDate(java.util.Calendar inputDate) throws 
         java.rmi.RemoteException { return null; }
    public byte[] echoBase64(byte[] inputBase64) throws 
         java.rmi.RemoteException { return null; }
    /*
    public SOAPStruct echoStruct(SOAPStruct inputStruct) throws 
         java.rmi.RemoteException { return null; }
    */
    public SOAPStruct[] echoStructArray(SOAPStruct[] inputStructArray) throws 
         java.rmi.RemoteException { return null; }
    public byte[] echoHexBinary(byte[] inputHexBinary) throws 
         java.rmi.RemoteException { return null; }

    public SOAPStructSerializable echoAsStruct(float inputFloat, 
					       int inputInt, 
					       String inputString)
	throws 
	    java.rmi.RemoteException
    {
	return null;
    }

}

// End of file.

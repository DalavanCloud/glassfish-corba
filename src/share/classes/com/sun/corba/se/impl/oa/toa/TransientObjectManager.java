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
/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 * US Government Users Restricted Rights - Use, duplication or
 * disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

package com.sun.corba.se.impl.oa.toa;

import com.sun.corba.se.impl.orbutil.ORBUtility ;
import com.sun.corba.se.spi.orb.ORB ;
import org.glassfish.gmbal.Description;
import org.glassfish.gmbal.ManagedAttribute;
import org.glassfish.gmbal.ManagedData;

@ManagedData
@Description( "Maintains mapping from Object ID to servant")
public final class TransientObjectManager {
    private ORB orb ;
    private int maxSize = 128;
    private Element[] elementArray; 
    private Element freeList;

    @ManagedAttribute() 
    @Description( "The element array mapping indices into servants" ) 
    // XXX Really should return a deep clone!
    private synchronized Element[] getElements() {
        return elementArray.clone() ;
    }
    void dprint( String msg ) {
	ORBUtility.dprint( this, msg ) ;
    }

    public TransientObjectManager( ORB orb )
    {
	this.orb = orb ;

        elementArray = new Element[maxSize];
        elementArray[maxSize-1] = new Element(maxSize-1,null);
        for ( int i=maxSize-2; i>=0; i-- ) 
            elementArray[i] = new Element(i,elementArray[i+1]);
        freeList = elementArray[0];
    }

    public synchronized byte[] storeServant(java.lang.Object servant, java.lang.Object servantData)
    {
        if ( freeList == null ) 
            doubleSize();

        Element elem = freeList;
        freeList = (Element)freeList.servant;
        
        byte[] result = elem.getKey(servant, servantData);
	if (orb.transientObjectManagerDebugFlag)
	    dprint( "storeServant returns key for element " + elem ) ;
	return result ;
    }

    public synchronized java.lang.Object lookupServant(byte transientKey[]) 
    {
        int index = ORBUtility.bytesToInt(transientKey,0);
        int counter = ORBUtility.bytesToInt(transientKey,4);

	if (orb.transientObjectManagerDebugFlag)
	    dprint( "lookupServant called with index=" + index + ", counter=" + counter ) ;

        if (elementArray[index].counter == counter &&
            elementArray[index].valid ) {
	    if (orb.transientObjectManagerDebugFlag)
	        dprint( "\tcounter is valid" ) ;
            return elementArray[index].servant;
	}

        // servant not found 
	if (orb.transientObjectManagerDebugFlag)
	    dprint( "\tcounter is invalid" ) ;
        return null;
    }

    public synchronized java.lang.Object lookupServantData(byte transientKey[])
    {
        int index = ORBUtility.bytesToInt(transientKey,0);
        int counter = ORBUtility.bytesToInt(transientKey,4);

	if (orb.transientObjectManagerDebugFlag)
	    dprint( "lookupServantData called with index=" + index + ", counter=" + counter ) ;

        if (elementArray[index].counter == counter &&
            elementArray[index].valid ) {
	    if (orb.transientObjectManagerDebugFlag)
	        dprint( "\tcounter is valid" ) ;
            return elementArray[index].servantData;
	}

        // servant not found 
	if (orb.transientObjectManagerDebugFlag)
	    dprint( "\tcounter is invalid" ) ;
        return null;
    }

    public synchronized void deleteServant(byte transientKey[])
    {
        int index = ORBUtility.bytesToInt(transientKey,0);
	if (orb.transientObjectManagerDebugFlag)
	    dprint( "deleting servant at index=" + index ) ;

        elementArray[index].delete(freeList);
        freeList = elementArray[index];
    }

    public synchronized byte[] getKey(java.lang.Object servant)
    {
        for ( int i=0; i<maxSize; i++ )
            if ( elementArray[i].valid && 
                 elementArray[i].servant == servant )
                return elementArray[i].toBytes();

        // if we come here Object does not exist
	return null;
    }

    private void doubleSize()
    {
        // Assume caller is synchronized

        Element old[] = elementArray;
        int oldSize = maxSize;
        maxSize *= 2;
        elementArray = new Element[maxSize];

        for ( int i=0; i<oldSize; i++ )
            elementArray[i] = old[i];    

        elementArray[maxSize-1] = new Element(maxSize-1,null);
        for ( int i=maxSize-2; i>=oldSize; i-- ) 
            elementArray[i] = new Element(i,elementArray[i+1]);
        freeList = elementArray[oldSize];
    }
}


@ManagedData
@Description( "A single element mapping one ObjectId to a Servant")
final class Element {
    java.lang.Object servant=null;     // also stores "next pointer" in free list
    java.lang.Object servantData=null;    
    int index=-1;
    int counter=0; 
    boolean valid=false; // valid=true if this Element contains
    // a valid servant

    @ManagedAttribute
    @Description( "The servant" )
    private synchronized Object getServant() {
        return servant ;
    }

    @ManagedAttribute
    @Description( "The servant data" )
    private synchronized Object getServantData() {
        return servantData ;
    }

    @ManagedAttribute
    @Description( "The reuse counter")
    private synchronized int getReuseCounter() {
        return counter ;
    }

    @ManagedAttribute
    @Description( "The index of this entry")
    private synchronized int getIndex() {
        return index ;
    }

    Element(int i, java.lang.Object next)
    {
        servant = next;
        index = i;
    }

    byte[] getKey(java.lang.Object servant, java.lang.Object servantData)
    {
        this.servant = servant;
        this.servantData = servantData;
        this.valid = true;

        return toBytes();
    }

    byte[] toBytes()
    {    
        // Convert the index+counter into an 8-byte (big-endian) key.

        byte key[] = new byte[8];
        ORBUtility.intToBytes(index, key, 0);
        ORBUtility.intToBytes(counter, key, 4);

        return key;
    }

    void delete(Element freeList)
    {
        if ( !valid )    // prevent double deletion
            return;
        counter++;
        servantData = null;
        valid = false;

        // add this to freeList
        servant = freeList;
    }

    public String toString() 
    {
	return "Element[" + index + ", " + counter + "]" ;
    }
}


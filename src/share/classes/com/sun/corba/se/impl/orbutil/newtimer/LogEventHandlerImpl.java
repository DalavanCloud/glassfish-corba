/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2006-2007 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.corba.se.impl.orbutil.newtimer ;

import java.util.Iterator ;
import java.util.NoSuchElementException ;

import com.sun.corba.se.spi.orbutil.newtimer.Controllable ;
import com.sun.corba.se.spi.orbutil.newtimer.TimerFactory ;
import com.sun.corba.se.spi.orbutil.newtimer.TimerEvent ;
import com.sun.corba.se.spi.orbutil.newtimer.LogEventHandler ;
import com.sun.corba.se.spi.orbutil.newtimer.Timer ;
import com.sun.corba.se.spi.orbutil.newtimer.NamedBase ;

// XXX This needs to be able to properly handle multiple reporting threads!
public class LogEventHandlerImpl extends NamedBase implements LogEventHandler {
    // Default number of entries in data
    private static final int DEFAULT_SIZE = 1000 ;

    // Default increment to number of entries in data
    private static final int DEFAULT_INCREMENT = 1000 ;

    // This is an array for speed.  All data is interleaved here:
    // data[2n] is the id, data[2n+1] is the timestamp for all n >= 0.
    // The array will be resized as needed.
    // id is actually 2*id for enter, 2*id+1 for exit.
    private long[] data ;

    private int size ;
    private int increment ;
    
    // Index of the next free slot in data 
    private int nextFree ;

    LogEventHandlerImpl( TimerFactory factory, String name ) {
	super( factory, name ) ;
	initData( DEFAULT_SIZE, DEFAULT_INCREMENT ) ;
    }

    public synchronized Iterator<TimerEvent> iterator() {
	return new LogEventHandlerIterator( factory(), data, nextFree ) ;
    }

    private void initData( int size, int increment ) {
        this.size = 2*size ;
        this.increment = 2*increment ;
	data = new long[ this.size ] ;
	nextFree = 0 ;
    }

    public void notify( TimerEvent event ) {
	final int id = 2*event.timer().id() + 
	    ((event.type() == TimerEvent.TimerEventType.ENTER) ? 0 : 1) ;
	log( id, event.time() ) ;
    }

    // XXX ignore old compensation idea; do we need it here?
    private synchronized void log( int id, long time ) {
        if (data.length - nextFree < 2) {
            // grow the array
	    int newSize = data.length + 2*increment ;
	    long[] newData = new long[ newSize ] ;
	    System.arraycopy( data, 0, newData, 0, data.length ) ;
	    data = newData ;
	}

        int index = nextFree ;
        nextFree += 2 ;
        
	data[ index ] = id ;
        data[ index + 1 ] = time ;
    }

    public synchronized void clear() {
	initData( size, increment ) ;
    }
}


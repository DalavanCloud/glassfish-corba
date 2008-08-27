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
package com.sun.corba.se.impl.encoding;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.LinkedList;

import com.sun.corba.se.impl.encoding.BufferQueue;
import com.sun.corba.se.impl.encoding.BufferManagerWrite;
import com.sun.corba.se.spi.orbutil.ORBConstants;
import com.sun.corba.se.impl.protocol.giopmsgheaders.Message;
import com.sun.corba.se.impl.encoding.ByteBufferWithInfo;
import com.sun.corba.se.impl.protocol.giopmsgheaders.MessageBase;
import com.sun.corba.se.impl.protocol.giopmsgheaders.FragmentMessage;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.impl.encoding.CDROutputObject;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.pept.transport.Connection;
import com.sun.corba.se.pept.transport.ByteBufferPool;
import com.sun.corba.se.pept.encoding.OutputObject;

/**
 * Collect buffer manager.
 */
public class BufferManagerWriteCollect extends BufferManagerWrite
{
    private BufferQueue queue = new BufferQueue();

    private boolean sentFragment = false;
    private boolean debug = false;


    BufferManagerWriteCollect(ORB orb)
    {
        super(orb);
         if (orb != null)
            debug = orb.transportDebugFlag;
    }

    public boolean sentFragment() {
        return sentFragment;
    }

    /**
     * Returns the correct buffer size for this type of
     * buffer manager as set in the ORB.
     */
    public int getBufferSize() {
        return orb.getORBData().getGIOPFragmentSize();
    }

    // Set the fragment's "more fragments" bit to true, put it in the
    // queue, and allocate a new bbwi.
    public void overflow (ByteBufferWithInfo bbwi)
    {
        // Set the fragment's moreFragments field to true
        MessageBase.setFlag(bbwi.getByteBuffer(), Message.MORE_FRAGMENTS_BIT);

        // Enqueue the previous fragment
        queue.enqueue(bbwi);

        // Create a new bbwi
        ByteBufferWithInfo newBbwi = new ByteBufferWithInfo(orb, this);
        newBbwi.setFragmented(true);

        // XREVISIT - Downcast
        ((CDROutputObject)outputObject).setByteBufferWithInfo(newBbwi);

        // Now we must marshal in the fragment header/GIOP header

        // REVISIT - we can optimize this by not creating the fragment message
        // each time.  

        // XREVISIT - Downcast
        FragmentMessage header =
              ((CDROutputObject)outputObject).getMessageHeader()
                                             .createFragmentMessage();

        header.write((CDROutputObject)outputObject);
    }

    // Send all fragments
    public void sendMessage ()
    {
        // Enqueue the last fragment
        queue.enqueue(((CDROutputObject)outputObject).getByteBufferWithInfo());

        Iterator bufs = iterator();

        Connection conn = 
                          ((OutputObject)outputObject).getMessageMediator().
                                                       getConnection();

        // With the collect strategy, we must lock the connection
        // while fragments are being sent.  This is so that there are
        // no interleved fragments in GIOP 1.1.
        //
        // Note that this thread must not call writeLock again in any
        // of its send methods!
        conn.writeLock();

        try {

            // Get a reference to ByteBufferPool so that the ByteBufferWithInfo
            // ByteBuffer can be released to the ByteBufferPool
            ByteBufferPool byteBufferPool = orb.getByteBufferPool();

            while (bufs.hasNext()) {
                
                ByteBufferWithInfo bbwi = (ByteBufferWithInfo)bufs.next();
                ((CDROutputObject)outputObject).setByteBufferWithInfo(bbwi);
                
                conn.sendWithoutLock(((CDROutputObject)outputObject));

                sentFragment = true;

                // Release ByteBufferWithInfo's ByteBuffer back to the pool
                // of ByteBuffers.
                if (debug)
                {
                    // print address of ByteBuffer being released
                    int bbAddress = System.identityHashCode(bbwi.getByteBuffer());
                    StringBuffer sb = new StringBuffer(80);
                    sb.append("sendMessage() - releasing ByteBuffer id (");
                    sb.append(bbAddress).append(") to ByteBufferPool.");
                    String msg = sb.toString();
                    dprint(msg);
                }
                byteBufferPool.releaseByteBuffer(bbwi.getByteBuffer());
                bbwi.setByteBuffer(null);
                bbwi = null;
            }

            sentFullMessage = true;
            
        } finally {

            conn.writeUnlock();
        }
    }

    /**
     * Close the BufferManagerWrite - do any outstanding cleanup.
     *
     * For a BufferManagerWriteGrow any queued ByteBufferWithInfo must
     * have its ByteBuffer released to the ByteBufferPool.
     */
    public void close()
    {
        // iterate thru queue and release any ByteBufferWithInfo's
        // ByteBuffer that may be remaining on the queue to the
        // ByteBufferPool.

        Iterator bufs = iterator();

        ByteBufferPool byteBufferPool = orb.getByteBufferPool();

        while (bufs.hasNext())
        {
            ByteBufferWithInfo bbwi = (ByteBufferWithInfo)bufs.next();
            if (bbwi != null && bbwi.getByteBuffer() != null)
            {
                if (debug)
                {
                    // print address of ByteBuffer being released
                    int bbAddress = System.identityHashCode(bbwi.getByteBuffer());
                    StringBuffer sb = new StringBuffer(80);
                    sb.append("close() - releasing ByteBuffer id (");
                    sb.append(bbAddress).append(") to ByteBufferPool.");
                    String msg = sb.toString();
                    dprint(msg);
                }
                 byteBufferPool.releaseByteBuffer(bbwi.getByteBuffer());
                 bbwi.setByteBuffer(null);
                 bbwi = null;
            }
        }
    }

    private void dprint(String msg)
    {
        ORBUtility.dprint("BufferManagerWriteCollect", msg);
    }

    private Iterator iterator ()
    {
	return new BufferManagerWriteCollectIterator();
    }

    private class BufferManagerWriteCollectIterator implements Iterator
    {
	public boolean hasNext ()
	{
            return queue.size() != 0;
	}

	public Object next ()
	{
            return queue.dequeue();
        }

	public void remove ()
	{
	    throw new UnsupportedOperationException();
	}
    }
}

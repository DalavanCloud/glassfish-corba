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

package com.sun.corba.se.spi.protocol;

import java.nio.ByteBuffer;

import com.sun.corba.se.spi.transport.CorbaConnection;

import com.sun.corba.se.impl.protocol.giopmsgheaders.Message;


/**
 *
 * An interface that knows how to parse bytes into a protocol data unit.
 */
public interface MessageParser {
    
    /**
     * Is this MessageParser expecting more data ?
     *
     * This method is typically called after a call to <code>parseBytes()</code>
     * to determine if the <code>ByteBuffer</code> which has been parsed
     * contains a partial <code>Message</code>.
     *
     * @return - <code>true</code> if more bytes are needed to construct a
     *           <code>Message</code>.  <code>false</code>, if no 
     *           additional bytes remain to be parsed into a <code>Message</code>.
     */
    boolean isExpectingMoreData();

    /**
     * If there are sufficient bytes in the <code>ByteBuffer</code> to compose a
     * <code>Message</code>, then return a newly initialized <code>Message</code>.
     * Otherwise, return null.
     *
     * When this method is first called, it is assumed that 
     * <code>ByteBuffer.position()</code> points to the location in the 
     * <code>ByteBuffer</code> where the beginning of the first
     * <code>Message</code> begins.
     * 
     * If there is no partial <code>Message</code> remaining in the 
     * <code>ByteBuffer</code> when this method exits, this method will e
     * <code>this.expectingMoreData</code> to <code>false</code>.
     * Otherwise, it will be set to <code>true</code>.
     * 
     * Callees of this method may check <code>isExpectingMoreData()</code> 
     * subsequently to determine if this <code>MessageParser</code> is expecting 
     * more data to complete a protocol data unit.  Callees may also 
     * subsequently check <code>hasMoreBytesToParse()</code> to determine if this 
     * <code>MessageParser</code> has more data to parse in the given
     * <code>ByteBuffer</code>.
     *
     * @return <code>Message</code> if one is found in the <code>ByteBuffer</code>.
     *         Otherwise, returns null.
     */
    // REVISIT - This interface should be declared without a CorbaConnection.
    //           As a result, this interface will likely be deprecated in a
    //           future release in favor of Message parseBytes(ByteBuffer byteBuffer)
    Message parseBytes(ByteBuffer byteBuffer, CorbaConnection connection);

    /**
     * Are there more bytes to be parsed in the <code>ByteBuffer</code> given
     * to this MessageParser's <code>parseBytes</code> ?
     *
     * This method is typically called after a call to <code>parseBytes()</code>
     * to determine if the <code>ByteBuffer</code> has more bytes which need to
     * parsed into a <code>Message</code>.
     *
     * @return <code>true</code> if there are more bytes to be parsed.
     *         Otherwise <code>false</code>.
     */
    boolean hasMoreBytesToParse();

    /**
     * Set the starting position where the next message in the
     * <code>ByteBuffer</code> given to <code>parseBytes()</code> begins.
     */
    void setNextMessageStartPosition(int position);

    /**
     * Get the starting position where the next message in the
     * <code>ByteBuffer</code> given to <code>parseBytes()</code> begins.
     */
    int getNextMessageStartPosition();

    /**
     * Return the suggested number of bytes needed to hold the next message
     * to be parsed.
     */
    int getSizeNeeded();
}

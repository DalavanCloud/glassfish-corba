/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright (c) 1997-2011 Oracle and/or its affiliates. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
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

package com.sun.corba.ee.impl.transport;


import com.sun.corba.ee.impl.encoding.CDRInputObject;
import com.sun.corba.ee.impl.encoding.CDROutputObject;
import com.sun.corba.ee.impl.encoding.CachedCodeBase;
import com.sun.corba.ee.impl.encoding.CodeSetComponentInfo;
import com.sun.corba.ee.impl.encoding.OSFCodeSetRegistry;
import com.sun.corba.ee.impl.protocol.MessageMediatorImpl;
import com.sun.corba.ee.impl.protocol.MessageParserImpl;
import com.sun.corba.ee.impl.protocol.giopmsgheaders.Message;
import com.sun.corba.ee.impl.protocol.giopmsgheaders.MessageBase;
import com.sun.corba.ee.spi.ior.IOR;
import com.sun.corba.ee.spi.ior.iiop.GIOPVersion;
import com.sun.corba.ee.spi.logging.ORBUtilSystemException;
import com.sun.corba.ee.spi.misc.ORBConstants;
import com.sun.corba.ee.spi.orb.ORB;
import com.sun.corba.ee.spi.protocol.MessageMediator;
import com.sun.corba.ee.spi.protocol.MessageParser;
import com.sun.corba.ee.spi.protocol.RequestId;
import com.sun.corba.ee.spi.threadpool.NoSuchThreadPoolException;
import com.sun.corba.ee.spi.threadpool.NoSuchWorkQueueException;
import com.sun.corba.ee.spi.threadpool.Work;
import com.sun.corba.ee.spi.trace.Transport;
import com.sun.corba.ee.spi.transport.*;
import com.sun.org.omg.SendingContext.CodeBase;
import org.glassfish.pfl.tf.spi.annotation.InfoMethod;
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.INTERNAL;
import org.omg.CORBA.SystemException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Harold Carr
 *         <p/>
 *         Note: this is the version WITHOUT the purgeCalls changes.
 *         The changes are in the 1.106 version, which is saved as
 *         SocketOrChannelConnectionImpl.1.106.sjava.
 */
@Transport
public class ConnectionImpl extends EventHandlerBase implements Connection, Work {

    protected static final ORBUtilSystemException wrapper =
            ORBUtilSystemException.self;

    ///
    // New transport.
    //

    protected SocketChannel socketChannel;

    private Message readGIOPMessage(ORB orb) {
        Message msg = readGIOPHeader(orb);
        return readGIOPBody(orb, msg);
    }

    // NOTE: This method is used only when the ORB is configured with
    //       "useNIOSelectToWait=false", aka use blocking Sockets/SocketChannels
    private Message readGIOPHeader(ORB orb) {
        try {
            ByteBuffer buf = read(Message.GIOPMessageHeaderLength, 0, Message.GIOPMessageHeaderLength);
            return MessageBase.parseGiopHeader(orb, this, buf, 0);
        } catch (IOException e) {
            throw wrapper.ioexceptionWhenReadingConnection(e, this);
        }
    }

    private Message readGIOPBody(ORB orb, Message msg) {
        ByteBuffer buf = msg.getByteBuffer();

        buf.position(Message.GIOPMessageHeaderLength);
        int msgSizeMinusHeader = msg.getSize() - Message.GIOPMessageHeaderLength;
        try {
            buf = read(buf, Message.GIOPMessageHeaderLength, msgSizeMinusHeader);
        } catch (IOException e) {
            throw wrapper.ioexceptionWhenReadingConnection(e, this);
        }

        msg.setByteBuffer(buf);

        TransportManager ctm = orb.getTransportManager();
        MessageTraceManagerImpl mtm = (MessageTraceManagerImpl) ctm.getMessageTraceManager();
        if (mtm.isEnabled()) {
            mtm.recordBodyReceived(buf);
        }

        return msg;
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    protected ByteBuffer byteBuffer = null;
    protected long enqueueTime;

    // REVISIT:
    // protected for test: genericRPCMSGFramework.IIOPConnection constructor.
    protected ContactInfo contactInfo;
    protected Acceptor acceptor;
    protected ConnectionCache connectionCache;

    //
    // From iiop.Connection.java
    //

    protected Socket socket;    // The socket used for this connection.
    protected long timeStamp = 0;
    protected boolean isServer = false;

    // Start at some value other than zero since this is a magic
    // value in some protocols.
    protected AtomicInteger requestId = new AtomicInteger(5);
    protected ResponseWaitingRoom responseWaitingRoom;
    private int state;
    protected final java.lang.Object stateEvent = new java.lang.Object();
    protected final java.lang.Object writeEvent = new java.lang.Object();
    protected boolean writeLocked;
    protected int serverRequestCount = 0;

    // Server request map: used on the server side of Connection
    // Maps request ID to IIOPInputStream.
    Map<Integer, MessageMediator> serverRequestMap = null;

    // This is a flag associated per connection telling us if the
    // initial set of sending contexts were sent to the receiver
    // already...
    protected boolean postInitialContexts = false;

    // Remote reference to CodeBase server (supplies
    // FullValueDescription, among other things)
    protected IOR codeBaseServerIOR;

    // CodeBase cache for this connection.  This will cache remote operations,
    // handle connecting, and ensure we don't do any remote operations until
    // necessary.
    protected CachedCodeBase cachedCodeBase = new CachedCodeBase(this);


    // transport read / write timeout values
    protected TcpTimeouts tcpTimeouts;

    // A temporary selector for reading from non-blocking SocketChannels
    // when entire message is not read in one read.
    protected TemporarySelector tmpReadSelector;
    // A lock used for lazily initializing tmpReadSelector
    protected final java.lang.Object tmpReadSelectorLock = new java.lang.Object();

    private NioBufferWriter bufferWriter;
    protected Dispatcher dispatcher = DISPATCHER;

    interface Dispatcher {
        boolean dispatch(MessageMediator messageMediator);
    }

    final static Dispatcher DISPATCHER = new Dispatcher() {
        @Override
        public boolean dispatch(MessageMediator messageMediator) {
            return messageMediator.dispatch();
        }
    };


    // Mapping of a fragmented messages by request id and its corresponding
    // fragmented messages stored in a queue. This mapping is used in the
    // optimized read strategy when message fragments arrive for a given
    // request id to ensure that message fragments get processed in the order
    // in which they arrive.
    // This is a ConcurrentHashMap because one Worker Thread can be putting
    // new entries into the ConcurrentHashMap after parsing new messages
    // via the MessageParser while a different Worker Thread can be removing
    // a different entry as a result of a different request id's final
    // message fragment having just been processed by a CorbaMessageMediator
    // via one of its handleInput methods.
    protected ConcurrentHashMap<RequestId, Queue<MessageMediator>> fragmentMap;

    // Used in genericRPCMSGFramework test.
    public ConnectionImpl(ORB orb) {
        this.orb = orb;

        setWork(this);
        responseWaitingRoom = new ResponseWaitingRoomImpl(orb, this);
        setTcpTimeouts(orb.getORBData().getTransportTcpTimeouts());
    }

    // Both client and servers.
    protected ConnectionImpl(ORB orb,
                             boolean useSelectThreadToWait,
                             boolean useWorkerThread) {
        this(orb);
        setUseSelectThreadToWait(useSelectThreadToWait);
        setUseWorkerThreadForEvent(useWorkerThread);

        if (useSelectThreadToWait) {
            // initialize fragmentMap
            fragmentMap = new ConcurrentHashMap<RequestId, Queue<MessageMediator>>();
        }
    }

    // Client constructor.
    private ConnectionImpl(ORB orb,
                           ContactInfo contactInfo,
                           boolean useSelectThreadToWait,
                           boolean useWorkerThread,
                           String socketType,
                           String hostname,
                           int port) {
        this(orb, useSelectThreadToWait, useWorkerThread);

        this.contactInfo = contactInfo;

        try {
            defineSocket(useSelectThreadToWait,
                    orb.getORBData().getSocketFactory().createSocket(socketType, new InetSocketAddress(hostname, port)));
        } catch (Throwable t) {
            throw wrapper.connectFailure(t, socketType, hostname,
                    Integer.toString(port));
        }
        state = OPENING;
    }

    protected final void defineSocket(boolean useSelectThreadToWait, Socket socket) throws IOException {
        this.socket = socket;
        socketChannel = socket.getChannel();

        if (socketChannel == null) {
            setUseSelectThreadToWait(false);  // IMPORTANT: non-channel-backed sockets must use dedicated reader threads.
        } else {
            socketChannel.configureBlocking(!useSelectThreadToWait);
        }
    }

    // Client-side convenience.
    public ConnectionImpl(ORB orb,
                          ContactInfo contactInfo,
                          String socketType,
                          String hostname,
                          int port) {
        this(orb, contactInfo,
                orb.getORBData().connectionSocketUseSelectThreadToWait(),
                orb.getORBData().connectionSocketUseWorkerThreadForEvent(),
                socketType, hostname, port);
    }

    // Server-side constructor.
    private ConnectionImpl(ORB orb, Acceptor acceptor, Socket socket,
                           boolean useSelectThreadToWait, boolean useWorkerThread) {
        this(orb, useSelectThreadToWait, useWorkerThread);

        try {
            defineSocket(useSelectThreadToWait, socket);
        } catch (IOException e) {
            RuntimeException rte = new RuntimeException();
            rte.initCause(e);
            throw rte;
        }

        this.acceptor = acceptor;
        serverRequestMap = Collections.synchronizedMap(new HashMap<Integer, MessageMediator>());
        isServer = true;

        state = ESTABLISHED;
    }

    // Server-side convenience
    public ConnectionImpl(ORB orb,
                          Acceptor acceptor,
                          Socket socket) {
        this(orb, acceptor, socket,
                (socket.getChannel() != null && orb.getORBData().connectionSocketUseSelectThreadToWait()),
                (socket.getChannel() != null && orb.getORBData().connectionSocketUseWorkerThreadForEvent()));
    }

    ////////////////////////////////////////////////////
    //
    // framework.transport.Connection
    //

    public boolean shouldRegisterReadEvent() {
        return true;
    }

    public boolean shouldRegisterServerReadEvent() {
        return true;
    }

    @Transport
    public boolean read() {
        MessageMediator messageMediator = readBits();

        // Null can happen when client closes stream causing purgecalls.
        return messageMediator == null || dispatcher.dispatch(messageMediator);
    }

    private void unregisterForEventAndPurgeCalls(SystemException ex) {
        // REVISIT - make sure reader thread is killed.
        orb.getTransportManager().getSelector(0).unregisterForEvent(this);
        // Notify anyone waiting.
        purgeCalls(ex, true, false);
    }

    @Transport
    protected MessageMediator readBits() {
        try {
            return createMessageMediator();
        } catch (ThreadDeath td) {
            try {
                purgeCalls(wrapper.connectionAbort(td), false, false);
            } catch (Throwable t) {
                exceptionInfo("purgeCalls", t);
            }
            throw td;
        } catch (Throwable ex) {
            exceptionInfo("readBits", ex);

            if (ex instanceof SystemException) {
                SystemException se = (SystemException) ex;
                if (se.minor == ORBUtilSystemException.CONNECTION_REBIND) {
                    unregisterForEventAndPurgeCalls(se);
                    throw se;
                } else {
                    try {
                        if (se instanceof INTERNAL) {
                            sendMessageError(GIOPVersion.DEFAULT_VERSION);
                        }
                    } catch (IOException e) {
                        exceptionInfo("sendMessageError", e);
                    }
                }
            }
            unregisterForEventAndPurgeCalls(wrapper.connectionAbort(ex));

            // REVISIT
            //keepRunning = false;
            // REVISIT - if this is called after purgeCalls then
            // the state of the socket is ABORT so the writeLock
            // in close throws an exception.  It is ignored but
            // causes IBM (screen scraping) tests to fail.
            //close();
            throw wrapper.throwableInReadBits(ex);
        }
    }

    private MessageMediator createMessageMediator() {
        Message msg = readGIOPMessage(orb);

        ByteBuffer byteBuffer = msg.getByteBuffer();
        msg.setByteBuffer(null);

        return new MessageMediatorImpl(orb, this, msg, byteBuffer);
    }


    public boolean hasSocketChannel() {
        return getSocketChannel() != null;
    }

    // NOTE: This method can throw a connection rebind SystemException.
    @Transport
    public ByteBuffer read(int size, int offset, int length)
            throws IOException {
        try {
            if (hasSocketChannel()) {
                ByteBuffer lbb = orb.getByteBufferPool().getByteBuffer(size);
                lbb.position(offset);
                lbb.limit(size);
                readFully(lbb, length);
                return lbb;
            }

            byte[] buf = new byte[size];
            // getSocket().getInputStream() can throw an IOException
            // if the socket is closed. Hence, we check the connection
            // state CLOSE_RECVD if an IOException is thrown here 
            // instead of in readFully()
            readFully(getSocket().getInputStream(), buf, offset, length);
            ByteBuffer nbb = ByteBuffer.wrap(buf);
            nbb.limit(size);
            return nbb;
        } catch (IOException ioe) {
            if (getState() == CLOSE_RECVD) {
                throw wrapper.connectionRebind(ioe);
            } else {
                throw ioe;
            }
        }
    }

    // NOTE: This method is used only when the ORB is configured with
    //       "useNIOSelectToWait=false", aka use blocking Sockets/SocketChannels.
    // NOTE: This method can throw a connection rebind SystemException.
    @Transport
    public ByteBuffer read(ByteBuffer byteBuffer, int offset, int length)
            throws IOException {
        try {
            int size = offset + length;
            if (hasSocketChannel()) {
                if (size > byteBuffer.capacity()) {
                    if (orb.transportDebugFlag) {
                        // print address of ByteBuffer being released
                        int bbAddress = System.identityHashCode(byteBuffer);
                        bbInfo(bbAddress);
                    }
                    orb.getByteBufferPool().releaseByteBuffer(byteBuffer);
                    byteBuffer = orb.getByteBufferPool().getByteBuffer(size);
                }
                byteBuffer.position(offset);
                byteBuffer.limit(size);
                readFully(byteBuffer, length);
                byteBuffer.position(0);
                byteBuffer.limit(size);
                return byteBuffer;
            }
            if (byteBuffer.isDirect()) {
                throw wrapper.unexpectedDirectByteBufferWithNonChannelSocket();
            }
            byte[] buf = new byte[size];
            // getSocket().getInputStream() can throw an IOException
            // if the socket is closed. Hence, we check the connection
            // state CLOSE_RECVD if an IOException is thrown here 
            // instead of in readFully()
            readFully(getSocket().getInputStream(), buf,
                    offset, length);
            return ByteBuffer.wrap(buf);
        } catch (IOException ioe) {
            if (getState() == CLOSE_RECVD) {
                throw wrapper.connectionRebind(ioe);
            } else {
                throw ioe;
            }
        }
    }

    // REVISIT - Logic in this method that utilizes TCP timeouts can be removed
    //           removed since this method is used only when 
    //           'useNIOSelectToWait=false', aka blocking SocketChannels/Sockets.
    // NOTE: This method is used only when the ORB is configured with
    //       "useNIOSelectToWait=false", aka use blocking Sockets/SocketChannels
    @Transport
    private void readFully(ByteBuffer byteBuffer, int size)
            throws IOException {
        int n = 0;
        int bytecount = 0;
        TcpTimeouts.Waiter waiter = tcpTimeouts.waiter();

        // The reading of data incorporates a strategy to detect a
        // rogue client.

        do {
            bytecount = getSocketChannel().read(byteBuffer);

            if (bytecount < 0) {
                throw new IOException("End-of-stream");
            } else if (bytecount == 0) {
                TemporarySelector tmpSelector = null;
                SelectionKey sk = null;
                try {
                    tmpSelector = getTemporaryReadSelector();
                    sk = tmpSelector.registerChannel(getSocketChannel(),
                            SelectionKey.OP_READ);
                    do {
                        int nsel = tmpSelector.select(waiter.getTimeForSleep());
                        if (nsel > 0) {
                            tmpSelector.removeSelectedKey(sk);
                            bytecount = getSocketChannel().read(byteBuffer);

                            if (bytecount < 0) {
                                throw new IOException("End-of-stream");
                            } else {
                                n += bytecount;
                            }
                        }

                        if (n < size) {
                            // not all bytes read, increase select timeout
                            // REVISIT - Should we only increase wait timeout if
                            //           an actual timeout occurred?
                            waiter.advance();
                        }
                    } while (n < size && !waiter.isExpired());
                } catch (IOException ioe) {
                    throw wrapper.exceptionWhenReadingWithTemporarySelector(ioe,
                            n, size, waiter.timeWaiting(),
                            tcpTimeouts.get_max_time_to_wait());
                } finally {
                    if (tmpSelector != null) {
                        tmpSelector.cancelAndFlushSelector(sk);
                    }
                }
            } else {
                n += bytecount;
            }
        } while (n < size && !waiter.isExpired());

        if (n < size && waiter.isExpired()) {
            // failed to read entire message
            throw wrapper.transportReadTimeoutExceeded(size, n,
                    tcpTimeouts.get_max_time_to_wait(),
                    waiter.timeWaiting());
        }
    }

    // NOTE: This method is used only when the ORB is configured with
    //       "useNIOSelectToWait=false", aka use blocking java.net.Socket
    // REVISIT - Logic in this method that utilizes TCP timeouts can be removed
    //           removed since this method use is for blocking java.net.Sockets.
    // To support non-channel connections.
    @Transport
    public void readFully(java.io.InputStream is, byte[] buf,
                          int offset, int size)
            throws IOException {
        int n = 0;
        int bytecount = 0;
        TcpTimeouts.Waiter waiter = tcpTimeouts.waiter();

        // The reading of data incorporates a strategy to detect a
        // rogue client. The strategy is implemented as follows. As
        // long as data is being read, at least 1 byte or more, we
        // assume we have a well behaved client. If no data is read,
        // then we sleep for a time to wait, re-calculate a new time to
        // wait which is lengthier than the previous time spent waiting.
        // Then, if the total time spent waiting does not exceed a
        // maximum time we are willing to wait, we attempt another
        // read. If the maximum amount of time we are willing to
        // spend waiting for more data is exceeded, we throw an
        // IOException.

        // NOTE: Reading of GIOP headers are treated with a smaller
        //       maximum time to wait threshold. Based on extensive
        //       performance testing, all GIOP headers are being
        //       read in 1 read access.

        do {
            bytecount = is.read(buf, offset + n, size - n);

            if (bytecount < 0) {
                throw new IOException("End-of-stream");
            } else if (bytecount == 0) {
                // REVISIT - This entire if (bytecount == 0)
                //           block of code can be removed
                //           since is.read() is a blocking
                //           read and it is not possible
                //           to read 0 bytes without
                //           throwing an IOException.
                readFullySleeping(waiter.getTime());

                waiter.sleepTime();
                waiter.advance();
            } else {
                n += bytecount;
            }
        } while (n < size && !waiter.isExpired());

        if (n < size && waiter.isExpired()) {
            // failed to read entire message
            throw wrapper.transportReadTimeoutExceeded(
                    size, n, tcpTimeouts.get_max_time_to_wait(),
                    waiter.timeWaiting());
        }
    }

    // NOTE: This method can throw a connection rebind SystemException.
    @Transport
    public void write(ByteBuffer byteBuffer) throws IOException {
        try {
            if (hasSocketChannel()) {
                if (getSocketChannel().isBlocking()) {
                    throw wrapper.temporaryWriteSelectorWithBlockingConnection(this);
                }
                writeUsingNio(byteBuffer);
            } else {
                if (!byteBuffer.hasArray()) {
                    throw wrapper.unexpectedDirectByteBufferWithNonChannelSocket();
                }

                byte[] tmpBuf = new byte[byteBuffer.limit()];
                System.arraycopy(byteBuffer.array(), byteBuffer.arrayOffset(), tmpBuf, 0, tmpBuf.length);
                getSocket().getOutputStream().write(tmpBuf, 0, tmpBuf.length);
                getSocket().getOutputStream().flush();
            }

            // TimeStamp connection to indicate it has been used
            // Note granularity of connection usage is assumed for
            // now to be that of a IIOP packet.
            getConnectionCache().stampTime(this);
        } catch (IOException ioe) {
            if (getState() == CLOSE_RECVD) {
                throw wrapper.connectionRebind(ioe);
            } else {
                throw ioe;
            }
        }
    }

    private void writeUsingNio(ByteBuffer byteBuffer) throws IOException {
        if (bufferWriter == null)
            bufferWriter = new NioBufferWriter(getSocketChannel(), tcpTimeouts);
        bufferWriter.write(byteBuffer);
    }

    /**
     * Note:it is possible for this to be called more than once
     */
    @Transport
    public synchronized void close() {
        writeLock();

        // REVISIT It will be good to have a read lock on the reader thread
        // before we proceed further, to avoid the reader thread (server side)
        // from processing requests. This avoids the risk that a new request
        // will be accepted by ReaderThread while the ListenerThread is
        // attempting to close this connection.

        if (isBusy()) { // we are busy!
            writeUnlock();
            doNotCloseBusyConnection();
            return;
        }

        try {
            try {
                sendCloseConnection(GIOPVersion.V1_0);
            } catch (Throwable t) {
                wrapper.exceptionWhenSendingCloseConnection(t);
            }

            synchronized (stateEvent) {
                state = CLOSE_SENT;
                stateEvent.notifyAll();
            }

            // stop the reader without causing it to do purgeCalls
            //Exception ex = new Exception();
            //reader.stop(ex); // REVISIT

            // NOTE: !!!!!!
            // This does writeUnlock().
            purgeCalls(wrapper.connectionRebind(), false, true);

        } catch (Exception ex) {
            wrapper.exceptionInPurgeCalls(ex);
        }

        closeConnectionResources();
    }

    @Transport
    public void closeConnectionResources() {
        Selector selector = orb.getTransportManager().getSelector(0);
        selector.unregisterForEvent(this);
        closeSocketAndTemporarySelectors();
    }

    @InfoMethod
    private void closingSocketChannel() {
    }

    @InfoMethod
    private void IOExceptionOnClose(Exception e) {
    }

    @Transport
    protected void closeSocketAndTemporarySelectors() {
        try {
            if (socketChannel != null) {
                closeTemporarySelectors();
                closingSocketChannel();
                socketChannel.socket().close();
            }
        } catch (IOException e) {
            IOExceptionOnClose(e);
        } finally {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (Exception e) {
                IOExceptionOnClose(e);
            }
        }
    }

    public Acceptor getAcceptor() {
        return acceptor;
    }

    public ContactInfo getContactInfo() {
        return contactInfo;
    }

    public EventHandler getEventHandler() {
        return this;
    }

    // This is used by the GIOPOutputObject in order to
    // throw the correct error when handling code sets.
    // Can we determine if we are on the server side by
    // other means?  XREVISIT
    public boolean isServer() {
        return isServer;
    }

    public boolean isClosed() {
        boolean result = true;
        if (socketChannel != null) {
            result = !socketChannel.isOpen();
        } else if (socket != null) {
            result = socket.isClosed();
        }
        return result;
    }

    public boolean isBusy() {
        if (serverRequestCount > 0 ||
                getResponseWaitingRoom().numberRegistered() > 0) {
            return true;
        } else {
            return false;
        }
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(long time) {
        timeStamp = time;
    }

    protected int getState() {
        return state;
    }

    protected void setState(int state) {
        this.state = state;
    }

    public void setState(String stateString) {
        synchronized (stateEvent) {
            if (stateString.equals("ESTABLISHED")) {
                state = ESTABLISHED;
                stateEvent.notifyAll();
            } else {
                // REVISIT: ASSERT
            }
        }
    }

    /**
     * Sets the writeLock for this connection.
     * If the writeLock is already set by someone else, block till the
     * writeLock is released and can set by us.
     * IMPORTANT: this connection's lock must be acquired before
     * setting the writeLock and must be unlocked after setting the writeLock.
     */
    @Transport
    public void writeLock() {
        // Keep looping till we can set the writeLock.
        while (true) {
            int localState;
            synchronized (stateEvent) {
                localState = getState();
            }

            localStateInfo(localState);

            switch (localState) {

                case OPENING:
                    synchronized (stateEvent) {
                        if (getState() != OPENING) {
                            // somebody has changed 'state' so be careful
                            break;
                        }
                        try {
                            stateEvent.wait();
                        } catch (InterruptedException ie) {
                            wrapper.openingWaitInterrupted(ie);
                        }
                    }
                    // Loop back
                    break;

                case ESTABLISHED:
                    synchronized (writeEvent) {
                        if (!writeLocked) {
                            writeLocked = true;
                            return;
                        }

                        try {
                            // do not stay here too long if state != ESTABLISHED
                            // Bug 4752117
                            while (getState() == ESTABLISHED && writeLocked) {
                                writeEvent.wait(100);
                            }
                        } catch (InterruptedException ie) {
                            wrapper.establishedWaitInterrupted(ie);
                        }
                    }
                    // Loop back
                    break;

                case ABORT:
                    synchronized (stateEvent) {
                        if (getState() != ABORT) {
                            break;
                        }
                        throw wrapper.writeErrorSend();
                    }

                case CLOSE_RECVD:
                    // the connection has been closed or closing
                    // ==> throw rebind exception
                    synchronized (stateEvent) {
                        if (getState() != CLOSE_RECVD) {
                            break;
                        }
                        throw wrapper.connectionRebind();
                    }

                default:
                    // REVISIT
                    throw new RuntimeException(".writeLock: bad state");
            }
        }
    }

    @Transport
    public void writeUnlock() {
        synchronized (writeEvent) {
            writeLocked = false;
            writeEvent.notify(); // wake up one guy waiting to write
        }
    }

    // Assumes the caller handles writeLock and writeUnlock
    // NOTE: This method can throw a connection rebind SystemException.
    public void sendWithoutLock(CDROutputObject outputObject) {
        // Don't we need to check for CloseConnection
        // here?  REVISIT

        // XREVISIT - Shouldn't the MessageMediator 
        // be the one to handle writing the data here?

        try {
            // Write the fragment/message
            CDROutputObject cdrOutputObject = outputObject;
            cdrOutputObject.writeTo(this);

            // REVISIT - no flush?
            //socket.getOutputStream().flush();

        } catch (IOException exc) {
            // Since IIOPOutputStream's msgheader is set only once, and not
            // altered during sending multiple fragments, the original 
            // msgheader will always have the requestId.
            // REVISIT This could be optimized to send a CancelRequest only
            // if any fragments had been sent already.

            // IIOPOutputStream will cleanup the connection info when it
            // sees this exception.
            final SystemException sysexc = (getState() == CLOSE_RECVD) ?
                    wrapper.connectionRebindMaybe(exc) :
                    wrapper.writeErrorSend(exc);

            purgeCalls(sysexc, false, true);

            throw sysexc;
        }
    }

    public void registerWaiter(MessageMediator messageMediator) {
        responseWaitingRoom.registerWaiter(messageMediator);
    }

    public void unregisterWaiter(MessageMediator messageMediator) {
        responseWaitingRoom.unregisterWaiter(messageMediator);
    }

    public CDRInputObject waitForResponse(MessageMediator messageMediator) {
        return responseWaitingRoom.waitForResponse(messageMediator);
    }

    public void setConnectionCache(ConnectionCache connectionCache) {
        this.connectionCache = connectionCache;
    }

    public ConnectionCache getConnectionCache() {
        return connectionCache;
    }

    ////////////////////////////////////////////////////
    //
    // EventHandler methods
    //

    @Override
    public void setUseSelectThreadToWait(boolean x) {
        useSelectThreadToWait = x;
    }

    public SelectableChannel getChannel() {
        return socketChannel;
    }

    public int getInterestOps() {
        return SelectionKey.OP_READ;
    }

    //    public Acceptor getAcceptor() - already defined above.

    public Connection getConnection() {
        return this;
    }

    ////////////////////////////////////////////////////
    //
    // Work methods.
    //

    public String getName() {
        return this.toString();
    }

    @Transport
    public void doWork() {
        try {
            // IMPORTANT: Sanity checks on SelectionKeys such as
            //            SelectorKey.isValid() should not be done
            //            here.
            //

            if (!shouldUseSelectThreadToWait()) {
                read();
            } else {
                // use optimized read strategy
                doOptimizedReadStrategy();
            }
        } catch (Throwable t) {
            exceptionInfo(t);
        }
    }

    public void setEnqueueTime(long timeInMillis) {
        enqueueTime = timeInMillis;
    }

    public long getEnqueueTime() {
        return enqueueTime;
    }

    ////////////////////////////////////////////////////
    //
    // spi.transport.CorbaConnection.
    //

    public ResponseWaitingRoom getResponseWaitingRoom() {
        return responseWaitingRoom;
    }

    // REVISIT - inteface defines isServer but already defined in 
    // higher interface.

    public void serverRequestMapPut(int reqId, MessageMediator messageMediator) {
        serverRequestMap.put(reqId, messageMediator);
    }

    public MessageMediator serverRequestMapGet(int reqId) {
        return serverRequestMap.get(reqId);
    }

    public void serverRequestMapRemove(int reqId) {
        serverRequestMap.remove(reqId);
    }

    public Queue<MessageMediator> getFragmentList(RequestId corbaRequestId) {
        return fragmentMap.get(corbaRequestId);
    }

    public void removeFragmentList(RequestId corbaRequestId) {
        fragmentMap.remove(corbaRequestId);
    }

    // REVISIT: this is also defined in:
    // com.sun.corba.ee.spi.legacy.connection.Connection
    public java.net.Socket getSocket() {
        return socket;
    }

    /**
     * It is possible for a Close Connection to have been
     * * sent here, but we will not check for this. A "lazy"
     * * Exception will be thrown in the Worker thread after the
     * * incoming request has been processed even though the connection
     * * is closed before the request is processed. This is o.k because
     * * it is a boundary condition. To prevent it we would have to add
     * * more locks which would reduce performance in the normal case.
     */
    public synchronized void serverRequestProcessingBegins() {
        serverRequestCount++;
    }

    public synchronized void serverRequestProcessingEnds() {
        serverRequestCount--;
    }

    //
    //
    //

    public int getNextRequestId() {
        return requestId.getAndIncrement();
    }

    // Negotiated code sets for char and wchar data
    protected CodeSetComponentInfo.CodeSetContext codeSetContext = null;

    public ORB getBroker() {
        return orb;
    }

    public synchronized CodeSetComponentInfo.CodeSetContext getCodeSetContext() {
        return codeSetContext;
    }

    public synchronized void setCodeSetContext(CodeSetComponentInfo.CodeSetContext csc) {
        if (codeSetContext == null) {

            if (OSFCodeSetRegistry.lookupEntry(csc.getCharCodeSet()) == null ||
                    OSFCodeSetRegistry.lookupEntry(csc.getWCharCodeSet()) == null) {
                // If the client says it's negotiated a code set that
                // isn't a fallback and we never said we support, then
                // it has a bug.
                throw wrapper.badCodesetsFromClient();
            }

            codeSetContext = csc;
        }
    }

    //
    // from iiop.IIOPConnection.java
    //

    // Map request ID to an InputObject.
    // This is so the client thread can start unmarshaling
    // the reply and remove it from the out_calls map while the
    // ReaderThread can still obtain the input stream to give
    // new fragments.  Only the ReaderThread touches the clientReplyMap,
    // so it doesn't incur synchronization overhead.

    public MessageMediator clientRequestMapGet(int requestId) {
        return responseWaitingRoom.getMessageMediator(requestId);
    }

    protected MessageMediator clientReply_1_1;

    public void clientReply_1_1_Put(MessageMediator x) {
        clientReply_1_1 = x;
    }

    public MessageMediator clientReply_1_1_Get() {
        return clientReply_1_1;
    }

    public void clientReply_1_1_Remove() {
        clientReply_1_1 = null;
    }

    protected MessageMediator serverRequest_1_1;

    public void serverRequest_1_1_Put(MessageMediator x) {
        serverRequest_1_1 = x;
    }

    public MessageMediator serverRequest_1_1_Get() {
        return serverRequest_1_1;
    }

    public void serverRequest_1_1_Remove() {
        serverRequest_1_1 = null;
    }

    protected String getStateString(int state) {
        synchronized (stateEvent) {
            switch (state) {
                case OPENING:
                    return "OPENING";
                case ESTABLISHED:
                    return "ESTABLISHED";
                case CLOSE_SENT:
                    return "CLOSE_SENT";
                case CLOSE_RECVD:
                    return "CLOSE_RECVD";
                case ABORT:
                    return "ABORT";
                default:
                    return "???";
            }
        }
    }

    public synchronized boolean isPostInitialContexts() {
        return postInitialContexts;
    }

    // Can never be unset...
    public synchronized void setPostInitialContexts() {
        postInitialContexts = true;
    }

    /**
     * Wake up the outstanding requests on the connection, and hand them
     * COMM_FAILURE exception with a given minor code.
     * <p/>
     * Also, delete connection from connection table and
     * stop the reader thread.
     * <p/>
     * Note that this should only ever be called by the Reader thread for
     * this connection.
     *
     * @param die      Kill the reader thread (this thread) before exiting.
     * @param lockHeld true if the calling thread holds the lock on the connection
     */
    @Transport
    public void purgeCalls(SystemException systemException, boolean die,
                           boolean lockHeld) {

        int minor_code = systemException.minor;
        // If this invocation is a result of ThreadDeath caused
        // by a previous execution of this routine, just exit.
        synchronized (stateEvent) {
            localStateInfo(getState());
            if ((getState() == ABORT) || (getState() == CLOSE_RECVD)) {
                return;
            }
        }

        // Grab the writeLock (freeze the calls)
        try {
            if (!lockHeld) {
                writeLock();
            }
        } catch (SystemException ex) {
            exceptionInfo(ex);
        }

        // Mark the state of the connection
        // and determine the request status
        synchronized (stateEvent) {
            if (minor_code == ORBUtilSystemException.CONNECTION_REBIND) {
                state = CLOSE_RECVD;
                systemException.completed = CompletionStatus.COMPLETED_NO;
            } else {
                state = ABORT;
                systemException.completed = CompletionStatus.COMPLETED_MAYBE;
            }
            stateEvent.notifyAll();
        }

        closeSocketAndTemporarySelectors();

        // Notify waiters (server-side processing only)

        if (serverRequest_1_1 != null) { // GIOP 1.1
            serverRequest_1_1.cancelRequest();
        }

        if (serverRequestMap != null) { // GIOP 1.2
            for (MessageMediator mm : serverRequestMap.values()) {
                mm.cancelRequest();
            }
        }

        // Signal all threads with outstanding requests on this
        // connection and give them the SystemException;

        responseWaitingRoom.signalExceptionToAllWaiters(systemException);

        if (contactInfo != null) {
            ((OutboundConnectionCache) connectionCache).remove(contactInfo);
        } else if (acceptor != null) {
            ((InboundConnectionCache) connectionCache).remove(this);
        }

        //
        // REVISIT: Stop the reader thread
        //

        // Signal all the waiters of the writeLock.
        // There are 4 types of writeLock waiters:
        // 1. Send waiters:
        // 2. SendReply waiters:
        // 3. cleanUp waiters:
        // 4. purge_call waiters:
        //

        writeUnlock();
    }

    /**
     * **********************************************************************
     * The following methods are for dealing with Connection cleaning for
     * better scalability of servers in high network load conditions.
     * ************************************************************************
     */

    public void sendCloseConnection(GIOPVersion giopVersion)
            throws IOException {
        Message msg = MessageBase.createCloseConnection(giopVersion);
        sendHelper(giopVersion, msg);
    }

    public void sendMessageError(GIOPVersion giopVersion)
            throws IOException {
        Message msg = MessageBase.createMessageError(giopVersion);
        sendHelper(giopVersion, msg);
    }

    /**
     * Send a CancelRequest message. This does not lock the connection, so the
     * caller needs to ensure this method is called appropriately.
     *
     * @throws IOException - could be due to abortive connection closure.
     */
    public void sendCancelRequest(GIOPVersion giopVersion, int requestId)
            throws IOException {

        Message msg = MessageBase.createCancelRequest(giopVersion, requestId);
        sendHelper(giopVersion, msg);
    }

    protected void sendHelper(GIOPVersion giopVersion, Message msg)
            throws IOException {
        // REVISIT: See comments in CDROutputObject constructor.
        CDROutputObject outputObject =
                new CDROutputObject(orb, null, giopVersion, this, msg,
                        ORBConstants.STREAM_FORMAT_VERSION_1);
        msg.write(outputObject);

        outputObject.writeTo(this);
    }

    // NOTE: This method can throw a connection rebind SystemException.
    public void sendCancelRequestWithLock(GIOPVersion giopVersion,
                                          int requestId)
            throws IOException {
        writeLock();
        try {
            sendCancelRequest(giopVersion, requestId);
        } catch (IOException ioe) {
            if (getState() == CLOSE_RECVD) {
                throw wrapper.connectionRebind(ioe);
            } else {
                throw ioe;
            }
        } finally {
            writeUnlock();
        }
    }

    // Begin Code Base methods ---------------------------------------
    //
    // Set this connection's code base IOR.  The IOR comes from the
    // SendingContext.  This is an optional service context, but all
    // JavaSoft ORBs send it.
    //
    // The set and get methods don't need to be synchronized since the
    // first possible get would occur during reading a valuetype, and
    // that would be after the set.

    // Sets this connection's code base IOR.  This is done after
    // getting the IOR out of the SendingContext service context.
    // Our ORBs always send this, but it's optional in CORBA.

    public final void setCodeBaseIOR(IOR ior) {
        codeBaseServerIOR = ior;
    }

    public final IOR getCodeBaseIOR() {
        return codeBaseServerIOR;
    }

    // Get a CodeBase stub to use in unmarshaling.  The CachedCodeBase
    // won't connect to the remote codebase unless it's necessary.
    public final CodeBase getCodeBase() {
        return cachedCodeBase;
    }

    // End Code Base methods -----------------------------------------

    // set transport read / write thresholds
    protected void setTcpTimeouts(TcpTimeouts tcpTimeouts) {
        this.tcpTimeouts = tcpTimeouts;
    }

    @Transport
    protected void doOptimizedReadStrategy() {
        MessageParser messageParser;
        try {
            // get a new ByteBuffer from ByteBufferPool ?
            if (byteBuffer == null || !byteBuffer.hasRemaining()) {
                byteBuffer =
                        orb.getByteBufferPool().getByteBuffer(
                                orb.getORBData().getReadByteBufferSize());
            }

            // Create a MessageParser. A MessageParser will exist until read 
            // event handling is re-enabled on main SelectorThread.
            messageParser = new MessageParserImpl(orb);

            // start of a message must begin at byteBuffer's current position
            messageParser.setNextMessageStartPosition(byteBuffer.position());

            int bytesRead = 0;
            do {
                bytesRead = nonBlockingRead();
                if (bytesRead > 0) {
                    byteBuffer.limit(byteBuffer.position())
                            .position(messageParser.getNextMessageStartPosition());
                    parseBytesAndDispatchMessages(messageParser);
                    if (messageParser.isExpectingMoreData()) {
                        // End of data in byteBuffer ?
                        if (byteBuffer.position() == byteBuffer.capacity()) {
                            byteBuffer = getNewBufferAndCopyOld(messageParser);
                        }
                    }
                }
            } while (nonBlockingReadWhileLoopConditionIsTrue(messageParser, bytesRead));

            // if expecting more data or using 'always enter blocking read'
            // strategy (the default), then go to a blocking read using
            // a temporary selector.
            if (orb.getORBData().alwaysEnterBlockingRead() || messageParser.isExpectingMoreData()) {
                blockingRead(messageParser);
            }

            // Always ensure subsequent calls to this method has
            // byteBuffer.position() set to the location where
            // the next message should begin
            byteBuffer.position(messageParser.getNextMessageStartPosition());

            // Conection is no longer expecting more data.
            // Re-enable read event handling on main selector
            resumeSelectOnMainSelector();

        } catch (ThreadDeath td) {
            try {
                purgeCalls(wrapper.connectionAbort(td), false, false);
            } catch (Throwable t) {
                exceptionInfo(t);
            }
            throw td;
        } catch (Throwable ex) {
            if (ex instanceof SystemException) {
                SystemException se = (SystemException) ex;
                if (se.minor == ORBUtilSystemException.CONNECTION_REBIND) {
                    unregisterForEventAndPurgeCalls(se);
                    throw se;
                } else {
                    try {
                        if (se instanceof INTERNAL) {
                            sendMessageError(GIOPVersion.DEFAULT_VERSION);
                        }
                    } catch (IOException e) {
                        exceptionInfo(e);
                    }
                }
            }
            unregisterForEventAndPurgeCalls(wrapper.connectionAbort(ex));

            // REVISIT
            //keepRunning = false;
            // REVISIT - if this is called after purgeCalls then
            // the state of the socket is ABORT so the writeLock
            // in close throws an exception.  It is ignored but
            // causes IBM (screen scraping) tests to fail.
            //close();
            throw wrapper.throwableInDoOptimizedReadStrategy(ex);
        }
    }

    @Transport
    protected void blockingRead(MessageParser messageParser) {
        // Precondition: byteBuffer's position must be pointing to where next 
        //               bit of data should be read and MessageParser's next 
        //               message start position must be set.

        TcpTimeouts.Waiter waiter = tcpTimeouts.waiter();
        TemporarySelector tmpSelector = null;
        SelectionKey sk = null;
        try {
            getConnectionCache().stampTime(this);
            tmpSelector = getTemporaryReadSelector();
            sk = tmpSelector.registerChannel(getSocketChannel(),
                    SelectionKey.OP_READ);
            do {
                int nsel = tmpSelector.select(waiter.getTimeForSleep());
                if (nsel > 0) {
                    tmpSelector.removeSelectedKey(sk);
                    int bytesRead = getSocketChannel().read(byteBuffer);
                    if (bytesRead > 0) {
                        //byteBuffer.flip();
                        byteBuffer.limit(byteBuffer.position())
                                .position(messageParser.getNextMessageStartPosition());
                        parseBytesAndDispatchMessages(messageParser);
                        if (messageParser.isExpectingMoreData()) {
                            // End of data in byteBuffer ?
                            if (byteBuffer.position() == byteBuffer.capacity()) {
                                byteBuffer = getNewBufferAndCopyOld(messageParser);
                            }
                        }
                        // reset waiter because we got some data
                        waiter = tcpTimeouts.waiter();
                    } else if (bytesRead < 0) {
                        Exception exc = new IOException("End-of-stream");
                        throw wrapper.blockingReadEndOfStream(
                                exc, exc.toString(), this.toString());
                    } else { // bytesRead == 0, unlikely but possible
                        waiter.advance();
                    }
                } else { // select operation timed out
                    waiter.advance();
                }
            } while (blockingReadWhileLoopConditionIsTrue(messageParser, waiter));

            // If MessageParser is not expecting more data, then we leave this
            // blocking read. Otherwise, we have timed out waiting for some
            // expected data to arrive.
            if (messageParser.isExpectingMoreData()) {
                // failed to read data when we were expecting more
                // and exceeded time willing to wait for additional data
                throw wrapper.blockingReadTimeout(
                        tcpTimeouts.get_max_time_to_wait(), waiter.timeWaiting());
            }
        } catch (IOException ioe) {
            throw wrapper.exceptionBlockingReadWithTemporarySelector(ioe, this);
        } finally {
            if (tmpSelector != null) {
                try {
                    tmpSelector.cancelAndFlushSelector(sk);
                } catch (IOException ex) {
                    wrapper.unexpectedExceptionCancelAndFlushTempSelector(ex);
                }
            }
        }
    }

    @Transport
    protected void parseBytesAndDispatchMessages(MessageParser messageParser) {
        do {
            Message message = messageParser.parseBytes(byteBuffer, this);
            if (message != null) {
                ByteBuffer msgBuffer = message.getByteBuffer();
                MessageMediatorImpl messageMediator =
                        new MessageMediatorImpl(orb, this, message, msgBuffer);

                // Special handling of messages which are fragmented
                boolean addToWorkerThreadQueue = true;
                if (MessageBase.messageSupportsFragments(message)) {
                    // Is this the first fragment ?
                    if (message.getType() != Message.GIOPFragment) {
                        // NOTE: First message fragment will not be GIOPFragment
                        // type
                        if (message.moreFragmentsToFollow()) {
                            // Create an entry in fragmentMap so fragments
                            // will be processed in order.
                            RequestId corbaRequestId =
                                    MessageBase.getRequestIdFromMessageBytes(message);
                            fragmentMap.put(corbaRequestId, new LinkedList<MessageMediator>());
                            addedEntryToFragmentMap(corbaRequestId);
                        }
                    } else {
                        // Not the first fragment. Append to the request id's
                        // queue in the fragmentMap so fragments will be
                        // processed in order.
                        RequestId corbaRequestId =
                                MessageBase.getRequestIdFromMessageBytes(message);
                        Queue<MessageMediator> queue = fragmentMap.get(corbaRequestId);
                        if (queue != null) {
                            // REVISIT - In the future, the synchronized(queue),
                            // wait()/notify() construct should be replaced
                            // with something like a LinkedBlockingQueue
                            // from java.util.concurrent using its offer()
                            // and poll() methods.  But, at the time of the
                            // writing of this code, a LinkedBlockingQueue
                            // implementation is not performing as well as
                            // the synchronized(queue), wait(), notify()
                            // implementation.
                            synchronized (queue) {
                                queue.add(messageMediator);
                                queuedMessageFragment(corbaRequestId);
                                // Notify anyone who might be waiting on a
                                // fragment for this request id.
                                queue.notifyAll();
                            }
                            // Only after the previous fragment is processed
                            // in CorbaMessageMediatorImpl.handleInput() will
                            // the fragment Message that's been queued to
                            // the fragmentMap for a given request id be
                            // put on a WorkerThreadQueue for processing.
                            addToWorkerThreadQueue = false;
                        } else {
                            // Very, very unlikely. But, be defensive.
                            wrapper.noFragmentQueueForRequestId(
                                    corbaRequestId.toString());
                        }
                    }
                }

                // avoid memory leak,
                // see CorbaContactInfoBase.createMessageMediator()
                message.setByteBuffer(null);

                if (addToWorkerThreadQueue) {
                    addMessageMediatorToWorkQueue(messageMediator);
                }
            }
        } while (messageParser.hasMoreBytesToParse());
    }

    @Transport
    protected int nonBlockingRead() {
        int bytesRead = 0;
        SocketChannel sc = getSocketChannel();
        try {
            if (sc == null || sc.isBlocking()) {
                throw wrapper.nonBlockingReadOnBlockingSocketChannel(this);
            }
            bytesRead = sc.read(byteBuffer);
            if (bytesRead < 0) {
                throw new IOException("End-of-stream");
            }
            getConnectionCache().stampTime(this);
        } catch (IOException ioe) {
            if (getState() == CLOSE_RECVD) {
                throw wrapper.connectionRebind(ioe);
            } else {
                throw wrapper.ioexceptionWhenReadingConnection(ioe, this);
            }
        }

        return bytesRead;
    }

    private boolean blockingReadWhileLoopConditionIsTrue(
            MessageParser messageParser, TcpTimeouts.Waiter waiter) {
        // When orb.getORBData().blockingReadCheckMessageParser() is
        // true, we check both conditions, messageParser.isExpectingMoreData() 
        // and timeSpentWaiting < maxWaitTime. This is *NOT* the default.
        // If the messageParser.isExpectingMoreData() condition is not checked
        // the while loop will not exit until we have reached a timeout waiting
        // for more data. This is *NOW* the default. This means that control 
        // will be returned to the main Selector at a later time.  This has the
        // effect of being more patient in determining when a Connection should 
        // transition from being a "hot" Connnection to one that is not very 
        // "busy" or has gone cold/idle.
        final boolean checkMessageParser =
                orb.getORBData().blockingReadCheckMessageParser();

        if (checkMessageParser) {
            return messageParser.isExpectingMoreData() && !waiter.isExpired();
        } else {
            return !waiter.isExpired();
        }
    }

    private boolean nonBlockingReadWhileLoopConditionIsTrue(
            MessageParser messageParser, int bytesRead) {
        // When orb.getORBData().nonBlockingReadCheckMessageParser() is
        // true, we check both conditions, messageParser.isExpectingMoreData() and
        // bytesRead > 0.  If bytesRead > 0 is the only condition checked,
        // i.e. orb.getORBData().nonBlockingReadCheckMessageParser() is false,
        // then an additional read() would be done before exiting the while
        // loop. The default is to check both conditions.
        final boolean checkBothConditions =
                orb.getORBData().nonBlockingReadCheckMessageParser();
        if (checkBothConditions) {
            return (bytesRead > 0 && messageParser.isExpectingMoreData());
        } else {
            return bytesRead > 0;
        }
    }

    @Transport
    private ByteBuffer getNewBufferAndCopyOld(MessageParser messageParser) {
        ByteBuffer newByteBuffer = null;
        // Set byteBuffer position to the start position of data to be
        // copied into the re-allocated ByteBuffer.
        byteBuffer.position(messageParser.getNextMessageStartPosition());
        newByteBuffer = orb.getByteBufferPool().reAllocate(byteBuffer,
                messageParser.getSizeNeeded());
        messageParser.setNextMessageStartPosition(0);
        return newByteBuffer;
    }

    @Transport
    private void addMessageMediatorToWorkQueue(
            final MessageMediatorImpl messageMediator) {
        // Add messageMediator to work queue
        Throwable throwable = null;
        int poolToUse = -1;
        try {
            poolToUse = messageMediator.getThreadPoolToUse();
            orb.getThreadPoolManager().getThreadPool(poolToUse).getWorkQueue(0).addWork(messageMediator);
        } catch (NoSuchThreadPoolException e) {
            throwable = e;
        } catch (NoSuchWorkQueueException e) {
            throwable = e;
        }
        // REVISIT: need to close connection?
        if (throwable != null) {
            throw wrapper.noSuchThreadpoolOrQueue(throwable, poolToUse);
        }
    }

    @Transport
    private void resumeSelectOnMainSelector() {
        // NOTE: VERY IMPORTANT:
        // Re-enable read event handling on main Selector after getting to 
        // the point that proper serialization of fragments is ensured.
        // parseBytesAndDispatchMessages() and MessageParserImpl.parseBytes()
        // ensures this by tracking fragment messages for a given request id
        // for GIOP 1.2 and tracking GIOP 1.1 fragment messages.

        // IMPORTANT: To avoid bug (4953599), we force the Thread that does the 
        // NIO select to also do the enable/disable of interest ops using 
        // SelectionKey.interestOps(Ops of Interest). Otherwise, the 
        // SelectionKey.interestOps(Ops of Interest) may block indefinitely in
        // this thread.
        orb.getTransportManager().getSelector(0).registerInterestOps(this);
    }

    @Transport
    protected TemporarySelector getTemporaryReadSelector() throws IOException {
        // If one asks for a temporary read selector on a blocking connection,
        // it is an error.
        if (getSocketChannel() == null || getSocketChannel().isBlocking()) {
            throw wrapper.temporaryReadSelectorWithBlockingConnection(this);
        }
        synchronized (tmpReadSelectorLock) {
            if (tmpReadSelector == null) {
                tmpReadSelector = new TemporarySelector(getSocketChannel());
            }
        }
        return tmpReadSelector;
    }

    @Transport
    protected void closeTemporarySelectors() throws IOException {
        synchronized (tmpReadSelectorLock) {
            if (tmpReadSelector != null) {
                closingReadSelector(tmpReadSelector);
                try {
                    tmpReadSelector.close();
                } catch (IOException ex) {
                    throw ex;
                }
            }
        }

        if (bufferWriter != null)
            bufferWriter.closeTemporaryWriteSelector();
    }

    @Override
    public String toString() {
        synchronized (stateEvent) {
            String str;
            if (socketChannel != null) {
                str = socketChannel.toString();
            } else if (socket != null) {
                str = socket.toString();
            } else {
                str = "<no connection!>";
            }

            return "SocketOrChannelConnectionImpl[ "
                    + str + " "
                    + getStateString(getState()) + " "
                    + shouldUseSelectThreadToWait() + " "
                    + shouldUseWorkerThreadForEvent()
                    + "]";
        }
    }

    @InfoMethod
    private void exceptionInfo(Throwable t) {
    }

    @InfoMethod
    private void exceptionInfo(String string, Throwable t) {
    }

    @InfoMethod
    private void bbInfo(int bbAddress) {
    }

    @InfoMethod
    private void readFullySleeping(int time) {
    }

    @InfoMethod
    private void doNotCloseBusyConnection() {
    }

    @InfoMethod
    private void localStateInfo(int localState) {
    }

    @InfoMethod
    private void addedEntryToFragmentMap(RequestId corbaRequestId) {
    }

    @InfoMethod
    private void queuedMessageFragment(RequestId corbaRequestId) {
    }

    @InfoMethod
    private void closingReadSelector(TemporarySelector tmpReadSelector) {
    }
}

// End of file.

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

package com.sun.corba.se.impl.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.EmptyStackException;
import java.util.Iterator;
import java.util.Queue;

import org.omg.CORBA.Any;
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.ExceptionList;
import org.omg.CORBA.INTERNAL;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.UnknownUserException;
import org.omg.CORBA.UNKNOWN;
import org.omg.CORBA.portable.UnknownException;
import org.omg.CORBA_2_3.portable.InputStream;
import org.omg.CORBA_2_3.portable.OutputStream;
import org.omg.IOP.ExceptionDetailMessage;
import org.omg.IOP.TAG_RMI_CUSTOM_MAX_STREAM_FORMAT;

import com.sun.corba.se.pept.broker.Broker;
import com.sun.corba.se.pept.encoding.InputObject;
import com.sun.corba.se.pept.encoding.OutputObject;
import com.sun.corba.se.pept.protocol.MessageMediator;
import com.sun.corba.se.pept.protocol.ProtocolHandler;
import com.sun.corba.se.pept.transport.Connection;

import com.sun.corba.se.spi.ior.IOR;
import com.sun.corba.se.spi.ior.ObjectKey;
import com.sun.corba.se.spi.ior.ObjectKeyTemplate;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.spi.ior.iiop.IIOPProfileTemplate;
import com.sun.corba.se.spi.ior.iiop.MaxStreamFormatVersionComponent;
import com.sun.corba.se.spi.oa.OAInvocationInfo;
import com.sun.corba.se.spi.oa.ObjectAdapter;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.orb.ObjectKeyCacheEntry;
import com.sun.corba.se.spi.orbutil.threadpool.NoSuchThreadPoolException;
import com.sun.corba.se.spi.orbutil.threadpool.NoSuchWorkQueueException;
import com.sun.corba.se.spi.orbutil.threadpool.Work;
import com.sun.corba.se.spi.protocol.CorbaMessageMediator;
import com.sun.corba.se.spi.protocol.CorbaProtocolHandler;
import com.sun.corba.se.spi.protocol.CorbaRequestId;
import com.sun.corba.se.spi.protocol.CorbaServerRequestDispatcher;
import com.sun.corba.se.spi.protocol.ForwardException;
import com.sun.corba.se.spi.servicecontext.MaxStreamFormatVersionServiceContext;
import com.sun.corba.se.spi.servicecontext.ORBVersionServiceContext;
import com.sun.corba.se.spi.servicecontext.ServiceContexts;
import com.sun.corba.se.spi.servicecontext.ServiceContextsCache;
import com.sun.corba.se.spi.servicecontext.SendingContextServiceContext;
import com.sun.corba.se.spi.servicecontext.ServiceContextDefaults;
import com.sun.corba.se.spi.servicecontext.UEInfoServiceContext;
import com.sun.corba.se.spi.servicecontext.UnknownServiceContext;
import com.sun.corba.se.spi.transport.CorbaConnection;
import com.sun.corba.se.spi.transport.CorbaContactInfo;

import com.sun.corba.se.impl.corba.RequestImpl;
import com.sun.corba.se.impl.encoding.BufferManagerFactory;
import com.sun.corba.se.impl.encoding.BufferManagerReadStream;
import com.sun.corba.se.impl.encoding.CDRInputObject;
import com.sun.corba.se.impl.encoding.CDROutputObject;
import com.sun.corba.se.impl.encoding.EncapsOutputStream;
import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.logging.InterceptorsSystemException;
import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.impl.protocol.giopmsgheaders.AddressingDispositionHelper;
import com.sun.corba.se.impl.protocol.giopmsgheaders.CancelRequestMessage;
import com.sun.corba.se.impl.protocol.giopmsgheaders.FragmentMessage_1_1;
import com.sun.corba.se.impl.protocol.giopmsgheaders.FragmentMessage_1_2;
import com.sun.corba.se.impl.protocol.giopmsgheaders.LocateRequestMessage;
import com.sun.corba.se.impl.protocol.giopmsgheaders.LocateRequestMessage_1_0;
import com.sun.corba.se.impl.protocol.giopmsgheaders.LocateRequestMessage_1_1;
import com.sun.corba.se.impl.protocol.giopmsgheaders.LocateRequestMessage_1_2;
import com.sun.corba.se.impl.protocol.giopmsgheaders.LocateReplyOrReplyMessage;
import com.sun.corba.se.impl.protocol.giopmsgheaders.LocateReplyMessage;
import com.sun.corba.se.impl.protocol.giopmsgheaders.LocateReplyMessage_1_0;
import com.sun.corba.se.impl.protocol.giopmsgheaders.LocateReplyMessage_1_1;
import com.sun.corba.se.impl.protocol.giopmsgheaders.LocateReplyMessage_1_2;
import com.sun.corba.se.impl.protocol.giopmsgheaders.Message;
import com.sun.corba.se.impl.protocol.giopmsgheaders.MessageBase;
import com.sun.corba.se.impl.protocol.giopmsgheaders.MessageHandler;
import com.sun.corba.se.impl.protocol.giopmsgheaders.ReplyMessage;
import com.sun.corba.se.impl.protocol.giopmsgheaders.ReplyMessage_1_0;
import com.sun.corba.se.impl.protocol.giopmsgheaders.ReplyMessage_1_1;
import com.sun.corba.se.impl.protocol.giopmsgheaders.ReplyMessage_1_2;
import com.sun.corba.se.impl.protocol.giopmsgheaders.RequestMessage;
import com.sun.corba.se.impl.protocol.giopmsgheaders.RequestMessage_1_0 ;
import com.sun.corba.se.impl.protocol.giopmsgheaders.RequestMessage_1_1 ;
import com.sun.corba.se.impl.protocol.giopmsgheaders.RequestMessage_1_2 ;

// REVISIT: make sure no memory leaks in client/server request/reply maps.
// REVISIT: normalize requestHeader, replyHeader, messageHeader.

/**
 * @author Harold Carr
 */
public class CorbaMessageMediatorImpl
    implements 
	CorbaMessageMediator,
	CorbaProtocolHandler,
	MessageHandler,
        Work
{
    protected ORB orb;
    protected ORBUtilSystemException wrapper ;
    protected InterceptorsSystemException interceptorWrapper ;
    protected CorbaContactInfo contactInfo;
    protected CorbaConnection connection;
    protected short addrDisposition;
    protected CDROutputObject outputObject;
    protected CDRInputObject inputObject;
    protected Message messageHeader;
    protected RequestMessage requestHeader;
    protected LocateReplyOrReplyMessage replyHeader;
    protected String replyExceptionDetailMessage;
    protected IOR replyIOR;
    protected Integer requestIdInteger;
    protected Message dispatchHeader;
    protected ByteBuffer dispatchByteBuffer;
    protected byte streamFormatVersion;
    protected boolean streamFormatVersionSet = false;

    protected org.omg.CORBA.Request diiRequest;

    protected boolean cancelRequestAlreadySent = false;

    protected ProtocolHandler protocolHandler;
    protected boolean _executeReturnServantInResponseConstructor = false;
    protected boolean _executeRemoveThreadInfoInResponseConstructor = false;
    protected boolean _executePIInResponseConstructor = false;

    // The localMaxVersion is used for caching the value of 
    //MaxStreamFormatVersion if the ORB has been created by the app server  
    private static byte localMaxVersion =  ORBUtility.getMaxStreamFormatVersion();

    // time this CorbaMessageMediator (Work) was added to a WorkQueue.
    private long enqueueTime;

    //
    // Client-side constructor.
    //

    public CorbaMessageMediatorImpl(ORB orb,
				    CorbaContactInfo contactInfo,
				    Connection connection,
				    GIOPVersion giopVersion,
				    IOR ior,
				    int requestId,
				    short addrDisposition,
				    String operationName,
				    boolean isOneWay)
    {
	this( orb, connection ) ;
	    
	this.contactInfo = contactInfo;
	this.addrDisposition = addrDisposition;

	if (orb.getORBData().isAppServerMode() == true) {
	    streamFormatVersion = localMaxVersion;
	} else {
	    streamFormatVersion =
	        getStreamFormatVersionForThisRequest(
		    this.contactInfo.getEffectiveTargetIOR(),
		    giopVersion);
	}
	streamFormatVersionSet = true;

	byte encodingVersion =
	    ORBUtility.chooseEncodingVersion(orb, ior, giopVersion);
	ORBUtility.pushEncVersionToThreadLocalState(encodingVersion);
	requestHeader = (RequestMessage) MessageBase.createRequest(
            this.orb,
	    giopVersion,
	    encodingVersion,
	    requestId,
	    !isOneWay,
	    this.contactInfo.getEffectiveTargetIOR(),
	    this.addrDisposition,
	    operationName,
	    ServiceContextDefaults.makeServiceContexts(orb),
	    null);
    }

    //
    // Acceptor constructor.
    //

    public CorbaMessageMediatorImpl(ORB orb,
				    Connection connection) 
    {
	this.orb = orb;
	this.connection = (CorbaConnection)connection;
	this.wrapper = orb.getLogWrapperTable().get_RPC_PROTOCOL_ORBUtil() ;
	this.interceptorWrapper = 
	    orb.getLogWrapperTable().get_RPC_PROTOCOL_Interceptors() ;

    }

    //
    // Dispatcher constructor.
    //

    // Note: in some cases (e.g., a reply message) this message 
    // mediator will only be used for dispatch.  Then the original 
    // request side mediator will take over. 
    public CorbaMessageMediatorImpl(ORB orb,
				    CorbaConnection connection,
				    Message dispatchHeader,
				    ByteBuffer byteBuffer)
    {
	this( orb, connection ) ;
	this.dispatchHeader = dispatchHeader;
        this.dispatchByteBuffer = byteBuffer;
    }

    ////////////////////////////////////////////////////
    //
    // MessageMediator
    //

    public Broker getBroker() 
    {
	return orb;
    }

    public CorbaContactInfo getContactInfo()
    {
	return contactInfo;
    }

    public Connection getConnection()
    {
	return connection;
    }

    public void initializeMessage() 
    {
	getRequestHeader().write(outputObject);
    }

    public void finishSendingRequest() 
    {
	// REVISIT: probably move logic in outputObject to here.
        outputObject.finishSendingMessage();
    }

    public InputObject waitForResponse()
    {
	if (getRequestHeader().isResponseExpected()) {
	    return connection.waitForResponse(this);
	}
	return null;
    }

    public void setOutputObject(OutputObject outputObject)
    {
	this.outputObject = (CDROutputObject) outputObject;
    }

    public OutputObject getOutputObject()
    {
	return outputObject;
    }

    public void setInputObject(InputObject inputObject)
    {
	this.inputObject = (CDRInputObject) inputObject;
    }

    public InputObject getInputObject()
    {
	return inputObject;
    }

    ////////////////////////////////////////////////////
    //
    // CorbaMessageMediator
    //

    public void setReplyHeader(LocateReplyOrReplyMessage header)
    {
	this.replyHeader = header;
	this.replyIOR = header.getIOR(); // REVISIT - need separate field?
    }

    public LocateReplyMessage getLocateReplyHeader()
    {
	return (LocateReplyMessage) replyHeader;
    }
    
    public ReplyMessage getReplyHeader()
    {
	return (ReplyMessage) replyHeader;
    }

    public void setReplyExceptionDetailMessage(String message)
    {
	replyExceptionDetailMessage = message;
    }
    
    public RequestMessage getRequestHeader()
    {
	return requestHeader;
    }
    
    public GIOPVersion getGIOPVersion()
    {
	if (messageHeader != null) {
	    return messageHeader.getGIOPVersion();
	}
	return getRequestHeader().getGIOPVersion();
    }

    public byte getEncodingVersion() {
	if (messageHeader != null) {
	    return messageHeader.getEncodingVersion();
	}
	return getRequestHeader().getEncodingVersion();
    }

    public int getRequestId()
    {
	return getRequestHeader().getRequestId();
    }

    // REVISIT: The use of requestIdInteger should be removed and use Java's
    //          autoboxing instead.
    public Integer getRequestIdInteger()
    {
	if (requestIdInteger == null) {
	    requestIdInteger = Integer.valueOf(getRequestHeader().getRequestId());
	}
	return requestIdInteger;
    }

    public boolean isOneWay()
    {
	return ! getRequestHeader().isResponseExpected();
    }

    public short getAddrDisposition()
    {
	return addrDisposition;
    }

    public String getOperationName()
    {
	return getRequestHeader().getOperation();
    }

    public ServiceContexts getRequestServiceContexts()
    {
	return getRequestHeader().getServiceContexts();
    }

    public void setRequestServiceContexts(ServiceContexts sc)
    {
        getRequestHeader().setServiceContexts(sc);
    }

    public ServiceContexts getReplyServiceContexts()
    {
	return getReplyHeader().getServiceContexts();
    }

    public void sendCancelRequestIfFinalFragmentNotSent()
    {
	if ((!sentFullMessage()) && sentFragment() && 
	    (!cancelRequestAlreadySent) && !connection.isClosed())
        {
	    try {
		if (orb.subcontractDebugFlag) {
		    dprint(".sendCancelRequestIfFinalFragmentNotSent->: " 
			   + opAndId(this));
		}
		connection.sendCancelRequestWithLock(getGIOPVersion(),
						     getRequestId());
		// Case: first a location forward, then a marshaling 
		// exception (e.g., non-serializable object).  Only
		// send cancel once.
		cancelRequestAlreadySent = true;
	    } catch (SystemException se) {
		if (se.minor == ORBUtilSystemException.CONNECTION_REBIND) {
                    connection.purgeCalls(se, true, false);
		}
		else {
		    if (orb.subcontractDebugFlag) {
		       dprint(".sendCancelRequestIfFinalFragmentNotSent: !ERROR : " + 
			        opAndId(this), se);
		    }
		    throw se;
		}
	    } catch (IOException e) {
		if (orb.subcontractDebugFlag) {
		    dprint(".sendCancelRequestIfFinalFragmentNotSent: !ERROR : " + opAndId(this),
			   e);
		}

		// REVISIT: we could attempt to send a final incomplete
		// fragment in this case.
		throw interceptorWrapper.ioexceptionDuringCancelRequest(
		    CompletionStatus.COMPLETED_MAYBE, e );
	    } finally {
		if (orb.subcontractDebugFlag) {
		    dprint(".sendCancelRequestIfFinalFragmentNotSent<-: "
			   + opAndId(this));
		}
	    }
	}
    }

    public boolean sentFullMessage()
    {
	if (outputObject == null) {
	    return false;
	} else {
	    return outputObject.getBufferManager().sentFullMessage();
	}
    }

    public boolean sentFragment()
    {
	return outputObject.getBufferManager().sentFragment();
    }

    public void setDIIInfo(org.omg.CORBA.Request diiRequest)
    {
	this.diiRequest = diiRequest;
    }

    public boolean isDIIRequest()
    {
	return diiRequest != null;
    }

    public Exception unmarshalDIIUserException(String repoId, InputStream is)
    {
	if (! isDIIRequest()) {
	    return null;
	}

	ExceptionList _exceptions = diiRequest.exceptions();

	try {
	    // Find the typecode for the exception
	    for (int i=0; i<_exceptions.count() ; i++) {
		TypeCode tc = _exceptions.item(i);
		if ( tc.id().equals(repoId) ) {
		    // Since we dont have the actual user exception
		    // class, the spec says we have to create an
		    // UnknownUserException and put it in the
		    // environment.
		    Any eany = orb.create_any();
		    eany.read_value(is, (TypeCode)tc);

		    return new UnknownUserException(eany);
		}
	    }
	} catch (Exception b) {
	    throw wrapper.unexpectedDiiException(b);
	}

	// must be a truly unknown exception
	return wrapper.unknownCorbaExc( CompletionStatus.COMPLETED_MAYBE); 
    }

    public void setDIIException(Exception exception)
    {
	diiRequest.env().exception(exception);
    }

    public void handleDIIReply(InputStream inputStream)
    {
	if (! isDIIRequest()) {
	    return;
	}
	((RequestImpl)diiRequest).unmarshalReply(inputStream);
    }

    public Message getDispatchHeader()
    {
	return dispatchHeader;
    }

    public void setDispatchHeader(Message msg)
    {
	dispatchHeader = msg;
    }

    public ByteBuffer getDispatchBuffer()
    {
	return dispatchByteBuffer;
    }

    public void setDispatchBuffer(ByteBuffer byteBuffer)
    {
	dispatchByteBuffer = byteBuffer;
    }

    public int getThreadPoolToUse() {
	int poolToUse = 0;
	Message msg = getDispatchHeader();
	// A null msg should never happen. But, we'll be
	// defensive just in case.
	if (msg != null) {
	    poolToUse = msg.getThreadPoolToUse();
	}
	return poolToUse;
    }

    /**
     * NOTE:
     *
     * This call is the transition from the transport block to the protocol
     * block.
    */
    public boolean dispatch()
    {
	try {
	    if (transportDebug()) {
		dprint(".dispatch->: " + this.connection);
	    }

	    boolean result = getProtocolHandler().handleRequest(this);

	    return result;

	} catch (ThreadDeath td) {
	    if (transportDebug()) {
		dprint(".dispatch: ThreadDeath", td );
	    }
	    try {
		connection.purgeCalls(wrapper.connectionAbort(td), false, false);
	    } catch (Throwable t) {
		if (transportDebug()) {
		    dprint(".dispatch: CorbaConnection.purgeCalls: " + 
                            this.connection + "Throwable", t);
		}
	    }
	    throw td;
	} catch (Throwable ex) {
	    if (transportDebug()) {
		dprint(".dispatch: Throwable", ex ) ;
	    }

	    try {
		if (ex instanceof INTERNAL) {
		    connection.sendMessageError(GIOPVersion.DEFAULT_VERSION);
		}
	    } catch (IOException e) {
		if (transportDebug()) {
		    dprint(".dispatch: CorbaConnection.sendMessageError: " +
                           this.connection + "IOException", e);
		}
	    }
	    connection.purgeCalls(wrapper.connectionAbort(ex), false, false);
	    // REVISIT
	    //keepRunning = false;
	} finally {
	    if (transportDebug()) {
		dprint(".dispatch<-: " + this.connection);
	    }
	}
        return true;
    }

    public byte getStreamFormatVersion()
    {
	// REVISIT: ContactInfo/Acceptor output object factories
	// just use this.  Maybe need to distinguish:
	//    createOutputObjectForRequest
	//    createOutputObjectForReply
	// then do getStreamFormatVersionForRequest/ForReply here.
	if (streamFormatVersionSet) {
	    return streamFormatVersion;
	}
	return getStreamFormatVersionForReply();
    }

    /**
     * If the RMI-IIOP maximum stream format version service context
     * is present, it indicates the maximum stream format version we
     * could use for the reply.  If it isn't present, the default is
     * 2 for GIOP 1.3 or greater, 1 for lower.
     *
     * This is only sent on requests.  Clients can find out the
     * server's maximum by looking for a tagged component in the IOR.
     */
    public byte getStreamFormatVersionForReply() {

	// NOTE: The request service contexts may indicate the max.
        ServiceContexts svc = getRequestServiceContexts();

	MaxStreamFormatVersionServiceContext msfvsc
	    = (MaxStreamFormatVersionServiceContext)svc.get(
		MaxStreamFormatVersionServiceContext.SERVICE_CONTEXT_ID);
	    
	if (msfvsc != null) {
            byte remoteMaxVersion = msfvsc.getMaximumStreamFormatVersion();

            return (byte)Math.min(localMaxVersion, remoteMaxVersion);
        } else {
            // Defaults to 1 for GIOP 1.2 or less, 2 for
            // GIOP 1.3 or higher.
            if (getGIOPVersion().lessThan(GIOPVersion.V1_3))
                return ORBConstants.STREAM_FORMAT_VERSION_1;
            else
                return ORBConstants.STREAM_FORMAT_VERSION_2;
        }
    }

    public boolean isSystemExceptionReply() 
    {
	return replyHeader.getReplyStatus() == ReplyMessage.SYSTEM_EXCEPTION;
    }

    public boolean isUserExceptionReply()
    {
	return replyHeader.getReplyStatus() == ReplyMessage.USER_EXCEPTION;
    }

    public boolean isLocationForwardReply()
    {
	return ( (replyHeader.getReplyStatus() == ReplyMessage.LOCATION_FORWARD) ||
		 (replyHeader.getReplyStatus() == ReplyMessage.LOCATION_FORWARD_PERM) );
	//return replyHeader.getReplyStatus() == ReplyMessage.LOCATION_FORWARD;
    }
    
    public boolean isDifferentAddrDispositionRequestedReply()
    {
	return replyHeader.getReplyStatus() == ReplyMessage.NEEDS_ADDRESSING_MODE;
    }
    
    public short getAddrDispositionReply()
    {
	return replyHeader.getAddrDisposition();
    }
    
    public IOR getForwardedIOR()
    {
	return replyHeader.getIOR();
    }

    public SystemException getSystemExceptionReply()
    {
	return replyHeader.getSystemException(replyExceptionDetailMessage);
    }

    ////////////////////////////////////////////////////
    //
    // Used by server side.
    // 

    public ObjectKeyCacheEntry getObjectKeyCacheEntry()
    {
	return getRequestHeader().getObjectKeyCacheEntry() ;
    }

    public void setProtocolHandler(CorbaProtocolHandler protocolHandler)
    {
	throw wrapper.methodShouldNotBeCalled() ;
    }

    public CorbaProtocolHandler getProtocolHandler()
    {
	// REVISIT: should look up in orb registry.
	return this;
    }

    ////////////////////////////////////////////////////
    //
    // ResponseHandler
    //

    public org.omg.CORBA.portable.OutputStream createReply()
    {
	// Note: relies on side-effect of setting mediator output field.
	// REVISIT - cast - need interface
	getProtocolHandler().createResponse(this, (ServiceContexts) null);
	return (OutputStream) getOutputObject();
    }

    public org.omg.CORBA.portable.OutputStream createExceptionReply()
    {
	// Note: relies on side-effect of setting mediator output field.
	// REVISIT - cast - need interface
	getProtocolHandler().createUserExceptionResponse(this, (ServiceContexts) null);
	return (OutputStream) getOutputObject();
    }

    public boolean executeReturnServantInResponseConstructor()
    {
	return _executeReturnServantInResponseConstructor;

    }

    public void setExecuteReturnServantInResponseConstructor(boolean b)
    {
	_executeReturnServantInResponseConstructor = b;
    }

    public boolean executeRemoveThreadInfoInResponseConstructor()
    {
	return _executeRemoveThreadInfoInResponseConstructor;
    }

    public void setExecuteRemoveThreadInfoInResponseConstructor(boolean b)
    {
	_executeRemoveThreadInfoInResponseConstructor = b;
    }

    public boolean executePIInResponseConstructor()
    {
	return _executePIInResponseConstructor;
    }

    public void setExecutePIInResponseConstructor( boolean b )
    {
	_executePIInResponseConstructor = b;
    }

    private byte getStreamFormatVersionForThisRequest(IOR ior,
                                                      GIOPVersion giopVersion)
    {

	IOR effectiveTargetIOR = 
	    this.contactInfo.getEffectiveTargetIOR();
        IIOPProfileTemplate temp =
	    (IIOPProfileTemplate)effectiveTargetIOR.getProfile().getTaggedProfileTemplate();
        Iterator iter = temp.iteratorById(TAG_RMI_CUSTOM_MAX_STREAM_FORMAT.value);
        if (!iter.hasNext()) {
            // Didn't have the max stream format version tagged
            // component.
            if (giopVersion.lessThan(GIOPVersion.V1_3))
                return ORBConstants.STREAM_FORMAT_VERSION_1;
            else
                return ORBConstants.STREAM_FORMAT_VERSION_2;
        }

        byte remoteMaxVersion
            = ((MaxStreamFormatVersionComponent)iter.next()).getMaxStreamFormatVersion();

        return (byte)Math.min(localMaxVersion, remoteMaxVersion);
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////

    // REVISIT - This could be a separate implementation object looked
    // up in a registry.  However it needs some state in the message 
    // mediator so combine for now.


    protected boolean isThreadDone = false;

    ////////////////////////////////////////////////////
    //
    // pept.protocol.ProtocolHandler
    //

    public boolean handleRequest(MessageMediator messageMediator)
    {
	try {
	    byte encodingVersion = dispatchHeader.getEncodingVersion();
	    ORBUtility.pushEncVersionToThreadLocalState(encodingVersion);
	    dispatchHeader.callback(this);
	} catch (IOException e) {
	    // REVISIT - this should be handled internally.
	    ;
	} finally {
	    ORBUtility.popEncVersionFromThreadLocalState();
	}
	return isThreadDone;
    }

    ////////////////////////////////////////////////////
    //
    // iiop.messages.MessageHandler
    //

    private void resumeOptimizedReadProcessing(Message message) {
        if (orb.transportDebugFlag) {
 	    dprint(".resumeOptimizedReadProcessing:-> request id/" + 
                   message.getCorbaRequestId().toString() + ": " + message);
        }

        if (message.moreFragmentsToFollow()) {
            if (orb.transportDebugFlag) {
                dprint(".resumeOptimizedReadProcessing: getting " +
                       "CorbaMessageMediator with next fragment for request " +
                       "id/" + message.getCorbaRequestId().toString());
            }
            CorbaMessageMediator messageMediator = null;
            CorbaRequestId requestId = message.getCorbaRequestId();
            Queue<CorbaMessageMediator> queue = 
                                          connection.getFragmentList(requestId);
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
                while (messageMediator == null) {
                    if (queue.size() > 0) {
                        messageMediator = queue.poll();
                    } else {
                        try {
                            queue.wait();
                        } catch (InterruptedException ex) {
                            wrapper.resumeOptimizedReadThreadInterrupted(ex);
                        }
                    }
                }
            }
            // Add CorbaMessageMediator to ThreadPool's WorkQueue to process the
            // next fragment.
            // Although we could call messageMeditor.doWork() rather than putting
            // the messageMediator on the WorkQueue, we do not because calling
            // doWork() would increase the depth of the call stack. Since this
            // thread is done processing the Work it was given, it is very likely
            // it will be the thread that executes the Work (messageMediator)we
            // put the on the WorkQueue here.
            addMessageMediatorToWorkQueue(messageMediator);
        } else {
            if (message.getType() == Message.GIOPFragment || 
                message.getType() == Message.GIOPCancelRequest) {
                // applies to FragmentMessage_1_[1|2] and CancelRequestMessage
                // when using non-blocking NIO SocketChannels
                CorbaRequestId requestId = message.getCorbaRequestId();
                if (orb.transportDebugFlag) {
                    dprint(".resumeOptimizedReadProcessing: done processing " +
                           "fragments for request id/" + requestId + 
                           ": will remove fragment list from fragmentMap on " +
                           "connection: " + connection.toString());
                }
                connection.removeFragmentList(requestId);
            }
        }
        if (orb.transportDebugFlag) {
	    dprint(".resumeOptimizedReadProcessing:<- request id/" + 
                   message.getCorbaRequestId().toString() + ": " + message);
        }
    }

    private void addMessageMediatorToWorkQueue(final CorbaMessageMediator messageMediator) {
        // Add messageMediator to work queue
        Throwable throwable = null;
        try {
            int poolToUse = messageMediator.getThreadPoolToUse();
            if (orb.transportDebugFlag) {
                dprint(".addMessageMediatorToWorkQueue: " +
                        "add Work to pool: " + poolToUse);
            }
            orb.getThreadPoolManager().getThreadPool(poolToUse).getWorkQueue(0).
                             addWork((CorbaMessageMediatorImpl)messageMediator);
        } catch (NoSuchThreadPoolException e) {
            throwable = e;
        } catch (NoSuchWorkQueueException e) {
            throwable = e;
        }
        // REVISIT: need to close connection?
        if (throwable != null) {
            if (orb.transportDebugFlag) {
                dprint(".addMessageMeditorToWorkQueue: " + throwable);
            }
            throw wrapper.noSuchThreadpoolOrQueue(throwable);
        }
    }

    private void setWorkThenPoolOrResumeOptimizedRead(Message header)
    {
	if (getConnection().getEventHandler().shouldUseSelectThreadToWait()) {
            resumeOptimizedReadProcessing(header);
	} else {
	    // Leader/Follower when using reader thread.
	    // When this thread is done working it will go back in pool.
	
	    isThreadDone = true;

	    // First unregister current registration.
	    orb.getTransportManager().getSelector(0)
		.unregisterForEvent(getConnection().getEventHandler());
	    // Have another thread become the reader.
	    orb.getTransportManager().getSelector(0)
		.registerForEvent(getConnection().getEventHandler());
	}
    }

    private void setWorkThenReadOrResumeOptimizedRead(Message header)
    {
	if (getConnection().getEventHandler().shouldUseSelectThreadToWait()) {
            resumeOptimizedReadProcessing(header);
	} else {
	    // When using reader thread then wen this thread is 
	    // done working it will continue reading.
	    isThreadDone = false;
	}
    }

    private void setInputObject()
    {
	// REVISIT: refactor createInputObject (and createMessageMediator)
	// into base PlugInFactory.  Get via connection (either ContactInfo
	// or Acceptor).
	if (getConnection().getContactInfo() != null) {
	    inputObject = (CDRInputObject)
		getConnection().getContactInfo()
		.createInputObject(orb, this);
	} else if (getConnection().getAcceptor() != null) {
	    inputObject = (CDRInputObject)
		getConnection().getAcceptor()
		.createInputObject(orb, this);
	} else {
	    throw new RuntimeException("CorbaMessageMediatorImpl.setInputObject");
	}
	inputObject.setMessageMediator(this);
	setInputObject(inputObject);
    }

    private void signalResponseReceived()
    {
	// This will end up using the MessageMediator associated with
	// the original request instead of the current mediator (which
	// need to be constructed to hold the dispatchBuffer and connection).
	connection.getResponseWaitingRoom()
	    .responseReceived((InputObject)inputObject);
    }

    // This handles message types for which we don't create classes.
    public void handleInput(Message header) throws IOException
    {
	try {
	    messageHeader = header;

	    if (transportDebug())
		dprint(".handleInput->: " 
		       + MessageBase.typeToString(header.getType()));

	    setWorkThenReadOrResumeOptimizedRead(header);

	    switch(header.getType()) 
            {
            case Message.GIOPCloseConnection:
		if (transportDebug()) {
		    dprint(".handleInput: CloseConnection: purging");
		}
                connection.purgeCalls(wrapper.connectionRebind(), true, false);
                break;
            case Message.GIOPMessageError:
		if (transportDebug()) {
		    dprint(".handleInput: MessageError: purging");
		}
                connection.purgeCalls(wrapper.recvMsgError(), true, false);
                break;
            default:
		if (transportDebug()) {
		    dprint(".handleInput: ERROR: "
			   + MessageBase.typeToString(header.getType()));
		}
		throw wrapper.badGiopRequestType() ;
	    }
            releaseByteBufferToPool();
	} finally {
	    if (transportDebug()) {
		dprint(".handleInput<-: " 
		       + MessageBase.typeToString(header.getType()));
	    }
	}
    }

    public void handleInput(RequestMessage_1_0 header) throws IOException
    {
	try {
	    if (transportDebug()) dprint(".REQUEST 1.0->: " + header);
	    try {
		messageHeader = requestHeader = (RequestMessage) header;
		setInputObject();
	    } finally {
		setWorkThenPoolOrResumeOptimizedRead(header);
	    }
	    getProtocolHandler().handleRequest(header, this);
	} catch (Throwable t) {
	    if (transportDebug())
		dprint(".REQUEST 1.0: !!ERROR!!: " + header, t);
	    // Mask the exception from thread.;
	} finally {
	    if (transportDebug()) dprint(".REQUEST 1.0<-: " + header);
	}
    }
    
    public void handleInput(RequestMessage_1_1 header) throws IOException
    {
	try {
	    if (transportDebug()) dprint(".REQUEST 1.1->: " + header);
	    try {
		messageHeader = requestHeader = (RequestMessage) header;
		setInputObject();
		connection.serverRequest_1_1_Put(this);
	    } finally {
		setWorkThenPoolOrResumeOptimizedRead(header);
	    }
	    getProtocolHandler().handleRequest(header, this);
	} catch (Throwable t) {
	    if (transportDebug())
		dprint(".REQUEST 1.1: !!ERROR!!: " + header, t);
	    // Mask the exception from thread.;
	} finally {
	    if (transportDebug()) dprint(".REQUEST 1.1<-: " + header);
	}
    }

    // REVISIT: this is identical to 1_0 except for fragment part.
    public void handleInput(RequestMessage_1_2 header) throws IOException
    {
	try {
	    try {

		messageHeader = requestHeader = (RequestMessage) header;

		header.unmarshalRequestID(dispatchByteBuffer);
		setInputObject();

		if (transportDebug()) dprint(".REQUEST 1.2->: id/" 
					     + header.getRequestId() 
					     + ": "
					     + header);
	    
		// NOTE: in the old code this used to be done conditionally:
		// if (header.moreFragmentsToFollow()).
		// Now we always put it in. We take it out when
		// the response is done.
		// This must happen now so if a header is fragmented the stream
		// may be found.
		connection.serverRequestMapPut(header.getRequestId(), this);
	    } finally {
		// Leader/Follower.
		// Note: This *MUST* come after putting stream in above map
		// since the header may be fragmented and you do not want to
		// start reading and/or processing again until the map above
                // is set.
		setWorkThenPoolOrResumeOptimizedRead(header);
	    }
	    //inputObject.unmarshalHeader(); // done in subcontract.
	    getProtocolHandler().handleRequest(header, this);
	} catch (Throwable t) {
	    if (transportDebug()) dprint(".REQUEST 1.2: id/"
					 + header.getRequestId()
					 + ": !!ERROR!!: "
					 + header,
					 t);
	    // Mask the exception from thread.;
	} finally {
	    connection.serverRequestMapRemove(header.getRequestId());

	    if (transportDebug()) dprint(".REQUEST 1.2<-: id/" 
					 + header.getRequestId() 
					 + ": "
					 + header);
	}
    }

    public void handleInput(ReplyMessage_1_0 header) throws IOException
    {
	try {
	    try {
		if (transportDebug()) dprint(".REPLY 1.0->: " + header);
		messageHeader = replyHeader = (ReplyMessage) header;
		setInputObject();

		// REVISIT: this should be done by waiting thread.
		inputObject.unmarshalHeader();

		signalResponseReceived();
	    } finally{
		setWorkThenReadOrResumeOptimizedRead(header);
	    }
	} catch (Throwable t) {
	    if (transportDebug())dprint(".REPLY 1.0: !!ERROR!!: " + header, t);
	    // Mask the exception from thread.;
	} finally {
	    if (transportDebug()) dprint(".REPLY 1.0<-: " + header);
	}
    }
    
    public void handleInput(ReplyMessage_1_1 header) throws IOException
    {
	try {
	    if (transportDebug()) dprint(".REPLY 1.1->: " + header);
	    messageHeader = replyHeader = (ReplyMessage) header;
	    setInputObject();

	    if (header.moreFragmentsToFollow()) {

		// More fragments are coming to complete this reply, so keep
		// a reference to the InputStream so we can add the fragments
		connection.clientReply_1_1_Put(this);
            
		// In 1.1, we can't assume that we have the request ID in the
		// first fragment.  Thus, another thread is used 
		// to be the reader while this thread unmarshals
		// the extended header and wakes up the client thread.
		setWorkThenPoolOrResumeOptimizedRead(header);

		// REVISIT - error handling.
		// This must be done now.
		inputObject.unmarshalHeader();

		signalResponseReceived();

	    } else {

		// Not fragmented, therefore we know the request
		// ID is here.  Thus, we can unmarshal the extended header
		// and wake up the client thread without using a third
		// thread as above.

		// REVISIT - error handling during unmarshal.
		// This must be done now to get the request id.
		inputObject.unmarshalHeader();

		signalResponseReceived();

		setWorkThenReadOrResumeOptimizedRead(header);
	    }
	} catch (Throwable t) {
	    if (transportDebug()) dprint(".REPLY 1.1: !!ERROR!!: " + header);
	    // Mask the exception from thread.;
	} finally {
	    if (transportDebug()) dprint(".REPLY 1.1<-: " + header);
	}
    }

    public void handleInput(ReplyMessage_1_2 header) throws IOException
    {
	try {
	    try {
		messageHeader = replyHeader = (ReplyMessage) header;

		// We know that the request ID is in the first fragment
		header.unmarshalRequestID(dispatchByteBuffer);

		if (transportDebug()) {
		    dprint(".REPLY 1.2->: id/" 
			   + + header.getRequestId()
			   + ": more?: " + header.moreFragmentsToFollow()
			   + ": " + header);
		}
		
		setInputObject();

		signalResponseReceived();
	    } finally {
		setWorkThenReadOrResumeOptimizedRead(header);
	    }
	} catch (Throwable t) {
	    if (transportDebug()) dprint(".REPLY 1.2: id/"
					 + header.getRequestId()
					 + ": !!ERROR!!: " 
					 + header, t);
	    // Mask the exception from thread.;
	} finally {
	    if (transportDebug()) dprint(".REPLY 1.2<-: id/"
					 + header.getRequestId() 
					 + ": "
					 + header);
	}
    }

    public void handleInput(LocateRequestMessage_1_0 header) throws IOException
    {
	try {
	    if (transportDebug())
		dprint(".LOCATE_REQUEST 1.0->: " + header);
	    try {
		messageHeader = header;
		setInputObject();
	    } finally {
		setWorkThenPoolOrResumeOptimizedRead(header);
	    }
	    getProtocolHandler().handleRequest(header, this);
	} catch (Throwable t) {
	    if (transportDebug()) 
		dprint(".LOCATE_REQUEST 1.0: !!ERROR!!: " + header, t);
	    // Mask the exception from thread.;
	} finally {
	    if (transportDebug()) 
		dprint(".LOCATE_REQUEST 1.0<-: " + header);
	}

    }

    public void handleInput(LocateRequestMessage_1_1 header) throws IOException
    {
	try {
	    if (transportDebug()) 
		dprint(".LOCATE_REQUEST 1.1->: " + header);
	    try {
		messageHeader = header;
		setInputObject();
	    } finally {
		setWorkThenPoolOrResumeOptimizedRead(header);
	    }
	    getProtocolHandler().handleRequest(header, this);
	} catch (Throwable t) {
	    if (transportDebug()) 
		dprint(".LOCATE_REQUEST 1.1: !!ERROR!!: " + header, t);
	    // Mask the exception from thread.;
	} finally {
	    if (transportDebug()) 
		dprint(".LOCATE_REQUEST 1.1<-:" + header);
	}
    }

    public void handleInput(LocateRequestMessage_1_2 header) throws IOException
    {
	try {
	    try {
		messageHeader = header;

		header.unmarshalRequestID(dispatchByteBuffer);
		setInputObject();

		if (transportDebug()) 
		    dprint(".LOCATE_REQUEST 1.2->: id/"
			   + header.getRequestId() 
			   + ": "
			   + header);

		if (header.moreFragmentsToFollow()) {
		    connection.serverRequestMapPut(header.getRequestId(),this);
		}
	    } finally {
		setWorkThenPoolOrResumeOptimizedRead(header);
	    }
	    getProtocolHandler().handleRequest(header, this);
	} catch (Throwable t) {
	    if (transportDebug()) 
		dprint(".LOCATE_REQUEST 1.2: id/"
		       + header.getRequestId()
		       + ": !!ERROR!!: " 
		       + header, t);
	    // Mask the exception from thread.;
	} finally {
	    if (transportDebug()) 
		dprint(".LOCATE_REQUEST 1.2<-: id/"
		       + header.getRequestId() 
		       + ": "
		       + header);
	}
    }

    public void handleInput(LocateReplyMessage_1_0 header) throws IOException
    {
	try {
	    if (transportDebug()) 
		dprint(".LOCATE_REPLY 1.0->:" + header);
	    try {
		messageHeader = header;
		setInputObject();
		inputObject.unmarshalHeader(); // REVISIT Put in subcontract.
		signalResponseReceived();
	    } finally {
		setWorkThenReadOrResumeOptimizedRead(header);
	    }
	} catch (Throwable t) {
	    if (transportDebug()) 
		dprint(".LOCATE_REPLY 1.0: !!ERROR!!: " + header, t);
	    // Mask the exception from thread.;
	} finally {
	    if (transportDebug()) 
		dprint(".LOCATE_REPLY 1.0<-: " + header);
	}
    }

    public void handleInput(LocateReplyMessage_1_1 header) throws IOException
    {
	try {
	    if (transportDebug()) dprint(".LOCATE_REPLY 1.1->: " + header);
	    try {
		messageHeader = header;
		setInputObject();
		// Fragmented LocateReplies are not allowed in 1.1.
		inputObject.unmarshalHeader();
		signalResponseReceived();
	    } finally {
		setWorkThenReadOrResumeOptimizedRead(header);
	    }
	} catch (Throwable t) {
	    if (transportDebug()) 
		dprint(".LOCATE_REPLY 1.1: !!ERROR!!: " + header, t);
	    // Mask the exception from thread.;
	} finally {
	    if (transportDebug()) dprint(".LOCATE_REPLY 1.1<-: " + header);
	}
    }

    public void handleInput(LocateReplyMessage_1_2 header) throws IOException
    {
	try {
	    try {
		messageHeader = header;

		// No need to put in client reply map - already there.
		header.unmarshalRequestID(dispatchByteBuffer);

		setInputObject();

		if (transportDebug()) dprint(".LOCATE_REPLY 1.2->: id/"
					     + header.getRequestId() 
					     + ": "
					     + header);

		signalResponseReceived();
	    } finally {
		setWorkThenPoolOrResumeOptimizedRead(header);
	    }
	} catch (Throwable t) {
	    if (transportDebug()) 
		dprint(".LOCATE_REPLY 1.2: id/"
		       + header.getRequestId()
		       + ": !!ERROR!!: " 
		       + header, t);
	    // Mask the exception from thread.;
	} finally {
	    if (transportDebug()) dprint(".LOCATE_REPLY 1.2<-: id/"
					 + header.getRequestId() 
					 + ": "
					 + header);
	}
    }

    public void handleInput(FragmentMessage_1_1 header) throws IOException
    {
	try {
	    if (transportDebug()) {
		dprint(".FRAGMENT 1.1->: "
		       + "more?: " + header.moreFragmentsToFollow()
		       + ": " + header);
	    }
	    try {
		messageHeader = header;
		MessageMediator mediator = null;
		CDRInputObject inputObject = null;

		if (connection.isServer()) {
		    mediator = connection.serverRequest_1_1_Get();
		} else {
		    mediator = connection.clientReply_1_1_Get();
		}
		if (mediator != null) {
		    inputObject = (CDRInputObject) mediator.getInputObject();
		}

		// If no input stream available, then discard the fragment.
		// This can happen:
		// 1. if a fragment message is received prior to receiving
		//    the original request/reply message. Very unlikely.
		// 2. if a fragment message is received after the
		//    reply has been sent (early replies)
		// Note: In the case of early replies, the fragments received
		// during the request processing (which are never unmarshaled),
		// will eventually be discarded by the GC.
		if (inputObject == null) {
		    if (transportDebug()) 
			dprint(".FRAGMENT 1.1: ++++DISCARDING++++: " + header);
                    // need to release dispatchByteBuffer to pool if
                    // we are discarding
                    releaseByteBufferToPool();
		    return;
		}

		inputObject.getBufferManager()
		    .processFragment(dispatchByteBuffer, header);

		if (! header.moreFragmentsToFollow()) {
		    if (connection.isServer()) {
			connection.serverRequest_1_1_Remove();
		    } else {
			connection.clientReply_1_1_Remove();
		    }
		}
	    } finally {
		// NOTE: This *must* come after queing the fragment
		// when using the selector to ensure fragments stay in order.
		setWorkThenReadOrResumeOptimizedRead(header);
	    }
	} catch (Throwable t) {
	    if (transportDebug()) 
		dprint(".FRAGMENT 1.1: !!ERROR!!: " + header, t);
	    // Mask the exception from thread.;
	} finally {
	    if (transportDebug()) dprint(".FRAGMENT 1.1<-: " + header);
	}
    }

    public void handleInput(FragmentMessage_1_2 header) throws IOException
    {
	try {
	    try {
		messageHeader = header;

		// Note:  We know it's a 1.2 fragment, we have the data, but
		// we need the IIOPInputStream instance to unmarshal the
		// request ID... but we need the request ID to get the
		// IIOPInputStream instance. So we peek at the raw bytes.

		header.unmarshalRequestID(dispatchByteBuffer);

		if (transportDebug()) {
		    dprint(".FRAGMENT 1.2->: id/"
			   + header.getRequestId()
			   + ": more?: " + header.moreFragmentsToFollow()
			   + ": " + header);
		}

		MessageMediator mediator = null;
		InputObject inputObject = null;

		if (connection.isServer()) {
		    mediator =
			connection.serverRequestMapGet(header.getRequestId());
		} else {
		    mediator = 
			connection.clientRequestMapGet(header.getRequestId());
		}
		if (mediator != null) {
		    inputObject = mediator.getInputObject();
		}
		// See 1.1 comments.
		if (inputObject == null) {
		    if (transportDebug()) {
			dprint(".FRAGMENT 1.2: id/"
			       + header.getRequestId()
			       + ": ++++DISCARDING++++: "
			       + header);
		    }
                    // need to release dispatchByteBuffer to pool if
                    // we are discarding
                    releaseByteBufferToPool();
		    return;
		}
		((CDRInputObject)inputObject)
		    .getBufferManager().processFragment(
                                     dispatchByteBuffer, header);

		// REVISIT: but if it is a server don't you have to remove the
		// stream from the map?
		if (! connection.isServer()) {
		    /* REVISIT
		     * No need to do anything.
		     * Should we mark that last was received?
		     if (! header.moreFragmentsToFollow()) {
		     // Last fragment.
		     }
		    */
		}
	    } finally {
		// NOTE: This *must* come after queing the fragment
		// when using the selector to ensure fragments stay in order.
                setWorkThenReadOrResumeOptimizedRead(header);
	    }
	} catch (Throwable t) {
	    if (transportDebug()) 
		dprint(".FRAGMENT 1.2: id/"
		       + header.getRequestId()
		       + ": !!ERROR!!: " 
		       + header, t);
	    // Mask the exception from thread.;
	} finally {
	    if (transportDebug()) dprint(".FRAGMENT 1.2<-: id/"
					 + header.getRequestId() 
					 + ": "
					 + header);
	}
    }

    public void handleInput(CancelRequestMessage header) throws IOException
    {
	try {
	    try {
		messageHeader = header;
		setInputObject();

		// REVISIT: Move these two to subcontract.
		inputObject.unmarshalHeader();

		if (transportDebug()) dprint(".CANCEL->: id/" 
					     + header.getRequestId() + ": "
					     + header.getGIOPVersion() + ": "
					     + header);

		processCancelRequest(header.getRequestId());
                releaseByteBufferToPool();
	    } finally {
                setWorkThenReadOrResumeOptimizedRead(header);
	    }
	} catch (Throwable t) {
	    if (transportDebug()) dprint(".CANCEL: id/"
					 + header.getRequestId()
					 + ": !!ERROR!!: " 
					 + header, t);
	    // Mask the exception from thread.;
	} finally {
	    if (transportDebug()) dprint(".CANCEL<-: id/" 
					 + header.getRequestId() + ": "
					 + header.getGIOPVersion() + ": "
					 + header);
	}
    }
    
    private void throwNotImplemented()
    {
	isThreadDone = false;
	throwNotImplemented("");
    }

    private void throwNotImplemented(String msg)
    {
	throw new RuntimeException("CorbaMessageMediatorImpl: not implemented " + msg);
    }

    private void dprint(String msg, Throwable t) 
    {
	dprint(msg);
	t.printStackTrace(System.out);
    }

    private void dprint(String msg) 
    {
	ORBUtility.dprint("CorbaMessageMediatorImpl", msg);
    }

    protected String opAndId(CorbaMessageMediator mediator)
    {
	return ORBUtility.operationNameAndRequestId(mediator);
    }

    private boolean transportDebug()
    {
	return orb.transportDebugFlag;
    }

    // REVISIT: move this to subcontract (but both client and server need it).
    private final void processCancelRequest(int cancelReqId) {

        // The GIOP version of CancelRequest does not matter, since
        // CancelRequest_1_0 could be sent to cancel a request which
        // has a different GIOP version.

        /*
         * CancelRequest processing logic :
         *
         *  - find the request with matching requestId
         *
         *  - call cancelProcessing() in BufferManagerRead [BMR]
         *
         *  - the hope is that worker thread would call BMR.underflow()
         *    to wait for more fragments to come in. When BMR.underflow() is
         *    called, if a CancelRequest had already arrived,  
	 *    the worker thread would throw ThreadDeath,
         *    else the thread would wait to be notified of the
         *    arrival of a new fragment or CancelRequest. Upon notification,
         *    the woken up thread would check to see if a CancelRequest had
         *    arrived and if so throw a ThreadDeath or it will continue to
         *    process the received fragment.
         *
         *  - if all the fragments had been received prior to CancelRequest
         *    then the worker thread would never block in BMR.underflow().
         *    So, setting the abort flag in BMR has no effect. The request
         *    processing will complete normally.
         *
         *  - in the case where the server has received enough fragments to 
	 *    start processing the request and the server sends out 
	 *    an early reply. In such a case if the CancelRequest arrives 
	 *    after the reply has been sent, it has no effect.
         */

        if (!connection.isServer()) {
	    return; // we do not support bi-directional giop yet, ignore.
        }

        // Try to get hold of the InputStream buffer.
        // In the case of 1.0 requests there is no way to get hold of
        // InputStream. Try out the 1.1 and 1.2 cases.

        // was the request 1.2 ?
	MessageMediator mediator = connection.serverRequestMapGet(cancelReqId);
	int requestId ;
        if (mediator == null) { 
	    // was the request 1.1 ?
	    mediator = connection.serverRequest_1_1_Get();
            if (mediator == null) {
		// XXX log this!
                // either the request was 1.0
                // or an early reply has already been sent
                // or request processing is over
                // or its a spurious CancelRequest
                return; // do nothing.
            }

	    requestId = ((CorbaMessageMediator) mediator).getRequestId();

            if (requestId != cancelReqId) {
                // A spurious 1.1 CancelRequest has been received.
		// XXX log this!
                return; // do nothing
            }

	    if (requestId == 0) { // special case
		// XXX log this
		// this means that
		// 1. the 1.1 requests' requestId has not been received
		//    i.e., a CancelRequest was received even before the
		//    1.1 request was received. The spec disallows this.
		// 2. or the 1.1 request has a requestId 0.
		//
		// It is a little tricky to distinguish these two. So, be
		// conservative and do not cancel the request. Downside is that
		// 1.1 requests with requestId of 0 will never be cancelled.
		return; // do nothing
	    }
	} else {
	    requestId = ((CorbaMessageMediator) mediator).getRequestId();
	}

	Message msg = ((CorbaMessageMediator)mediator).getRequestHeader();
	if (msg.getType() != Message.GIOPRequest) {
	    // Any mediator obtained here should only ever be for a GIOP
	    // request.
	    wrapper.badMessageTypeForCancel() ;	
	}

	// At this point we have a valid message mediator that contains
	// a valid requestId.

        // at this point we have chosen a request to be cancelled. But we
        // do not know if the target object's method has been invoked or not.
        // Request input stream being available simply means that the request
        // processing is not over yet. simply set the abort flag in the
        // BMRS and hope that the worker thread would notice it (this can
        // happen only if the request stream is being unmarshalled and the
        // target's method has not been invoked yet). This guarantees
        // that the requests which have been dispatched to the
        // target's method will never be cancelled.

        BufferManagerReadStream bufferManager = (BufferManagerReadStream)
	    ((CDRInputObject)mediator.getInputObject()).getBufferManager();
        bufferManager.cancelProcessing(cancelReqId);
    }

    ////////////////////////////////////////////////////
    //
    // spi.protocol.CorbaProtocolHandler
    //

    public void handleRequest(RequestMessage msg,
			      CorbaMessageMediator messageMediator)
    {
	try {
	    beginRequest(messageMediator);
	    try {
		handleRequestRequest(messageMediator);
		if (messageMediator.isOneWay()) {
		    return;
		}
	    } catch (Throwable t) {
		if (messageMediator.isOneWay()) {
		    return;
		}
		handleThrowableDuringServerDispatch(
                    messageMediator, t, CompletionStatus.COMPLETED_MAYBE);
	    }
	    sendResponse(messageMediator);
        } catch (Throwable t) {
	    dispatchError(messageMediator, "RequestMessage", t);
	} finally {
	    endRequest(messageMediator);
	}
    }

    public void handleRequest(LocateRequestMessage msg,
			      CorbaMessageMediator messageMediator)
    {
	try {
	    beginRequest(messageMediator);
	    try {
		handleLocateRequest(messageMediator);
	    } catch (Throwable t) {
		handleThrowableDuringServerDispatch(
	            messageMediator, t, CompletionStatus.COMPLETED_MAYBE);
	    }
	    sendResponse(messageMediator);
        } catch (Throwable t) {
	    dispatchError(messageMediator, "LocateRequestMessage", t);
	} finally {
	    endRequest(messageMediator);
	}
    }

    private void beginRequest(CorbaMessageMediator messageMediator)
    {
	ORB orb = (ORB) messageMediator.getBroker();
	if (orb.subcontractDebugFlag) {
	    dprint(".handleRequest->:");
	}
	connection.serverRequestProcessingBegins();
    }

    private void dispatchError(CorbaMessageMediator messageMediator,
			       String msg, Throwable t)
    {
	if (orb.subcontractDebugFlag) {
	    dprint(".handleRequest: " + opAndId(messageMediator) 
		   + ": !!ERROR!!: "
		   + msg,
		   t);
	}
	// REVISIT - this makes hcks sendTwoObjects fail
	// messageMediator.getConnection().close();
    }

    private void sendResponse(CorbaMessageMediator messageMediator)
    {
	if (orb.subcontractDebugFlag) {
	    dprint(".handleRequest: " + opAndId(messageMediator) 
		   + ": sending response");
	}
	// REVISIT - type and location
	CDROutputObject outputObject = (CDROutputObject)
	    messageMediator.getOutputObject();
	if (outputObject != null) {
	    // REVISIT - can be null for TRANSIENT below.
	    outputObject.finishSendingMessage();
	}
    }

    private void endRequest(CorbaMessageMediator messageMediator)
    {
	ORB orb = (ORB) messageMediator.getBroker();
	if (orb.subcontractDebugFlag) {
	    dprint(".handleRequest<-: " + opAndId(messageMediator));
	}

        // release NIO ByteBuffers to ByteBufferPool

        try {
            OutputObject outputObj = messageMediator.getOutputObject();
            if (outputObj != null) {
		outputObj.close();
            }
            InputObject inputObj = messageMediator.getInputObject();
            if (inputObj != null) {
		inputObj.close();
            }
        } catch (IOException ex) {
            // Given what close() does, this catch shouldn't ever happen.
            // See CDRInput/OutputObject.close() for more info.
            // It also won't result in a Corba error if an IOException happens.
	    if (orb.subcontractDebugFlag) {
                dprint(".endRequest: IOException:" + ex.getMessage(), ex);
	    }
        } finally {
	    ((CorbaConnection)messageMediator.getConnection()).serverRequestProcessingEnds();
	}
    }

    protected void handleRequestRequest(CorbaMessageMediator messageMediator)
    {
	// Does nothing if already unmarshaled.
	((CDRInputObject)messageMediator.getInputObject()).unmarshalHeader();

        ORB orb = (ORB)messageMediator.getBroker();
	orb.checkShutdownState();

	ObjectKey okey = messageMediator.getObjectKeyCacheEntry().getObjectKey();
        if (orb.subcontractDebugFlag) {
	    ObjectKeyTemplate oktemp = okey.getTemplate() ;
	    dprint( ".handleRequest: " + opAndId(messageMediator)
		    + ": dispatching to scid: " + oktemp.getSubcontractId());
	}

	CorbaServerRequestDispatcher sc = okey.getServerRequestDispatcher(orb);

	if (orb.subcontractDebugFlag) {
	    dprint(".handleRequest: " + opAndId(messageMediator)
		   + ": dispatching to sc: " + sc);
	}

	if (sc == null) {
	    throw wrapper.noServerScInDispatch() ;
	}

	// NOTE:
	// This is necessary so mediator can act as ResponseHandler
	// and pass necessary info to response constructors located
	// in the subcontract.
	// REVISIT - same class right now.
	//messageMediator.setProtocolHandler(this);

        try {
            orb.startingDispatch();
	    sc.dispatch(messageMediator);
        } finally {
            orb.finishedDispatch();
        }
    }

    protected void handleLocateRequest(CorbaMessageMediator messageMediator)
    {
	ORB orb = (ORB)messageMediator.getBroker();
	LocateRequestMessage msg = (LocateRequestMessage)
	    messageMediator.getDispatchHeader();
	IOR ior = null;
	LocateReplyMessage reply = null;
	short addrDisp = -1; 

	try {
	    ((CDRInputObject)messageMediator.getInputObject()).unmarshalHeader();
	    ObjectKey okey = msg.getObjectKeyCacheEntry().getObjectKey() ;
	    CorbaServerRequestDispatcher sc = okey.getServerRequestDispatcher( orb ) ;
	    if (sc == null) {
		return;
	    }

	    ior = sc.locate(okey);

	    if ( ior == null ) {
		reply = MessageBase.createLocateReply(
		            orb, msg.getGIOPVersion(),
			    msg.getEncodingVersion(), 
                            msg.getRequestId(),
			    LocateReplyMessage.OBJECT_HERE, null);

	    } else {
		reply = MessageBase.createLocateReply(
		            orb, msg.getGIOPVersion(),
			    msg.getEncodingVersion(),
                            msg.getRequestId(),
			    LocateReplyMessage.OBJECT_FORWARD, ior);
	    }
	    // REVISIT: Should we catch SystemExceptions?

	} catch (AddressingDispositionException ex) {

	    // create a response containing the expected target
	    // addressing disposition.
	    
	    reply = MessageBase.createLocateReply(
		        orb, msg.getGIOPVersion(),
			msg.getEncodingVersion(),
                        msg.getRequestId(),
			LocateReplyMessage.LOC_NEEDS_ADDRESSING_MODE, null);

	    addrDisp = ex.expectedAddrDisp();

	} catch (RequestCanceledException ex) {

	    return; // no need to send reply

	} catch ( Exception ex ) {

	    // REVISIT If exception is not OBJECT_NOT_EXIST, it should
	    // have a different reply

	    // This handles OBJECT_NOT_EXIST exceptions thrown in
	    // the subcontract or obj manager. Send back UNKNOWN_OBJECT.

	    reply = MessageBase.createLocateReply(
		        orb, msg.getGIOPVersion(),
			msg.getEncodingVersion(),
			msg.getRequestId(),
			LocateReplyMessage.UNKNOWN_OBJECT, null);
	}

	CDROutputObject outputObject =
	    createAppropriateOutputObject(messageMediator,
					  msg, reply);
	messageMediator.setOutputObject(outputObject);
	outputObject.setMessageMediator(messageMediator);

	reply.write(outputObject);
	// outputObject.setMessage(reply); // REVISIT - not necessary
	if (ior != null) {
	    ior.write(outputObject);
	}
	if (addrDisp != -1) {
	    AddressingDispositionHelper.write(outputObject, addrDisp);
	}
    }

    private CDROutputObject createAppropriateOutputObject(
        CorbaMessageMediator messageMediator,
	Message msg, LocateReplyMessage reply)
    {
	CDROutputObject outputObject;

	if (msg.getGIOPVersion().lessThan(GIOPVersion.V1_2)) {
	    // locate msgs 1.0 & 1.1 :=> grow, 
	    // REVISIT - build from factory
	    outputObject = new CDROutputObject(
		             (ORB) messageMediator.getBroker(),
			     this,
			     GIOPVersion.V1_0,
			     (CorbaConnection) messageMediator.getConnection(),
			     reply,
			     ORBConstants.STREAM_FORMAT_VERSION_1);
	} else {
	    // 1.2 :=> stream
	    // REVISIT - build from factory
	    outputObject = new CDROutputObject(
		             (ORB) messageMediator.getBroker(),
			     messageMediator,
			     reply,
			     ORBConstants.STREAM_FORMAT_VERSION_1);
	}
	return outputObject;
    }

    public void handleThrowableDuringServerDispatch(
        CorbaMessageMediator messageMediator,
        Throwable throwable,
        CompletionStatus completionStatus)
    {
	if (((ORB)messageMediator.getBroker()).subcontractDebugFlag) {
	    dprint(".handleThrowableDuringServerDispatch: "
		   + opAndId(messageMediator) + ": "
		   + throwable);
	}

	// If we haven't unmarshaled the header, we probably don't
	// have enough information to even send back a reply.

	// REVISIT
	// Cannot do this check.  When target addressing disposition does
	// not match (during header unmarshaling) it throws an exception
	// to be handled here.
	/*
	if (! ((CDRInputObject)messageMediator.getInputObject())
	    .unmarshaledHeader()) {
	    return;
	}
	*/
	handleThrowableDuringServerDispatch(messageMediator, 
					    throwable,
					    completionStatus,
					    1);
    }


    // REVISIT - catch and ignore RequestCanceledException.

    protected void handleThrowableDuringServerDispatch(
        CorbaMessageMediator messageMediator,
	Throwable throwable,
	CompletionStatus completionStatus,
	int iteration)
    {
	if (iteration > 10) {
	    if (((ORB)messageMediator.getBroker()).subcontractDebugFlag) {
		dprint(".handleThrowableDuringServerDispatch: " 
		       + opAndId(messageMediator)
		       + ": cannot handle: "
		       + throwable);
	    }

	    // REVISIT - should we close connection?
	    RuntimeException rte =
		new RuntimeException("handleThrowableDuringServerDispatch: " +
				     "cannot create response.");
	    rte.initCause(throwable);
	    throw rte;
	}

	try {
	    if (throwable instanceof ForwardException) {
		ForwardException fex = (ForwardException)throwable ;
		createLocationForward( messageMediator, fex.getIOR(), null ) ;
		return;
	    }

	    if (throwable instanceof AddressingDispositionException) {
		handleAddressingDisposition(
                    messageMediator,
		    (AddressingDispositionException)throwable);
		return;
	    } 

	    // Else.

	    SystemException sex = 
		convertThrowableToSystemException(throwable, completionStatus);

	    createSystemExceptionResponse(messageMediator, sex, null);
	    return;

	} catch (Throwable throwable2) {

	    // User code (e.g., postinvoke, interceptors) may change
	    // the exception, so we end up back here.
	    // Report the changed exception.

	    handleThrowableDuringServerDispatch(messageMediator,
						throwable2,
						completionStatus,
						iteration + 1);
	    return;
	}
    }

    protected SystemException convertThrowableToSystemException(
        Throwable throwable,
	CompletionStatus completionStatus)
    {
	if (throwable instanceof SystemException) {
	    return (SystemException)throwable;
	}

	if (throwable instanceof RequestCanceledException) {
	    // Reporting an exception response causes the
	    // poa current stack, the interceptor stacks, etc.
	    // to be balanced.  It also notifies interceptors
	    // that the request was cancelled.

	    return wrapper.requestCanceled( throwable ) ;
	}

	// NOTE: We do not trap ThreadDeath above Throwable.
	// There is no reason to stop the thread.  It is
	// just a worker thread.  The ORB never throws
	// ThreadDeath.  Client code may (e.g., in ServantManagers,
	// interceptors, or servants) but that should not
	// effect the ORB threads.  So it is just handled
	// generically.

	//
	// Last resort.
	// If user code throws a non-SystemException report it generically.
	//

	return wrapper.runtimeexception( CompletionStatus.COMPLETED_MAYBE,
					 throwable,
					 throwable.getClass().getName(),
					 throwable.getMessage());
    }

    protected void handleAddressingDisposition(
        CorbaMessageMediator messageMediator,
	AddressingDispositionException ex)
    {

	short addrDisp = -1;

	// from iiop.RequestProcessor.

	// Respond with expected target addressing disposition.
                    
	switch (messageMediator.getRequestHeader().getType()) {
	case Message.GIOPRequest :
	    ORB orb = (ORB)messageMediator.getBroker() ;

	    ReplyMessage replyHeader = MessageBase.createReply( orb, 
		messageMediator.getGIOPVersion(), 
		messageMediator.getEncodingVersion(), 
		messageMediator.getRequestId(), 
		ReplyMessage.NEEDS_ADDRESSING_MODE, 
		ServiceContextDefaults.makeServiceContexts(orb), null);
	    
	    // REVISIT: via acceptor factory.
	    CDROutputObject outputObject = new CDROutputObject(
                (ORB)messageMediator.getBroker(),
		this,
		messageMediator.getGIOPVersion(),
		(CorbaConnection)messageMediator.getConnection(),
		replyHeader,
		ORBConstants.STREAM_FORMAT_VERSION_1);
	    messageMediator.setOutputObject(outputObject);
	    outputObject.setMessageMediator(messageMediator);
	    replyHeader.write(outputObject);
	    AddressingDispositionHelper.write(outputObject,
					      ex.expectedAddrDisp());
	    return;

	case Message.GIOPLocateRequest :
	    LocateReplyMessage locateReplyHeader = MessageBase.createLocateReply(
	        (ORB)messageMediator.getBroker(),
		messageMediator.getGIOPVersion(),
		messageMediator.getEncodingVersion(),
		messageMediator.getRequestId(),
		LocateReplyMessage.LOC_NEEDS_ADDRESSING_MODE,
		null);                                   

	    addrDisp = ex.expectedAddrDisp();

	    // REVISIT: via acceptor factory.
	    outputObject = 
		createAppropriateOutputObject(messageMediator,
					      messageMediator.getRequestHeader(),
					      locateReplyHeader);
	    messageMediator.setOutputObject(outputObject);
	    outputObject.setMessageMediator(messageMediator);
	    locateReplyHeader.write(outputObject);
	    IOR ior = null;
	    if (ior != null) {
		ior.write(outputObject);
	    }
	    if (addrDisp != -1) {
		AddressingDispositionHelper.write(outputObject, addrDisp);
	    }
	    return;
	}
    }

    public CorbaMessageMediator createResponse(
        CorbaMessageMediator messageMediator,
	ServiceContexts svc)
    {
	// REVISIT: ignore service contexts during framework transition.
	// They are set in SubcontractResponseHandler to the wrong connection.
	// Then they would be set again here and a duplicate contexts
	// exception occurs.
	return createResponseHelper(
            messageMediator,
	    getServiceContextsForReply(messageMediator, null));
    }

    public CorbaMessageMediator createUserExceptionResponse(
        CorbaMessageMediator messageMediator, ServiceContexts svc) 
    {
	// REVISIT - same as above
	return createResponseHelper(
            messageMediator,
	    getServiceContextsForReply(messageMediator, null),
	    true);
    }

    public CorbaMessageMediator createUnknownExceptionResponse(
        CorbaMessageMediator messageMediator, UnknownException ex)
    {
	// NOTE: This service context container gets augmented in
	// tail call.
	ServiceContexts contexts = null;
	SystemException sys = new UNKNOWN( 0, 
	    CompletionStatus.COMPLETED_MAYBE);
	contexts = ServiceContextDefaults.makeServiceContexts( 
	    (ORB)messageMediator.getBroker() );
	UEInfoServiceContext uei = 
	    ServiceContextDefaults.makeUEInfoServiceContext(sys);
	contexts.put( uei ) ;
	return createSystemExceptionResponse(messageMediator, sys, contexts);
    }

    public CorbaMessageMediator createSystemExceptionResponse(
        CorbaMessageMediator messageMediator,
	SystemException ex,
	ServiceContexts svc)
    {
	if (messageMediator.getConnection() != null) {
	    // It is possible that fragments of response have already been
	    // sent.  Then an error may occur (e.g. marshaling error like
	    // non serializable object).  In that case it is too late
	    // to send the exception.  We just return the existing fragmented
	    // stream here.  This will cause an incomplete last fragment
	    // to be sent.  Then the other side will get a marshaling error
	    // when attempting to unmarshal.
	    
	    // REVISIT: Impl - make interface method to do the following.
	    CorbaMessageMediatorImpl mediator = (CorbaMessageMediatorImpl)
		((CorbaConnection)messageMediator.getConnection())
		.serverRequestMapGet(messageMediator.getRequestId());

	    OutputObject existingOutputObject = null;
	    if (mediator != null) {
		existingOutputObject = mediator.getOutputObject();
	    }

	    // REVISIT: need to think about messageMediator containing correct
	    // pointer to output object.
	    if (existingOutputObject != null &&
		mediator.sentFragment() && 
		! mediator.sentFullMessage())
	    {
		return mediator;
	    }
	}
    
	// Only do this if interceptors have been initialized on this request
	// and have not completed their lifecycle (otherwise the info stack
	// may be empty or have a different request's entry on top).
	if (messageMediator.executePIInResponseConstructor()) {
	    // REVISIT: not necessary in framework now?
	    // Inform Portable Interceptors of the SystemException.  This is
	    // required to be done here because the ending interception point
	    // is called in the when creating the response below
	    // but we do not currently write the SystemException into the 
	    // response until after the ending point is called.
	    ((ORB)messageMediator.getBroker()).getPIHandler().setServerPIInfo( ex );
	}

	if (((ORB)messageMediator.getBroker()).subcontractDebugFlag &&
	    ex != null)
        {
            dprint(".createSystemExceptionResponse: " 
		   + opAndId(messageMediator),
		   ex);
	}

	ServiceContexts serviceContexts = 
	    getServiceContextsForReply(messageMediator, svc);

	// NOTE: We MUST add the service context before creating
	// the response since service contexts are written to the
	// stream when the response object is created.

	addExceptionDetailMessage(messageMediator, ex, serviceContexts);

        CorbaMessageMediator response =
	    createResponseHelper(messageMediator, serviceContexts, false);

	// NOTE: From here on, it is too late to add more service contexts.
	// They have already been serialized to the stream (and maybe fragments
	// sent).

	ORBUtility.writeSystemException(
            ex, (OutputStream)response.getOutputObject());

	return response;
    }

    private void addExceptionDetailMessage(CorbaMessageMediator mediator,
					   SystemException ex,
					   ServiceContexts serviceContexts)
    {
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	PrintWriter pw = new PrintWriter(baos);
	ex.printStackTrace(pw);
	pw.flush(); // NOTE: you must flush or baos will be empty.
	EncapsOutputStream encapsOutputStream = 
	    new EncapsOutputStream((ORB)mediator.getBroker());
	encapsOutputStream.putEndian();
	encapsOutputStream.write_wstring(baos.toString());
	UnknownServiceContext serviceContext =
	    ServiceContextDefaults.makeUnknownServiceContext(
		ExceptionDetailMessage.value,
	        encapsOutputStream.toByteArray());
	serviceContexts.put(serviceContext);
    }

    public CorbaMessageMediator createLocationForward(
        CorbaMessageMediator messageMediator, IOR ior, ServiceContexts svc)
    {
        ReplyMessage reply 
            = MessageBase.createReply(
                  (ORB)messageMediator.getBroker(),
		  messageMediator.getGIOPVersion(),
		  messageMediator.getEncodingVersion(),
		  messageMediator.getRequestId(), 
		  ReplyMessage.LOCATION_FORWARD,
		  getServiceContextsForReply(messageMediator, svc), 
		  ior);

	return createResponseHelper(messageMediator, reply, ior);
    }

    protected CorbaMessageMediator createResponseHelper(
        CorbaMessageMediator messageMediator, ServiceContexts svc)
    {
	ReplyMessage message = 
	    MessageBase.createReply(
		(ORB)messageMediator.getBroker(),
		messageMediator.getGIOPVersion(),
		messageMediator.getEncodingVersion(),
		messageMediator.getRequestId(),
		ReplyMessage.NO_EXCEPTION,
		svc,
		null);
	return createResponseHelper(messageMediator, message, null);
    }

    protected CorbaMessageMediator createResponseHelper(
        CorbaMessageMediator messageMediator, ServiceContexts svc,boolean user)
    {
	ReplyMessage message =
            MessageBase.createReply(
                (ORB)messageMediator.getBroker(),
		messageMediator.getGIOPVersion(), 
		messageMediator.getEncodingVersion(),
		messageMediator.getRequestId(),
		user ? ReplyMessage.USER_EXCEPTION :
		       ReplyMessage.SYSTEM_EXCEPTION,
		svc,
		null);
	return createResponseHelper(messageMediator, message, null);
    }

    // REVISIT - IOR arg is ignored.
    protected CorbaMessageMediator createResponseHelper(
        CorbaMessageMediator messageMediator, ReplyMessage reply, IOR ior)
    {
	// REVISIT - these should be invoked from subcontract.
	runServantPostInvoke(messageMediator);
	runInterceptors(messageMediator, reply);
	runRemoveThreadInfo(messageMediator);

        if (((ORB)messageMediator.getBroker()).subcontractDebugFlag) {
	    dprint(".createResponseHelper: " 
		   + opAndId(messageMediator) + ": "
		   + reply);
	}
		      
	messageMediator.setReplyHeader(reply);

	OutputObject replyOutputObject;
	// REVISIT = do not use null.
	// 
	if (messageMediator.getConnection() == null) {
	    // REVISIT - needs factory
	    replyOutputObject = 
		new CDROutputObject(orb, messageMediator,
				    messageMediator.getReplyHeader(),
				    messageMediator.getStreamFormatVersion(),
				    BufferManagerFactory.GROW);
	} else {
	    replyOutputObject = messageMediator.getConnection().getAcceptor()
	     .createOutputObject(messageMediator.getBroker(), messageMediator);
	}
	messageMediator.setOutputObject(replyOutputObject);
	messageMediator.getOutputObject().setMessageMediator(messageMediator);

	reply.write((OutputStream) messageMediator.getOutputObject());
	if (reply.getIOR() != null) {
	    reply.getIOR().write((OutputStream) messageMediator.getOutputObject());
	}
	// REVISIT - not necessary?
	//messageMediator.this.replyIOR = reply.getIOR();

	// NOTE: The mediator holds onto output object so return value
	// not really necessary.
	return messageMediator;
    }

    protected void runServantPostInvoke(CorbaMessageMediator messageMediator)
    {
	// Run ServantLocator::postinvoke.  This may cause a SystemException
	// which will throw out of the constructor and return later
	// to construct a reply for that exception.  The internal logic
	// of returnServant makes sure that postinvoke is only called once.
	// REVISIT: instead of instanceof, put method on all orbs.
	ORB orb = null;
	// This flag is to deal with BootstrapServer use of reply streams,
	// with ServerRequestDispatcher's use of reply streams, etc.
	if (messageMediator.executeReturnServantInResponseConstructor()) {
	    // It is possible to get marshaling errors in the skeleton after
	    // postinvoke has completed.  We must set this to false so that
	    // when the error exception reply is constructed we don't try
	    // to incorrectly access poa current (which will be the wrong
	    // one or an empty stack.
	    messageMediator.setExecuteReturnServantInResponseConstructor(false);
	    messageMediator.setExecuteRemoveThreadInfoInResponseConstructor(true);

	    try {
		orb = (ORB)messageMediator.getBroker();
		OAInvocationInfo info = orb.peekInvocationInfo() ;
		ObjectAdapter oa = info.oa();
		try {
		    oa.returnServant() ;
		} catch (Throwable thr) {
		    wrapper.unexpectedException( thr ) ;

		    if (thr instanceof Error)
			throw (Error)thr ;
		    else if (thr instanceof RuntimeException)
			throw (RuntimeException)thr ;
		} finally {
		    oa.exit();
		}
	    } catch (EmptyStackException ese) {
		throw wrapper.emptyStackRunServantPostInvoke( ese ) ;
	    }
	}
    }

    protected void runInterceptors(CorbaMessageMediator messageMediator,
				   ReplyMessage reply)
    {
	if( messageMediator.executePIInResponseConstructor() ) {
	    // Invoke server request ending interception points (send_*):
	    // Note: this may end up with a SystemException or an internal
	    // Runtime ForwardRequest
	    ((ORB)messageMediator.getBroker()).getPIHandler().
		invokeServerPIEndingPoint( reply );

	    // Note this will be executed even if a ForwardRequest or 
	    // SystemException is thrown by a Portable Interceptors ending 
	    // point since we end up in this constructor again anyway.
	    ((ORB)messageMediator.getBroker()).getPIHandler().
		cleanupServerPIRequest();

	    // See createSystemExceptionResponse for why this is necesary.
	    messageMediator.setExecutePIInResponseConstructor(false);
	}
    }

    protected void runRemoveThreadInfo(CorbaMessageMediator messageMediator)
    {
	// Once you get here then the final reply is available (i.e.,
	// postinvoke and interceptors have completed.
	if (messageMediator.executeRemoveThreadInfoInResponseConstructor()) {
	    messageMediator.setExecuteRemoveThreadInfoInResponseConstructor(false);
	    ((ORB)messageMediator.getBroker()).popInvocationInfo() ;
	}
    }

    protected ServiceContexts getServiceContextsForReply(
        CorbaMessageMediator messageMediator, ServiceContexts contexts) 
    {
	CorbaConnection c = (CorbaConnection) messageMediator.getConnection();

        if (((ORB)messageMediator.getBroker()).subcontractDebugFlag) {
            dprint(".getServiceContextsForReply: " 
		   + opAndId(messageMediator)
		   + ": " + c);
	}
	
	// NOTE : We only want to send the runtime context the first time,
	// only in the case where the encoding is set to CDR.
	if (contexts == null) {
	    if (getGIOPVersion().equals(GIOPVersion.V1_2) && 
		c != null && 
		c.getBroker().getORBData().alwaysSendCodeSetServiceContext() &&
		(getEncodingVersion() == ORBConstants.CDR_ENC_VERSION)) {
	        if (!c.isPostInitialContexts()) {
		    c.setPostInitialContexts();
		    contexts = ((ORB)messageMediator.getBroker()).
		      getServiceContextsCache().get(ServiceContextsCache.CASE.SERVER_INITIAL);
		} else {
		    contexts = ((ORB)messageMediator.getBroker()).
		      getServiceContextsCache().get(ServiceContextsCache.CASE.SERVER_SUBSEQUENT);
		}
		return contexts;
	    } else {
	        contexts = ServiceContextDefaults.makeServiceContexts(
						  (ORB)messageMediator.getBroker()); 
	    }
	} 


	if (c != null && !c.isPostInitialContexts() &&
	        (getEncodingVersion() == ORBConstants.CDR_ENC_VERSION)) {
	    c.setPostInitialContexts();
	    SendingContextServiceContext scsc = 
		ServiceContextDefaults.makeSendingContextServiceContext( 
		    ((ORB)messageMediator.getBroker()).getFVDCodeBaseIOR()) ; 

	    if (contexts.get( scsc.getId() ) != null)
		throw wrapper.duplicateSendingContextServiceContext() ;

	    contexts.put( scsc ) ;

	    if ( ((ORB)messageMediator.getBroker()).subcontractDebugFlag) 
                dprint(".getServiceContextsForReply: "
		       + opAndId(messageMediator)
		       + ": added SendingContextServiceContext" ) ;
	}

        // send ORBVersion servicecontext as part of the Reply

        ORBVersionServiceContext ovsc 
            = ServiceContextDefaults.makeORBVersionServiceContext();

	if (contexts.get( ovsc.getId() ) != null)
	    throw wrapper.duplicateOrbVersionServiceContext() ;

	contexts.put( ovsc ) ;

	if ( ((ORB)messageMediator.getBroker()).subcontractDebugFlag)
	    dprint(".getServiceContextsForReply: "
		   + opAndId(messageMediator)
	           + ": added ORB version service context");

        return contexts;
    }

    // REVISIT - this method should be migrated to orbutil.ORBUtility
    //           since all locations that release ByteBuffers use
    //           very similar logic and debug information.
    private void releaseByteBufferToPool() {
        if (dispatchByteBuffer != null) {
            orb.getByteBufferPool().releaseByteBuffer(dispatchByteBuffer);
            if (transportDebug()) {
                int bbId = System.identityHashCode(dispatchByteBuffer);
                StringBuffer sb = new StringBuffer();
                sb.append(".handleInput: releasing ByteBuffer (" + bbId + 
                          ") to ByteBufferPool");
                dprint(sb.toString());
             }
        }
    }

    public void cancelRequest() {
	CDRInputObject inputObject = (CDRInputObject) getInputObject();
	if (inputObject != null) {
	    BufferManagerReadStream bufferManager =
		(BufferManagerReadStream) inputObject.getBufferManager();
	    bufferManager.cancelProcessing(getRequestId());
	}
    }

    // 
    // Work implementation
    //
    
    /**
     * Execute dispatch in its own WorkerThread. Prior to this method being
     * called this object must be initialized with a valid connection 
     * (CorbaConnection), orb (ORB), dispatchHeader (Message) and 
     * dispatchByteBuffer (ByteBuffer).
     */
    public void doWork() {
        try {
            if (orb.transportDebugFlag) {
		dprint(".doWork->: " + this);
	    }
            dispatch();
        } catch (Throwable t) {
	    if (orb.transportDebugFlag) {
		dprint(".doWork: ignoring Throwable: "
		       + t
		       + " " + this);
	    }
        } finally {
	    if (orb.transportDebugFlag) {
		dprint(".doWork<-: " + this);
	    }
	}
    }

    public void setEnqueueTime(long timeInMillis) {
        enqueueTime = timeInMillis;
    }

    public long getEnqueueTime() {
        return enqueueTime;
    }

    public String getName() {
        return toString();
    }
}

// End of file.



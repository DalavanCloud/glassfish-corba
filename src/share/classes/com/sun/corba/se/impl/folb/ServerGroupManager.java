/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Sun Microsystems, Inc. All rights reserved.
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
// Created       : 2005 Jun 08 (Tue) 14:04:09 by Harold Carr.
// Last Modified : 2005 Sep 28 (Wed) 09:40:45 by Harold Carr.
//

package com.sun.corba.se.impl.folb;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.server.UID;
import java.util.List;
import java.util.LinkedList;
import javax.rmi.PortableRemoteObject;

import org.omg.CORBA.Any;
import org.omg.CORBA.BAD_PARAM;
import org.omg.CORBA.ORBPackage.InvalidName;
import org.omg.IOP.Codec;
import org.omg.IOP.CodecPackage.InvalidTypeForEncoding;
import org.omg.IOP.CodecFactory;
import org.omg.IOP.CodecFactoryHelper;
import org.omg.IOP.CodecFactoryPackage.UnknownEncoding;
import org.omg.IOP.Encoding;
import org.omg.IOP.ServiceContext;
import org.omg.IOP.TaggedComponent;
import org.omg.PortableInterceptor.ForwardRequest;
import org.omg.PortableInterceptor.ForwardRequestHelper;
import org.omg.PortableInterceptor.IORInfo;
import org.omg.PortableInterceptor.IORInterceptor;
import org.omg.PortableInterceptor.ObjectReferenceTemplate;
import org.omg.PortableInterceptor.ORBInitializer;
import org.omg.PortableInterceptor.ORBInitInfo;
import org.omg.PortableInterceptor.ServerRequestInterceptor;
import org.omg.PortableInterceptor.ServerRequestInfo;

import com.sun.corba.se.spi.folb.CSIv2SSLTaggedComponentHandler;
import com.sun.corba.se.spi.folb.GroupInfoService;
import com.sun.corba.se.spi.folb.GroupInfoServiceObserver;
import com.sun.corba.se.spi.folb.ClusterInstanceInfo;
import com.sun.corba.se.spi.folb.ClusterInstanceInfoHelper;
import com.sun.corba.se.spi.folb.SocketInfo;

import com.sun.corba.se.spi.oa.rfm.ReferenceFactory;
import com.sun.corba.se.spi.oa.rfm.ReferenceFactoryManager;
import com.sun.corba.se.spi.orb.ORBConfigurator ;
import com.sun.corba.se.spi.orb.DataCollector ;
import com.sun.corba.se.spi.orb.ORB;

import com.sun.corba.se.spi.orbutil.ORBConstants;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.spi.orbutil.tf.annotation.InfoMethod;
import com.sun.corba.se.spi.trace.Folb;

/**
 * @author Harold Carr
 */
@Folb
public class ServerGroupManager
    extends
        org.omg.CORBA.LocalObject
    implements 
	GroupInfoServiceObserver,
	IORInterceptor,
	ORBConfigurator,
	ORBInitializer,
	ServerRequestInterceptor
{
    private static final String baseMsg = ServerGroupManager.class.getName();
    private static final long serialVersionUID = -3197578705750630503L;

    private ORB orb;
    private GroupInfoService gis;
    private CSIv2SSLTaggedComponentHandler csiv2SSLTaggedComponentHandler;
    private String membershipLabel;
    private enum MembershipChangeState { IDLE, DOING_WORK, RETRY_REQUIRED };
    private MembershipChangeState membershipChangeState =
	MembershipChangeState.IDLE;
    private ReferenceFactoryManager referenceFactoryManager;
    private Codec codec;
    private boolean initialized = false;

    // REVISIT - the app server identifies socket "types" with
    // these strings.  Should be an official API.
    private static final String ORB_LISTENER = "orb-listener";
    private static final String SSL = "SSL";

    @InfoMethod
    private void alreadyInitialized() { }

    @Folb
    private void initialize() {

	if (initialized) {
            alreadyInitialized();
	    return;
	}

	try {
	    initialized = true;

	    updateMembershipLabel();

	    CodecFactory codecFactory =
		CodecFactoryHelper.narrow(
		  orb.resolve_initial_references(
                      ORBConstants.CODEC_FACTORY_NAME));

	    codec = codecFactory.create_codec(
                new Encoding((short)0, (byte)1, (byte)2));

	    referenceFactoryManager = (ReferenceFactoryManager)
		orb.resolve_initial_references(
	            ORBConstants.REFERENCE_FACTORY_MANAGER);

	    gis = (GroupInfoService) PortableRemoteObject.narrow(
                orb.resolve_initial_references(
	            ORBConstants.FOLB_SERVER_GROUP_INFO_SERVICE),
		GroupInfoService.class);

	    gis.addObserver(this);

	    try {
		csiv2SSLTaggedComponentHandler =
		    (CSIv2SSLTaggedComponentHandler)
		    orb.resolve_initial_references(
                        ORBConstants.CSI_V2_SSL_TAGGED_COMPONENT_HANDLER);
	    } catch (InvalidName e) {
		csiv2SSLTaggedComponentHandler = null;
                // XXX log
	    }
	} catch (InvalidName e) {
            // XXX log
	} catch (UnknownEncoding e) {
            // XXX log
	}
    }

    ////////////////////////////////////////////////////
    //
    // Interceptor operations
    //

    public String name() {
	return baseMsg; 
    }

    public void destroy() {
    }

    ////////////////////////////////////////////////////
    //
    // IORInterceptor
    //

    @InfoMethod
    private void adapterName( String[] arr ) { }

    @InfoMethod
    private void addingAddresses() { }

    @InfoMethod
    private void notAddingAddress() { }

    @InfoMethod
    private void addingMembershipLabel( String ml ) { }

    @InfoMethod
    private void notAddingMembershipLabel( ) { }

    @Folb
    public void establish_components(IORInfo iorInfo) {
	try {
	    initialize();

	    // Only handle ReferenceFactory adapters.
	    String[] adapterName = 
	        ((com.sun.corba.se.impl.interceptors.IORInfoImpl)iorInfo)
	            .getObjectAdapter().getAdapterTemplate().adapter_name();

            adapterName( adapterName ) ;

	    ReferenceFactory rf = referenceFactoryManager.find(adapterName);
	    if (rf == null) {
	        if (gis.shouldAddAddressesToNonReferenceFactory(adapterName)) {
                    addingAddresses() ;
		} else {
                    notAddingAddress();
		    return;
		}
	    }

	    // Get all addressing information.

	    // both CLEAR and SSL
	    List<ClusterInstanceInfo> info = 
		gis.getClusterInstanceInfo(adapterName);

	    // Let security handle SSL infomation.
	    if (csiv2SSLTaggedComponentHandler != null) {
		TaggedComponent csiv2 = 
		    csiv2SSLTaggedComponentHandler.insert(iorInfo, info);
		if (csiv2 != null) {
		    iorInfo.add_ior_component(csiv2);
		}
	    }

	    // Handle CLEAR_TEXT addresses.
	    for (ClusterInstanceInfo clusterInstanceInfo : info) {
		if (orb.folbDebugFlag) {
		    dprint(".establish_components: adding instance info for: " 
			   + clusterInstanceInfo.name 
			   + "; " + clusterInstanceInfo.weight 
			   + "; with addresses:");
		}

		List<SocketInfo> listOfSocketInfo = 
		    new LinkedList<SocketInfo>();

		for (int i = 0; i < clusterInstanceInfo.endpoints.length; ++i){
		    if (clusterInstanceInfo.endpoints[i].type.startsWith(SSL)){
			// NOTE: The standalone ORB test depends on accepting
			// types such as "t0", "W", etc.
			if (orb.folbDebugFlag) {
			    dprint(".establish_components: skipping: "
				   +       clusterInstanceInfo.endpoints[i].type
				   + "/" + clusterInstanceInfo.endpoints[i].host
				   + "/" + clusterInstanceInfo.endpoints[i].port);
			}
			continue;
		    }
		    if (orb.folbDebugFlag) {
			dprint(".establish_components: "
			       +       clusterInstanceInfo.endpoints[i].type
			       + "/" + clusterInstanceInfo.endpoints[i].host
			       + "/" + clusterInstanceInfo.endpoints[i].port);
		    }
		    listOfSocketInfo.add(clusterInstanceInfo.endpoints[i]);
		}
		// REVISIT - make orbutil utility and use in
		// FailoverIORInterceptor, IiopFolbGmsClient and here.
		SocketInfo[] arrayOfSocketInfo =
		    new SocketInfo[listOfSocketInfo.size()];
		int x = 0;
		for (SocketInfo si : listOfSocketInfo) {
		    arrayOfSocketInfo[x++] = si;
		}
		clusterInstanceInfo.endpoints = arrayOfSocketInfo;
		Any any = orb.create_any();
		ClusterInstanceInfoHelper.insert(any, clusterInstanceInfo);
		byte[] data = null;
		try {
		    data = codec.encode_value(any);
		} catch (InvalidTypeForEncoding e) {
                    // XXX log
		}
		TaggedComponent tc = new TaggedComponent(
	            ORBConstants.FOLB_MEMBER_ADDRESSES_TAGGED_COMPONENT_ID,
		    data);
		iorInfo.add_ior_component(tc);
	    }

	    // Handle membership label.
	    if (gis.shouldAddMembershipLabel(adapterName)) {
		// XXX "internationalize" getBytes
		TaggedComponent tc = new TaggedComponent(
                    ORBConstants.FOLB_MEMBERSHIP_LABEL_TAGGED_COMPONENT_ID,
		    membershipLabel.getBytes());

                addingMembershipLabel( membershipLabel );
		iorInfo.add_ior_component(tc);
	    } else {
                notAddingMembershipLabel();
	    }
	} catch (RuntimeException e) {
            // XXX log me
	}
    }

    public void components_established( IORInfo iorInfo ) {
    }

    public void adapter_manager_state_changed( int managerId, short state ) {
    }

    public void adapter_state_changed( ObjectReferenceTemplate[] templates,
        short state ) {
    }

    ////////////////////////////////////////////////////
    //
    // GroupInfoServiceObserver
    //

    @InfoMethod
    private void alreadyChangingMembership() { }

    @InfoMethod
    private void loopingForMembershipChange() { }

    @InfoMethod
    private void unexpectedStateForMembershipChange() { }

    @Folb
    public void membershipChange() {
	try {
	    synchronized (this) {
		if (membershipChangeState == MembershipChangeState.IDLE) {
		    membershipChangeState = MembershipChangeState.DOING_WORK;
		} else {
		    // State is DOING_WORK or RETRY_REQUIRED.
		    membershipChangeState = MembershipChangeState.RETRY_REQUIRED;
                    alreadyChangingMembership();
		    return;
		}
	    }

	    boolean loop;

	    do {
		loop = false;

		restartFactories();

		synchronized (this) {
		    if (membershipChangeState == MembershipChangeState.RETRY_REQUIRED) {
			membershipChangeState = MembershipChangeState.DOING_WORK;
			// One or more notifies arrived while processing
			// this notify.  Therefore do the restart again.
			loop = true;
                        loopingForMembershipChange();
		    } else if (membershipChangeState == MembershipChangeState.DOING_WORK) {
			membershipChangeState = MembershipChangeState.IDLE;
		    } else if (membershipChangeState == MembershipChangeState.IDLE) {
                        unexpectedStateForMembershipChange();
		    }
		}
	    } while (loop);
	    
	} catch (RuntimeException e) {
            // XXX log

	    // If we get an exception we need to ensure that we do not
	    // lock out further changes.
	    synchronized (this) {
		membershipChangeState = MembershipChangeState.IDLE;
	    }
	}
    }


    @Folb
    public class WorkerThread extends Thread {
        @InfoMethod
        private void suspendRFM() { }

        @InfoMethod
        private void updateMembershipLabelInfo() { }

        @InfoMethod
        private void restartFactories() { }

        @InfoMethod
        private void resumeRFM() { }

        @Folb
        public void run() {
            try {
                suspendRFM() ;
                referenceFactoryManager.suspend();

                // Requests have drained so update label.
                // IMPORTANT: do not update label until requests
                // have drained.  Otherwise responses will compare
                // against wrong label.
                updateMembershipLabelInfo();
                updateMembershipLabel();

                restartFactories();
                referenceFactoryManager.restartFactories();
            } finally {
                resumeRFM();
                referenceFactoryManager.resume();
            }
        }
    }

    @InfoMethod
    private void waitingForWorkerTermination() { }

    @Folb
    private void restartFactories() {
	//
	// REVISIT
	//
	// restart gets exception since a remote call is coming
	// in on a non-ReferenceFactory POA.  The ORB does not
	// discriminate the granularity of restart.
	// See ORBImpl.isDuringDispatch
	//
	// Workaround by using a different thread.
	//
	// Note: this is only a problem in the test because
	// the test client sends an "add" message that
	// is serviced by a server worker thread that calls
	// membershipChange.  This method calls restartFactories
	// that calls destory POA that calls isDuringDispatch.
	// isDuringDispatch uses a thread local to determine
	// it is a  dispatch.  Using another thread fools 
	// isDuringDispatch into letting this chain proceed.
	//
	
	final ReferenceFactoryManager rfm = referenceFactoryManager;

	Thread worker = new WorkerThread() ;
	
	worker.start();
	
	// Make sure the worker terminates before we continue
        waitingForWorkerTermination();
	boolean tryAgain;
	do {
	    tryAgain = false;

	    try { 
		worker.join(); 
	    } catch (InterruptedException e) { 
		// clear the interrupted status
                // XXX Is this the correct thread?
		Thread.currentThread().interrupted() ; 
		tryAgain = true; 
	    }
	} while (tryAgain);
    }

    @InfoMethod
    private void newMembershipLabel( String ml ) { }

    @Folb
    private void updateMembershipLabel() {
	UID uid = new UID();
	String hostAddress = null;
	try {
	    // REVISIT 
	    // name could match GroupInfoService's idea of instance id/name.
	    // Not necessary but easier to debug.
	    hostAddress = InetAddress.getLocalHost().getHostAddress();
	    membershipLabel = hostAddress + ":::" + uid;
            newMembershipLabel( membershipLabel );
	} catch (UnknownHostException e) {
            // XXX log
	}
    }

    ////////////////////////////////////////////////////
    //
    // ServerRequestInterceptor
    //

    @Folb
    public void receive_request_service_contexts(ServerRequestInfo ri)
    {
	initialize();
    }

    @Folb
    public void receive_request(ServerRequestInfo ri)
    {
	initialize();
    }

    public void send_reply(ServerRequestInfo ri)
    {
	send_star(".send_reply", ri);
    }

    public void send_exception(ServerRequestInfo ri)
    {
	send_star(".send_exception", ri);
    }

    public void send_other(ServerRequestInfo ri)
    {
	send_star(".send_other", ri);
    }

    @InfoMethod
    private void rfmIsHolding() { }

    @InfoMethod
    private void notManagedByReferenceFactory( String[] adapterName ) { }

    @InfoMethod 
    private void membershipLabelsEqual() { }
    
    @InfoMethod 
    private void membershipLabelsNotEqual() { }
    
    @InfoMethod 
    private void membershipLabelsNotPresent() { }
   
    @InfoMethod
    private void sendingUpdatedIOR( String[] adapterName ) { }

    /**
     * If the request membership label is out-of-date or missing
     * then return an updated IOR.
     */
    @Folb
    private void send_star(String point, ServerRequestInfo ri)
    {
	String[] adapterName = null;
	try {
	    adapterName = ri.adapter_name();

	    if (referenceFactoryManager.getState().value() == 
		org.omg.PortableServer.POAManagerPackage.State._HOLDING) {
                rfmIsHolding();
		return;
	    }

	    ReferenceFactory referenceFactory = 
		referenceFactoryManager.find(adapterName);

	    // Only handle RefenceFactory adapters.
	    if (referenceFactory == null) {
                notManagedByReferenceFactory( adapterName ) ;
		return;
	    }

	    // Handle membership label from request.
	    String requestMembershipLabel = null;
	    try {
		ServiceContext sc = ri.get_request_service_context(
                    ORBConstants.FOLB_MEMBERSHIP_LABEL_SERVICE_CONTEXT_ID);
		// REVISIT - internationalization
		if (sc != null) {
		    byte[] data = sc.context_data;
		    requestMembershipLabel = new String(data);

		    if (membershipLabel.equals(requestMembershipLabel)) {
                        membershipLabelsEqual();
			return;
		    }
                    membershipLabelsNotEqual();
		}
	    } catch (BAD_PARAM e) {
                membershipLabelsNotPresent();
		// REVISIT: CHECK: if not our ORB then return.  --
	    }

	    // Send IOR UPDATE
	    //
	    // At this point either the labels do not match
	    // or our ORB has sent a request without a label (e.g., bootstrap).
	    // Therefore send an updated IOR.
            sendingUpdatedIOR( adapterName ) ;
	    
	    byte[] objectId = ri.object_id();
	    org.omg.CORBA.Object ref = 
		referenceFactory.createReference(objectId);
	    Any any = orb.create_any();
	    // ForwardRequest is used for convenience.
	    // This code has nothing to do with PortableInterceptor.
	    ForwardRequest fr = new ForwardRequest(ref);
	    ForwardRequestHelper.insert(any, fr);
	    byte[] data = null;
	    try {
		data = codec.encode_value(any);
	    } catch (InvalidTypeForEncoding e) {
                // XXX log me
	    }
	    ServiceContext sc = new ServiceContext(
                ORBConstants.FOLB_IOR_UPDATE_SERVICE_CONTEXT_ID, data);
	    ri.add_reply_service_context(sc, false);
	} catch (RuntimeException e) {
            // XXX log
	} finally {
            // XXX log
	}
    }

    ////////////////////////////////////////////////////
    //
    // ORBInitializer
    //

    public void pre_init(ORBInitInfo info) 
    {
    }

    @Folb
    public void post_init(ORBInitInfo info) {
	try {
	    info.add_ior_interceptor(this);
	    info.add_server_request_interceptor(this);
	} catch (Exception e) {
            // XXX log
	}
    }

    ////////////////////////////////////////////////////
    //
    // ORBConfigurator
    //

    @Folb
    public void configure(DataCollector collector, ORB orb) 
    {
	this.orb = orb;

	// Setup for IOR and ServerRequest Interceptors
	orb.getORBData().addORBInitializer(this);
    }

    ////////////////////////////////////////////////////
    //
    // Implementation
    //

    private void dprint(String msg)
    {
	ORBUtility.dprint("ServerGroupManager", msg);
    }

    private void dprint(String msg, String[] x)
    {
        dprint(msg + " " + ORBUtility.formatStringArray(x));
    }
}

// End of file.

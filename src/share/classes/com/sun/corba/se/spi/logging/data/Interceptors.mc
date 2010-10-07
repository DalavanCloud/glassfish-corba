;  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
;  
;  Copyright (c) 1997-2010 Oracle and/or its affiliates. All rights reserved.
;  
;  The contents of this file are subject to the terms of either the GNU
;  General Public License Version 2 only ("GPL") or the Common Development
;  and Distribution License("CDDL") (collectively, the "License").  You
;  may not use this file except in compliance with the License.  You can
;  obtain a copy of the License at
;  https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
;  or packager/legal/LICENSE.txt.  See the License for the specific
;  language governing permissions and limitations under the License.
;  
;  When distributing the software, include this License Header Notice in each
;  file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
;  
;  GPL Classpath Exception:
;  Oracle designates this particular file as subject to the "Classpath"
;  exception as provided by Oracle in the GPL Version 2 section of the License
;  file that accompanied this code.
;  
;  Modifications:
;  If applicable, add the following below the License Header, with the fields
;  enclosed by brackets [] replaced by your own identifying information:
;  "Portions Copyright [year] [name of copyright owner]"
;  
;  Contributor(s):
;  If you wish your version of this file to be governed by only the CDDL or
;  only the GPL Version 2, indicate your decision by adding "[Contributor]
;  elects to include this software in this distribution under the [CDDL or GPL
;  Version 2] license."  If you don't indicate a single choice of license, a
;  recipient has the option to distribute your version of this file under
;  either the CDDL, the GPL Version 2 or to extend the choice of license to
;  its licensees as provided above.  However, if you add GPL Version 2 code
;  and therefore, elected the GPL Version 2 license, then the option applies
;  only if the new code is made subject to such option by the copyright
;  holder.

("com.sun.corba.se.impl.logging" "InterceptorsSystemException" INTERCEPTORS
    (
	(BAD_PARAM
	    (TYPE_OUT_OF_RANGE	     1 WARNING "Interceptor type {0} is out of range")
	    (NAME_NULL		     2 WARNING "Interceptor's name is null: use empty string for anonymous interceptors"))
	(BAD_INV_ORDER 
	    (RIR_INVALID_PRE_INIT    1 WARNING "resolve_initial_reference is invalid during pre_init")
	    (BAD_STATE1		     2 WARNING "Expected state {0}, but current state is {1}")
	    (BAD_STATE2		     3 WARNING "Expected state {0} or {1}, but current state is {2}"))
	(COMM_FAILURE 
	    (IOEXCEPTION_DURING_CANCEL_REQUEST 1 WARNING "IOException during cancel request"))
	(INTERNAL 
	    (EXCEPTION_WAS_NULL      1 WARNING "Exception was null")
	    (OBJECT_HAS_NO_DELEGATE  2 WARNING "Object has no delegate")
	    (DELEGATE_NOT_CLIENTSUB  3 WARNING "Delegate was not a ClientRequestDispatcher")
	    (OBJECT_NOT_OBJECTIMPL   4 WARNING "Object is not an ObjectImpl")
	    (EXCEPTION_INVALID       5 WARNING "Assertion failed: Interceptor set exception to UserException or ApplicationException")
	    (REPLY_STATUS_NOT_INIT    6 WARNING "Assertion failed: Reply status is initialized but not SYSTEM_EXCEPTION or LOCATION_FORWARD")
	    (EXCEPTION_IN_ARGUMENTS  7 WARNING "Exception in arguments")
	    (EXCEPTION_IN_EXCEPTIONS 8 WARNING "Exception in exceptions")
	    (EXCEPTION_IN_CONTEXTS   9 WARNING "Exception in contexts")
	    (EXCEPTION_WAS_NULL_2    10 WARNING "Another exception was null")
	    (SERVANT_INVALID         11 WARNING "Servant invalid")
	    (CANT_POP_ONLY_PICURRENT 12 WARNING "Can't pop only PICurrent")
	    (CANT_POP_ONLY_CURRENT_2 13 WARNING "Can't pop another PICurrent")
	    (PI_DSI_RESULT_IS_NULL   14 WARNING "DSI result is null")
	    (PI_DII_RESULT_IS_NULL   15 WARNING "DII result is null")
	    (EXCEPTION_UNAVAILABLE   16 WARNING "Exception is unavailable")
	    (CLIENT_INFO_STACK_NULL  17 WARNING "Assertion failed: client request info stack is null")
	    (SERVER_INFO_STACK_NULL  18 WARNING "Assertion failed: Server request info stack is null")
	    (MARK_AND_RESET_FAILED   19 WARNING "Mark and reset failed")
	    (SLOT_TABLE_INVARIANT    20 WARNING "currentIndex > tableContainer.size(): {0} > {1}")
	    (INTERCEPTOR_LIST_LOCKED 21 WARNING "InterceptorList is locked")
	    (SORT_SIZE_MISMATCH      22 WARNING "Invariant: sorted size + unsorted size == total size was violated")
            (IGNORED_EXCEPTION_IN_ESTABLISH_COMPONENTS
             23 FINE "Ignored exception in establish_components method for ObjectAdapter {0} (as per specification)")
            (EXCEPTION_IN_COMPONENTS_ESTABLISHED
             24 FINE "Exception in components_established method for ObjectAdapter {0}")
            (IGNORED_EXCEPTION_IN_ADAPTER_MANAGER_STATE_CHANGED
             25 FINE "Ignored exception in adapter_manager_state_changed method for managerId {0} and newState {1} (as per specification)")
            (IGNORED_EXCEPTION_IN_ADAPTER_STATE_CHANGED
             26 FINE "Ignored exception in adapter_state_changed method for templates {0} and newState {1} (as per specification)")
            )
	(NO_IMPLEMENT 
	    (PI_ORB_NOT_POLICY_BASED 1 WARNING "Policies not implemented"))
	(OBJECT_NOT_EXIST
	    (ORBINITINFO_INVALID     1 FINE "ORBInitInfo object is only valid during ORB_init"))
	(UNKNOWN
	    (UNKNOWN_REQUEST_INVOKE  
	     1 FINE "Unknown request invocation error"))
	))

;;; End of file.

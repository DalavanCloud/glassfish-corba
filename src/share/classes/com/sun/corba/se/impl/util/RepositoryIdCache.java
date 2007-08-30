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

package com.sun.corba.se.impl.util;

import java.util.Stack;
import java.util.Hashtable;
import java.util.EmptyStackException;
import java.util.Enumeration;

// Really limited pool - in this case just creating several at a time...
class RepositoryIdPool extends Stack {
    
    private static int MAX_CACHE_SIZE = 4;
    private RepositoryIdCache cache;
    
    public final synchronized RepositoryId popId() {

        try {
            return (RepositoryId)super.pop();
        }
        catch(EmptyStackException e) {
            increasePool(5);
            return (RepositoryId)super.pop();
        }

    }

    // Pool management
    final void increasePool(int size) {
        //if (cache.size() <= MAX_CACHE_SIZE)
	for (int i = size; i > 0; i--)
	    push(new RepositoryId());
        /*
	  // _REVISIT_ This will not work w/out either thread tracing or weak references.  I am
	  // betting that thread tracing almost completely negates benefit of reuse.  Until either
	  // 1.2 only inclusion or proof to the contrary, I'll leave it this way...
	  else {
	  int numToReclaim = cache.size() / 2;
	  Enumeration keys = cache.keys();
	  Enumeration elements = cache.elements();
	  for (int i = numToReclaim; i > 0; i--) {
	  Object key = keys.nextElement();
	  Object element = elements.nextElement();
                
	  push(element);
	  cache.remove(key);
	  }
	  }
        */
    }
    
    final void setCaches(RepositoryIdCache cache) {
        this.cache = cache;  
    }

}

public class RepositoryIdCache extends Hashtable {

    private RepositoryIdPool pool = new RepositoryIdPool();
    
    public RepositoryIdCache() {
        pool.setCaches(this);    
    }
    
    public final synchronized RepositoryId getId(String key) {
        RepositoryId repId = (RepositoryId)super.get(key);

        if (repId != null)
            return repId;
        else {
            //repId = pool.popId().init(key);
	    repId = new RepositoryId(key);
            put(key, repId);
            return repId;
        }

    }
}



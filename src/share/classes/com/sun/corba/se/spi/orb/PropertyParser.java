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
package com.sun.corba.se.spi.orb ;

import java.util.List ;
import java.util.LinkedList ;
import java.util.Map ;
import java.util.HashMap ;
import java.util.Iterator ;
import java.util.Properties ;

import com.sun.corba.se.impl.orb.ParserAction ;
import com.sun.corba.se.impl.orb.ParserActionFactory ;

public class PropertyParser {
    private List actions ;

    public PropertyParser( ) 
    {
	actions = new LinkedList() ;
    }

    public PropertyParser add( String propName, 
	Operation action, String fieldName )
    {
	actions.add( ParserActionFactory.makeNormalAction( propName, 
	    action, fieldName ) ) ;
	return this ;
    }

    public PropertyParser addPrefix( String propName, 
	Operation action, String fieldName, Class componentType )
    {
	actions.add( ParserActionFactory.makePrefixAction( propName, 
	    action, fieldName, componentType ) ) ;
	return this ;
    }

    /** Return a map from field name to value.
    */
    public Map parse( Properties props )
    {
	Map map = new HashMap() ;
	Iterator iter = actions.iterator() ;
	while (iter.hasNext()) {
	    ParserAction act = (ParserAction)(iter.next()) ;
    
	    Object result = act.apply( props ) ; 
		
	    // A null result means that the property was not set for
	    // this action, so do not override the default value in this case.
	    if (result != null)
		map.put( act.getFieldName(), result ) ;
	}

	return map ;
    }

    public Iterator iterator() 
    {
	return actions.iterator() ;
    }
}

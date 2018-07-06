/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2018 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

package com.sun.org.omg.CORBA;


/**
* com/sun/org/omg/CORBA/IDLTypeHelper.java
* Generated by the IDL-to-Java compiler (portable), version "3.0"
* from ir.idl
* Thursday, May 6, 1999 1:51:45 AM PDT
*/

// This file has been _CHANGED_

public final class IDLTypeHelper
{
    private static String  _id = "IDL:omg.org/CORBA/IDLType:1.0";

    public IDLTypeHelper()
    {
    }

    // _CHANGED_
    //public static void insert (org.omg.CORBA.Any a, com.sun.org.omg.CORBA.IDLType that)
    public static void insert (org.omg.CORBA.Any a, org.omg.CORBA.IDLType that)
    {
        org.omg.CORBA.portable.OutputStream out = a.create_output_stream ();
        a.type (type ());
        write (out, that);
        a.read_value (out.create_input_stream (), type ());
    }

    // _CHANGED_
    //public static com.sun.org.omg.CORBA.IDLType extract (org.omg.CORBA.Any a)
    public static org.omg.CORBA.IDLType extract (org.omg.CORBA.Any a)
    {
        return read (a.create_input_stream ());
    }

    private static org.omg.CORBA.TypeCode __typeCode = null;
    synchronized public static org.omg.CORBA.TypeCode type ()
    {
        if (__typeCode == null)
            {
                __typeCode = org.omg.CORBA.ORB.init ().create_interface_tc (com.sun.org.omg.CORBA.IDLTypeHelper.id (), "IDLType");
            }
        return __typeCode;
    }

    public static String id ()
    {
        return _id;
    }

    // _CHANGED_
    //public static com.sun.org.omg.CORBA.IDLType read (org.omg.CORBA.portable.InputStream istream)
    public static org.omg.CORBA.IDLType read (org.omg.CORBA.portable.InputStream istream)
    {
        return narrow (istream.read_Object (org.glassfish.corba.org.omg.CORBA._IDLTypeStub.class));
    }

    // _CHANGED_
    //public static void write (org.omg.CORBA.portable.OutputStream ostream, com.sun.org.omg.CORBA.IDLType value)
    public static void write (org.omg.CORBA.portable.OutputStream ostream, org.omg.CORBA.IDLType value)
    {
        ostream.write_Object ((org.omg.CORBA.Object) value);
    }

    // _CHANGED_
    //public static com.sun.org.omg.CORBA.IDLType narrow (org.omg.CORBA.Object obj)
    public static org.omg.CORBA.IDLType narrow (org.omg.CORBA.Object obj)
    {
        if (obj == null)
            return null;
        // _CHANGED_
        //else if (obj instanceof com.sun.org.omg.CORBA.IDLType)
        else if (obj instanceof org.omg.CORBA.IDLType)
            // _CHANGED_
            //return (com.sun.org.omg.CORBA.IDLType)obj;
            return (org.omg.CORBA.IDLType)obj;
        else if (!obj._is_a (id ()))
            throw new org.omg.CORBA.BAD_PARAM ();
        else
            {
                org.omg.CORBA.portable.Delegate delegate = ((org.omg.CORBA.portable.ObjectImpl)obj)._get_delegate ();
                return new org.glassfish.corba.org.omg.CORBA._IDLTypeStub(delegate);
            }
    }

}
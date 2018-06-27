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

package org.omg.PortableServer;

/**
* org/omg/PortableServer/POAHelper.java .
* Generated by the IDL-to-Java compiler (portable), version "3.1"
* from ../../../../src/share/classes/org/omg/PortableServer/poa.idl
* Tuesday, October 23, 2001 1:16:58 PM PDT
*/


/**
 * A POA object manages the implementation of a
 * collection of objects. The POA supports a name space
 * for the objects, which are identified by Object Ids.
 * A POA also provides a name space for POAs. A POA is
 * created as a child of an existing POA, which forms a
 * hierarchy starting with the root POA. A POA object
 * must not be exported to other processes, or
 * externalized with ORB::object_to_string.
 */
abstract public class POAHelper
{
    private static String  _id = "IDL:omg.org/PortableServer/POA:2.3";

    public static void insert (org.omg.CORBA.Any a, 
        org.omg.PortableServer.POA that)
    {
        org.omg.CORBA.portable.OutputStream out = a.create_output_stream ();
        a.type (type ());
        write (out, that);
        a.read_value (out.create_input_stream (), type ());
    }

    public static org.omg.PortableServer.POA extract (org.omg.CORBA.Any a)
    {
        return read (a.create_input_stream ());
    }

    private static org.omg.CORBA.TypeCode __typeCode = null;
    synchronized public static org.omg.CORBA.TypeCode type ()
    {
        if (__typeCode == null)
        {
            __typeCode = org.omg.CORBA.ORB.init ().create_interface_tc (org.omg.PortableServer.POAHelper.id (), "POA");
        }
        return __typeCode;
    }

    public static String id ()
    {
        return _id;
    }

    public static org.omg.PortableServer.POA read (
        org.omg.CORBA.portable.InputStream istream)
    {
        throw new org.omg.CORBA.MARSHAL ();
    }

    public static void write (org.omg.CORBA.portable.OutputStream ostream, 
       org.omg.PortableServer.POA value)
    {
        throw new org.omg.CORBA.MARSHAL ();
    }

    public static org.omg.PortableServer.POA narrow (org.omg.CORBA.Object obj)
    {
       if (obj == null)
           return null;
       else if (obj instanceof org.omg.PortableServer.POA)
           return (org.omg.PortableServer.POA)obj;
       else if (!obj._is_a (id ()))
          throw new org.omg.CORBA.BAD_PARAM ();
       return null;
    }
}


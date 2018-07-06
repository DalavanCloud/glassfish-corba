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

package fake.omega;


/**
* fake/omega/JulietHelper.java
* Generated by the IBM IDL-to-Java compiler (portable), version "3.0"
* from d:/java/java/idl/omega/Juliet.idl
* 01 May 1999 14:09:29 o'clock GMT+00:00
*/

public final class JulietHelper implements com.sun.org.omg.CORBA.portable.ValueHelper
{
    private static String  _id = "IDL:phoney.pfix/omega/Juliet:1.0";

    private static JulietHelper helper = new JulietHelper ();

    private static String[] _truncatable_ids = {
        _id   };

    public JulietHelper()
    {
    }

    public static void insert (org.omg.CORBA.Any a, fake.omega.Juliet that)
    {
        org.omg.CORBA.portable.OutputStream out = a.create_output_stream ();
        a.type (type ());
        write (out, that);
        a.read_value (out.create_input_stream (), type ());
    }

    public static fake.omega.Juliet extract (org.omg.CORBA.Any a)
    {
        return read (a.create_input_stream ());
    }

    private static org.omg.CORBA.TypeCode __typeCode = null;
    private static boolean __active = false;
    synchronized public static org.omg.CORBA.TypeCode type ()
    {
        if (__typeCode == null)
            {
                synchronized (org.omg.CORBA.TypeCode.class)
                    {
                        if (__typeCode == null)
                            {
                                if (__active)
                                    {
                                        return org.omg.CORBA.ORB.init().create_recursive_tc ( _id );
                                    }
                                __active = true;
                                __typeCode = org.omg.CORBA.ORB.init ().get_primitive_tc (org.omg.CORBA.TCKind.tk_long);
                                __typeCode = org.omg.CORBA.ORB.init ().create_value_box_tc (_id, "Juliet", __typeCode);
                                __active = false;
                            }
                    }
            }
        return __typeCode;
    }

    public static String id ()
    {
        return _id;
    }

    public static fake.omega.Juliet read (org.omg.CORBA.portable.InputStream istream)
    {
        return (fake.omega.Juliet) ((org.omg.CORBA_2_3.portable.InputStream) istream).read_value (get_instance());
    }

    public java.io.Serializable read_value (org.omg.CORBA.portable.InputStream istream)
    {
        int tmp = istream.read_long ();
        return new fake.omega.Juliet (tmp);
    }

    public static void write (org.omg.CORBA.portable.OutputStream ostream, fake.omega.Juliet value)
    {
        ((org.omg.CORBA_2_3.portable.OutputStream) ostream).write_value (value, get_instance());
    }

    public void write_value (org.omg.CORBA.portable.OutputStream ostream, java.io.Serializable obj)
    {
        fake.omega.Juliet value  = (fake.omega.Juliet) obj;
        ostream.write_long (value.value);
    }

    public String get_id ()
    {
        return _id;
    }

    public org.omg.CORBA.TypeCode get_type ()
    {
        return type ();
    }

    public static com.sun.org.omg.CORBA.portable.ValueHelper get_instance ()
    {
        return helper;
    }

    public Class get_class ()
    {
        return fake.omega.Juliet.class;
    }

    public String[] get_truncatable_base_ids ()
    {
        return _truncatable_ids;
    }

}
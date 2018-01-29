package org.glassfish.idlj;


/**
* org/glassfish/idlj/CORBAUnionHelper.java .
* Generated by the IDL-to-Java compiler (portable), version "4.1"
* from /Users/rgold/projects/glassfish/glassfish-corba/idlj/src/main/idl/org/glassfish/idlj/CORBAServerTest.idl
* Monday, January 29, 2018 11:19:41 AM EST
*/

abstract public class CORBAUnionHelper
{
  private static String  _id = "IDL:org/glassfish/idlj/CORBAUnion/CORBAUnion:1.0";

  public static void insert (org.omg.CORBA.Any a, org.glassfish.idlj.CORBAUnion that)
  {
    org.omg.CORBA.portable.OutputStream out = a.create_output_stream ();
    a.type (type ());
    write (out, that);
    a.read_value (out.create_input_stream (), type ());
  }

  public static org.glassfish.idlj.CORBAUnion extract (org.omg.CORBA.Any a)
  {
    return read (a.create_input_stream ());
  }

  private static org.omg.CORBA.TypeCode __typeCode = null;
  synchronized public static org.omg.CORBA.TypeCode type ()
  {
    if (__typeCode == null)
    {
      org.omg.CORBA.TypeCode _disTypeCode0;
      _disTypeCode0 = org.omg.CORBA.ORB.init ().get_primitive_tc (org.omg.CORBA.TCKind.tk_long);
      org.omg.CORBA.UnionMember[] _members0 = new org.omg.CORBA.UnionMember [6];
      org.omg.CORBA.TypeCode _tcOf_members0;
      org.omg.CORBA.Any _anyOf_members0;

      // Branch for b (case label 0)
      _anyOf_members0 = org.omg.CORBA.ORB.init ().create_any ();
      _anyOf_members0.insert_long ((int)0);
      _tcOf_members0 = org.omg.CORBA.ORB.init ().get_primitive_tc (org.omg.CORBA.TCKind.tk_boolean);
      _members0[0] = new org.omg.CORBA.UnionMember (
        "b",
        _anyOf_members0,
        _tcOf_members0,
        null);

      // Branch for w (case label 1)
      _anyOf_members0 = org.omg.CORBA.ORB.init ().create_any ();
      _anyOf_members0.insert_long ((int)1);
      _tcOf_members0 = org.omg.CORBA.ORB.init ().create_wstring_tc (0);
      _members0[1] = new org.omg.CORBA.UnionMember (
        "w",
        _anyOf_members0,
        _tcOf_members0,
        null);

      // Branch for s (case label 2)
      _anyOf_members0 = org.omg.CORBA.ORB.init ().create_any ();
      _anyOf_members0.insert_long ((int)2);
      _tcOf_members0 = org.omg.CORBA.ORB.init ().create_string_tc (0);
      _members0[2] = new org.omg.CORBA.UnionMember (
        "s",
        _anyOf_members0,
        _tcOf_members0,
        null);

      // Branch for a (case label 3)
      _anyOf_members0 = org.omg.CORBA.ORB.init ().create_any ();
      _anyOf_members0.insert_long ((int)3);
      _tcOf_members0 = org.omg.CORBA.ORB.init ().get_primitive_tc (org.omg.CORBA.TCKind.tk_any);
      _members0[3] = new org.omg.CORBA.UnionMember (
        "a",
        _anyOf_members0,
        _tcOf_members0,
        null);

      // Branch for cs (case label 4)
      _anyOf_members0 = org.omg.CORBA.ORB.init ().create_any ();
      _anyOf_members0.insert_long ((int)4);
      _tcOf_members0 = org.glassfish.idlj.CORBAStructHelper.type ();
      _members0[4] = new org.omg.CORBA.UnionMember (
        "cs",
        _anyOf_members0,
        _tcOf_members0,
        null);

      // Branch for lll (Default case)
      _anyOf_members0 = org.omg.CORBA.ORB.init ().create_any ();
      _anyOf_members0.insert_octet ((byte)0); // default member label
      _tcOf_members0 = org.omg.CORBA.ORB.init ().get_primitive_tc (org.omg.CORBA.TCKind.tk_longlong);
      _members0[5] = new org.omg.CORBA.UnionMember (
        "lll",
        _anyOf_members0,
        _tcOf_members0,
        null);
      __typeCode = org.omg.CORBA.ORB.init ().create_union_tc (org.glassfish.idlj.CORBAUnionHelper.id (), "CORBAUnion", _disTypeCode0, _members0);
    }
    return __typeCode;
  }

  public static String id ()
  {
    return _id;
  }

  public static org.glassfish.idlj.CORBAUnion read (org.omg.CORBA.portable.InputStream istream)
  {
    org.glassfish.idlj.CORBAUnion value = new org.glassfish.idlj.CORBAUnion ();
    int _dis0 = (int)0;
    _dis0 = istream.read_long ();
    switch (_dis0)
    {
      case 0:
        boolean _b = false;
        _b = istream.read_boolean ();
        value.b (_b);
        break;
      case 1:
        String _w = null;
        _w = istream.read_wstring ();
        value.w (_w);
        break;
      case 2:
        String _s = null;
        _s = istream.read_string ();
        value.s (_s);
        break;
      case 3:
        org.omg.CORBA.Any _a = null;
        _a = istream.read_any ();
        value.a (_a);
        break;
      case 4:
        org.glassfish.idlj.CORBAStruct _cs = null;
        _cs = org.glassfish.idlj.CORBAStructHelper.read (istream);
        value.cs (_cs);
        break;
      default:
        long _lll = (long)0;
        _lll = istream.read_longlong ();
        value.lll (_dis0, _lll);
        break;
    }
    return value;
  }

  public static void write (org.omg.CORBA.portable.OutputStream ostream, org.glassfish.idlj.CORBAUnion value)
  {
    ostream.write_long (value.discriminator ());
    switch (value.discriminator ())
    {
      case 0:
        ostream.write_boolean (value.b ());
        break;
      case 1:
        ostream.write_wstring (value.w ());
        break;
      case 2:
        ostream.write_string (value.s ());
        break;
      case 3:
        ostream.write_any (value.a ());
        break;
      case 4:
        org.glassfish.idlj.CORBAStructHelper.write (ostream, value.cs ());
        break;
      default:
        ostream.write_longlong (value.lll ());
        break;
    }
  }

}
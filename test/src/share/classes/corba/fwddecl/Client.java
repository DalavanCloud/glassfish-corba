/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2000-2007 Sun Microsystems, Inc. All rights reserved.
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
package corba.fwddecl;

import org.omg.CORBA.Any;
import org.omg.CORBA_2_3.portable.OutputStream ;
import org.omg.CORBA_2_3.portable.InputStream ;

import java.util.Properties ;

import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.orbutil.misc.ObjectUtility ;

import com.sun.corba.se.impl.encoding.CDRInputStream ;
import com.sun.corba.se.impl.encoding.CDROutputStream ;
import com.sun.corba.se.impl.encoding.EncapsInputStream ;
import com.sun.corba.se.impl.encoding.EncapsOutputStream ;

public class Client
{
    private ORB orb;

    private OutputStream newOutputStream()
    {
	return new EncapsOutputStream( orb ) ;
    }

    private CDRInputStream makeInputStream( OutputStream os ) 
    {
	byte[] bytes = getBytes( os ) ;
	return makeInputStream( bytes ) ;
    }

    private byte[] getBytes( OutputStream os ) 
    {
	CDROutputStream cos = (CDROutputStream)os ;
	byte[] bytes = cos.toByteArray() ;
	return bytes ;
    }

    private CDRInputStream makeInputStream( byte[] data ) 
    {
	return new EncapsInputStream( orb, data, data.length ) ;
    }

    public void verifyNewFoo() {
	try {

	    Any any = orb.create_any();

	    NewFoo n1 = new NewFoo(1, new NewFoo[0]);
	    NewFoo n2 = new NewFoo(2, new NewFoo[0]);
	    NewFoo n3 = new NewFoo(3, new NewFoo[] {n1, n2});
	    
	    // Use insert and extract and then test equality
	    NewFooHelper.insert(any, n3);
	    NewFoo o = NewFooHelper.extract(any);
	    if (!ObjectUtility.equals(n3, o)) {
		throw new Exception("The objects are not equal");
	    }

	    // Use write and read and then test equality
	    OutputStream os = newOutputStream();
	    NewFooHelper.write(os, n3);
	    InputStream is = makeInputStream(os);
	    NewFoo o1 = NewFooHelper.read(is);
	    if (!ObjectUtility.equals(n3, o1)) {
		throw new Exception("The objects are not equal");
	    }

	    System.out.println("The test for NewFoo passed !!!");
	} catch (Exception e) {
	    System.out.println("ERROR : " + e) ;
	    e.printStackTrace(System.out);
	    System.exit(1);
	}
    }

    public void verifyBar() {
	try {
	    Any any = orb.create_any();

	    Bar b1 = new Bar();
	    b1.l_mem(12);
	    
	    Bar b2 = new Bar();
	    b2.l_mem(13);
	    
	    Bar b3 = new Bar();
	    b3.s_mem(new corba.fwddecl.BarPackage.Foo(5.0d, new Bar[]{b1}));
	    
	    Bar b4 = new Bar();
	    b4.s_mem(new corba.fwddecl.BarPackage.Foo(10.0d, new Bar[]{b3}));
	    
	    Bar b5 = new Bar();
	    b5.s_mem(new corba.fwddecl.BarPackage.Foo(15.0d, new Bar[]{b2,b4}));
	    
	    // Use insert and extract and then test equality
	    BarHelper.insert(any, b5);
	    Bar o = BarHelper.extract(any);
	    if (!ObjectUtility.equals(b5, o)) {
		throw new Exception("The objects are not equal");
	    }

	    // Use write and read and then test equality
	    OutputStream os = newOutputStream();
	    BarHelper.write(os, b5);
	    InputStream is = makeInputStream(os);
	    Bar o1 = BarHelper.read(is);
	    if (!ObjectUtility.equals(b5, o1)) {
		throw new Exception("The objects are not equal");
	    }

	    System.out.println("The test for Bar passed !!!");
	} catch (Exception e) {
	    System.out.println("ERROR : " + e) ;
	    e.printStackTrace(System.out);
	    System.exit(1);
	}

    }

    public void verifyMoreFoo() {
	try {

	    Any any = orb.create_any();

	    MoreFoo n1 = new MoreFoo(1, null, new MoreFoo[0], new MoreFoo[0][0]);
	    MoreFoo n2 = new MoreFoo(2, null, new MoreFoo[0], new MoreFoo[0][0]);
	    MoreFoo n3 = new MoreFoo(3, null, new MoreFoo[0], new MoreFoo[0][0]);
	    MoreFoo n4 = new MoreFoo(5, null, new MoreFoo[0], new MoreFoo[0][0]);
	    MoreFoo n5 = new MoreFoo(6, null, new MoreFoo[0], new MoreFoo[0][0]);
	    MoreFoo n6 = new MoreFoo(7, null, new MoreFoo[0], new MoreFoo[0][0]);

	    MoreFoo n7 = new MoreFoo(8, null, new MoreFoo[]{n1, n2},
		                     new MoreFoo[][]{{n3, n4}, {n5, n6}});
	    
	    // Use insert and extract and then test equality
	    MoreFooHelper.insert(any, n7);
	    MoreFoo o = MoreFooHelper.extract(any);
	    if (!ObjectUtility.equals(n7, o)) {
		throw new Exception("The objects are not equal");
	    }

	    // Use write and read and then test equality
	    OutputStream os = newOutputStream();
	    MoreFooHelper.write(os, n7);
	    InputStream is = makeInputStream(os);
	    MoreFoo o1 = MoreFooHelper.read(is);
	    if (!ObjectUtility.equals(n7, o1)) {
		throw new Exception("The objects are not equal");
	    }

	    System.out.println("The test for MoreFoo passed !!!");
	} catch (Exception e) {
	    System.out.println("ERROR : " + e) ;
	    e.printStackTrace(System.out);
	    System.exit(1);
	}
    }

    private void runtest() {
	verifyNewFoo();
	verifyBar();
	verifyMoreFoo();
    }

    public Client(Properties props, String args[]) {
	this.orb = (ORB)ORB.init( args, props ) ;
	runtest();
    }

    public static void main(String args[])
    {        
	try{
	    Properties props = new Properties( System.getProperties() ) ;
	    props.put( "org.omg.CORBA.ORBClass", 
		"com.sun.corba.se.impl.orb.ORBImpl" ) ;
	    new Client( props, args) ;
        } catch (Exception e) {
            System.out.println("ERROR : " + e) ;
            e.printStackTrace(System.out);
            System.exit (1);
        }
    }
}

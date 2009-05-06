/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2005-2007 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.corba.se.impl.presentation.rmi.codegen;

import java.io.PrintStream ;

import java.lang.reflect.Method ;

import java.security.ProtectionDomain ;

import java.util.Properties ;
import java.util.List ;
import java.util.ArrayList ;

import com.sun.corba.se.spi.orbutil.generic.Pair ;

import com.sun.corba.se.spi.orbutil.codegen.Utility ;
import com.sun.corba.se.spi.orbutil.codegen.Type ;
import com.sun.corba.se.spi.orbutil.codegen.Expression;
import com.sun.corba.se.spi.orbutil.codegen.Primitives ;
import com.sun.corba.se.spi.orbutil.codegen.MethodInfo ;
import com.sun.corba.se.spi.orbutil.codegen.Variable ;

import static java.lang.reflect.Modifier.* ;


import static com.sun.corba.se.spi.orbutil.codegen.Wrapper.* ;

/** Generate a proxy with a specified base class.  
 */
public class CodegenProxyCreator {
    private String className ;
    private Type superClass ;
    private List<Type> interfaces ;
    private List<MethodInfo> methods ;

    private static final Properties debugProps = new Properties() ;
    private static final Properties emptyProps = new Properties() ;

    static {
	debugProps.setProperty( DUMP_AFTER_SETUP_VISITOR, "true" ) ;
	debugProps.setProperty( TRACE_BYTE_CODE_GENERATION, "true" ) ;
	debugProps.setProperty( USE_ASM_VERIFIER, "true" ) ;
    }

    public CodegenProxyCreator( String className, String superClassName,
	Class[] interfaces, Method[] methods ) {

	this.className = className ;
	this.superClass = Type._class( superClassName ) ;

	this.interfaces = new ArrayList<Type>() ;
	for (Class cls : interfaces) {
	    this.interfaces.add( Type.type( cls ) ) ;
	}

	this.methods = new ArrayList<MethodInfo>() ;
	for (Method method : methods) {
	    this.methods.add( Utility.getMethodInfo( method ) ) ;
	}
    }

    /** Construct a generator for a proxy class 
     * that implements the given interfaces and extends superClass.
     * superClass must satisfy the following requirements:
     * <ol>
     * <li>It must have an accessible no args constructor</li>
     * <li>It must have a method satisfying the signature
     * <code>    Object invoke( int methodNumber, Object[] args ) throws Throwable
     * </code>
     * </li>
     * <li>The invoke method described above must be accessible
     * to the generated class (generally either public or 
     * protected.</li>
     * </ol>
     * <p>
     * Each method in methods is implemented by a method that:
     * <ol>
     * <li>Creates an array sized to hold the args</li>
     * <li>Wraps args of primitive type in the appropriate wrapper.</li>
     * <li>Copies each arg or wrapper arg into the array.</li>
     * <li>Calls invoke with a method number corresponding to the
     * index of the method in methods.  Note that the invoke implementation
     * must use the same method array to figure out which method has been
     * invoked.</li>
     * <li>Return the result (if any), extracting values from wrappers
     * as needed to handle a return value of a primitive type.</li>
     * </ol>
     * <p>
     * Note that the generated methods ignore exceptions.
     * It is assumed that the invoke method may throw any
     * desired exception.
     * @param className the name of the generated class
     * @param superClassName the name of the class extends by the generated class
     * @param interfaces the interfaces implemented by the generated class
     * @param methods the methods that the generated class implements
     */
    public Class<?> create( ProtectionDomain pd, ClassLoader cl,
	boolean debug, PrintStream ps ) {

	Pair<String,String> nm = splitClassName( className ) ;

	_clear() ;
	_setClassLoader( cl ) ;
	_package( nm.first() ) ;
	_class( PUBLIC, nm.second(), superClass, interfaces ) ;

	_constructor( PUBLIC ) ;
	_body() ;
	    _expr(_super());
	_end() ;

	_method( PRIVATE, _Object(), "writeReplace" ) ;
	_body() ;
	    _return(_call(_this(), "selfAsBaseClass" )) ;
	_end() ;

	int ctr=0 ;
	for (MethodInfo method : methods) 
	    createMethod( ctr++, method ) ;
    
	_end() ; // of _class

	return _generate( cl, pd, debug ? debugProps : emptyProps, ps ) ;
    }

    private static final Type objectArrayType = Type._array(_Object()) ;

    private static void createMethod( int mnum, MethodInfo method ) {
	Type rtype = method.returnType() ;
	_method( method.modifiers() & ~ABSTRACT, rtype, method.name()) ;
	
	List<Expression> args = new ArrayList<Expression>() ;
	for (Variable var : method.arguments() ) 
	    args.add( _arg( var.type(), var.ident() ) ) ;

	_body() ;
	    List<Expression> wrappedArgs = new ArrayList<Expression>() ;
	    for (Expression arg : args) {
		wrappedArgs.add( Primitives.wrap( arg ) ) ;
	    }
	    
	    Expression invokeArgs = _define( objectArrayType, "args",
		_new_array_init( _Object(), wrappedArgs ) ) ;

	    // create expression to call the invoke method
	    Expression invokeExpression = _call(
		_this(), "invoke", _const(mnum), invokeArgs ) ;

	    // return result if non-void
	    if (rtype == _void()) {
		_expr( invokeExpression ) ;
		_return() ;
	    } else {
		Expression resultExpr = _define( _Object(), "result", invokeExpression ) ;

		if (rtype != _Object()) {
		    if (rtype.isPrimitive()) {
			// Must cast resultExpr to expected type, or unwrap won't work!
			Type ctype = Primitives.getWrapperTypeForPrimitive( rtype ) ;
			Expression cexpr = _cast( ctype, resultExpr ) ;
			_return( Primitives.unwrap( cexpr ) ) ;
		    } else {
			_return(_cast(rtype, resultExpr )) ;
		    }
		} else {
		    _return( resultExpr ) ;
		}
	    }
	_end() ; // of method
    }
}

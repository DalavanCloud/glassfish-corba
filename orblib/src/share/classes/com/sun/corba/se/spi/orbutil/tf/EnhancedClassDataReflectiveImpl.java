/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2002-2010 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.corba.se.spi.orbutil.tf ;

import com.sun.corba.se.spi.orbutil.tf.annotation.MethodMonitorGroup;
import com.sun.corba.se.spi.orbutil.newtimer.TimingPointType;
import com.sun.corba.se.spi.orbutil.tf.annotation.InfoMethod;
import com.sun.corba.se.spi.orbutil.tf.annotation.TracingName;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.glassfish.gmbal.Description;
import org.objectweb.asm.Type;

public class EnhancedClassDataReflectiveImpl extends EnhancedClassDataBase {
    private final Class<?> currentClass ;

    private boolean isMMAnnotation( Annotation an ) {
	return an.annotationType().isAnnotationPresent(
            MethodMonitorGroup.class ) ;
    }

    // Get Set<String> for MM annotations present on class
    private void processClassAnnotations() {
        final Annotation[] classAnnotations =
            currentClass.getAnnotations() ;
        if (classAnnotations != null) {
            for (Annotation an : classAnnotations) {
                final String aname = Type.getInternalName( an.annotationType() ) ;
		if (isMMAnnotation(an)) {
                    annoNamesForClass.add( aname ) ;
		}
	    }

	    List<String> acnames = new ArrayList( annoNamesForClass ) ;
	    Collections.sort( acnames ) ;

	    int ctr=0 ;
	    for (String aname : acnames ) {
	        annoToHolderName.put( aname, "__$mm$__" + ctr ) ;
		ctr++ ;
            }

            if (util.getDebug()) {
                util.msg( "Enhancing class " + currentClass.getName() ) ;
                util.msg( "\tannoNamesForClass = " + annoNamesForClass ) ;
                util.msg( "\tannoToHolderName = " + annoToHolderName ) ;
            }
        }
    }

    // Scan methods:
    //    - Build List<String> to map names of MM annotated methods to ints
    //      validate: such methods must have exactly 1 MM annotation that
    //          is in annoNamesForClass, or must be @InfoMethod annotated.
    //      Note: this must check for a @TracingName annotation.
    //    - In parallel with names of methods, construct descriptions and
    //      TimingPointTypes arrays.
    //    - Build Set<String> of all InfoMethod annotated methods.
    //      validate: such methods must be private, return void, and have
    //          an empty body.  May NOT have MM annotation.
    private void scanMethods() {
        final Method[] methods = currentClass.getDeclaredMethods() ;
        Map<String,String> mmnToDescriptions =
            new HashMap<String,String>() ;

        Map<String,TimingPointType> mmnToTPT =
            new HashMap<String,TimingPointType>() ;

        Map<String,String> mmnToAnnotationName = 
            new HashMap<String,String>() ;

        for (Method mn : methods) {
            final String mname = mn.getName() ;
            final String mdesc = util.getFullMethodDescriptor( mn ) ;

            String monitoredMethodMMAnno = null ;
            String shortClassName = className ;
            int index = shortClassName.lastIndexOf( '/' ) ;
            if (index >= 0) {
                shortClassName = className.substring( index + 1 ) ;
            }

            String description = "Timer for method " + mname 
                + " in class " + shortClassName ; // default
            String mmName = mname ; // default
            TimingPointType tpt = TimingPointType.BOTH ; // default for non InfoMethod

            boolean hasMethodInfoAnno = false ;

            final Annotation[] annotations = mn.getDeclaredAnnotations() ;
            if (annotations != null) {
                for (Annotation an : annotations) {
		    final String aname = Type.getInternalName(
			an.annotationType() ) ;

                    if (aname.equals( TRACING_NAME )) {
                        TracingName tname = (TracingName)an ;
                        if (tname.value().length() > 0) {
                            mmName = tname.value() ;
                        }
                    } else if (aname.equals( DESCRIPTION_NAME )) {
                        Description desc = (Description)an ;
                        if (desc.value().length() > 0) {
                            description = desc.value() ;
                        }
                    } else if (aname.equals( INFO_METHOD_NAME)) {
                        // Check for private method!
                        if (!util.hasAccess( mn.getModifiers(),
		            Modifier.PRIVATE )) {

                            util.error( "Method " + mdesc
                                + " for Class " + currentClass.getName()
                                + " is a non-private @InfoMethod,"
                                + " which is not allowed" ) ;
                        }

                        hasMethodInfoAnno = true ;
                        InfoMethod im = (InfoMethod)an ;
                        tpt = im.tpType() ;
                    } else if (annoNamesForClass.contains( aname)) {
                        if (monitoredMethodMMAnno == null) {
                            monitoredMethodMMAnno = aname ;
                        } else {
                            util.error( "Method " + mdesc
                                + " for Class " + currentClass.getName()
                                + "has multiple MM annotations" ) ;
                        }
                    } else if (isMMAnnotation( an )) {
                            util.error( "Method " + mdesc
                                + " for Class " + currentClass.getName()
                                + " has an MM annotation " + an + " which "
                                + "is not present on its class" ) ;
                    }
                }

                if (hasMethodInfoAnno && monitoredMethodMMAnno != null) {
                    util.error( "Method " + mdesc
                        + " for Class " + currentClass.getName()
                        + " has both @InfoMethod annotation and"
                        + " a MM annotation" ) ;
                }

                // This check is not really essential, but it simplifies
                // passing information to later phases for code generation
                // if we can assume that all @InfoMethod annotated methods
                // are non-static. (Simply because we only need to look for
                // INVOKESPECIAL).
                final boolean isStatic = util.hasAccess( mn.getModifiers(),
                    Modifier.STATIC ) ;
                if (hasMethodInfoAnno && isStatic) {
                    util.error( "Method " + mdesc            
                        + " for Class " + currentClass.getName()
                        + " is a static method, but must not be" ) ;
                }

                // TF Annotations are not permitted on constructors
                if (mname.equals( "<init>" )) {
                    if (hasMethodInfoAnno) {
                        util.error( "Constructors must not have an "
                            + "@InfoMethod annotations") ;
                    } else if (monitoredMethodMMAnno != null) {
                        util.error( "Constructors must not have an "
                            + "MM annotation") ;
                    }
                }

                // This will be a null value for InfoMethods, which is what
                // we want.
                mmnToAnnotationName.put(mmName, monitoredMethodMMAnno ) ;

                // We could have a method at this point that is annotated with
                // something OTHER than tracing annotations.  Do not add
                // such methods to the ECD.
                if (hasMethodInfoAnno || (monitoredMethodMMAnno != null)) {
                    // Both infoMethods and MM annotated methods go into 
                    // methodNames
                    methodNames.add( mmName ) ;

                    mmnToDescriptions.put( mmName, description ) ;
                    mmnToTPT.put( mmName, tpt ) ;

                    if (hasMethodInfoAnno) {
                        infoMethodDescs.add( mdesc ) ;
                    } else {
                        mmMethodDescs.add( mdesc ) ;
                        methodToAnno.put( mdesc, monitoredMethodMMAnno ) ;
                    }
                }
            }
        }

        Collections.sort( methodNames ) ;

        for (String str : methodNames ) {
            methodDescriptions.add( mmnToDescriptions.get( str ) ) ;
            methodTPTs.add( mmnToTPT.get( str ) ) ;
            methodAnnoList.add( mmnToAnnotationName.get( str ) ) ;
        }

        if (util.getDebug()) {
            util.msg( "\tinfoMethodSignature = " + infoMethodDescs ) ;
            util.msg( "\tmmMethodSignature = " + mmMethodDescs ) ;
            util.msg( "\tmethodNames = " + methodNames ) ;
            util.msg( "\tmethodToAnno = " + methodToAnno ) ;
            util.msg( "\tmethodDescriptions = " + methodDescriptions ) ;
            util.msg( "\tmethodTPTs = " + methodTPTs ) ;
        }
    }

    public EnhancedClassDataReflectiveImpl( Util util, 
        Class<?> cn ) {

        super( util, null ) ;

        currentClass = cn ;

        // Compute data here: only look at data available to
        // java reflection, so that a runtime version of 
        // EnhancedClassData using reflection can be created.
        className = cn.getName() ;
        processClassAnnotations() ;
        scanMethods();
    }

}

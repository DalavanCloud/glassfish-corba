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

package com.sun.corba.se.impl.orbutil;

import com.sun.corba.se.spi.orb.ORBVersion;
import com.sun.corba.se.spi.orb.ORB;

public abstract class RepositoryIdFactory
{
    private static final RepIdDelegator_1_3_1 ladybirdDelegator
        = new RepIdDelegator_1_3_1();

    private static final RepIdDelegator currentDelegator
        = new RepIdDelegator();

    /**
     * Returns the latest version RepositoryIdStrings instance
     */
    public static RepositoryIdStrings getRepIdStringsFactory()
    {
        return currentDelegator;
    }

    /**
     * Checks the version of the ORB and returns the appropriate
     * RepositoryIdStrings instance.
     */
    public static RepositoryIdStrings getRepIdStringsFactory(ORB orb)
    {
        if (orb != null) {
            switch (orb.getORBVersion().getORBType()) {
                case ORBVersion.NEWER:
                case ORBVersion.FOREIGN:
                case ORBVersion.JDK1_3_1_01:
                    return currentDelegator;
                case ORBVersion.NEW:
                    return ladybirdDelegator;
                default:
                    return currentDelegator;
            }
        } else
            return currentDelegator;
    }

    /**
     * Returns the latest version RepositoryIdUtility instance
     */
    public static RepositoryIdUtility getRepIdUtility()
    {
        return currentDelegator;
    }

    /**
     * Checks the version of the ORB and returns the appropriate
     * RepositoryIdUtility instance.
     */
    public static RepositoryIdUtility getRepIdUtility(ORB orb)
    {
        if (orb != null) {
            switch (orb.getORBVersion().getORBType()) {
                case ORBVersion.NEWER:
                case ORBVersion.FOREIGN:
                case ORBVersion.JDK1_3_1_01:
                    return currentDelegator;
                case ORBVersion.NEW:
                    return ladybirdDelegator;
                default:
                    return currentDelegator;
            }
        } else
            return currentDelegator;
    }
}

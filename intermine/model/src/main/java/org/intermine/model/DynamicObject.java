package org.intermine.model;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.lang.reflect.InvocationHandler;

/**
 * Implemented by every class that org.intermine.util.DynamicUtil generates for an object
 * of more than one model class (for example a Company that is also Broke). It replaces
 * cglib's net.sf.cglib.proxy.Factory as the marker of such classes:
 * org.intermine.metadata.Util.decomposeClass splits a class that implements it into the
 * model classes it is made of.
 *
 * The method name does not start with get, so TypeUtil.getFieldInfos does not take it
 * for a field.
 *
 * @author InterMine
 */
public interface DynamicObject
{
    /**
     * Returns the handler that holds this object's field values (an
     * org.intermine.util.DynamicBean). Every method of the model classes is sent to it.
     *
     * @return the handler
     */
    InvocationHandler dynamicHandler();
}

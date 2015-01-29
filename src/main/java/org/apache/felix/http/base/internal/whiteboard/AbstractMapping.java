/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.http.base.internal.whiteboard;

import java.util.Hashtable;

import org.osgi.framework.Bundle;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;

abstract class AbstractMapping
{
    private final Bundle bundle;
    private HttpContext context;
    private final Hashtable<String, String> initParams;
    private boolean registered;

    protected AbstractMapping(final Bundle bundle)
    {
        this.bundle = bundle;
        this.context = null;
        this.initParams = new Hashtable<String, String>();
        this.registered = false;
    }

    public Bundle getBundle()
    {
        return bundle;
    }

    public void setContext(HttpContext context)
    {
        this.context = context;
    }

    public final HttpContext getContext()
    {
        return this.context;
    }

    public final Hashtable<String, String> getInitParams()
    {
        return this.initParams;
    }

    boolean isRegistered()
    {
        return registered;
    }

    void setRegistered(boolean registered)
    {
        this.registered = registered;
    }

    public abstract void register(HttpService httpService);

    public abstract void unregister(HttpService httpService);

}
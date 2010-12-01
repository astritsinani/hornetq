/*
 * Copyright 2009 Red Hat, Inc.
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package org.hornetq.core.client.impl;

import java.io.Serializable;

import org.hornetq.api.core.Pair;
import org.hornetq.api.core.TransportConfiguration;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 *         Created Aug 16, 2010
 */
public class TopologyMember implements Serializable
{
   private static final long serialVersionUID = 1123652191795626133L;

   private final Pair<TransportConfiguration, TransportConfiguration> connector;

   private final int distance;

   public TopologyMember(Pair<TransportConfiguration, TransportConfiguration> connector, int distance)
   {
      this.connector = connector;
      this.distance = distance;
   }

   public Pair<TransportConfiguration, TransportConfiguration> getConnector()
   {
      return connector;
   }

   public int getDistance()
   {
      return distance;
   }
   
   @Override
   public String toString()
   {
      return "TopologyMember[distance=" + distance + ", connector=" + connector + "]";
   }
}

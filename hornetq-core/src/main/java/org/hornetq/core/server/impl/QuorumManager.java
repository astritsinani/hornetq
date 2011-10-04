package org.hornetq.core.server.impl;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.hornetq.api.core.Pair;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.ClusterTopologyListener;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.core.client.impl.ServerLocatorImpl;
import org.hornetq.core.server.cluster.ClusterManager;

/**
 * Manages a quorum of servers used to determine whether a given server is running or not.
 * <p>
 * The use case scenario is an eventual connection loss between the live and the backup, where the
 * quorum will help a remote backup in deciding whether to replace its 'live' server or to wait for
 * it.
 */
final class QuorumManager implements ClusterTopologyListener
{

   // private static final Logger LOG = Logger.getLogger(QuorumManager.class);

   // volatile boolean started;
   private final ClusterManager clusterManager;
   private final String targetServerName;
   private final Map<String, Pair<TransportConfiguration, TransportConfiguration>> nodes =
            new ConcurrentHashMap<String, Pair<TransportConfiguration, TransportConfiguration>>();
   private static final long DISCOVERY_TIMEOUT = 3;

   public QuorumManager(ClusterManager clusterManager, String nodeID)
   {
      this.clusterManager = clusterManager;
      this.targetServerName = nodeID;

      clusterManager.addClusterTopologyListener(this, true);
   }

   @Override
   public void nodeUP(String nodeID, Pair<TransportConfiguration, TransportConfiguration> connectorPair, boolean last)
   {
      if (targetServerName.equals(nodeID))
      {
         return;
      }
      nodes.put(nodeID, connectorPair);
   }

   @Override
   public void nodeDown(String nodeID)
   {
      if (targetServerName.equals(nodeID))
      {
         // targetReturned = false;
         // trigger action

         // decide to wake backup
         clusterManager.removeClusterTopologyListener(this, true);
      }
      nodes.remove(nodeID);
   }

   public boolean isNodeDown()
   {
      boolean liveShutdownCleanly = !nodes.containsKey(targetServerName);
      boolean noOtherServersAround = nodes.size() == 0;
      if (liveShutdownCleanly || noOtherServersAround)
         return true;
      // go for the vote...
      // Set<ServerLocator> currentNodes = new HashSet(nodes.entrySet());
      final int size = nodes.size();
      Set<ServerLocator> locatorsList = new HashSet<ServerLocator>(size);
      AtomicInteger pingCount = new AtomicInteger(0);
      ExecutorService pool = Executors.newFixedThreadPool(size);
      final CountDownLatch latch = new CountDownLatch(size);
      try
      {
         for (Entry<String, Pair<TransportConfiguration, TransportConfiguration>> pair : nodes.entrySet())
         {
            if (targetServerName.equals(pair.getKey()))
               continue;
            TransportConfiguration serverTC = pair.getValue().a;
            ServerLocatorImpl locator = (ServerLocatorImpl)HornetQClient.createServerLocatorWithoutHA(serverTC);
            locatorsList.add(locator);
            pool.submit(new ServerConnect(latch, pingCount, locator));
         }
         // Some servers may have disappeared between the latch creation
         for (int i = 0; i < size - locatorsList.size(); i++)
         {
            latch.countDown();
         }
         try
         {
            latch.await();
         }
         catch (InterruptedException interruption)
         {
            // No-op. As the best the quorum can do now is to return the latest number it has
         }
         return pingCount.get() * 2 >= locatorsList.size();
      }
      finally
      {
         for (ServerLocator locator: locatorsList){
            try
            {
               locator.close();
            }
            catch (Exception e)
            {
               // no-op
            }
         }
         pool.shutdownNow();
      }
   }

   private static class ServerConnect implements Runnable
   {
      private final ServerLocatorImpl locator;
      private final CountDownLatch latch;
      private final AtomicInteger count;

      public ServerConnect(CountDownLatch latch, AtomicInteger count, ServerLocatorImpl serverLocator)
      {
         locator = serverLocator;
         this.latch = latch;
         this.count = count;
      }

      @Override
      public void run()
      {
         locator.setReconnectAttempts(-1);
         locator.getDiscoveryGroupConfiguration().setDiscoveryInitialWaitTimeout(DISCOVERY_TIMEOUT);

         final ClientSessionFactory liveServerSessionFactory;
         try
         {
            liveServerSessionFactory = locator.connect();
            if (liveServerSessionFactory != null)
            {
               count.incrementAndGet();
            }
         }
         catch (Exception e)
         {
            // no-op
         }
         finally
         {
            latch.countDown();
         }
      }

   }
}

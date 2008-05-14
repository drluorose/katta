/**
 * Copyright 2008 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.sf.katta;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;

import net.sf.katta.master.DefaultDistributionPolicy;
import net.sf.katta.master.IPaths;
import net.sf.katta.master.Master;
import net.sf.katta.master.MasterMetaData;
import net.sf.katta.slave.Slave;
import net.sf.katta.util.KattaException;
import net.sf.katta.util.Logger;
import net.sf.katta.util.NetworkUtil;
import net.sf.katta.util.ZkConfiguration;
import net.sf.katta.zk.ZKClient;

import com.yahoo.zookeeper.server.NIOServerCnxn;
import com.yahoo.zookeeper.server.ServerStats;
import com.yahoo.zookeeper.server.ZooKeeperServer;
import com.yahoo.zookeeper.server.NIOServerCnxn.Factory;
import com.yahoo.zookeeper.server.quorum.QuorumPeer;
import com.yahoo.zookeeper.server.quorum.QuorumPeer.QuorumServer;

public class Server {

  private QuorumPeer _quorumPeer;

  private ZooKeeperServer _zk;

  private Factory _nioFactory;

  private Master _master;

  // private ZkConfiguration _conf;

  private Slave _slave;

  public Server(final ZkConfiguration conf) {
    if (Logger.isInfo()) {
      final String[] localHostNames = NetworkUtil.getLocalHostNames();
      String names = "";
      for (int i = 0; i < localHostNames.length; i++) {
        final String name = localHostNames[i];
        names += " " + name;
        if (i + 1 != localHostNames.length) {
          names += ",";
        }
      }
      Logger.info("Starting Server on: " + names + "...");
    }
    startZooKeeperServer(conf);
  }

  public void startMasterOrSlave(final ZKClient client, final boolean master) throws KattaException {
    // we might need to wait for the server to startup..
    System.out.println("Server.startMasterOrSlave()");
    client.waitForZooKeeper(30000);

    final boolean masterConfigured = beMaster(client);
    if (Logger.isDebug()) {
      Logger.debug("MasterConfigured: " + masterConfigured);
      Logger.debug("Master name: " + master);
    }
    if (masterConfigured && master) {
      // TODO make policy configurable
      Logger.info("Starting Master...");
      _master = new Master(client, new DefaultDistributionPolicy());
      _master.start();
    } else if (!masterConfigured && master) {
      // TODO make a master fail over here
    } else {
      Logger.info("Starting Slave...");
      _slave = new Slave(client);
      _slave.start();
    }
  }

  private boolean beMaster(final ZKClient client) {
    synchronized (client.getSyncMutex()) {
      try {
        client.createDefaultStructure();
        final String localhostName = NetworkUtil.getLocalhostName();
        // no master so this one will be master
        final MasterMetaData freshMaster = new MasterMetaData(localhostName, System.currentTimeMillis());
        if (!client.exists(IPaths.MASTER)) {
          Logger.info("Creating master node: " + localhostName);
          client.createEphemeral(IPaths.MASTER, freshMaster);
          return true;
        } else {
          // there is a master file, now we check if this is may be one this
          // server wrote.
          final MasterMetaData oldMaster = new MasterMetaData();
          client.readData(IPaths.MASTER, oldMaster);
          if (oldMaster.getMasterName().equals(localhostName)) {
            // looks like I'm the master ..
            Logger.info("Old master file was found ...");
            client.createEphemeral(IPaths.MASTER, freshMaster);
            return true;
          } else {
            // TODO here goes the master fail over.
            return false;
          }
        }
      } catch (final KattaException e) {
        throw new RuntimeException("Failed to communicate with ZooKeeper", e);
      }
    }
  }

  // check if this server has to start a zookepper server
  private void startZooKeeperServer(final ZkConfiguration conf) {
    final String[] localhostHostNames = NetworkUtil.getLocalHostNames();
    final String servers = conf.getZKServers();
    // check if this server needs to start a _zk server.
    if (NetworkUtil.hostNamesInList(servers, localhostHostNames)) {
      // yes this server needs to start a zookeeper server
      final int port = conf.getZKClientPort();
      // check if this maschine is already something running..
      boolean free = false;
      try {
        final ServerSocket socket = new ServerSocket(port);
        free = true;
        socket.close();
      } catch (final Exception e) {
        free = false;
      }
      if (free) {
        final String[] hosts = servers.split(",");

        final int tickTime = conf.getZKTickTime();
        final File dataDir = conf.getZKDataDir();
        final File dataLogDir = conf.getZKDataLogDir();
        dataDir.mkdirs();
        dataLogDir.mkdirs();

        if (hosts.length > 1) {
          // multiple zk servers
          startQuorumPeer(conf, localhostHostNames, hosts, tickTime, dataDir, dataLogDir);
        } else {
          // single zk server
          startSingleZkServer(conf, tickTime, dataDir, dataLogDir, port);
          Logger.info("ZooKeeper server started...");
        }
      } else {
        Logger.warn("No zookeeper server was started, port is already blocked!");
      }
    } else {
      final String msg = "This is server is not configured to be a zookeeper server";
      Logger.error(msg);
      throw new RuntimeException(msg);
    }

  }

  private void startSingleZkServer(final ZkConfiguration conf, final int tickTime, final File dataDir,
      final File dataLogDir, final int port) {
    try {
      ServerStats.registerAsConcrete();
      _zk = new ZooKeeperServer(dataDir, dataLogDir, tickTime);
      _zk.startup();
      _nioFactory = new NIOServerCnxn.Factory(port);
      _nioFactory.setZooKeeperServer(_zk);
    } catch (final IOException e) {
      throw new RuntimeException("Unable to start single ZooKeeper server.", e);
    } catch (final InterruptedException e) {
      Logger.warn("ZooKeeper server was interrupted.", e);
    }
  }

  private void startQuorumPeer(final ZkConfiguration conf, final String[] localhostHostNames, final String[] hosts,
      final int tickTime, final File dataDir, final File dataLogDir) {
    Logger.info("Starting ZooKeeper Server...");
    final ArrayList<QuorumServer> peers = new ArrayList<QuorumServer>();
    long myId = -1;
    int myPort = -1;
    for (int i = 0; i < hosts.length; i++) {
      final String[] hostAndPort = hosts[i].split(":");
      final String host = hostAndPort[0];
      final int port = Integer.parseInt(hostAndPort[1]);
      final InetSocketAddress inetSocketAddress = new InetSocketAddress(host, port);
      peers.add(new QuorumServer(i, inetSocketAddress));
      if (NetworkUtil.hostNameInArray(localhostHostNames, host)) {
        myId = i;
        myPort = port;
      }
    }

    final int initLimit = conf.getZKInitLimit();
    final int syncLimit = conf.getZKSyncLimit();

    final int electionAlg = 0;
    final int clientPort = conf.getZKClientPort();
    try {
      _quorumPeer = new QuorumPeer(peers, dataDir, dataLogDir, clientPort, electionAlg, myPort, myId, tickTime,
          initLimit, syncLimit);
      _quorumPeer.start();
    } catch (final IOException e) {
      throw new RuntimeException("Could not start QuorumPeer ZooKeeper server.", e);
    }
  }

  public void join() {
    if (_quorumPeer != null) {
      try {
        _quorumPeer.join();
      } catch (final InterruptedException e) {
        Logger.info("QuorumPeer was interruped.", e);
      }
      _quorumPeer.shutdown();
    } else if (_nioFactory != null) {
      try {
        _nioFactory.join();
      } catch (final InterruptedException e) {
        Logger.info("Nio server was interruped.", e);
      }
      _zk.shutdown();
    } else if (_slave != null) {
      try {
        _slave.join();
      } catch (final InterruptedException e) {
        Logger.info("Slave was interruped.", e);
      }
    }
  }

  public static void main(final String[] args) throws InterruptedException, KattaException {
    if (args.length != 1) {
      usage();
      System.exit(1);
    }
    boolean master = false;
    if (args[0].equalsIgnoreCase("-master")) {
      master = true;
    } else if (args[0].equalsIgnoreCase("-salve")) {
      master = false;
    } else {
      usage();
      System.exit(1);
    }

    final ZkConfiguration conf = new ZkConfiguration();
    final Server server = new Server(conf);
    final ZKClient client = new ZKClient(conf);
    client.waitForZooKeeper(5000);
    server.startMasterOrSlave(client, master);
    server.join();
  }

  private static void usage() {
    System.out.println("net.sf.katta.Server -master|slave");

  }

  public void shutdown() {
    Logger.info("Shutting down master...");
    if (_quorumPeer != null) {
      _quorumPeer.interrupt();
      _quorumPeer.shutdown();
    }
    if (_nioFactory != null) {
      _nioFactory.interrupt();
      _nioFactory.shutdown();
    }
    if (_slave != null) {
      _slave.shutdown();
    }
    if (_zk != null) {
      _zk.shutdown();
    }
    try {
      Thread.sleep(1000);
    } catch (final InterruptedException e) {
      throw new RuntimeException("Waiting to shutodown the server was interrupted.", e);
    }
  }
}

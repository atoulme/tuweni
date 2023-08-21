// Copyright The Tuweni Authors
// SPDX-License-Identifier: Apache-2.0
package org.apache.tuweni.plumtree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.concurrent.AsyncCompletion;
import org.apache.tuweni.concurrent.CompletableAsyncCompletion;
import org.apache.tuweni.crypto.Hash;
import org.apache.tuweni.junit.BouncyCastleExtension;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(BouncyCastleExtension.class)
class StateTest {

  private static class PeerImpl implements Peer {
    private final String id = UUID.randomUUID().toString();

    @Override
    public int compareTo(@NotNull Peer o) {
      return ((PeerImpl) o).id.compareTo(id);
    }
  }

  private static class MockMessageSender implements MessageSender {

    Verb verb;
    Peer peer;
    Bytes hash;
    Bytes payload;

    CompletableAsyncCompletion waitForMessageSent;

    @Override
    public void sendMessage(
        Verb verb, Map<String, Bytes> attributes, Peer peer, Bytes hash, Bytes payload) {
      this.verb = verb;
      this.peer = peer;
      this.hash = hash;
      this.payload = payload;
      if (waitForMessageSent != null) {
        waitForMessageSent.complete();
      }
    }
  }

  private static final MessageListener messageListener = (messageBody, attributes, peer) -> {};

  @Test
  void testInitialState() {
    EphemeralPeerRepository repo = new EphemeralPeerRepository();
    assertTrue(repo.peers().isEmpty());
    assertTrue(repo.lazyPushPeers().isEmpty());
    assertTrue(repo.eagerPushPeers().isEmpty());
  }

  @Test
  void firstRoundWithThreePeers() {
    EphemeralPeerRepository repo = new EphemeralPeerRepository();
    State state =
        new State(
            repo,
            Hash::keccak256,
            new MockMessageSender(),
            messageListener,
            (message, peer) -> true,
            (peer) -> true);
    state.addPeer(new PeerImpl());
    state.addPeer(new PeerImpl());
    state.addPeer(new PeerImpl());
    assertTrue(repo.lazyPushPeers().isEmpty());
    assertEquals(3, repo.eagerPushPeers().size());
  }

  @Test
  void firstRoundWithTwoPeers() {
    EphemeralPeerRepository repo = new EphemeralPeerRepository();
    State state =
        new State(
            repo,
            Hash::keccak256,
            new MockMessageSender(),
            messageListener,
            (message, peer) -> true,
            (peer) -> true);
    state.addPeer(new PeerImpl());
    state.addPeer(new PeerImpl());
    assertTrue(repo.lazyPushPeers().isEmpty());
    assertEquals(2, repo.eagerPushPeers().size());
  }

  @Test
  void firstRoundWithOnePeer() {
    EphemeralPeerRepository repo = new EphemeralPeerRepository();
    State state =
        new State(
            repo,
            Hash::keccak256,
            new MockMessageSender(),
            messageListener,
            (message, peer) -> true,
            (peer) -> true);
    state.addPeer(new PeerImpl());
    assertTrue(repo.lazyPushPeers().isEmpty());
    assertEquals(1, repo.eagerPushPeers().size());
  }

  @Test
  void removePeer() {
    EphemeralPeerRepository repo = new EphemeralPeerRepository();
    State state =
        new State(
            repo,
            Hash::keccak256,
            new MockMessageSender(),
            messageListener,
            (message, peer) -> true,
            (peer) -> true);
    Peer peer = new PeerImpl();
    state.addPeer(peer);
    state.removePeer(peer);
    assertTrue(repo.peers().isEmpty());
    assertTrue(repo.lazyPushPeers().isEmpty());
    assertTrue(repo.eagerPushPeers().isEmpty());
  }

  @Test
  void prunePeer() {
    EphemeralPeerRepository repo = new EphemeralPeerRepository();
    State state =
        new State(
            repo,
            Hash::keccak256,
            new MockMessageSender(),
            messageListener,
            (message, peer) -> true,
            (peer) -> true);
    Peer peer = new PeerImpl();
    state.addPeer(peer);
    state.receivePruneMessage(peer);
    assertEquals(0, repo.eagerPushPeers().size());
    assertEquals(1, repo.lazyPushPeers().size());
  }

  @Test
  void graftPeer() {
    EphemeralPeerRepository repo = new EphemeralPeerRepository();
    State state =
        new State(
            repo,
            Hash::keccak256,
            new MockMessageSender(),
            messageListener,
            (message, peer) -> true,
            (peer) -> true);
    Peer peer = new PeerImpl();
    state.addPeer(peer);
    state.receivePruneMessage(peer);
    assertEquals(0, repo.eagerPushPeers().size());
    assertEquals(1, repo.lazyPushPeers().size());
    state.receiveGraftMessage(peer, Bytes32.random());
    assertEquals(1, repo.eagerPushPeers().size());
    assertEquals(0, repo.lazyPushPeers().size());
  }

  @Test
  void receiveFullMessageFromEagerPeer() {
    EphemeralPeerRepository repo = new EphemeralPeerRepository();
    MockMessageSender messageSender = new MockMessageSender();
    State state =
        new State(
            repo,
            Hash::keccak256,
            messageSender,
            messageListener,
            (message, peer) -> true,
            (peer) -> true);
    Peer peer = new PeerImpl();
    state.addPeer(peer);
    Peer otherPeer = new PeerImpl();
    state.addPeer(otherPeer);
    Bytes32 msg = Bytes32.random();
    Map<String, Bytes> attributes =
        Collections.singletonMap(
            "message_type", Bytes.wrap("BLOCK".getBytes(StandardCharsets.UTF_8)));
    state.receiveGossipMessage(peer, attributes, msg, Hash.keccak256(msg));
    assertEquals(msg, messageSender.payload);
    assertEquals(otherPeer, messageSender.peer);
  }

  @Test
  void receiveFullMessageFromEagerPeerWithALazyPeer() {
    EphemeralPeerRepository repo = new EphemeralPeerRepository();
    MockMessageSender messageSender = new MockMessageSender();
    State state =
        new State(
            repo,
            Hash::keccak256,
            messageSender,
            messageListener,
            (message, peer) -> true,
            (peer) -> true);
    Peer peer = new PeerImpl();
    state.addPeer(peer);
    Peer otherPeer = new PeerImpl();
    state.addPeer(otherPeer);
    Bytes32 msg = Bytes32.random();
    Peer lazyPeer = new PeerImpl();
    state.addPeer(lazyPeer);
    repo.moveToLazy(lazyPeer);
    Map<String, Bytes> attributes =
        Collections.singletonMap(
            "message_type", Bytes.wrap("BLOCK".getBytes(StandardCharsets.UTF_8)));
    state.receiveGossipMessage(peer, attributes, msg, Hash.keccak256(msg));
    assertEquals(msg, messageSender.payload);
    assertEquals(otherPeer, messageSender.peer);
    state.processQueue();
    assertEquals(Hash.keccak256(msg), messageSender.hash);
    assertEquals(lazyPeer, messageSender.peer);
    assertEquals(MessageSender.Verb.IHAVE, messageSender.verb);
    assertTrue(state.lazyQueue.isEmpty());
  }

  @Test
  void receiveFullMessageFromEagerPeerThenPartialMessageFromLazyPeer() {
    EphemeralPeerRepository repo = new EphemeralPeerRepository();
    MockMessageSender messageSender = new MockMessageSender();
    State state =
        new State(
            repo,
            Hash::keccak256,
            messageSender,
            messageListener,
            (message, peer) -> true,
            (peer) -> true);
    Peer peer = new PeerImpl();
    state.addPeer(peer);
    Peer lazyPeer = new PeerImpl();
    state.addPeer(lazyPeer);
    repo.moveToLazy(lazyPeer);
    Bytes message = Bytes32.random();
    Map<String, Bytes> attributes =
        Collections.singletonMap(
            "message_type", Bytes.wrap("BLOCK".getBytes(StandardCharsets.UTF_8)));
    state.receiveGossipMessage(peer, attributes, message, Hash.keccak256(message));
    state.receiveIHaveMessage(lazyPeer, message);
    assertNull(messageSender.payload);
    assertNull(messageSender.peer);
  }

  @Test
  void receivePartialMessageFromLazyPeerAndNoFullMessage() throws Exception {
    EphemeralPeerRepository repo = new EphemeralPeerRepository();
    MockMessageSender messageSender = new MockMessageSender();
    messageSender.waitForMessageSent = AsyncCompletion.incomplete();
    State state =
        new State(
            repo,
            Hash::keccak256,
            messageSender,
            messageListener,
            (message, peer) -> true,
            (peer) -> true,
            100,
            4000);
    Peer peer = new PeerImpl();
    state.addPeer(peer);
    Peer lazyPeer = new PeerImpl();
    state.addPeer(lazyPeer);
    repo.moveToLazy(lazyPeer);
    Bytes message = Bytes32.random();
    state.receiveIHaveMessage(lazyPeer, message);
    messageSender.waitForMessageSent.join();
    assertEquals(message, messageSender.hash);
    assertEquals(lazyPeer, messageSender.peer);
    assertEquals(MessageSender.Verb.GRAFT, messageSender.verb);
  }

  @Test
  void receivePartialMessageFromLazyPeerAndThenFullMessage() throws Exception {
    EphemeralPeerRepository repo = new EphemeralPeerRepository();
    MockMessageSender messageSender = new MockMessageSender();
    State state =
        new State(
            repo,
            Hash::keccak256,
            messageSender,
            messageListener,
            (message, peer) -> true,
            (peer) -> true,
            500,
            4000);
    Peer peer = new PeerImpl();
    state.addPeer(peer);
    Peer lazyPeer = new PeerImpl();
    state.addPeer(lazyPeer);
    repo.moveToLazy(lazyPeer);
    Bytes message = Bytes32.random();
    state.receiveIHaveMessage(lazyPeer, Hash.keccak256(message));
    Thread.sleep(100);
    Map<String, Bytes> attributes =
        Collections.singletonMap(
            "message_type", Bytes.wrap("BLOCK".getBytes(StandardCharsets.UTF_8)));
    state.receiveGossipMessage(peer, attributes, message, Hash.keccak256(message));
    Thread.sleep(500);
    assertNull(messageSender.verb);
    assertNull(messageSender.payload);
    assertNull(messageSender.peer);
  }

  @Test
  void receiveFullMessageFromUnknownPeer() {
    EphemeralPeerRepository repo = new EphemeralPeerRepository();
    MockMessageSender messageSender = new MockMessageSender();
    State state =
        new State(
            repo,
            Hash::keccak256,
            messageSender,
            messageListener,
            (message, peer) -> true,
            (peer) -> true);
    Peer peer = new PeerImpl();
    Bytes message = Bytes32.random();
    Map<String, Bytes> attributes =
        Collections.singletonMap(
            "message_type", Bytes.wrap("BLOCK".getBytes(StandardCharsets.UTF_8)));
    state.receiveGossipMessage(peer, attributes, message, Hash.keccak256(message));
    assertEquals(1, repo.eagerPushPeers().size());
    assertEquals(0, repo.lazyPushPeers().size());
    assertEquals(peer, repo.eagerPushPeers().iterator().next());
  }

  @Test
  void prunePeerWhenReceivingTwiceTheSameFullMessage() {
    EphemeralPeerRepository repo = new EphemeralPeerRepository();
    MockMessageSender messageSender = new MockMessageSender();
    State state =
        new State(
            repo,
            Hash::keccak256,
            messageSender,
            messageListener,
            (message, peer) -> true,
            (peer) -> true);
    Peer peer = new PeerImpl();
    Peer secondPeer = new PeerImpl();
    Bytes message = Bytes32.random();
    Map<String, Bytes> attributes =
        Collections.singletonMap(
            "message_type", Bytes.wrap("BLOCK".getBytes(StandardCharsets.UTF_8)));
    state.receiveGossipMessage(peer, attributes, message, Hash.keccak256(message));
    state.receiveGossipMessage(secondPeer, attributes, message, Hash.keccak256(message));
    assertEquals(2, repo.eagerPushPeers().size());
    assertEquals(0, repo.lazyPushPeers().size());
    assertNull(messageSender.payload);
    assertEquals(secondPeer, messageSender.peer);
    assertEquals(MessageSender.Verb.PRUNE, messageSender.verb);
  }
}

package hammerfest.net;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Hôte et clients réels sur la machine (localhost), ports dédiés aux tests. */
class LobbyTest {

    static final int TCP = 54655, UDP = 54657, DISC = 54656;

    static NetEvent await(Supplier<NetEvent> poll, long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            NetEvent e = poll.get();
            if (e != null) return e;
            Thread.sleep(10);
        }
        return null;
    }

    @Test
    void discoverAcceptRefuseAndExchange() throws Exception {
        try (HostSession host = new HostSession("Alexis")) {
            host.start(TCP, UDP, DISC);

            List<LanDiscovery.HostInfo> found = LanDiscovery.discover(DISC, 800);
            assertTrue(found.stream().anyMatch(h -> h.hostName.equals("Alexis") && h.tcpPort == TCP),
                    "partie annoncée sous le nom de l'hôte : " + found);

            ClientSession a = new ClientSession();
            a.connect(InetAddress.getLoopbackAddress(), TCP, UDP, "Bob", "id-bob");
            NetEvent ask = await(host::poll, 3000);
            assertNotNull(ask);
            assertEquals(NetEvent.Type.JOIN_ASKED, ask.type);
            assertEquals("Bob", ask.name);

            // une deuxième demande attend son tour, puis est refusée quand la partie est complète
            ClientSession b = new ClientSession();
            b.connect(InetAddress.getLoopbackAddress(), TCP, UDP, "Carole", "id-carole");
            Thread.sleep(200);

            host.accept(ask.name);
            NetEvent ok = await(a::poll, 3000);
            assertEquals(NetEvent.Type.ACCEPTED, ok.type);
            assertEquals("Alexis", ok.name, "le client connaît le nom de l'hôte");
            assertEquals("Bob", host.peerName());

            NetEvent full = await(b::poll, 3000);
            assertEquals(NetEvent.Type.REFUSED, full.type);
            assertEquals("Partie complète", full.text);

            // échange de messages de jeu
            Net.InputMsg in = new Net.InputMsg();
            in.pid = 1;
            in.tick = 7;
            in.bits = 5;
            a.send(in);
            NetEvent msg = await(host::poll, 3000);
            assertEquals(NetEvent.Type.MESSAGE, msg.type);
            assertEquals(7, ((Net.InputMsg) msg.message).tick);

            a.close();
            NetEvent left = await(host::poll, 3000);
            assertEquals(NetEvent.Type.DISCONNECTED, left.type);
            b.close();
        }
    }

    @Test
    void hostCanRefuse() throws Exception {
        try (HostSession host = new HostSession("Alexis")) {
            host.start(TCP + 10, UDP + 10, DISC + 10);
            ClientSession a = new ClientSession();
            a.connect(InetAddress.getLoopbackAddress(), TCP + 10, UDP + 10, "Bob", "id-bob");
            assertEquals(NetEvent.Type.JOIN_ASKED, await(host::poll, 3000).type);
            host.reject("Refusé par l'hôte");
            NetEvent r = await(a::poll, 3000);
            assertEquals(NetEvent.Type.REFUSED, r.type);
            assertEquals("Alexis", r.name);
            assertEquals("Refusé par l'hôte", r.text);
            a.close();
        }
    }
}

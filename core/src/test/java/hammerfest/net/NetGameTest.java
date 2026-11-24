package hammerfest.net;

import hammerfest.data.LevelSet;
import hammerfest.json.Jsons;
import hammerfest.sim.FrameSource;
import hammerfest.sim.Game;
import hammerfest.sim.InputFrame;
import hammerfest.sim.entity.Player;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Partie en ligne de bout en bout sur localhost : instantanés de l'hôte vers le client, touches du client vers l'hôte. */
class NetGameTest {

    static final int TCP = 54755, UDP = 54757, DISC = 54756;

    @Test
    void framesAndInputsTravel() throws Exception {
        LevelSet set = Jsons.read(new String(Files.readAllBytes(Path.of("data/levels/adventure.json")), StandardCharsets.UTF_8), LevelSet.class);
        Game game = new Game(set, FrameSource.DEFAULT, 42L, Set.of(), Set.of());
        game.start(0, 2);

        try (HostSession host = new HostSession("Alexis")) {
            host.start(TCP, UDP, DISC);
            ClientSession client = new ClientSession();
            client.connect(InetAddress.getLoopbackAddress(), TCP, UDP, "Bob", "id-bob");
            NetEvent ask = LobbyTest.await(host::poll, 3000);
            host.accept(ask.name);
            assertEquals(NetEvent.Type.ACCEPTED, LobbyTest.await(client::poll, 3000).type);

            // instantané complet (grille comprise) reçu tel quel par le client
            for (int i = 0; i < 40; i++) game.tick(new InputFrame(game.cycle, 0), new InputFrame(game.cycle, 0));
            RenderFrame sent = RenderFrame.capture(game, new ArrayList<>(game.drainEvents()), true);
            host.send(sent);
            NetEvent got = LobbyTest.await(client::poll, 3000);
            assertNotNull(got, "instantané reçu");
            RenderFrame f = (RenderFrame) got.message;
            assertEquals(sent.cycle, f.cycle);
            assertEquals(sent.ids.length, f.ids.length);
            assertArrayEquals(sent.grid, f.grid);
            assertEquals("adventure", f.setName);
            assertEquals(2, f.names.length);

            // touches du client : le joueur 2 va à droite chez l'hôte
            Player sandy = game.players().get(1);
            double x0 = sandy.x;
            Net.InputMsg in = new Net.InputMsg();
            in.pid = 1;
            in.bits = InputFrame.RIGHT;
            client.send(in);
            NetEvent msg = LobbyTest.await(host::poll, 3000);
            int bits = ((Net.InputMsg) msg.message).bits;
            for (int i = 0; i < 20; i++) game.tick(new InputFrame(game.cycle, 0), new InputFrame(game.cycle, bits));
            assertTrue(sandy.x > x0, "le joueur distant se déplace");
            client.close();
        }
    }
}

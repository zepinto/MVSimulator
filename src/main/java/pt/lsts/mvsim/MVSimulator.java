package pt.lsts.mvsim;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import pt.lsts.imc4j.msg.Message;
import pt.lsts.imc4j.util.ImcConsumable;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class MVSimulator implements ImcConsumable {
    private File duneBinDir, duneCfgDir;
    private String baseConfig;
    private ConcurrentHashMap<Integer, NetSocket> sockets = new ConcurrentHashMap<>();
    private ArrayList<DuneSimulator> simulators = new ArrayList<>();

    private int startLocalServer(int port) {
        NetServer tcpServer = Vertx.vertx().createNetServer();
        tcpServer.connectHandler(socket -> {
            System.out.println("Connection from "+socket.remoteAddress());
            socket.handler(buffer -> {
                try {
                    Message m = Message.deserialize(buffer.getBytes());
                    sockets.putIfAbsent(m.src, socket);
                }
                catch (Exception e) {
                }
            });
        });
        tcpServer.listen(port, "localhost", res -> {
            if (res.succeeded()) {
                System.out.println("TCP Server listening on port "+tcpServer.actualPort());
            }
            else {
                System.err.println("Failed to bind. Exiting.");
                System.exit(1);
            }
        });

        return tcpServer.actualPort();
    }

    public MVSimulator(File duneBinDir, File duneCfgDir, String baseConfig) {
        this.baseConfig = baseConfig;
        this.duneBinDir = duneBinDir;
        this.duneCfgDir = duneCfgDir;
    }

    public void startSimulators(int num) {
        int port = 9997;
        startLocalServer(port);

        for (int i = 0; i < num; i++) {
            DuneSimulator sim = startSimulator(port, 0x0100 + i);
            simulators.add(sim);
        }
    }

    public DuneSimulator startSimulator(int tcpPort, int imcId) {
        try {
            DuneSimulator sim = new DuneSimulator(duneBinDir, duneCfgDir, baseConfig);
            sim.removeSections("Transports.TCP.Server", "Transports.UDP", "Transports.Announce", "Transports.Discovery", "Transports.HTTP", "Transports.FTP", "Transports.Logging");
            sim.setSimExecutionFreq(20);
            sim.addTcpClient(tcpPort, "EstimatedState",
                    "LogBookEntry", "EntityState", "VehicleState", "PlanControlState");
            sim.setImcId(imcId);
            sim.startSimulator();
            simulators.add(sim);
            return sim;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) {
        MVSimulator sim = new MVSimulator(new File("/home/zp/workspace/dune/build"),
                new File("/home/zp/workspace/dune/source/etc"), "lauv-xplore-1");

        sim.startSimulators(200);
        Vertx.vertx().setPeriodic(10_000, event -> System.out.println(sim.sockets));
    }
}

package pt.lsts.mvsim;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DuneSimulator {

    private Process duneSimulator = null;
    private InetSocketAddress tcpServer = null;
    private DuneIni config = null;
    private File configDir, duneDir;
    private String[] duneCommand;
    private static final String PROFILE = "Simulation";

    public void setSimExecutionFreq(int freq) {
        config.set("Simulators.VSIM", "Execution Frequency", ""+freq);
    }

    public void setTimeMultiplier(double multipler) {
        config.set("Simulators.VSIM", "Simulation time multiplier", String.format("%.1f", multipler));
    }

    public void setImcId(int imcId) {
        String name = config.get("General", "Vehicle");
        config.set("IMC Addresses", name, ""+imcId);
    }

    public void addTcpServer(int port, String... messagesToListen) {
        config.set("Transports.TCP.Server", Stream.of(new String[][]{
                {"Enabled", "Always"},
                {"Entity Label", "TCP Server"},
                {"Port", "" + port},
                {"Transports", Arrays.asList(messagesToListen).stream().collect(Collectors.joining(", "))}
        }).collect(Collectors.toMap(d -> d[0], d -> d[1])));
        tcpServer = new InetSocketAddress("localhost", port);
    }

    public void addTcpClient(int port, String... messagesToListen) {
        config.set("Transports.TCP.Client", Stream.of(new String[][]{
                {"Enabled", "Always"},
                {"Entity Label", "TCP Client"},
                {"Server - Port", "" + port},
                {"Server - Address", "localhost"},
                {"Transports", Arrays.asList(messagesToListen).stream().collect(Collectors.joining(", "))}
        }).collect(Collectors.toMap(d -> d[0], d -> d[1])));
    }

    public void removeSections(String... sectionNames) {
        List<String> sectionsToRemove = Arrays.asList(sectionNames);

        config.removeSections(sec -> {
            String name = sec.getName();
            if (name.contains("/"))
                name = name.substring(0, name.indexOf("/"));
            return sectionsToRemove.contains(name);
        });
    }

    public void waitFor() throws  InterruptedException {
        duneSimulator.waitFor();
    }

    public void startSimulator() throws IOException {
        int num = 1;
        File configFile = new File(configDir, String.format("%s-%04d.ini", "simulator", num));

        while (configFile.exists()) {
            num++;
            configFile = new File(configDir, String.format("%s-%04d.ini", "simulator", num));
        }

        config.save(configFile);
        configFile.deleteOnExit();

        String cmd = new File(duneDir, "dune").getCanonicalPath();
        duneCommand = new String[] {cmd, "-c",
                configFile.getName().substring(0, configFile.getName().indexOf(".")), "-p", "Simulation"};

        duneSimulator = new ProcessBuilder().command(duneCommand)
                .directory(duneDir)
                .redirectErrorStream(true)
                .start();

        BufferedReader output = new BufferedReader(new InputStreamReader(duneSimulator.getInputStream()));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stop();
        }));

        CompletableFuture.runAsync(() -> {
            while (duneSimulator.isAlive()) {
                try {
                    System.out.println(output.readLine());
                }
                catch (Exception e) {}
            }
        });
    }

    public DuneSimulator(File duneBuildDir, File duneConfigDir, String configuration) throws Exception {
        this.duneDir = duneBuildDir;
        this.configDir = duneConfigDir;
        config = new DuneIni(new File(duneConfigDir, configuration+".ini"));
    }

    public void stop() {
        if (duneSimulator != null)
            duneSimulator.destroy();
    }

    public static void main(String[] args) throws Exception {
        File duneBinDir = new File("/home/zp/workspace/dune/build");
        File duneCfgDir = new File("/home/zp/workspace/dune/source/etc");
        System.out.println(System.getProperty("JAVA_HOME"));
        DuneSimulator sim = null;
        for (int i = 0; i < 100; i++) {
            try {
                sim = new DuneSimulator(duneBinDir, duneCfgDir, "development/xp1-soi");
                sim.removeSections("Transports.TCP.Server", "Transports.UDP", "Transports.Announce", "Transports.Discovery", "Transports.HTTP", "Transports.FTP", "Transports.Logging");
                sim.setSimExecutionFreq(20);
                sim.addTcpClient(8050, "EstimatedState",
                        "LogBookEntry", "EntityState", "VehicleState", "PlanControlState");
                //sim.setTimeMultiplier(100);
                sim.startSimulator();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        sim.waitFor();
    }
}

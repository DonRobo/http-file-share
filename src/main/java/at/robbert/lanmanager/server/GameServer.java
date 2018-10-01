package at.robbert.lanmanager.server;

import java.io.File;

import org.eclipse.jetty.server.Server;

public class GameServer {

	public static void main(final String[] args) throws Exception {
		if (args.length != 1) {
			throw new IllegalArgumentException("Wrong number of arguments: " + args.length);
		}

		final Server server = new Server(8080);
		server.setHandler(new LanHandler(new File(args[0])));
		server.start();
		server.join();
	}
}

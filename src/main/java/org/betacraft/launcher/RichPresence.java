package org.betacraft.launcher;

import java.net.URLDecoder;

import net.arikia.dev.drpc.DiscordEventHandlers;
import net.arikia.dev.drpc.DiscordRPC;
import net.arikia.dev.drpc.DiscordRichPresence;

/**
 * Discord status of a running game. What it shows is set in the launcher (Discord settings):
 *   line 1: the instance and version   ("RISUGAMI (b1.7.3-modloader)")
 *   line 2: the server or singleplayer ("On server: Modern Beta SMP")  + "(19 of 400)" from the player count
 */
public class RichPresence {
	public static final String SHOW_INSTANCE = "discordShowInstance";
	public static final String SHOW_SERVER = "discordShowServer";
	public static final String SHOW_PLAYERS = "discordShowPlayers";
	public static final String SHOW_NICK = "discordShowNick";

	private static String user, uuid, version, instance, server, serverName;
	private static long started;
	private static ServerStatus.Info status;

	public static boolean flag(String key) {
		return BC.getFlag(key, true);
	}

	/** Starts the status for this game, called once in the game process */
	public static void start(String user, String uuid, String version, String instance, String server) {
		RichPresence.uuid = uuid;
		// JNA sends strings in the system code page by default (cp1251 on Russian Windows),
		// Discord wants UTF-8 - otherwise Cyrillic shows up as question marks
		System.setProperty("jna.encoding", "UTF-8");

		RichPresence.user = user;
		RichPresence.version = version;
		RichPresence.instance = instance;
		RichPresence.server = server;
		RichPresence.started = System.currentTimeMillis() / 1000;

		String name = System.getProperty("zloylauncher.serverName");
		if (name != null) {
			try {
				serverName = URLDecoder.decode(name, "UTF-8");
			} catch (Exception e) {
				serverName = name;
			}
		}

		DiscordRPC.discordInitialize(BC.getDiscordAppId(), new DiscordEventHandlers(), true);
		update();

		if (server != null && flag(SHOW_SERVER) && flag(SHOW_PLAYERS)) {
			Thread t = new Thread("RPC-Server-Status") {
				public void run() {
					while (true) {
						try {
							status = ServerStatus.query(server());
							if (serverName == null && status.listName != null) serverName = status.listName;
							update();
							Thread.sleep(60000L);
						} catch (Throwable t) {
							return;
						}
					}
				}
			};
			t.setDaemon(true);
			t.start();
		}
	}

	private static String server() {
		return server;
	}

	static synchronized void update() {
		DiscordRPC.discordUpdatePresence(build());
	}

	static DiscordRichPresence build() {
		DiscordRichPresence p = new DiscordRichPresence();
		p.startTimestamp = started;
		p.largeImageKey = BC.getDiscordImageKey();
		p.largeImageText = BC.NAME + " " + Launcher.VERSION;
		if (flag(SHOW_NICK) && user != null) {
			// the player's head as the small picture, Steve for offline accounts
			boolean licensed = uuid != null && uuid.replace("-", "").length() == 32;
			p.smallImageKey = "https://mc-heads.net/avatar/" + (licensed ? uuid.replace("-", "") : "MHF_Steve") + "/64";
			p.smallImageText = user;
		}

		p.details = flag(SHOW_INSTANCE) && instance != null
				? instance + " (" + version + ")"
				: String.format(Lang.WRAP_VERSION, version);

		if (server != null && flag(SHOW_SERVER)) {
			String shown = serverName != null && serverName.length() > 0 ? serverName : SavedServers.host(server);
			p.state = String.format(Lang.RPC_ON_SERVER, shown);
			if (flag(SHOW_PLAYERS) && status != null && status.known() && status.max > 0) {
				// Discord shows these as "(19 of 400)" after the state
				p.partyId = "zl-" + server;
				p.partySize = Math.max(1, status.online);
				p.partyMax = status.max;
			}
		} else if (flag(SHOW_NICK) && user != null) {
			p.state = String.format(Lang.WRAP_USER, user);
		} else {
			p.state = server != null ? Lang.RPC_MULTIPLAYER : Lang.RPC_SINGLEPLAYER;
		}
		return p;
	}
}

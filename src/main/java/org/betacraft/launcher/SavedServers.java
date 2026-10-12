package org.betacraft.launcher;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** The user's own server list, kept in launcher/servers.json */
public class SavedServers {
	/** Version value meaning "detect it when joining" */
	public static final String AUTO = "auto";

	public static class Server {
		/** Stays the same when the server is renamed or moves, names its icon file */
		public String id;
		public String name;
		public String address;
		/** A version name, or AUTO */
		public String version = AUTO;
		/** Last auto-detected version and how it was found, so joining doesn't detect every time */
		public String detectedVersion;
		public String detectedHow;
		public long lastJoined;

		public Server() {
			this.id = java.util.UUID.randomUUID().toString();
		}

		public Server(String name, String address, String version) {
			this();
			this.name = name;
			this.address = address;
			this.version = version == null ? AUTO : version;
		}

		public boolean isAuto() {
			return version == null || AUTO.equals(version);
		}

		/** The version to join with, null if it still has to be detected */
		public String effectiveVersion() {
			return isAuto() ? detectedVersion : version;
		}

		/** The server's own icon; when the file doesn't exist the gray pack icon is shown */
		public File iconFile() {
			return new File(BC.get() + "launcher" + File.separator + "server-icons", id + ".png");
		}

		/** host:port, with the default port added */
		public String fullAddress() {
			return withPort(address);
		}
	}

	private static class FileFormat {
		int format = 1;
		List<Server> servers = new ArrayList<Server>();
	}

	public static List<Server> servers = new ArrayList<Server>();

	public static File getFile() {
		return new File(BC.get() + "launcher", "servers.json");
	}

	public static synchronized void load() {
		try {
			File f = getFile();
			if (!f.isFile()) {
				servers = new ArrayList<Server>();
				return;
			}
			FileFormat ff = Util.gson.fromJson(new String(Util.readBytes(f), "UTF-8"), FileFormat.class);
			List<Server> loaded = new ArrayList<Server>();
			if (ff != null && ff.servers != null) {
				for (Server s : ff.servers) {
					if (s == null || s.address == null || s.address.trim().length() == 0) continue;
					if (s.id == null) s.id = java.util.UUID.randomUUID().toString();
					loaded.add(s);
				}
			}
			servers = loaded;
		} catch (Throwable t) {
			System.err.println("Couldn't read servers.json");
			t.printStackTrace();
		}
	}

	public static synchronized void save() {
		try {
			File f = getFile();
			f.getParentFile().mkdirs();
			FileFormat ff = new FileFormat();
			ff.servers = servers;
			File temp = new File(f.getPath() + ".tmp-" + System.nanoTime());
			Util.writeBytes(temp, Util.gsonPretty.toJson(ff).getBytes("UTF-8"));
			Files.move(temp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	public static String withPort(String address) {
		if (address == null) return "";
		String a = address.trim();
		// a bare IPv6 address has colons of its own
		if (a.startsWith("[") ? !a.contains("]:") : !a.contains(":")) a = a + ":25565";
		return a;
	}

	public static String host(String address) {
		String a = withPort(address);
		return a.substring(0, a.lastIndexOf(':')).replace("[", "").replace("]", "");
	}

	public static int port(String address) {
		String a = withPort(address);
		try {
			return Integer.parseInt(a.substring(a.lastIndexOf(':') + 1).trim());
		} catch (NumberFormatException e) {
			return 25565;
		}
	}

	/** Same server, ignoring case and the default port */
	public static boolean sameAddress(String a, String b) {
		return withPort(a).equalsIgnoreCase(withPort(b));
	}

	public static Server find(String address) {
		for (Server s : servers) {
			if (sameAddress(s.address, address)) return s;
		}
		return null;
	}
}

package org.betacraft.launcher;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.betacraft.launcher.Release.VersionInfo;

/**
 * Guesses which game version a server wants, without logging in to it:
 * 1. BetaCraft's public server list knows the exact version of the servers listed there;
 * 2. otherwise the server itself is asked: the server-list ping (b1.8 and newer), then the
 *    login handshake of beta 1.5+, alpha/early beta and classic. Old servers only reveal
 *    the era, so the most common version of that era is picked.
 */
public class ServerVersionDetector {
	private static final int TIMEOUT = 4000;
	private static final String PROBE_NAME = "ZloyPing";
	private static final Pattern JOIN_LINK = Pattern.compile("join://([^/\"]+)/([^/\"]*)/([^/\"]*)/([^\"<]*)");

	public static class Result {
		/** Version name to launch, null when nothing usable was found */
		public final String version;
		/** Human readable explanation */
		public final String how;
		/** True when the version is known exactly, false for "most likely" */
		public final boolean exact;

		Result(String version, String how, boolean exact) {
			this.version = version;
			this.how = how;
			this.exact = exact;
		}
	}

	public static Result detect(String address) {
		Result r = fromBetaCraftList(address);
		if (r != null && r.version != null) return r;

		String host = SavedServers.host(address);
		int port = SavedServers.port(address);

		Result ping = legacyPing(host, port);
		if (ping != null) return ping;

		Result beta = betaHandshake(host, port);
		if (beta != null) return beta;

		Result alpha = alphaHandshake(host, port);
		if (alpha != null) return alpha;

		return new Result(null, Lang.DETECT_FAILED, false);
	}

	// ---- 1. BetaCraft server list ----

	static Result fromBetaCraftList(String address) {
		try {
			HttpURLConnection con = (HttpURLConnection) new URL("http://api.betacraft.uk/v2/legacy_server_list").openConnection();
			con.setRequestMethod("POST");
			con.setDoInput(true);
			con.setDoOutput(true);
			con.setConnectTimeout(TIMEOUT);
			con.setReadTimeout(TIMEOUT * 2);
			InputStream in = con.getInputStream();
			Scanner s = new Scanner(in, "UTF-8");
			StringBuilder html = new StringBuilder();
			while (s.hasNextLine()) html.append(s.nextLine()).append('\n');
			s.close();

			Matcher m = JOIN_LINK.matcher(html);
			while (m.find()) {
				if (!SavedServers.sameAddress(m.group(1), address)) continue;
				String protocol = m.group(3);
				String preferred = URLDecoder.decode(m.group(4).trim(), "UTF-8");

				if (Release.getReleaseByName(preferred) != null) {
					return new Result(preferred, String.format(Lang.DETECT_BY_LIST, preferred), true);
				}
				String byProtocol = releaseForProtocol(protocol);
				if (byProtocol != null) {
					return new Result(byProtocol, String.format(Lang.DETECT_BY_LIST, byProtocol), true);
				}
				return new Result(null, String.format(Lang.DETECT_UNSUPPORTED, preferred), false);
			}
		} catch (Throwable t) {
			System.out.println("BetaCraft server list unavailable for detection: " + t);
		}
		return null;
	}

	/** First plain (not modded) version speaking the given protocol, e.g. "beta_14" -> b1.7.3 */
	static String releaseForProtocol(String protocol) {
		if (protocol == null || protocol.length() == 0) return null;
		String fallback = null;
		for (Release r : Release.versions) {
			VersionInfo info = r.getInfo();
			if (info == null || !protocol.equals(info.getProtocol())) continue;
			if (!info.isCustom() && ModsRepository.getMod(r.getName()) == null) return r.getName();
			if (fallback == null) fallback = r.getName();
		}
		return fallback;
	}

	static String existing(String... names) {
		for (String n : names) {
			if (Release.getReleaseByName(n) != null) return n;
		}
		return null;
	}

	// ---- 2. asking the server ----

	private static Socket connect(String host, int port) throws Exception {
		Socket s = new Socket();
		s.connect(new InetSocketAddress(host, port), TIMEOUT);
		s.setSoTimeout(TIMEOUT);
		return s;
	}

	/** Server list ping: b1.8 - 1.3 answer "motd§online§max", 1.4+ answer "§1\0protocol\0version\0..." */
	static Result legacyPing(String host, int port) {
		Socket s = null;
		try {
			s = connect(host, port);
			DataOutputStream out = new DataOutputStream(s.getOutputStream());
			DataInputStream in = new DataInputStream(s.getInputStream());
			out.write(new byte[] {(byte) 0xFE, 0x01});
			out.flush();

			if (in.read() != 0xFF) return null;
			String reply = readString16(in, 512);
			if (reply == null) return null;

			if (reply.startsWith("§1\u0000")) {
				String[] parts = reply.split("\u0000");
				String gameVersion = parts.length > 2 ? parts[2] : "";
				String name = matchVersionName(gameVersion);
				if (name != null) return new Result(name, String.format(Lang.DETECT_BY_PING, gameVersion), true);
				return new Result(null, String.format(Lang.DETECT_UNSUPPORTED, gameVersion), false);
			}
			if (reply.contains("§")) {
				String name = existing("b1.8.1", "1.2.5", "1.0");
				return new Result(name, Lang.DETECT_ERA_B18, false);
			}
			// a kick message without §: an older server that didn't like the ping
			return null;
		} catch (Throwable t) {
			return null;
		} finally {
			close(s);
		}
	}

	/** Beta 1.5 - 1.7.3 handshake (UTF-16 strings); a classic server kicks with its own packet instead */
	static Result betaHandshake(String host, int port) {
		Socket s = null;
		try {
			s = connect(host, port);
			DataOutputStream out = new DataOutputStream(s.getOutputStream());
			DataInputStream in = new DataInputStream(s.getInputStream());
			out.write(0x02);
			out.writeShort(PROBE_NAME.length());
			out.writeChars(PROBE_NAME);
			out.flush();

			int id = in.read();
			if (id == 0x0E) {
				return new Result(existing("c0.30-c-1900", "c0.30-c-renew"), Lang.DETECT_ERA_CLASSIC, false);
			}
			if (id != 0x02) return null;
			// the connection hash: "-" or a hex string, read as UTF-16 only beta 1.5+ gives a sane answer
			if (readString16(in, 64) == null) return null;
			return new Result(existing("b1.7.3"), Lang.DETECT_ERA_BETA, false);
		} catch (Throwable t) {
			return null;
		} finally {
			close(s);
		}
	}

	/** Alpha and beta up to 1.4 used modified UTF-8 strings */
	static Result alphaHandshake(String host, int port) {
		Socket s = null;
		try {
			s = connect(host, port);
			DataOutputStream out = new DataOutputStream(s.getOutputStream());
			DataInputStream in = new DataInputStream(s.getInputStream());
			out.write(0x02);
			out.writeUTF(PROBE_NAME);
			out.flush();

			if (in.read() != 0x02) return null;
			String hash = in.readUTF();
			if (hash.length() > 64) return null;
			return new Result(existing("a1.2.6", "b1.4_01", "a1.1.2_01"), Lang.DETECT_ERA_ALPHA, false);
		} catch (Throwable t) {
			return null;
		} finally {
			close(s);
		}
	}

	private static String readString16(DataInputStream in, int maxLength) throws Exception {
		int len = in.readShort();
		if (len < 0 || len > maxLength) return null;
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < len; i++) b.append(in.readChar());
		return b.toString();
	}

	/** "1.5.2" or "CraftBukkit 1.4.7" -> a version the launcher has */
	static String matchVersionName(String gameVersion) {
		if (gameVersion == null) return null;
		if (Release.getReleaseByName(gameVersion.trim()) != null) return gameVersion.trim();
		Matcher m = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)").matcher(gameVersion);
		while (m.find()) {
			if (Release.getReleaseByName(m.group(1)) != null) return m.group(1);
		}
		return null;
	}

	private static void close(Socket s) {
		try {
			if (s != null) s.close();
		} catch (Throwable t) {}
	}
}

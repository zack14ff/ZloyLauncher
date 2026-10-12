package org.betacraft.launcher;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How many players are on a server. b1.8 and newer answer the server-list ping themselves;
 * older servers don't have one, so BetaCraft's public list is used when the server is listed there.
 */
public class ServerStatus {
	public static class Info {
		public int online = -1, max = -1;
		public String motd;
		/** Name the server has in BetaCraft's list, if it's there */
		public String listName;

		public boolean known() {
			return online >= 0;
		}
	}

	private static final Pattern ENTRY = Pattern.compile(
			"href=\"join://([^/\"]+)/[^\"]*\">.*?server__name\">(?:\\s*<span class=\"server__version\">[^<]*</span>)?([^<]*)</span>.*?server__players\">\\((\\d+)\\s*/\\s*(\\d+)\\)",
			Pattern.DOTALL);

	private static Map<String, Info> listCache = null;
	private static long listTime = 0;

	/**
	 * Listed servers are taken from the list: pinging a pre-b1.8 server works, but leaves
	 * a "Bad packet id" error in its log every time.
	 */
	public static Info query(String address) {
		Info listed = fromList(address);
		if (listed != null) return listed;
		Info ping = ping(SavedServers.host(address), SavedServers.port(address));
		return ping == null ? new Info() : ping;
	}

	/** Legacy server-list ping, works on b1.8 - 1.6 and on modern servers too */
	static Info ping(String host, int port) {
		Socket s = null;
		try {
			s = new Socket(Proxy.NO_PROXY);
			s.connect(new InetSocketAddress(host, port), 3000);
			s.setSoTimeout(3000);
			DataOutputStream out = new DataOutputStream(s.getOutputStream());
			DataInputStream in = new DataInputStream(s.getInputStream());
			out.write(new byte[] {(byte) 0xFE, 0x01});
			out.flush();
			if (in.read() != 0xFF) return null;
			int len = in.readShort();
			if (len < 0 || len > 1024) return null;
			StringBuilder b = new StringBuilder();
			for (int i = 0; i < len; i++) b.append(in.readChar());
			String reply = b.toString();

			Info info = new Info();
			if (reply.startsWith("§1\u0000")) {
				String[] p = reply.split("\u0000");
				if (p.length < 6) return null;
				info.motd = p[3];
				info.online = Integer.parseInt(p[4]);
				info.max = Integer.parseInt(p[5]);
				return info;
			}
			String[] p = reply.split("§");
			if (p.length < 3) return null;
			info.motd = p[0];
			info.online = Integer.parseInt(p[p.length - 2]);
			info.max = Integer.parseInt(p[p.length - 1]);
			return info;
		} catch (Throwable t) {
			return null;
		} finally {
			try {
				if (s != null) s.close();
			} catch (Throwable t) {}
		}
	}

	static Info fromList(String address) {
		Map<String, Info> list = betaCraftList();
		return list == null ? null : list.get(SavedServers.withPort(address).toLowerCase());
	}

	/** BetaCraft's public list, cached for a minute */
	static synchronized Map<String, Info> betaCraftList() {
		if (listCache != null && System.currentTimeMillis() - listTime < 60000L) return listCache;
		try {
			HttpURLConnection con = (HttpURLConnection) new URL("http://api.betacraft.uk/v2/legacy_server_list").openConnection(Proxy.NO_PROXY);
			con.setRequestMethod("POST");
			con.setDoOutput(true);
			con.setConnectTimeout(5000);
			con.setReadTimeout(10000);
			InputStream in = con.getInputStream();
			Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A");
			String html = s.hasNext() ? s.next() : "";
			s.close();

			Map<String, Info> map = new HashMap<String, Info>();
			Matcher m = ENTRY.matcher(html);
			while (m.find()) {
				Info info = new Info();
				info.listName = unescape(m.group(2).trim());
				info.online = Integer.parseInt(m.group(3));
				info.max = Integer.parseInt(m.group(4));
				map.put(SavedServers.withPort(m.group(1)).toLowerCase(), info);
			}
			listCache = map;
			listTime = System.currentTimeMillis();
		} catch (Throwable t) {
			System.out.println("BetaCraft server list unavailable: " + t);
		}
		return listCache;
	}

	/** Server icon BetaCraft keeps for listed servers, null if there is none */
	public static byte[] betaCraftIcon(String address) {
		try {
			HttpURLConnection con = (HttpURLConnection) new URL("http://api.betacraft.uk/v2/server_icon?socket="
					+ SavedServers.withPort(address)).openConnection(Proxy.NO_PROXY);
			con.setConnectTimeout(5000);
			con.setReadTimeout(10000);
			if (con.getResponseCode() != 200) return null;
			byte[] data = LegacyProxy.readAll(con.getInputStream());
			return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data)) == null ? null : data;
		} catch (Throwable t) {
			return null;
		}
	}

	private static String unescape(String s) {
		return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'");
	}
}

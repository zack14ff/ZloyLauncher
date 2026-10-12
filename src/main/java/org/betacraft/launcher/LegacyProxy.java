package org.betacraft.launcher;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.imageio.ImageIO;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A tiny HTTP proxy running inside the game process, standing in for the defunct betacraft.uk:11705 one.
 * Old Minecraft versions fetch sounds and skins from Mojang addresses that died years ago:
 * - sounds/music from s3.amazonaws.com/MinecraftResources (and www.minecraft.net/resources for the oldest),
 *   served here from Mojang's current asset storage (the "pre-1.6" index);
 * - skins and capes from s3.amazonaws.com/MinecraftSkins, skins.minecraft.net, www.minecraft.net/skin,
 *   served here from the Mojang profile API (modern 64x64 skins are cut to the old 64x32 layout);
 * - joinserver.jsp for online-mode servers, answered through the current session server.
 * Everything else is passed straight through.
 */
public class LegacyProxy {
	static final String ASSET_INDEX_URL = "https://launchermeta.mojang.com/v1/packages/3d8e55480977e32acd9844e545177e69a52f594b/pre-1.6.json";
	static final String RESOURCES_URL = "https://resources.download.minecraft.net/";
	static final String[] SOUND_FOLDERS = {"sound/", "sound3/", "newsound/", "music/", "newmusic/", "streaming/"};

	private final String username, accessToken, uuid;
	private Map<String, String[]> assets; // key -> {hash, size}
	private final Map<String, byte[]> skinCache = new HashMap<String, byte[]>();
	private final Map<String, byte[]> capeCache = new HashMap<String, byte[]>();

	private LegacyProxy(String username, String accessToken, String uuid) {
		this.username = username;
		this.accessToken = accessToken;
		this.uuid = uuid;
	}

	/** Starts the proxy and points this JVM's HTTP traffic at it. Returns the port, or -1 on failure. */
	public static int start(String username, String accessToken, String uuid) {
		try {
			final LegacyProxy proxy = new LegacyProxy(username, accessToken, uuid);
			final ServerSocket server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
			Thread acceptor = new Thread("ZloyProxy") {
				public void run() {
					while (true) {
						try {
							final Socket s = server.accept();
							Thread t = new Thread("ZloyProxy-Request") {
								public void run() {
									proxy.handle(s);
								}
							};
							t.setDaemon(true);
							t.start();
						} catch (Throwable t) {
							t.printStackTrace();
						}
					}
				}
			};
			acceptor.setDaemon(true);
			acceptor.start();

			int port = server.getLocalPort();
			System.setProperty("http.proxyHost", "127.0.0.1");
			System.setProperty("http.proxyPort", Integer.toString(port));
			System.out.println("Skin & sound proxy running on 127.0.0.1:" + port);
			return port;
		} catch (Throwable t) {
			System.err.println("Couldn't start the skin & sound proxy");
			t.printStackTrace();
			return -1;
		}
	}

	// ---- HTTP plumbing ----

	private void handle(Socket s) {
		try {
			s.setSoTimeout(30000);
			InputStream in = s.getInputStream();
			OutputStream out = s.getOutputStream();

			String requestLine = readLine(in);
			if (requestLine == null || requestLine.length() == 0) return;
			Map<String, String> headers = new HashMap<String, String>();
			String line;
			while ((line = readLine(in)) != null && line.length() > 0) {
				int colon = line.indexOf(':');
				if (colon > 0) headers.put(line.substring(0, colon).trim().toLowerCase(), line.substring(colon + 1).trim());
			}
			String[] parts = requestLine.split(" ");
			String method = parts[0];
			String target = parts.length > 1 ? parts[1] : "/";

			byte[] body = null;
			if (headers.containsKey("content-length")) {
				int len = Integer.parseInt(headers.get("content-length"));
				body = new byte[len];
				int read = 0;
				while (read < len) {
					int n = in.read(body, read, len - read);
					if (n < 0) break;
					read += n;
				}
			}

			URL url = target.startsWith("http") ? new URL(target) : new URL("http://" + headers.get("host") + target);
			Response r = route(method, url, headers, body);
			write(out, r);
		} catch (Throwable t) {
			System.err.println("Proxy request failed: " + t);
		} finally {
			try {
				s.close();
			} catch (Throwable t) {}
		}
	}

	static class Response {
		int code;
		String type;
		byte[] body;

		Response(int code, String type, byte[] body) {
			this.code = code;
			this.type = type;
			this.body = body == null ? new byte[0] : body;
		}

		static Response text(String s) {
			try {
				return new Response(200, "text/plain", s.getBytes("UTF-8"));
			} catch (Exception e) {
				return notFound();
			}
		}

		static Response notFound() {
			return new Response(404, "text/plain", new byte[0]);
		}
	}

	private static void write(OutputStream out, Response r) throws Exception {
		String status = r.code == 200 ? "OK" : r.code == 404 ? "Not Found" : "Status";
		String head = "HTTP/1.0 " + r.code + " " + status + "\r\n"
				+ "Content-Type: " + (r.type == null ? "application/octet-stream" : r.type) + "\r\n"
				+ "Content-Length: " + r.body.length + "\r\n"
				+ "Connection: close\r\n\r\n";
		out.write(head.getBytes("ISO-8859-1"));
		out.write(r.body);
		out.flush();
	}

	private static String readLine(InputStream in) throws Exception {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		int c;
		while ((c = in.read()) != -1) {
			if (c == '\n') break;
			if (c != '\r') b.write(c);
		}
		if (c == -1 && b.size() == 0) return null;
		return new String(b.toByteArray(), "ISO-8859-1");
	}

	// ---- routing ----

	Response route(String method, URL url, Map<String, String> headers, byte[] body) {
		String host = url.getHost().toLowerCase();
		String path = url.getPath();
		Map<String, String> query = parseQuery(url.getQuery());

		try {
			// sounds and music
			if (host.equals("s3.amazonaws.com") && path.startsWith("/MinecraftResources")) {
				String key = decode(path.substring("/MinecraftResources".length()));
				if (key.startsWith("/")) key = key.substring(1);
				return key.length() == 0 ? resourceListXml() : resourceFile(key);
			}
			if (host.endsWith("minecraft.net") && path.startsWith("/resources")) {
				String key = decode(path.substring("/resources".length()));
				if (key.startsWith("/")) key = key.substring(1);
				return key.length() == 0 ? resourceListText() : resourceFile(key);
			}

			// skins and capes
			if (path.startsWith("/MinecraftSkins/") || path.startsWith("/skin/")) {
				return png(skin(nameFromPath(path)));
			}
			if (path.startsWith("/MinecraftCloaks/")) {
				return png(cape(nameFromPath(path)));
			}
			if (path.startsWith("/cloak/get.jsp")) {
				return png(cape(query.get("user")));
			}

			// online-mode servers
			if (path.endsWith("/joinserver.jsp")) {
				return Response.text(joinServer(query.get("serverId")) ? "OK" : "Bad login");
			}
			if (path.endsWith("/haspaid.jsp")) {
				return Response.text("true");
			}
		} catch (Throwable t) {
			System.err.println("Proxy couldn't serve " + url + ": " + t);
			return Response.notFound();
		}
		return passThrough(method, url, headers, body);
	}

	private static String nameFromPath(String path) {
		String name = path.substring(path.lastIndexOf('/') + 1);
		if (name.toLowerCase().endsWith(".png")) name = name.substring(0, name.length() - 4);
		return decode(name);
	}

	private static Response png(byte[] data) {
		return data == null ? Response.notFound() : new Response(200, "image/png", data);
	}

	private Response passThrough(String method, URL url, Map<String, String> headers, byte[] body) {
		try {
			HttpURLConnection con = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
			con.setRequestMethod(method);
			con.setConnectTimeout(10000);
			con.setReadTimeout(20000);
			for (Entry<String, String> h : headers.entrySet()) {
				String k = h.getKey();
				if (k.equals("host") || k.startsWith("proxy-") || k.equals("connection") || k.equals("content-length")) continue;
				con.setRequestProperty(k, h.getValue());
			}
			if (body != null && body.length > 0) {
				con.setDoOutput(true);
				con.getOutputStream().write(body);
			}
			int code = con.getResponseCode();
			InputStream in = code >= 400 ? con.getErrorStream() : con.getInputStream();
			return new Response(code, con.getContentType(), in == null ? null : readAll(in));
		} catch (Throwable t) {
			return Response.notFound();
		}
	}

	// ---- sounds ----

	private synchronized Map<String, String[]> assets() throws Exception {
		if (assets != null) return assets;
		File cached = new File(BC.get() + "launcher" + File.separator + "assets", "pre-1.6.json");
		byte[] json = null;
		try {
			json = download(ASSET_INDEX_URL);
			cached.getParentFile().mkdirs();
			Util.writeBytes(cached, json);
		} catch (Throwable t) {
			// offline: use the copy from last time
			if (cached.isFile()) json = Util.readBytes(cached);
			else throw new Exception("asset index unavailable", t);
		}

		Map<String, String[]> map = new HashMap<String, String[]>();
		JsonObject objects = new JsonParser().parse(new String(json, "UTF-8")).getAsJsonObject().getAsJsonObject("objects");
		for (Entry<String, JsonElement> e : objects.entrySet()) {
			if (!isSound(e.getKey())) continue;
			JsonObject o = e.getValue().getAsJsonObject();
			map.put(e.getKey(), new String[] {o.get("hash").getAsString(), o.get("size").getAsString()});
		}
		System.out.println("Sound index: " + map.size() + " files");
		assets = map;
		return map;
	}

	private static boolean isSound(String key) {
		for (String f : SOUND_FOLDERS) {
			if (key.startsWith(f)) return true;
		}
		return false;
	}

	/** The S3 bucket listing beta and later versions read */
	private Response resourceListXml() throws Exception {
		StringBuilder b = new StringBuilder();
		b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		b.append("<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"><Name>MinecraftResources</Name>");
		b.append("<Prefix></Prefix><Marker></Marker><MaxKeys>1000</MaxKeys><IsTruncated>false</IsTruncated>");
		for (Entry<String, String[]> e : sortedAssets()) {
			b.append("<Contents><Key>").append(escapeXml(e.getKey())).append("</Key>");
			b.append("<LastModified>2011-01-01T00:00:00.000Z</LastModified>");
			b.append("<ETag>\"").append(e.getValue()[0]).append("\"</ETag>");
			b.append("<Size>").append(e.getValue()[1]).append("</Size><StorageClass>STANDARD</StorageClass></Contents>");
		}
		b.append("</ListBucketResult>");
		return new Response(200, "application/xml", b.toString().getBytes("UTF-8"));
	}

	/** The plain list Indev/Infdev/early Alpha read: "path,size,date" per line */
	private Response resourceListText() throws Exception {
		StringBuilder b = new StringBuilder();
		for (Entry<String, String[]> e : sortedAssets()) {
			b.append(e.getKey()).append(',').append(e.getValue()[1]).append(",1\n");
		}
		return Response.text(b.toString());
	}

	private List<Entry<String, String[]>> sortedAssets() throws Exception {
		List<Entry<String, String[]>> list = new ArrayList<Entry<String, String[]>>(assets().entrySet());
		java.util.Collections.sort(list, new java.util.Comparator<Entry<String, String[]>>() {
			public int compare(Entry<String, String[]> a, Entry<String, String[]> b) {
				return a.getKey().compareTo(b.getKey());
			}
		});
		return list;
	}

	private Response resourceFile(String key) throws Exception {
		String[] a = assets().get(key);
		if (a == null) return Response.notFound();
		String hash = a[0];
		String sub = hash.substring(0, 2) + File.separator + hash;

		// already on disk: our cache, or the official launcher's
		File ours = new File(BC.get() + "launcher" + File.separator + "assets" + File.separator + "objects", sub);
		String appData = System.getenv("APPDATA");
		File official = appData == null ? null : new File(appData, ".minecraft" + File.separator + "assets" + File.separator + "objects" + File.separator + sub);
		for (File f : new File[] {ours, official}) {
			if (f != null && f.isFile() && Long.toString(f.length()).equals(a[1])) {
				return new Response(200, "application/octet-stream", Util.readBytes(f));
			}
		}

		byte[] data = download(RESOURCES_URL + hash.substring(0, 2) + "/" + hash);
		ours.getParentFile().mkdirs();
		Util.writeBytes(ours, data);
		return new Response(200, "application/octet-stream", data);
	}

	// ---- skins ----

	private byte[] skin(String name) {
		return texture(name, "SKIN", skinCache);
	}

	private byte[] cape(String name) {
		return texture(name, "CAPE", capeCache);
	}

	private byte[] texture(String name, String kind, Map<String, byte[]> cache) {
		if (name == null || name.length() == 0) return null;
		String key = name.toLowerCase();
		synchronized (cache) {
			if (cache.containsKey(key)) return cache.get(key);
		}
		byte[] result = null;
		try {
			String id = name.equalsIgnoreCase(username) && uuid != null && uuid.length() == 32 ? uuid : lookupUuid(name);
			if (id != null) {
				String url = textureUrl(id, kind);
				if (url != null) result = toLegacyLayout(download(url.replace("http://", "https://")));
			}
		} catch (Throwable t) {
			System.out.println("No " + kind.toLowerCase() + " for " + name + ": " + t);
		}
		synchronized (cache) {
			cache.put(key, result);
		}
		return result;
	}

	private static String lookupUuid(String name) throws Exception {
		HttpURLConnection con = (HttpURLConnection) new URL("https://api.mojang.com/users/profiles/minecraft/" + name).openConnection(Proxy.NO_PROXY);
		con.setConnectTimeout(8000);
		con.setReadTimeout(8000);
		if (con.getResponseCode() != 200) return null;
		JsonObject o = new JsonParser().parse(new String(readAll(con.getInputStream()), "UTF-8")).getAsJsonObject();
		return o.has("id") ? o.get("id").getAsString() : null;
	}

	static String textureUrl(String uuid, String kind) throws Exception {
		byte[] profile = download("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
		JsonObject o = new JsonParser().parse(new String(profile, "UTF-8")).getAsJsonObject();
		for (JsonElement p : o.getAsJsonArray("properties")) {
			JsonObject prop = p.getAsJsonObject();
			if (!"textures".equals(prop.get("name").getAsString())) continue;
			String decoded = new String(java.util.Base64.getDecoder().decode(prop.get("value").getAsString()), "UTF-8");
			JsonObject textures = new JsonParser().parse(decoded).getAsJsonObject().getAsJsonObject("textures");
			if (textures != null && textures.has(kind)) return textures.getAsJsonObject(kind).get("url").getAsString();
		}
		return null;
	}

	/** Old versions only understand 64x32 skins: modern 64x64 ones keep their top half */
	static byte[] toLegacyLayout(byte[] png) throws Exception {
		BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(png));
		if (img == null || img.getWidth() != 64 || img.getHeight() != 64) return png;
		BufferedImage legacy = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		legacy.getGraphics().drawImage(img, 0, 0, null);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(legacy, "png", out);
		return out.toByteArray();
	}

	// ---- online mode ----

	private boolean joinServer(String serverId) {
		if (accessToken == null || accessToken.equals("-") || uuid == null || uuid.length() != 32 || serverId == null) return false;
		try {
			HttpURLConnection con = (HttpURLConnection) new URL("https://sessionserver.mojang.com/session/minecraft/join").openConnection(Proxy.NO_PROXY);
			con.setRequestMethod("POST");
			con.setDoOutput(true);
			con.setRequestProperty("Content-Type", "application/json");
			JsonObject req = new JsonObject();
			req.addProperty("accessToken", accessToken);
			req.addProperty("selectedProfile", uuid);
			req.addProperty("serverId", serverId);
			con.getOutputStream().write(req.toString().getBytes("UTF-8"));
			int code = con.getResponseCode();
			return code == 204 || code == 200;
		} catch (Throwable t) {
			return false;
		}
	}

	// ---- helpers ----

	static byte[] download(String url) throws Exception {
		HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection(Proxy.NO_PROXY);
		con.setConnectTimeout(10000);
		con.setReadTimeout(30000);
		if (con.getResponseCode() != 200) throw new Exception("HTTP " + con.getResponseCode() + " for " + url);
		return readAll(con.getInputStream());
	}

	static byte[] readAll(InputStream in) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int n;
		while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
		in.close();
		return out.toByteArray();
	}

	private static Map<String, String> parseQuery(String q) {
		Map<String, String> map = new HashMap<String, String>();
		if (q == null) return map;
		for (String pair : q.split("&")) {
			int eq = pair.indexOf('=');
			if (eq > 0) map.put(pair.substring(0, eq), decode(pair.substring(eq + 1)));
		}
		return map;
	}

	private static String decode(String s) {
		try {
			return URLDecoder.decode(s.replace("+", "%2B"), "UTF-8");
		} catch (Exception e) {
			return s;
		}
	}

	private static String escapeXml(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}

package org.betacraft.launcher;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import uk.betacraft.auth.Credentials;

/** Player heads for the account lists: the face (and hat layer) cut out of the skin */
public class Heads {
	private static final Map<String, BufferedImage> faces = new ConcurrentHashMap<String, BufferedImage>();
	private static final Set<String> loading = java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
	private static BufferedImage steve;

	/**
	 * The head at the given size. A licensed account's real head is fetched in the background:
	 * until then Steve is returned, and whenRead runs once it's there.
	 */
	/** Keys whose lookup failed (e.g. an offline nickname nobody owns), with the time, so it isn't retried every repaint */
	private static final Map<String, Long> failed = new ConcurrentHashMap<String, Long>();

	/**
	 * Key of the head: the uuid of a license, or "name-nick" for an offline account - in the game an
	 * offline nickname gets the skin of the real player with that name, so the head shows it too.
	 */
	static String key(Credentials c) {
		if (AccountManager.isLicensed(c) && c.local_uuid != null) return c.local_uuid.replace("-", "");
		if (c.username != null && c.username.matches("[A-Za-z0-9_]{3,16}")) return "name-" + c.username.toLowerCase();
		return null;
	}

	public static BufferedImage get(Credentials c, int size, final Runnable whenReady) {
		BufferedImage face = null;
		final String key = key(c);
		if (key != null) {
			face = faces.get(key);
			if (face == null) {
				face = fromDisk(key);
				if (face != null) faces.put(key, face);
			}
			Long failedAt = failed.get(key);
			boolean mayTry = failedAt == null || System.currentTimeMillis() - failedAt > 10L * 60 * 1000;
			if ((face == null || isStale(key)) && mayTry && loading.add(key)) {
				new Thread("Head-" + key) {
					public void run() {
						BufferedImage fresh = download(key);
						loading.remove(key);
						if (fresh == null) {
							failed.put(key, System.currentTimeMillis());
							return;
						}
						failed.remove(key);
						faces.put(key, fresh);
						if (whenReady != null) SwingUtilities.invokeLater(whenReady);
					}
				}.start();
			}
		}
		if (face == null) face = steve();
		return scale(face, size);
	}

	private static File file(String uuid) {
		return new File(BC.get() + "launcher" + File.separator + "heads", uuid + ".png");
	}

	private static BufferedImage fromDisk(String uuid) {
		try {
			File f = file(uuid);
			return f.isFile() ? ImageIO.read(f) : null;
		} catch (Throwable t) {
			return null;
		}
	}

	/** Skins change: refresh a head once a day */
	private static boolean isStale(String uuid) {
		File f = file(uuid);
		return !f.isFile() || System.currentTimeMillis() - f.lastModified() > 24L * 3600 * 1000;
	}

	private static BufferedImage download(String key) {
		try {
			String uuid = key.startsWith("name-") ? LegacyProxy.lookupUuid(key.substring(5)) : key;
			if (uuid == null) return null;
			String url = LegacyProxy.textureUrl(uuid, "SKIN");
			if (url == null) return null;
			BufferedImage skin = ImageIO.read(new ByteArrayInputStream(LegacyProxy.download(url.replace("http://", "https://"))));
			if (skin == null || skin.getWidth() < 64) return null;

			BufferedImage face = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = face.createGraphics();
			g.drawImage(skin.getSubimage(8, 8, 8, 8), 0, 0, null);
			// hat layer; like the game, a fully opaque one counts as "no hat" (old skins filled it with a color)
			BufferedImage hat = skin.getSubimage(40, 8, 8, 8);
			if (hasTransparency(hat)) g.drawImage(hat, 0, 0, null);
			g.dispose();

			File f = file(key);
			f.getParentFile().mkdirs();
			ImageIO.write(face, "png", f);
			return face;
		} catch (Throwable t) {
			System.out.println("Couldn't load the head of " + key + ": " + t);
			return null;
		}
	}

	private static boolean hasTransparency(BufferedImage img) {
		for (int y = 0; y < img.getHeight(); y++)
			for (int x = 0; x < img.getWidth(); x++)
				if ((img.getRGB(x, y) >>> 24) < 128) return true;
		return false;
	}

	private static BufferedImage steve() {
		if (steve == null) {
			try {
				steve = ImageIO.read(Heads.class.getResourceAsStream("/icons/instance/steve.png"));
			} catch (Throwable t) {
				steve = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
			}
		}
		return steve;
	}

	private static BufferedImage scale(BufferedImage src, int size) {
		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.drawImage(src, 0, 0, size, size, null);
		g.dispose();
		return out;
	}
}

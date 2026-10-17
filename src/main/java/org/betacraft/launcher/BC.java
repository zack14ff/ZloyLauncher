package org.betacraft.launcher;

import java.io.File;

import javax.swing.JOptionPane;

import org.betacraft.launcher.Util.PropertyFile;

public class BC {
	/** Launcher name, used in titles and for the data folder */
	public static final String NAME = "ZloyLauncher";
	public static final String DIR_NAME = "zloylauncher";

	/**
	 * Lives in the launcher's own folder and holds the path of the data folder actually in use.
	 * This lets the launcher work in BetaCraft's folder (or any other one) instead of its own.
	 */
	public static final String LINK_FILE = "datadir.txt";

	public static File currentPath;
	public static File SETTINGS_FILE;
	public static PropertyFile SETTINGS;

	// TODO better check this before release
	public static boolean prerelease = false;
	public static boolean nightly = false;

	/** Folder of a portable ZloyLauncher.exe (one with portable.txt next to it); all data is kept there */
	public static final String PORTABLE_DIR = System.getProperty("zloylauncher.portableDir");
	/** The ZloyLauncher.exe the launcher was started from, null when started from the jar */
	public static final String EXE = System.getProperty("zloylauncher.exe");

	public static boolean portable = PORTABLE_DIR != null;
	public static boolean wrapped = false;

	/**
	 * Discord application shown in "Playing ...": its name (ZloyLauncher) is set on discord.com/developers.
	 * Another one can be set in launcher.settings (discordAppId / discordImageKey).
	 */
	public static final String DISCORD_APP_ID = "1561024189014745168";

	private static String resolved = null;

	public static String getDiscordAppId() {
		return getSetting("discordAppId", DISCORD_APP_ID);
	}

	/**
	 * Big picture of the status. Discord also takes a picture by its https address, so this is
	 * the ZloyLauncher logo straight from the repository - whichever Discord application is used.
	 */
	public static final String DISCORD_LOGO_URL = "https://raw.githubusercontent.com/zack14ff/ZloyLauncher/main/discord/logo.png";

	public static String getDiscordImageKey() {
		return getSetting("discordImageKey", DISCORD_LOGO_URL);
	}

	/** A true/false launcher setting, def when it isn't set */
	public static boolean getFlag(String key, boolean def) {
		String value = getSetting(key, null);
		return value == null ? def : Boolean.parseBoolean(value);
	}

	private static String getSetting(String key, String def) {
		String value = SETTINGS == null ? null : SETTINGS.getProperty(key);
		return value == null || value.trim().length() == 0 ? def : value.trim();
	}

	/** The data folder in use, ending with a separator */
	public static String get() {
		if (resolved == null) {
			resolved = resolve();
		}
		return resolved;
	}

	private static String resolve() {
		String own = getOwnDir();
		if (portable) return own;

		String linked = getLinkedDir();
		if (linked != null) {
			File dir = new File(linked);
			dir.mkdirs();
			if (dir.isDirectory()) {
				return withSeparator(dir.getAbsolutePath());
			}
			System.err.println("Linked data folder is unavailable, using own: " + linked);
		}
		return own;
	}

	/** The launcher's own data folder (.zloylauncher) */
	public static String getOwnDir() {
		if (OS.isWindows()) {
			return windowsPath();
		} else {
			return path();
		}
	}

	/** Where BetaCraft Launcher keeps its data on this system */
	public static String getBetaCraftDir() {
		if (OS.isWindows()) {
			return System.getenv("APPDATA") + "\\.betacraft\\";
		} else if (OS.isMac()) {
			return System.getProperty("user.home") + "/Library/Application Support/betacraft/";
		}
		return System.getProperty("user.home") + "/.betacraft/";
	}

	public static boolean betaCraftInstalled() {
		return new File(getBetaCraftDir(), "launcher").isDirectory();
	}

	public static File getLinkFile() {
		return new File(getOwnDir(), LINK_FILE);
	}

	/** Path of the linked data folder, or null when the own folder is used */
	public static String getLinkedDir() {
		File link = getLinkFile();
		if (!link.isFile()) return null;

		String[] lines = Util.read(link, "UTF-8");
		if (lines == null || lines.length == 0) return null;
		String path = lines[0].trim();
		return path.length() == 0 ? null : path;
	}

	public static boolean isLinkedToBetaCraft() {
		return samePath(get(), getBetaCraftDir());
	}

	public static boolean usesOwnDir() {
		return samePath(get(), getOwnDir());
	}

	/** Points the launcher at another data folder, null means its own one. Takes effect after a restart. */
	public static void setLinkedDir(String path) {
		File link = getLinkFile();
		if (path == null || samePath(path, getOwnDir())) {
			link.delete();
		} else {
			link.getParentFile().mkdirs();
			Util.write(link, new String[] {new File(path).getAbsolutePath()}, false);
		}
		// Deliberately not re-resolving here: on quit the launcher still saves to the folder it was using
	}

	/** Re-reads the link, only safe before anything was loaded from the data folder */
	public static void reload() {
		resolved = null;
	}

	public static boolean samePath(String a, String b) {
		if (a == null || b == null) return false;
		try {
			return new File(a).getCanonicalFile().equals(new File(b).getCanonicalFile());
		} catch (Throwable t) {
			return new File(a).getAbsoluteFile().equals(new File(b).getAbsoluteFile());
		}
	}

	private static String withSeparator(String path) {
		return path.endsWith(File.separator) ? path : path + File.separator;
	}

	public static String windowsPath() {
		if (portable) return prefBC() + "\\." + DIR_NAME + "\\";
		return System.getenv("APPDATA") + "\\." + DIR_NAME + "\\";
	}

	public static String path() {
		String folder = null;
		if (OS.isLinux()) {
			folder = System.getProperty("user.home") + "/." + DIR_NAME + "/";
			if (portable) folder = prefBC() + "/." + DIR_NAME + "/";
		} else if (OS.isMac()) {
			folder = System.getProperty("user.home") + "/Library/Application Support/" + DIR_NAME + "/";
			if (portable) folder = prefBC() + "/" + DIR_NAME + "/";
		} else {
			System.out.println("Your operating system is not supported.");
			JOptionPane.showMessageDialog(Window.mainWindow, "Your operating system is not supported ;(", "I'm sorry, but", JOptionPane.WARNING_MESSAGE);
			folder = prefBC() + "/" + DIR_NAME + "/";
		}

		File betacraft = new File(folder);
		betacraft.mkdirs();
		return folder;
	}

	public static String prefBC() {
		if (PORTABLE_DIR != null) return PORTABLE_DIR;
		if (wrapped) return currentPath.getAbsoluteFile().getParentFile().getParentFile().getParent();
		return currentPath.getAbsoluteFile().getParent();
	}

	public static String trimBetaCraftDir(String path) {
		return path.substring(BC.get().length());
	}
}

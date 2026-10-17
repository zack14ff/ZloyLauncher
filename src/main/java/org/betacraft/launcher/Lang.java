package org.betacraft.launcher;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;

import org.betacraft.launcher.Util.PropertyFile;

public class Lang extends JFrame implements LanguageElement {
	public static List<String> locales = new ArrayList<String>();

	static JList list;
	static DefaultListModel listModel;
	static JScrollPane listScroller;
	JButton OKButton;
	static JPanel panel;
	static GridBagConstraints constr;

	public static String locale_id = "1.09_17";

	/** Languages shipped inside the jar, they work offline and know the launcher's own strings */
	public static final String RUSSIAN = "Русский";
	public static final String DEFAULT_LANGUAGE = RUSSIAN;

	public static String getBundledResource(String lang) {
		if (RUSSIAN.equals(lang)) return "/lang/ru.txt";
		return null;
	}

	public static boolean isRussian() {
		return RUSSIAN.equals(BC.SETTINGS.getProperty("language"));
	}

	public Lang() {
		System.out.println("Language selection window opened.");
		this.setIconImage(Window.img);
		this.setMinimumSize(new Dimension(282, 386));

		this.setTitle(Lang.LANG);
		this.setResizable(true);
		final boolean panelnull = panel == null;

		if (panelnull) {
			panel = new JPanel();
			panel.setLayout(new GridBagLayout());
		}

		constr = new GridBagConstraints();

		constr.gridx = 0;
		constr.fill = GridBagConstraints.BOTH;
		constr.insets = new Insets(5, 5, 0, 5);
		constr.gridwidth = GridBagConstraints.RELATIVE;
		constr.gridheight = GridBagConstraints.RELATIVE;
		constr.gridy = 0;
		constr.weightx = 1.0;
		constr.weighty = 1.0;
		try {
			if (locales.isEmpty()) initLang();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		constr.gridy++;
		constr.weighty = GridBagConstraints.RELATIVE;
		constr.gridheight = 1;
		constr.insets = new Insets(5, 5, 5, 5);
		if (panelnull) {
			OKButton = new JButton(Lang.OPTIONS_OK);
			OKButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					setLang();
					Window.lang = null;
				}
			});
			panel.add(OKButton, constr);
		}
		this.add(panel);
		this.pack();
		this.setLocationRelativeTo(Window.mainWindow);
		this.setVisible(true);
	}

	public void update() {
		this.setTitle(Lang.LANG);
		OKButton.setText(Lang.OPTIONS_OK);
	}

	public void setLang() {
		String lang = (String) list.getSelectedValue();

		// Only return if we failed to download without a backup
		if (!download(lang).isPositive()) {
			return;
		}
		BC.SETTINGS.setProperty("language", lang);
		BC.SETTINGS.flushToDisk();
		dispose();
		Launcher.restart(Launcher.javaRuntime.getAbsolutePath());
	}

	public void initLang() throws IOException {
		try {
			URL url = new URL("http://files.betacraft.uk/launcher/assets/lang/" + locale_id + "/list.txt");

			Scanner scanner = new Scanner(url.openStream(), "UTF-8");
			String now;
			while (scanner.hasNextLine()) {
				now = scanner.nextLine();
				if (now.equalsIgnoreCase("")) continue;
				if (locales.contains(now)) continue;
				locales.add(now);
			}
			scanner.close();
		} catch (Throwable t) {
			System.err.println("Couldn't fetch the language list, offering bundled languages only");
			t.printStackTrace();
		}

		// Bundled languages are always available
		if (!locales.contains(RUSSIAN)) locales.add(0, RUSSIAN);
		if (!locales.contains("English")) locales.add(1, "English");

		int i = 0;
		int index = 0;
		listModel = new DefaultListModel();
		String lang = BC.SETTINGS.getProperty("language");
		for (String item : locales) {
			listModel.addElement(item);
			if (lang.equals(item)) {
				index = i;
			}
			i++;
		}


		list = new JList(listModel);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setLayoutOrientation(JList.VERTICAL);
		list.setVisibleRowCount(10);
		list.setSelectedIndex(index);

		if (listScroller != null) panel.remove(listScroller);

		listScroller = new JScrollPane(list);
		listScroller.setWheelScrollingEnabled(true);
		panel.add(listScroller, constr);
	}

	public static void applyNamesSwing() {
		try {
			UIManager.put("OptionPane.okButtonText", OPTIONS_OK);
			UIManager.put("OptionPane.cancelButtonText", CANCEL);
			UIManager.put("OptionPane.yesButtonText", YES);
			UIManager.put("OptionPane.noButtonText", NO);
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	public static boolean downloaded(String lang) {
		File file = new File(BC.get() + "launcher" + File.separator + "lang" + File.separator + lang + ".txt");
		if (file.exists()) {
			return true;
		}
		return false;
	}

	public static String encodeForURL(String string) {
		try {
			return URLEncoder.encode(string, "UTF-8").replace("+", "%20"); // java moment
		} catch (Throwable t) {
			t.printStackTrace();
			return null;
		} // impossible unless null
	}

	public static DownloadResult download(String lang) {
		String bundled = getBundledResource(lang);
		if (bundled != null) {
			InputStream in = Lang.class.getResourceAsStream(bundled);
			if (in != null) {
				File dest = new File(BC.get() + "launcher" + File.separator + "lang", lang + ".txt");
				dest.getParentFile().mkdirs();
				Util.copy(in, dest);
				return DownloadResult.OK;
			}
		}

		DownloadResult download = Launcher.download("http://files.betacraft.uk/launcher/assets/lang/" + locale_id + "/" + encodeForURL(lang) + ".txt", new File(BC.get() + "launcher" + File.separator + "lang", lang + ".txt"));
		if (!download.isPositive()) {
			JOptionPane.showMessageDialog(Window.mainWindow, "No Internet connection", "Language file download failed!", JOptionPane.ERROR_MESSAGE);
		}
		return download;
	}

	public static void refresh(boolean download, boolean force) {
		String lang = BC.SETTINGS.getProperty("language");
		if (lang == null) {
			lang = DEFAULT_LANGUAGE;
		}
		File file = new File(BC.get() + "launcher" + File.separator + "lang" + File.separator +  lang + ".txt");

		// Bundled languages get re-extracted, so updated launchers bring updated translations
		if (!download && getBundledResource(lang) != null) {
			download(lang);
		}

		if ((download && !download(lang).isOK()) || !file.exists()) {
			applyNamesSwing();
			return;
		}
		apply(new PropertyFile(file, "UTF-8"), force);
	}

	/** Loads the default bundled language without touching the data folder (used before it's chosen) */
	public static void preloadDefault() {
		try {
			File temp = File.createTempFile("zloylauncher-lang", ".txt");
			temp.deleteOnExit();
			InputStream in = Lang.class.getResourceAsStream(getBundledResource(DEFAULT_LANGUAGE));
			if (in == null) return;
			Util.copy(in, temp);
			apply(new PropertyFile(temp, "UTF-8"), false);
			temp.delete();
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private static void apply(PropertyFile langentries, boolean force) {
		if (langentries.hasProperty("version_button"))
			WINDOW_SELECT_VERSION = langentries.getProperty("version_button");

		if (langentries.hasProperty("play_button"))
			WINDOW_PLAY = langentries.getProperty("play_button");

		if (langentries.hasProperty("options_button"))
			WINDOW_OPTIONS = langentries.getProperty("options_button");

		// "launcher_title" from the language files is ignored on purpose - it carries the BetaCraft name

		if (langentries.hasProperty("language"))
			WINDOW_LANGUAGE = langentries.getProperty("language");

		if (langentries.hasProperty("downloading"))
			WINDOW_DOWNLOADING = langentries.getProperty("downloading");

		if (langentries.hasProperty("downloading_resource"))
			WINDOW_DOWNLOADING_RESOURCE = langentries.getProperty("downloading_resource");

		if (langentries.hasProperty("packing_mod"))
			PACKING_MOD = langentries.getProperty("packing_mod");

		if (langentries.hasProperty("manual_download"))
			MANUAL_DOWNLOAD = langentries.getProperty("manual_download");

		if (langentries.hasProperty("username_field_empty"))
			WINDOW_USERNAME_FIELD_EMPTY = langentries.getProperty("username_field_empty");


		if (langentries.hasProperty("lang_title"))
			LANG = langentries.getProperty("lang_title");


		if (langentries.hasProperty("unexpected_error"))
			UNEXPECTED_ERROR = langentries.getProperty("unexpected_error");

		if (langentries.hasProperty("no_connection"))
			ERR_NO_CONNECTION = langentries.getProperty("no_connection");

		if (langentries.hasProperty("download_fail"))
			ERR_DL_FAIL = langentries.getProperty("download_fail");

		if (langentries.hasProperty("no_selection"))
			ERR_NO_SELECTION = langentries.getProperty("no_selection");


		if (langentries.hasProperty("update_check"))
			OPTIONS_UPDATE_HEADER = langentries.getProperty("update_check");

		if (langentries.hasProperty("use_betacraft"))
			OPTIONS_PROXY = langentries.getProperty("use_betacraft");

		if (langentries.hasProperty("discord_rpc"))
			OPTIONS_RPC = langentries.getProperty("discord_rpc");
		
		if (langentries.hasProperty("intel_trick"))
			OPTIONS_INTEL_TRICK = langentries.getProperty("intel_trick");

		if (langentries.hasProperty("launch_arguments"))
			OPTIONS_LAUNCH_ARGS = langentries.getProperty("launch_arguments") + ":";

		if (langentries.hasProperty("keep_launcher_open"))
			OPTIONS_KEEP_OPEN = langentries.getProperty("keep_launcher_open");

		if (langentries.hasProperty("width"))
			OPTIONS_WIDTH = langentries.getProperty("width");

		if (langentries.hasProperty("height"))
			OPTIONS_HEIGHT = langentries.getProperty("height");

		if (langentries.hasProperty("options_title"))
			OPTIONS_TITLE = langentries.getProperty("options_title");

		if (langentries.hasProperty("ok"))
			OPTIONS_OK = langentries.getProperty("ok");


		if (langentries.hasProperty("sort_oldest"))
			SORT_FROM_OLDEST = langentries.getProperty("sort_oldest");

		if (langentries.hasProperty("sort_newest"))
			SORT_FROM_NEWEST = langentries.getProperty("sort_newest");

		if (langentries.hasProperty("version_title"))
			VERSION_LIST_TITLE = langentries.getProperty("version_title");


		if (langentries.hasProperty("addon_list_title"))
			ADDON_LIST_TITLE = langentries.getProperty("addon_list_title");

		if (langentries.hasProperty("addon_no_desc"))
			ADDON_NO_DESC = langentries.getProperty("addon_no_desc");

		if (langentries.hasProperty("addon_show_info"))
			ADDON_SHOW_INFO = langentries.getProperty("addon_show_info");


		if (langentries.hasProperty("logging_in"))
			LOGGING_IN = langentries.getProperty("logging_in");

		if (langentries.hasProperty("login_title"))
			LOGIN_TITLE = langentries.getProperty("login_title");

		if (langentries.hasProperty("log_in_button"))
			LOGIN_BUTTON = langentries.getProperty("log_in_button");

		if (langentries.hasProperty("log_out_button"))
			LOGOUT_BUTTON = langentries.getProperty("log_out_button");

		if (langentries.hasProperty("login_email_nickname"))
			LOGIN_EMAIL_NICKNAME = langentries.getProperty("login_email_nickname");

		if (langentries.hasProperty("login_password"))
			LOGIN_PASSWORD = langentries.getProperty("login_password");

		if (langentries.hasProperty("login_mojang_header"))
			LOGIN_MOJANG_HEADER = langentries.getProperty("login_mojang_header");

		if (langentries.hasProperty("login_microsoft_button"))
			LOGIN_MICROSOFT_BUTTON = langentries.getProperty("login_microsoft_button");

		if (langentries.hasProperty("login_microsoft_title"))
			LOGIN_MICROSOFT_TITLE = langentries.getProperty("login_microsoft_title");

		if (langentries.hasProperty("login_microsoft_error"))
			LOGIN_MICROSOFT_ERROR = langentries.getProperty("login_microsoft_error");

		if (langentries.hasProperty("login_microsoft_parent"))
			LOGIN_MICROSOFT_PARENT = langentries.getProperty("login_microsoft_parent");

		if (langentries.hasProperty("login_microsoft_no_xbox"))
			LOGIN_MICROSOFT_NO_XBOX = langentries.getProperty("login_microsoft_no_xbox");

		if (langentries.hasProperty("login_microsoft_no_minecraft"))
			LOGIN_MICROSOFT_NO_MINECRAFT = langentries.getProperty("login_microsoft_no_minecraft");

		if (langentries.hasProperty("login_microsoft_code_line1"))
			LOGIN_MICROSOFT_CODE_LINE1 = langentries.getProperty("login_microsoft_code_line1");

		if (langentries.hasProperty("login_microsoft_code_line2"))
			LOGIN_MICROSOFT_CODE_LINE2 = langentries.getProperty("login_microsoft_code_line2");
		
		if (langentries.hasProperty("login_relogin"))
			LOGIN_RELOGIN = langentries.getProperty("login_relogin");


		if (langentries.hasProperty("login_failed"))
			LOGIN_FAILED = langentries.getProperty("login_failed");

		if (langentries.hasProperty("login_failed_invalid_credentials"))
			LOGIN_FAILED_INVALID_CREDENTIALS = langentries.getProperty("login_failed_invalid_credentials");


		if (langentries.hasProperty("java_executable"))
			JAVA_EXECUTABLE = langentries.getProperty("java_executable");

		if (langentries.hasProperty("java_invalid"))
			JAVA_INVALID = langentries.getProperty("java_invalid");

		if (langentries.hasProperty("java_too_recent"))
			JAVA_TOO_RECENT = langentries.getProperty("java_too_recent");

		if (langentries.hasProperty("java_recommended_not_found"))
			JAVA_RECOMMENDED_NOT_FOUND = langentries.getProperty("java_recommended_not_found");

		if (langentries.hasProperty("java_wrong_arch"))
			JAVA_WRONG_ARCH = langentries.getProperty("java_wrong_arch");

		if (langentries.hasProperty("java_ssl_not_supported"))
			JAVA_SSL_NOT_SUPPORTED = langentries.getProperty("java_ssl_not_supported");

		if (langentries.hasProperty("java_ssl_to_download_resource"))
			JAVA_SSL_TO_DOWNLOAD_RESOURCE = langentries.getProperty("java_ssl_to_download_resource");

		if (langentries.hasProperty("java_ssl_to_microsoft_account"))
			JAVA_SSL_TO_MICROSOFT_ACCOUNT = langentries.getProperty("java_ssl_to_microsoft_account");

		if (langentries.hasProperty("java_ssl_to_authenticate"))
			JAVA_SSL_TO_AUTHENTICATE = langentries.getProperty("java_ssl_to_authenticate");

		if (langentries.hasProperty("java_intel_graphics"))
			JAVA_INTEL_GRAPHICS = langentries.getProperty("java_intel_graphics");

		if (langentries.hasProperty("java_intel_graphics_wiki"))
			JAVA_INTEL_GRAPHICS_WIKI = langentries.getProperty("java_intel_graphics_wiki");


		if (langentries.hasProperty("instance_directory"))
			INSTANCE_DIRECTORY = langentries.getProperty("instance_directory");

		if (langentries.hasProperty("instance_remove_directory"))
			INSTANCE_REMOVE_DIRECTORY = langentries.getProperty("instance_remove_directory");

		if (langentries.hasProperty("instance_game_directory_title"))
			INSTANCE_GAME_DIRECTORY_TITLE = langentries.getProperty("instance_game_directory_title");

		if (langentries.hasProperty("instance_remove_question"))
			INSTANCE_REMOVE_QUESTION = langentries.getProperty("instance_remove_question");

		if (langentries.hasProperty("instance_remove_title"))
			INSTANCE_REMOVE_TITLE = langentries.getProperty("instance_remove_title");

		if (langentries.hasProperty("instance_change_icon_name"))
			INSTANCE_CHANGE_ICON_NAME = langentries.getProperty("instance_change_icon_name");

		if (langentries.hasProperty("instance_change_icon_title"))
			INSTANCE_CHANGE_ICON_TITLE = langentries.getProperty("instance_change_icon_title");

		if (langentries.hasProperty("instance_change_icon_unsupported"))
			INSTANCE_CHANGE_ICON_UNSUPPORTED = langentries.getProperty("instance_change_icon_unsupported");

		if (langentries.hasProperty("instance_change_icon_unsupported_title"))
			INSTANCE_CHANGE_ICON_UNSUPPORTED_TITLE = langentries.getProperty("instance_change_icon_unsupported_title");

		if (langentries.hasProperty("instance_change_icon_failed"))
			INSTANCE_CHANGE_ICON_FAILED = langentries.getProperty("instance_change_icon_failed");

		if (langentries.hasProperty("instance_change_icon_failed_title"))
			INSTANCE_CHANGE_ICON_FAILED_TITLE = langentries.getProperty("instance_change_icon_failed_title");

		if (langentries.hasProperty("instance_name"))
			INSTANCE_NAME = langentries.getProperty("instance_name");

		if (langentries.hasProperty("instance_select_addons"))
			INSTANCE_SELECT_ADDONS = langentries.getProperty("instance_select_addons");

		if (langentries.hasProperty("instance_mods_repository"))
			INSTANCE_MODS_REPOSITORY = langentries.getProperty("instance_mods_repository");


		if (langentries.hasProperty("select_instance_title"))
			SELECT_INSTANCE_TITLE = langentries.getProperty("select_instance_title");

		if (langentries.hasProperty("select_instance_new"))
			SELECT_INSTANCE_NEW = langentries.getProperty("select_instance_new");


		if (langentries.hasProperty("new_version_found"))
			UPDATE_FOUND = langentries.getProperty("new_version_found");


		if (langentries.hasProperty("nick"))
			WRAP_USER = langentries.getProperty("nick");

		if (langentries.hasProperty("version"))
			WRAP_VERSION = langentries.getProperty("version");

		if (langentries.hasProperty("server"))
			WRAP_SERVER = langentries.getProperty("server");

		if (langentries.hasProperty("server_title"))
			WRAP_SERVER_TITLE = langentries.getProperty("server_title");

		if (langentries.hasProperty("classic_resize"))
			WRAP_CLASSIC_RESIZE = langentries.getProperty("classic_resize");


		if (langentries.hasProperty("tab_changelog"))
			TAB_CHANGELOG =  langentries.getProperty("tab_changelog");

		if (langentries.hasProperty("tab_instances"))
			TAB_INSTANCES =  langentries.getProperty("tab_instances");

		if (langentries.hasProperty("tab_servers"))
			TAB_SERVERS =  langentries.getProperty("tab_servers");


		if (langentries.hasProperty("version_custom"))
			VERSION_CUSTOM = langentries.getProperty("version_custom");


		if (langentries.hasProperty("browser_title"))
			BROWSER_TITLE = langentries.getProperty("browser_title");


		if (langentries.hasProperty("srv_loading"))
			TAB_SRV_LOADING = langentries.getProperty("srv_loading");

		if (langentries.hasProperty("srv_failed"))
			TAB_SRV_FAILED = langentries.getProperty("srv_failed");

		if (langentries.hasProperty("cl_loading"))
			TAB_CL_LOADING = langentries.getProperty("cl_loading");

		if (langentries.hasProperty("cl_failed"))
			TAB_CL_FAILED = langentries.getProperty("cl_failed");


		if (langentries.hasProperty("force_update"))
			FORCE_UPDATE = langentries.getProperty("force_update");

		if (langentries.hasProperty("console_output_for"))
			CONSOLE_OUTPUT_FOR = langentries.getProperty("console_output_for");

		if (langentries.hasProperty("console_output"))
			CONSOLE_OUTPUT = langentries.getProperty("console_output");


		if (langentries.hasProperty("yes"))
			YES = langentries.getProperty("yes");

		if (langentries.hasProperty("no"))
			NO = langentries.getProperty("no");

		if (langentries.hasProperty("cancel"))
			CANCEL = langentries.getProperty("cancel");

		if (langentries.hasProperty("select"))
			SELECT = langentries.getProperty("select");

		if (langentries.hasProperty("remove"))
			REMOVE = langentries.getProperty("remove");

		if (langentries.hasProperty("browse"))
			BROWSE = langentries.getProperty("browse");

		if (langentries.hasProperty("copy"))
			COPY = langentries.getProperty("copy");

		if (langentries.hasProperty("clear"))
			CLEAR = langentries.getProperty("clear");

		if (langentries.hasProperty("load"))
			LOAD = langentries.getProperty("load");

		if (langentries.hasProperty("close"))
			CLOSE = langentries.getProperty("close");

		if (langentries.hasProperty("link"))
			LINK = langentries.getProperty("link");


		if (langentries.hasProperty("pause"))
			PAUSE = langentries.getProperty("pause");

		if (langentries.hasProperty("unpause"))
			UNPAUSE = langentries.getProperty("unpause");

		ACCOUNTS_BUTTON = get(langentries, "accounts_button", ACCOUNTS_BUTTON);
		ACCOUNTS_TITLE = get(langentries, "accounts_title", ACCOUNTS_TITLE);
		ACCOUNTS_HINT = get(langentries, "accounts_hint", ACCOUNTS_HINT);
		ACCOUNT_LICENSED = get(langentries, "account_licensed", ACCOUNT_LICENSED);
		ACCOUNT_OFFLINE = get(langentries, "account_offline", ACCOUNT_OFFLINE);
		ACCOUNT_ADD_MICROSOFT = get(langentries, "account_add_microsoft", ACCOUNT_ADD_MICROSOFT);
		ACCOUNT_ADD_MICROSOFT_SHORT = get(langentries, "account_add_microsoft_short", ACCOUNT_ADD_MICROSOFT_SHORT);
		ACCOUNT_ADD_OFFLINE = get(langentries, "account_add_offline", ACCOUNT_ADD_OFFLINE);
		ACCOUNT_ADD_OFFLINE_SHORT = get(langentries, "account_add_offline_short", ACCOUNT_ADD_OFFLINE_SHORT);
		ACCOUNT_MANAGE = get(langentries, "account_manage", ACCOUNT_MANAGE);
		ACCOUNT_RENAME = get(langentries, "account_rename", ACCOUNT_RENAME);
		ACCOUNT_NICKNAME = get(langentries, "account_nickname", ACCOUNT_NICKNAME);
		ACCOUNT_NICK_INVALID = get(langentries, "account_nick_invalid", ACCOUNT_NICK_INVALID);
		ACCOUNT_NICK_TAKEN = get(langentries, "account_nick_taken", ACCOUNT_NICK_TAKEN);
		ACCOUNT_RENAME_LICENSED = get(langentries, "account_rename_licensed", ACCOUNT_RENAME_LICENSED);
		ACCOUNT_REMOVE_QUESTION = get(langentries, "account_remove_question", ACCOUNT_REMOVE_QUESTION);
		ACCOUNT_STATUS_LICENSED = get(langentries, "account_status_licensed", ACCOUNT_STATUS_LICENSED);
		ACCOUNT_STATUS_OFFLINE = get(langentries, "account_status_offline", ACCOUNT_STATUS_OFFLINE);
		ACCOUNT_SWITCH_FAILED = get(langentries, "account_switch_failed", ACCOUNT_SWITCH_FAILED);
		OPEN_GAME_DIR = get(langentries, "open_game_dir", OPEN_GAME_DIR);
		FOLDERS_BUTTON = get(langentries, "folders_button", FOLDERS_BUTTON);
		FOLDER_OPEN_LAUNCHER = get(langentries, "folder_open_launcher", FOLDER_OPEN_LAUNCHER);
		FOLDER_DATA_OWN = get(langentries, "folder_data_own", FOLDER_DATA_OWN);
		FOLDER_DATA_BETACRAFT = get(langentries, "folder_data_betacraft", FOLDER_DATA_BETACRAFT);
		FOLDER_DATA_CUSTOM = get(langentries, "folder_data_custom", FOLDER_DATA_CUSTOM);
		FOLDER_DATA_CHOOSE_TITLE = get(langentries, "folder_data_choose_title", FOLDER_DATA_CHOOSE_TITLE);
		FOLDER_DATA_RESTART = get(langentries, "folder_data_restart", FOLDER_DATA_RESTART);
		FOLDER_BETACRAFT_FOUND = get(langentries, "folder_betacraft_found", FOLDER_BETACRAFT_FOUND);
		FOLDER_BETACRAFT_MISSING = get(langentries, "folder_betacraft_missing", FOLDER_BETACRAFT_MISSING);
		FOLDER_LINKED_SUFFIX = get(langentries, "folder_linked_suffix", FOLDER_LINKED_SUFFIX);
		JAVA_SELECT_TITLE = get(langentries, "java_select_title", JAVA_SELECT_TITLE);
		JAVA_SELECT_HINT = get(langentries, "java_select_hint", JAVA_SELECT_HINT);
		JAVA_SELECT_REFRESH = get(langentries, "java_select_refresh", JAVA_SELECT_REFRESH);
		JAVA_SELECT_DOWNLOAD = get(langentries, "java_select_download", JAVA_SELECT_DOWNLOAD);
		JAVA_SELECT_SEARCHING = get(langentries, "java_select_searching", JAVA_SELECT_SEARCHING);
		JAVA_SELECT_NONE = get(langentries, "java_select_none", JAVA_SELECT_NONE);
		JAVA_SELECT_FOUND = get(langentries, "java_select_found", JAVA_SELECT_FOUND);
		JAVA_SELECT_RECOMMENDED = get(langentries, "java_select_recommended", JAVA_SELECT_RECOMMENDED);
		JAVA_SELECT_TOO_NEW = get(langentries, "java_select_too_new", JAVA_SELECT_TOO_NEW);
		JAVA_SELECT_TOO_OLD = get(langentries, "java_select_too_old", JAVA_SELECT_TOO_OLD);
		JAVA_SELECT_32BIT = get(langentries, "java_select_32bit", JAVA_SELECT_32BIT);
		JAVA_SELECT_BUTTON = get(langentries, "java_select_button", JAVA_SELECT_BUTTON);
		JAVA_INVALID_FILE = get(langentries, "java_invalid_file", JAVA_INVALID_FILE);
		JAVA_COLUMN_VERSION = get(langentries, "java_column_version", JAVA_COLUMN_VERSION);
		JAVA_COLUMN_ARCH = get(langentries, "java_column_arch", JAVA_COLUMN_ARCH);
		JAVA_COLUMN_VENDOR = get(langentries, "java_column_vendor", JAVA_COLUMN_VENDOR);
		JAVA_COLUMN_PATH = get(langentries, "java_column_path", JAVA_COLUMN_PATH);
		ICON_PICKER_TITLE = get(langentries, "icon_picker_title", ICON_PICKER_TITLE);
		ICON_PICKER_OWN = get(langentries, "icon_picker_own", ICON_PICKER_OWN);
		ICON_PICKER_RESET = get(langentries, "icon_picker_reset", ICON_PICKER_RESET);
		INSTANCES_NEW = get(langentries, "instances_new", INSTANCES_NEW);
		INSTANCES_EDIT = get(langentries, "instances_edit", INSTANCES_EDIT);
		INSTANCES_ICON = get(langentries, "instances_icon", INSTANCES_ICON);
		INSTANCES_COPY = get(langentries, "instances_copy", INSTANCES_COPY);
		INSTANCES_FOLDER = get(langentries, "instances_folder", INSTANCES_FOLDER);
		INSTANCES_RENAME = get(langentries, "instances_rename", INSTANCES_RENAME);
		INSTANCES_NOTES = get(langentries, "instances_notes", INSTANCES_NOTES);
		INSTANCES_NOTES_PROMPT = get(langentries, "instances_notes_prompt", INSTANCES_NOTES_PROMPT);
		INSTANCES_SEARCH = get(langentries, "instances_search", INSTANCES_SEARCH);
		INSTANCES_NOTHING_FOUND = get(langentries, "instances_nothing_found", INSTANCES_NOTHING_FOUND);
		INSTANCES_EMPTY = get(langentries, "instances_empty", INSTANCES_EMPTY);
		INSTANCES_LAST_PLAYED = get(langentries, "instances_last_played", INSTANCES_LAST_PLAYED);
		INSTANCES_NEVER = get(langentries, "instances_never", INSTANCES_NEVER);
		INSTANCES_PLAY_TIME = get(langentries, "instances_play_time", INSTANCES_PLAY_TIME);
		INSTANCES_BAD_NAME = get(langentries, "instances_bad_name", INSTANCES_BAD_NAME);
		INSTANCES_NAME_TAKEN = get(langentries, "instances_name_taken", INSTANCES_NAME_TAKEN);
		TIME_MINUTES = get(langentries, "time_minutes", TIME_MINUTES);
		TIME_HOURS_MINUTES = get(langentries, "time_hours_minutes", TIME_HOURS_MINUTES);
		ACCOUNT_LICENSE_FAILED_SUFFIX = get(langentries, "account_license_failed_suffix", ACCOUNT_LICENSE_FAILED_SUFFIX);
		MODS_REFRESH = get(langentries, "mods_refresh", MODS_REFRESH);
		MODS_REFRESHING = get(langentries, "mods_refreshing", MODS_REFRESHING);
		SERVERS_MINE = get(langentries, "servers_mine", SERVERS_MINE);
		SERVERS_PUBLIC = get(langentries, "servers_public", SERVERS_PUBLIC);
		SERVERS_JOIN = get(langentries, "servers_join", SERVERS_JOIN);
		SERVERS_ADD = get(langentries, "servers_add", SERVERS_ADD);
		SERVERS_EDIT = get(langentries, "servers_edit", SERVERS_EDIT);
		SERVERS_EMPTY = get(langentries, "servers_empty", SERVERS_EMPTY);
		SERVERS_PUBLIC_HINT = get(langentries, "servers_public_hint", SERVERS_PUBLIC_HINT);
		RPC_ON_SERVER = get(langentries, "rpc_on_server", RPC_ON_SERVER);
		RPC_SINGLEPLAYER = get(langentries, "rpc_singleplayer", RPC_SINGLEPLAYER);
		RPC_MULTIPLAYER = get(langentries, "rpc_multiplayer", RPC_MULTIPLAYER);
		DISCORD_SETTINGS = get(langentries, "discord_settings", DISCORD_SETTINGS);
		DISCORD_SETTINGS_TITLE = get(langentries, "discord_settings_title", DISCORD_SETTINGS_TITLE);
		DISCORD_SHOW_INSTANCE = get(langentries, "discord_show_instance", DISCORD_SHOW_INSTANCE);
		DISCORD_SHOW_SERVER = get(langentries, "discord_show_server", DISCORD_SHOW_SERVER);
		DISCORD_SHOW_PLAYERS = get(langentries, "discord_show_players", DISCORD_SHOW_PLAYERS);
		DISCORD_SHOW_NICK = get(langentries, "discord_show_nick", DISCORD_SHOW_NICK);
		DISCORD_PREVIEW_PLAYING = get(langentries, "discord_preview_playing", DISCORD_PREVIEW_PLAYING);
		ICON_PICKER_BETACRAFT = get(langentries, "icon_picker_betacraft", ICON_PICKER_BETACRAFT);
		ICON_PICKER_BETACRAFT_NONE = get(langentries, "icon_picker_betacraft_none", ICON_PICKER_BETACRAFT_NONE);
		SERVERS_ONLINE = get(langentries, "servers_online", SERVERS_ONLINE);
		SERVERS_ONLINE_CHECKING = get(langentries, "servers_online_checking", SERVERS_ONLINE_CHECKING);
		SERVERS_ONLINE_UNKNOWN = get(langentries, "servers_online_unknown", SERVERS_ONLINE_UNKNOWN);
		SHORTCUT_DESKTOP = get(langentries, "shortcut_desktop", SHORTCUT_DESKTOP);
		SHORTCUT_START_MENU = get(langentries, "shortcut_start_menu", SHORTCUT_START_MENU);
		SHORTCUT_NEEDS_EXE = get(langentries, "shortcut_needs_exe", SHORTCUT_NEEDS_EXE);
		SHORTCUT_DONE = get(langentries, "shortcut_done", SHORTCUT_DONE);
		SHORTCUT_FAILED = get(langentries, "shortcut_failed", SHORTCUT_FAILED);
		SHORTCUT_OFFER = get(langentries, "shortcut_offer", SHORTCUT_OFFER);
		SERVERS_REMOVE_QUESTION = get(langentries, "servers_remove_question", SERVERS_REMOVE_QUESTION);
		SERVERS_REDETECT = get(langentries, "servers_redetect", SERVERS_REDETECT);
		SERVERS_AUTO = get(langentries, "servers_auto", SERVERS_AUTO);
		SERVERS_AUTO_DETECTED = get(langentries, "servers_auto_detected", SERVERS_AUTO_DETECTED);
		SERVERS_ADD_TITLE = get(langentries, "servers_add_title", SERVERS_ADD_TITLE);
		SERVERS_EDIT_TITLE = get(langentries, "servers_edit_title", SERVERS_EDIT_TITLE);
		SERVERS_NAME = get(langentries, "servers_name", SERVERS_NAME);
		SERVERS_ADDRESS = get(langentries, "servers_address", SERVERS_ADDRESS);
		SERVERS_VERSION = get(langentries, "servers_version", SERVERS_VERSION);
		SERVERS_DETECT_NOW = get(langentries, "servers_detect_now", SERVERS_DETECT_NOW);
		SERVERS_NO_ADDRESS = get(langentries, "servers_no_address", SERVERS_NO_ADDRESS);
		SERVERS_SAVE_PUBLIC = get(langentries, "servers_save_public", SERVERS_SAVE_PUBLIC);
		SERVERS_SAVED = get(langentries, "servers_saved", SERVERS_SAVED);
		DETECT_RUNNING = get(langentries, "detect_running", DETECT_RUNNING);
		DETECT_BY_LIST = get(langentries, "detect_by_list", DETECT_BY_LIST);
		DETECT_BY_PING = get(langentries, "detect_by_ping", DETECT_BY_PING);
		DETECT_UNSUPPORTED = get(langentries, "detect_unsupported", DETECT_UNSUPPORTED);
		DETECT_ERA_B18 = get(langentries, "detect_era_b18", DETECT_ERA_B18);
		DETECT_ERA_BETA = get(langentries, "detect_era_beta", DETECT_ERA_BETA);
		DETECT_ERA_ALPHA = get(langentries, "detect_era_alpha", DETECT_ERA_ALPHA);
		DETECT_ERA_CLASSIC = get(langentries, "detect_era_classic", DETECT_ERA_CLASSIC);
		DETECT_FAILED = get(langentries, "detect_failed", DETECT_FAILED);
		DETECT_CHOOSE_MANUALLY = get(langentries, "detect_choose_manually", DETECT_CHOOSE_MANUALLY);

		applyNamesSwing();

		if (force) {
			Window.mainWindow.update();
			if (Window.addonsList != null) Window.addonsList.update();
			if (Window.modsRepo != null) Window.modsRepo.update();
			if (Window.lang != null) Window.lang.update();
			if (Window.instanceList != null) Window.instanceList.update();
			if (Window.instanceSettings != null) Window.instanceSettings.update();
			if (Window.versionsList != null) Window.versionsList.update();
			if (Window.accountManager != null) Window.accountManager.update();
		}
	}

	private static String get(PropertyFile entries, String key, String fallback) {
		return entries.hasProperty(key) ? entries.getProperty(key) : fallback;
	}

	public static String UPDATE_FOUND = "There is a new version of the launcher available (%s). Would you like to update?";

	public static String WINDOW_PLAY = "Play";
	public static String WINDOW_SELECT_VERSION = "Select version";
	public static String WINDOW_LANGUAGE = "Language";
	public static String WINDOW_OPTIONS = "Edit instance";
	public static String WINDOW_TITLE = BC.NAME + " v" + Launcher.VERSION;
	public static String WINDOW_DOWNLOADING = "Downloading ...";
	public static String WINDOW_DOWNLOADING_RESOURCE = "Downloading: %s";
	public static String PACKING_MOD = "Packing mod ...";
	public static String MANUAL_DOWNLOAD = "Manual download";
	public static String WINDOW_USERNAME_FIELD_EMPTY = "The username field is empty!";

	public static String LANG = "Select language";

	public static String OPTIONS_PROXY = "Use skin & sound proxy";
	public static String OPTIONS_UPDATE_HEADER = "Update available";
	public static String OPTIONS_KEEP_OPEN = "Keep the launcher open";
	public static String OPTIONS_RPC = "Discord RPC";
	public static String OPTIONS_INTEL_TRICK = "Apply performance trick for Intel Graphics";
	public static String OPTIONS_LAUNCH_ARGS = "JVM arguments:";
	public static String OPTIONS_OK = "OK";
	public static String OPTIONS_WIDTH = "width:";
	public static String OPTIONS_HEIGHT = "height:";
	public static String OPTIONS_TITLE = "Instance settings";

	public static String SORT_FROM_OLDEST = "Sort: from oldest";
	public static String SORT_FROM_NEWEST = "Sort: from newest";
	public static String VERSION_LIST_TITLE = "Select version";

	public static String ADDON_LIST_TITLE = "Addons list";
	public static String ADDON_NO_DESC = "No description.";
	public static String ADDON_SHOW_INFO = "About";

	public static String LOGGING_IN = "Logging in...";
	public static String LOGIN_TITLE = "Log in";
	public static String LOGIN_BUTTON = "Log in";
	public static String LOGOUT_BUTTON = "Log out";
	public static String LOGIN_EMAIL_NICKNAME = "E-mail:";
	public static String LOGIN_PASSWORD = "Password:";
	public static String LOGIN_MOJANG_HEADER = "... or login with a Mojang account:";
	public static String LOGIN_MICROSOFT_BUTTON = "Login with Microsoft";
	public static String LOGIN_MICROSOFT_TITLE = "Login with your Microsoft account";
	public static String LOGIN_MICROSOFT_ERROR = "Microsoft authentication error";
	public static String LOGIN_MICROSOFT_PARENT = "Parental approval required. Add this account to Family to login.";
	public static String LOGIN_MICROSOFT_NO_XBOX = "No Xbox account registered";
	public static String LOGIN_MICROSOFT_NO_MINECRAFT = "You don't own Minecraft on this account.";
	public static String LOGIN_MICROSOFT_CODE_LINE1 = "To proceed, open up:";
	public static String LOGIN_MICROSOFT_CODE_LINE2 = "in a browser and type the code:";
	public static String LOGIN_RELOGIN = "You need to re-login to your Microsoft account (token timed out)";

	public static String LOGIN_FAILED = "Failed to complete the login process";
	public static String LOGIN_FAILED_INVALID_CREDENTIALS = "Invalid e-mail or password.";

	public static String JAVA_EXECUTABLE = "Java executable:";
	public static String JAVA_INVALID = "Given Java path is not valid. Do you want the instance to use recommended Java?";
	public static String JAVA_TOO_RECENT = "Given Java version is greater than 8. Do you want the instance to use recommended Java?";
	public static String JAVA_RECOMMENDED_NOT_FOUND = "Could not find recommended Java in your system. Do you want to download and install it?";
	public static String JAVA_WRONG_ARCH = "It seems that you're running the wrong architecture of Java (ARM or 32-bit x86). \nShould you run into issues, get 64-bit Java 8 from https://java.com and make your instance use it.";
	public static String JAVA_SSL_NOT_SUPPORTED = "Your Java is too old to connect to the required resource. Update your Java to %s";
	public static String JAVA_SSL_TO_MICROSOFT_ACCOUNT = "login to your Minecraft account.";
	public static String JAVA_SSL_TO_DOWNLOAD_RESOURCE = "download the required resource.";
	public static String JAVA_SSL_TO_AUTHENTICATE = "connect with this server.";
	public static String JAVA_INTEL_GRAPHICS = "Your machine has Intel HD Graphics. In order for the game to run as intended, you may have to use Java 8u51 for your instance (your current Java: %s)";
	public static String JAVA_INTEL_GRAPHICS_WIKI = "Learn how to get Java 8u51 here: https://github.com/betacraftuk/betacraft-launcher/wiki/intel";

	public static String INSTANCE_REMOVE_DIRECTORY = "Would you want to permanently remove this instance's directory? This cannot be undone.";
	public static String INSTANCE_DIRECTORY = "Instance directory:";
	public static String INSTANCE_GAME_DIRECTORY_TITLE = "Choose a directory for the instance";
	public static String INSTANCE_REMOVE_QUESTION = "Are you sure you want to remove this instance?";
	public static String INSTANCE_REMOVE_TITLE = "Remove instance";
	public static String INSTANCE_CHANGE_ICON_NAME = "Change icon";
	public static String INSTANCE_CHANGE_ICON_TITLE = "Choose a new icon for the instance";
	public static String INSTANCE_CHANGE_ICON_UNSUPPORTED = "Your icon file format is not supported. Currently supported formats: %s";
	public static String INSTANCE_CHANGE_ICON_UNSUPPORTED_TITLE = "Unsupported image format";
	public static String INSTANCE_CHANGE_ICON_FAILED = "Something went wrong: %s";
	public static String INSTANCE_CHANGE_ICON_FAILED_TITLE = "Failed to save icon";
	public static String INSTANCE_NAME = "Name:";
	public static String INSTANCE_SELECT_ADDONS = "Select addons";
	public static String INSTANCE_MODS_REPOSITORY = "Mods repository";

	public static String SELECT_INSTANCE_TITLE = "Select instance";
	public static String SELECT_INSTANCE_NEW = "New instance";

	public static String TAB_CHANGELOG = "Changelog";
	public static String TAB_INSTANCES = "Instances";
	public static String TAB_SERVERS = "Server list";

	public static String VERSION_CUSTOM = " [Custom]";

	public static String BROWSER_TITLE = "Webpage viewer";

	public static String TAB_SRV_LOADING = "Loading server list...";
	public static String TAB_SRV_FAILED = "Failed to list servers!";
	public static String TAB_CL_LOADING = "Loading update news...";
	public static String TAB_CL_FAILED = "Failed to load update news!";

	public static String FORCE_UPDATE = "Force update";
	public static String CONSOLE_OUTPUT_FOR = "Game log for \"%s\"";
	public static String CONSOLE_OUTPUT = "Game log";

	public static String WRAP_USER = "User: %s";
	public static String WRAP_VERSION = "Version: %s";
	public static String WRAP_SERVER = "Server IP (leave blank if you don't want to play online):";
	public static String WRAP_SERVER_TITLE = "Server IP";
	public static String WRAP_CLASSIC_RESIZE = "<html><font size=5>Resize the window to the size you want to play on.<br />Click anywhere inside this window to start the game.</font></html>";

	public static String UNEXPECTED_ERROR = "Unexpected error: %s";
	public static String ERR_NO_CONNECTION = "No stable internet connection.";
	public static String ERR_DL_FAIL = "Download failed.";
	public static String ERR_NO_SELECTION = "You must select something first!";

	public static String YES = "Yes";
	public static String NO = "No";
	public static String CANCEL = "Cancel";
	public static String SELECT = "Select";
	public static String REMOVE = "Remove";
	public static String BROWSE = "Browse";
	public static String COPY = "Copy";
	public static String CLEAR = "Clear";
	public static String LOAD = "Load";
	public static String CLOSE = "Close";
	public static String LINK = "Link";

	public static String ACCOUNTS_BUTTON = "Accounts";
	public static String ACCOUNTS_TITLE = "Accounts";
	public static String ACCOUNTS_HINT = "Licensed accounts log in with Microsoft, offline ones only need a nickname.";
	public static String ACCOUNT_LICENSED = "License";
	public static String ACCOUNT_OFFLINE = "Offline";
	public static String ACCOUNT_ADD_MICROSOFT = "Add licensed account (Microsoft)...";
	public static String ACCOUNT_ADD_MICROSOFT_SHORT = "+ License";
	public static String ACCOUNT_ADD_OFFLINE = "Add offline account...";
	public static String ACCOUNT_ADD_OFFLINE_SHORT = "+ Offline";
	public static String ACCOUNT_MANAGE = "Manage accounts...";
	public static String ACCOUNT_RENAME = "Rename";
	public static String ACCOUNT_NICKNAME = "Nickname:";
	public static String ACCOUNT_NICK_INVALID = "A nickname must be 1-16 characters long and contain only Latin letters, digits and _";
	public static String ACCOUNT_NICK_TAKEN = "There already is an offline account with this nickname.";
	public static String ACCOUNT_RENAME_LICENSED = "The nickname of a licensed account can only be changed on minecraft.net";
	public static String ACCOUNT_REMOVE_QUESTION = "Remove account %s from the launcher?";
	public static String ACCOUNT_STATUS_LICENSED = "Licensed: %s";
	public static String ACCOUNT_STATUS_OFFLINE = "Offline: %s";
	public static String ACCOUNT_SWITCH_FAILED = "Couldn't log in to %s. The current account stays active.";
	public static String OPEN_GAME_DIR = "Open instance folder";
	public static String FOLDERS_BUTTON = "Folders";
	public static String FOLDER_OPEN_LAUNCHER = "Open launcher folder";
	public static String FOLDER_DATA_OWN = "Own ZloyLauncher folder";
	public static String FOLDER_DATA_BETACRAFT = "BetaCraft folder (shared versions, instances, accounts)";
	public static String FOLDER_DATA_CUSTOM = "Other folder...";
	public static String FOLDER_DATA_CHOOSE_TITLE = "Choose a data folder for the launcher";
	public static String FOLDER_DATA_RESTART = "The launcher will use this data folder: %s. It will restart now. Continue?";
	public static String FOLDER_BETACRAFT_FOUND = "BetaCraft Launcher was found. Use its folder (%s)? Versions, instances and accounts will be shared. You can change this later in the Folders menu.";
	public static String FOLDER_BETACRAFT_MISSING = "BetaCraft Launcher's folder wasn't found: %s";
	public static String FOLDER_LINKED_SUFFIX = " [BetaCraft folder]";
	public static String JAVA_SELECT_TITLE = "Choose Java";
	public static String JAVA_SELECT_HINT = "Old Minecraft versions run best on 64-bit Java 8 (shown in green).";
	public static String JAVA_SELECT_REFRESH = "Refresh";
	public static String JAVA_SELECT_DOWNLOAD = "Download Java 8";
	public static String JAVA_SELECT_SEARCHING = "Looking for Java on this computer...";
	public static String JAVA_SELECT_NONE = "No Java found. Download Java 8 or point to java.exe with Browse.";
	public static String JAVA_SELECT_FOUND = "Java installations found: %d";
	public static String JAVA_SELECT_RECOMMENDED = "Recommended for old versions";
	public static String JAVA_SELECT_TOO_NEW = "Newer than 8: some old versions may not start";
	public static String JAVA_SELECT_TOO_OLD = "Older than 8: may lack features the launcher needs";
	public static String JAVA_SELECT_32BIT = "Java 8, but not 64-bit: less memory for the game";
	public static String JAVA_SELECT_BUTTON = "Choose...";
	public static String JAVA_INVALID_FILE = "This file doesn't look like a working Java.";
	public static String JAVA_COLUMN_VERSION = "Version";
	public static String JAVA_COLUMN_ARCH = "Architecture";
	public static String JAVA_COLUMN_VENDOR = "Vendor";
	public static String JAVA_COLUMN_PATH = "Path";
	public static String ICON_PICKER_TITLE = "Icon for %s";
	public static String ICON_PICKER_OWN = "Own picture...";
	public static String ICON_PICKER_RESET = "Default";
	public static String INSTANCES_NEW = "+ New instance";
	public static String INSTANCES_EDIT = "Settings";
	public static String INSTANCES_ICON = "Icon";
	public static String INSTANCES_COPY = "Copy";
	public static String INSTANCES_FOLDER = "Folder";
	public static String INSTANCES_RENAME = "Rename";
	public static String INSTANCES_NOTES = "Note";
	public static String INSTANCES_NOTES_PROMPT = "A short note about this instance:";
	public static String INSTANCES_SEARCH = "Search:";
	public static String INSTANCES_NOTHING_FOUND = "Nothing found";
	public static String INSTANCES_EMPTY = "No instances yet. Press \"+ New instance\".";
	public static String INSTANCES_LAST_PLAYED = "Last played:";
	public static String INSTANCES_NEVER = "never";
	public static String INSTANCES_PLAY_TIME = "Played:";
	public static String INSTANCES_BAD_NAME = "The name can't be empty or contain these characters: %s";
	public static String INSTANCES_NAME_TAKEN = "There already is an instance with this name.";
	public static String TIME_MINUTES = "%d min";
	public static String TIME_HOURS_MINUTES = "%d h %d min";
	public static String ACCOUNT_LICENSE_FAILED_SUFFIX = " (license login failed)";
	public static String MODS_REFRESH = "Refresh list";
	public static String MODS_REFRESHING = "Loading...";
	public static String SERVERS_MINE = "My servers";
	public static String SERVERS_PUBLIC = "BetaCraft servers";
	public static String SERVERS_JOIN = "Join";
	public static String SERVERS_ADD = "+ Add";
	public static String SERVERS_EDIT = "Edit";
	public static String SERVERS_EMPTY = "No saved servers yet: press \"+ Add\" or save one from the \"Servers\" tab.";
	public static String SERVERS_PUBLIC_HINT = "Click a server to join it. The version window that opens can also save it to \"My servers\".";
	public static String RPC_ON_SERVER = "On server: %s";
	public static String RPC_SINGLEPLAYER = "Singleplayer";
	public static String RPC_MULTIPLAYER = "Playing";
	public static String DISCORD_SETTINGS = "Discord status...";
	public static String DISCORD_SETTINGS_TITLE = "Discord status";
	public static String DISCORD_SHOW_INSTANCE = "Show the instance and version";
	public static String DISCORD_SHOW_SERVER = "Show the server I play on";
	public static String DISCORD_SHOW_PLAYERS = "Show how many players are online (19 of 400)";
	public static String DISCORD_SHOW_NICK = "Show my nickname";
	public static String DISCORD_PREVIEW_PLAYING = "Playing ZloyLauncher";
	public static String ICON_PICKER_BETACRAFT = "Icon from BetaCraft";
	public static String ICON_PICKER_BETACRAFT_NONE = "BetaCraft's list has no icon for this server.";
	public static String SERVERS_ONLINE = "%d / %d online";
	public static String SERVERS_ONLINE_CHECKING = "checking...";
	public static String SERVERS_ONLINE_UNKNOWN = "online unknown";
	public static String SHORTCUT_DESKTOP = "Shortcut on the desktop";
	public static String SHORTCUT_START_MENU = "Shortcut in the Start menu";
	public static String SHORTCUT_NEEDS_EXE = "Start the launcher from ZloyLauncher.exe to make shortcuts";
	public static String SHORTCUT_DONE = "Shortcut created. It can be pinned to the taskbar and Start like any program.";
	public static String SHORTCUT_FAILED = "Couldn't create the shortcut.";
	public static String SHORTCUT_OFFER = "Create ZloyLauncher shortcuts on the desktop and in the Start menu?";
	public static String SERVERS_REMOVE_QUESTION = "Remove server %s?";
	public static String SERVERS_REDETECT = "Detect the version again";
	public static String SERVERS_AUTO = "Auto (detect)";
	public static String SERVERS_AUTO_DETECTED = "Auto: %s";
	public static String SERVERS_ADD_TITLE = "Add server";
	public static String SERVERS_EDIT_TITLE = "Edit server";
	public static String SERVERS_NAME = "Name:";
	public static String SERVERS_ADDRESS = "Address:";
	public static String SERVERS_VERSION = "Version:";
	public static String SERVERS_DETECT_NOW = "Detect";
	public static String SERVERS_NO_ADDRESS = "Enter the server address.";
	public static String SERVERS_SAVE_PUBLIC = "Save to My servers";
	public static String SERVERS_SAVED = "Saved";
	public static String DETECT_RUNNING = "Detecting the version of %s...";
	public static String DETECT_BY_LIST = "%s (from BetaCraft's server list)";
	public static String DETECT_BY_PING = "The server reports version %s";
	public static String DETECT_UNSUPPORTED = "The server runs %s, which this launcher doesn't have.";
	public static String DETECT_ERA_B18 = "A b1.8 - 1.3 server (it doesn't report the exact version)";
	public static String DETECT_ERA_BETA = "A beta 1.5 - 1.7.3 server, most likely b1.7.3";
	public static String DETECT_ERA_ALPHA = "An alpha / early beta server (it doesn't report the exact version)";
	public static String DETECT_ERA_CLASSIC = "A classic server";
	public static String DETECT_FAILED = "Couldn't detect the version: the server doesn't answer or is unknown.";
	public static String DETECT_CHOOSE_MANUALLY = "Choose the version yourself.";

	public static String PAUSE = "Pause";
	public static String UNPAUSE = "Unpause";
}

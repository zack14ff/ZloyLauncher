package org.betacraft.launcher;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import uk.betacraft.auth.Credentials;
import uk.betacraft.auth.MicrosoftAuth;
import uk.betacraft.auth.NoAuth;

public class Window extends JFrame implements ActionListener, LanguageElement {

	public static JLabel selectedInstanceDisplay = null;
	public static JButton playButton, selectVersionButton, settingsButton, langButton, gameDirButton;
	public static JButton tabchangelog, tabservers, tabinstances, tabmyservers;
	public static JLabel accountStatus = null;
	public static JTextField nick_input;
	public static JButton accountsButton = null;
	public static AccountManager accountManager = null;
	public static BottomPanel bottomPanel = null;
	public static Component centerPanel = null;
	public static Window mainWindow = null;

	public static ModsRepository modsRepo = null;
	public static InstanceList instanceList = null;
	public static InstanceSettings instanceSettings = null;
	public static Lang lang = null;
	public static SelectAddons addonsList = null;
	public static SelectVersion versionsList = null;

	public static Tab tab = Tab.CHANGELOG;

	// Launcher's icon
	public static BufferedImage img;

	public Window() {
		try {
			InputStream imstream = this.getClass().getClassLoader().getResourceAsStream("icons/icon.png");
			img = ImageIO.read(imstream);
			this.setIconImage(img);
		} catch (Exception ex) {
			System.err.println("An error occurred while loading the window icon!");
			ex.printStackTrace();
		}

		mainWindow = this;
		setMinimumSize(new Dimension(800, 480));
		setPreferredSize(new Dimension(800, 480));
		setTitle(getWindowTitle());
		setLayout(new BorderLayout());
		setLocationRelativeTo(null);

		// Initialize components
		accountsButton = new JButton(Lang.ACCOUNTS_BUTTON);
		accountStatus = new JLabel(" ");
		playButton = new JButton(Lang.WINDOW_PLAY);
		selectedInstanceDisplay = new JLabel(Launcher.currentInstance.name + " [" + Launcher.currentInstance.version + "]");
		selectVersionButton = new JButton(Lang.WINDOW_SELECT_VERSION);
		nick_input = new JTextField(Launcher.getNickname(), 16);
		settingsButton = new JButton(Lang.WINDOW_OPTIONS);
		langButton = new JButton(Lang.WINDOW_LANGUAGE);
		gameDirButton = new JButton(Lang.FOLDERS_BUTTON);

		nick_input.setEnabled(false);
		nick_input.setText(Lang.LOGGING_IN);
		nick_input.getDocument().addDocumentListener(new DocumentListener() {
			public void changedUpdate(DocumentEvent e) {
				change();
			}
			public void removeUpdate(DocumentEvent e) {
				change();
			}
			public void insertUpdate(DocumentEvent e) {
				change();
			}

			public void change() {
				// The nickname of the current offline account can be typed in right here, like in the good old days
				if (!nick_input.isEnabled() || !(Launcher.auth instanceof NoAuth)) return;
				Launcher.auth.getCredentials().username = nick_input.getText().trim();
				updateAccountStatus();
				if (accountManager != null) accountManager.list.repaint();
			}
		});

		accountsButton.setEnabled(false);
		accountsButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				showAccountsMenu();
			}
		});

		bottomPanel = new BottomPanel();
		String tabname = BC.SETTINGS.getProperty("tab");
		Tab tab = tabname.equals("") ? Tab.CHANGELOG : Tab.valueOf(tabname.toUpperCase());
		setTab(tab);
		this.add(Window.bottomPanel, BorderLayout.SOUTH);

		JPanel stuffz = new JPanel() {
			public void update(final Graphics graphics) {
				this.paint(graphics);
			}

			public void paintComponent(Graphics g) {
				bottomPanel.paintComponent(g);
			}
		};
		stuffz.setLayout(new GridBagLayout());
		tabchangelog = new JButton(Lang.TAB_CHANGELOG);
		tabinstances = new JButton(Lang.TAB_INSTANCES);
		tabservers = new JButton(Lang.TAB_SERVERS);
		tabmyservers = new JButton(Lang.SERVERS_MINE);
		positionButtons();

		tabchangelog.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setTab(Tab.CHANGELOG);
			}
		});

		tabinstances.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setTab(Tab.INSTANCES);
			}
		});

		tabservers.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setTab(Tab.SERVER_LIST);
			}
		});

		tabmyservers.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setTab(Tab.MY_SERVERS);
			}
		});

		GridBagConstraints constr = new GridBagConstraints();
		constr.fill = GridBagConstraints.BOTH;
		constr.gridwidth = GridBagConstraints.RELATIVE;
		constr.gridy = 0;
		constr.gridx = 0;
		constr.weightx = 0.0;
		constr.gridwidth = 1;
		constr.insets = new Insets(0, 2, 0, 2);
		stuffz.add(tabchangelog, constr);

		constr.gridx = 1;
		stuffz.add(tabinstances, constr);

		constr.gridx = 2;
		stuffz.add(tabmyservers, constr);

		constr.gridx = 3;
		stuffz.add(tabservers, constr);

		this.add(stuffz, BorderLayout.NORTH);

		// Buttons are being added in BottomPanel.paintComponent()
		// because it will put them above the bottom panel's background? (TODO verify)

		// Add some texture to the components
		selectedInstanceDisplay.setForeground(Color.WHITE);
		accountStatus.setForeground(Color.LIGHT_GRAY);

		// Add listeners for components
		playButton.addActionListener(this);
		selectVersionButton.addActionListener(this);
		settingsButton.addActionListener(this);
		langButton.addActionListener(this);
		gameDirButton.addActionListener(this);

		this.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent windowEvent) {
				System.out.println("Disabling...");
				Window.quit(true);
			}
		});

		if (mainWindow.isVisible())
			this.pack();

		playButton.requestFocus();
	}

	public void update() {
		this.setTitle(getWindowTitle());

		accountsButton.setText(Lang.ACCOUNTS_BUTTON);
		gameDirButton.setText(Lang.FOLDERS_BUTTON);
		updateAccountStatus();

		playButton.setText(Lang.WINDOW_PLAY);
		selectVersionButton.setText(Lang.WINDOW_SELECT_VERSION);
		settingsButton.setText(Lang.WINDOW_OPTIONS);
		langButton.setText(Lang.WINDOW_LANGUAGE);
		tabchangelog.setText(Lang.TAB_CHANGELOG);
		tabinstances.setText(Lang.TAB_INSTANCES);
		tabservers.setText(Lang.TAB_SERVERS);
		tabmyservers.setText(Lang.SERVERS_MINE);

		positionButtons();
		if (mainWindow.isVisible())
			this.pack();
	}

	public static void continueMSA(MicrosoftAuth auth) {
		if (auth.authenticate()) {
			Util.saveAccounts();
		} else {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					JOptionPane.showMessageDialog(mainWindow, Lang.LOGIN_FAILED, Lang.LOGIN_MICROSOFT_ERROR, JOptionPane.ERROR_MESSAGE);
				}
			});
		}
		mainWindow.requestFocus();
	}

	/** Pops up the list of accounts under the accounts button */
	public static void showAccountsMenu() {
		buildAccountsMenu().show(accountsButton, 0, accountsButton.getHeight());
	}

	public static JPopupMenu buildAccountsMenu() {
		JPopupMenu menu = new JPopupMenu();
		Credentials current = Launcher.auth == null ? null : Launcher.auth.getCredentials();

		for (final Credentials c : Launcher.accounts.accounts) {
			if (c == null) continue;
			JRadioButtonMenuItem item = new JRadioButtonMenuItem(AccountManager.displayName(c), new javax.swing.ImageIcon(Heads.get(c, 16, null)));
			item.setSelected(AccountManager.sameAccount(c, current));
			if (AccountManager.isLicensed(c)) item.setForeground(AccountManager.LICENSED_MENU_COLOR);
			item.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					AccountManager.switchTo(c);
				}
			});
			menu.add(item);
		}
		menu.addSeparator();

		JMenuItem addMicrosoft = new JMenuItem(Lang.ACCOUNT_ADD_MICROSOFT);
		addMicrosoft.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				AccountManager.startMicrosoftLogin(mainWindow);
			}
		});
		menu.add(addMicrosoft);

		JMenuItem addOffline = new JMenuItem(Lang.ACCOUNT_ADD_OFFLINE);
		addOffline.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				AccountManager.addOffline(mainWindow);
			}
		});
		menu.add(addOffline);

		JMenuItem manage = new JMenuItem(Lang.ACCOUNT_MANAGE);
		manage.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (accountManager == null) new AccountManager();
				else accountManager.setVisible(true);
			}
		});
		menu.add(manage);

		return menu;
	}

	/** Shows that an account is being logged in to */
	public static void onAccountSwitching() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				nick_input.setEnabled(false);
				nick_input.setText(Lang.LOGGING_IN);
				accountsButton.setEnabled(false);
				playButton.setEnabled(false);
				if (accountManager != null) accountManager.refresh();
			}
		});
	}

	/** Refreshes everything that displays the current account */
	public static void onAccountChanged() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if (nick_input == null) return;
				boolean offline = Launcher.auth instanceof NoAuth;
				nick_input.setEnabled(false);
				nick_input.setText(Launcher.getNickname());
				nick_input.setEnabled(offline);
				accountsButton.setEnabled(true);
				playButton.setEnabled(true);
				updateAccountStatus();
				if (accountManager != null) accountManager.refresh();
			}
		});
	}

	public static void updateAccountStatus() {
		if (accountStatus == null || Launcher.auth == null || Launcher.auth.getCredentials() == null) return;
		Credentials c = Launcher.auth.getCredentials();
		boolean licensed = AccountManager.isLicensed(c);
		String status = String.format(licensed ? Lang.ACCOUNT_STATUS_LICENSED : Lang.ACCOUNT_STATUS_OFFLINE, c.username);
		if (!licensed && AccountManager.licenseLoginFailed) status += Lang.ACCOUNT_LICENSE_FAILED_SUFFIX;
		accountStatus.setText(status);
		accountStatus.setIcon(new javax.swing.ImageIcon(Heads.get(c, 16, new Runnable() {
			public void run() {
				updateAccountStatus();
			}
		})));
		accountStatus.setIconTextGap(5);
		accountStatus.setForeground(licensed ? AccountManager.LICENSED_COLOR : Color.LIGHT_GRAY);
	}

	public static void setTab(Tab tab) {

		if (tab == Tab.CHANGELOG) {
			// the news page ships with the launcher, so this is quick - and on the UI thread
			// it can't finish after another tab was picked and cover it
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					if (Window.centerPanel != null) mainWindow.getContentPane().remove(Window.centerPanel);
					centerPanel = new WebsitePanel().getUpdateNews(true);
					Window.tab = Tab.CHANGELOG;
					mainWindow.add(Window.centerPanel, BorderLayout.CENTER);
					mainWindow.setPreferredSize(mainWindow.getSize());
					if (mainWindow.isVisible()) mainWindow.pack();
					mainWindow.validate();
					mainWindow.repaint();
				}
			});
		} else if (tab == Tab.SERVER_LIST || tab == Tab.MY_SERVERS) {
			final Tab chosen = tab;
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					if (Window.centerPanel != null) mainWindow.getContentPane().remove(Window.centerPanel);
					centerPanel = new ServersPanel(chosen == Tab.MY_SERVERS);
					Window.tab = chosen;
					mainWindow.add(Window.centerPanel, BorderLayout.CENTER);
					mainWindow.setPreferredSize(mainWindow.getSize());
					if (mainWindow.isVisible()) mainWindow.pack();
					mainWindow.validate();
					mainWindow.repaint();
				}
			});
		} else if (tab == Tab.INSTANCES) {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					if (Window.centerPanel != null) mainWindow.getContentPane().remove(Window.centerPanel);
					centerPanel = new InstancesPanel();
					Window.tab = Tab.INSTANCES;
					mainWindow.add(Window.centerPanel, BorderLayout.CENTER);
					mainWindow.setPreferredSize(mainWindow.getSize());
					if (mainWindow.isVisible()) mainWindow.pack();
					mainWindow.validate();
					mainWindow.repaint();
				}
			});
		}
	}

	/** Re-reads the instances tab, if it's open */
	public static void refreshInstances() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if (centerPanel instanceof InstancesPanel) ((InstancesPanel) centerPanel).refresh();
			}
		});
	}

	/** Another instance got selected */
	public static void instanceSelected() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if (centerPanel instanceof InstancesPanel) ((InstancesPanel) centerPanel).selectionChanged();
			}
		});
	}

	public static void positionButtons() {
		// Find the most wide button and set its width to other buttons
		// Make copies to allow for estimating the preferred size on runtime
		JButton copyVersion = new JButton(selectVersionButton.getText());
		JButton copyLanguag = new JButton(langButton.getText());
		JButton copyInstanc = new JButton(settingsButton.getText());
		JButton copyGameDir = new JButton(gameDirButton.getText());
		JButton largest = copyVersion;
		if (largest.getPreferredSize().getWidth() < copyLanguag.getPreferredSize().getWidth()) {
			largest = copyLanguag;
		}
		if (largest.getPreferredSize().getWidth() < copyInstanc.getPreferredSize().getWidth()) {
			largest = copyInstanc;
		}
		if (largest.getPreferredSize().getWidth() < copyGameDir.getPreferredSize().getWidth()) {
			largest = copyGameDir;
		}
		selectVersionButton.setPreferredSize(largest.getPreferredSize());
		selectVersionButton.setSize(largest.getPreferredSize());
		langButton.setPreferredSize(largest.getPreferredSize());
		langButton.setSize(largest.getPreferredSize());
		settingsButton.setPreferredSize(largest.getPreferredSize());
		settingsButton.setSize(largest.getPreferredSize());
		gameDirButton.setPreferredSize(largest.getPreferredSize());
		gameDirButton.setSize(largest.getPreferredSize());

		JButton copyTabChan = new JButton(tabchangelog.getText());
		JButton copyTabInst = new JButton(tabinstances.getText());
		JButton copyTabServ = new JButton(tabservers.getText());
		JButton copyTabMine = new JButton(tabmyservers.getText());
		largest = copyTabServ;
		if (largest.getPreferredSize().getWidth() < copyTabMine.getPreferredSize().getWidth()) {
			largest = copyTabMine;
		}
		if (largest.getPreferredSize().getWidth() < copyTabInst.getPreferredSize().getWidth()) {
			largest = copyTabInst;
		}
		if (largest.getPreferredSize().getWidth() < copyTabChan.getPreferredSize().getWidth()) {
			largest = copyTabChan;
		}
		tabchangelog.setPreferredSize(largest.getPreferredSize());
		tabchangelog.setSize(largest.getPreferredSize());
		tabinstances.setPreferredSize(largest.getPreferredSize());
		tabinstances.setSize(largest.getPreferredSize());
		tabservers.setPreferredSize(largest.getPreferredSize());
		tabservers.setSize(largest.getPreferredSize());
		tabmyservers.setPreferredSize(largest.getPreferredSize());
		tabmyservers.setSize(largest.getPreferredSize());
	}

	public static void quit(boolean close) {
		if (mainWindow != null) mainWindow.setVisible(false);
		if (mainWindow != null) mainWindow.dispose();
		Util.saveAccounts();
		BC.SETTINGS.setProperty("tab", tab.name());
		BC.SETTINGS.flushToDisk();
		if (close) {
			for (Thread t : Launcher.totalThreads) {
				while (t.isAlive());
			}
			System.exit(0);
		}
	}

	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();

		// Initialize other windows if needed
		if (source == selectVersionButton && Release.versions.size() > 0) {
			if (versionsList == null) new SelectVersion();
			else versionsList.setVisible(true);
			SelectVersion.list.requestFocus();
		}
		if (source == settingsButton) {
			InstancesPanel.editInstance(Launcher.currentInstance);
		}
		if (source == langButton) {
			if (lang == null) new Lang();
			else lang.setVisible(true);
		}
		if (source == gameDirButton) {
			buildFoldersMenu().show(gameDirButton, 0, gameDirButton.getHeight());
		}

		if (source == playButton) {
			Util.saveAccounts();
			playButton.setEnabled(false);
			try {
				new Thread() {
					public void run() {
						int last = Launcher.totalThreads.size();
						while (last > 0) {
							last = Launcher.totalThreads.size();
							try {
								// wtf is wrong with java
								Thread.sleep(1L);
							} catch (InterruptedException e) {}
						}
						setStatus(playButton, Lang.WINDOW_DOWNLOADING);

						boolean success = Launcher.initStartup();

						// Update the button state
						setStatus(playButton, Lang.WINDOW_PLAY);
						playButton.setEnabled(true);

						// Start the wrapper
						if (success) new Launcher().launchGame(Launcher.currentInstance);
					}
				}.start();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	public static String getWindowTitle() {
		String title = Lang.WINDOW_TITLE + (BC.nightly ? " [NIGHTLY]" : "");
		if (BC.isLinkedToBetaCraft()) title += Lang.FOLDER_LINKED_SUFFIX;
		else if (!BC.usesOwnDir()) title += " [" + BC.get() + "]";
		return title;
	}

	/** Folders menu: open the instance/launcher folder, or pick which data folder the launcher works in */
	public static JPopupMenu buildFoldersMenu() {
		JPopupMenu menu = new JPopupMenu();

		JMenuItem openInstance = new JMenuItem(Lang.OPEN_GAME_DIR);
		openInstance.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				openGameDir();
			}
		});
		menu.add(openInstance);

		JMenuItem openLauncher = new JMenuItem(Lang.FOLDER_OPEN_LAUNCHER);
		openLauncher.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				openFolder(new File(BC.get()));
			}
		});
		menu.add(openLauncher);
		menu.addSeparator();

		boolean own = BC.usesOwnDir();
		boolean betacraft = BC.isLinkedToBetaCraft();

		JRadioButtonMenuItem ownItem = new JRadioButtonMenuItem(Lang.FOLDER_DATA_OWN);
		ownItem.setSelected(own);
		ownItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				changeDataDir(null);
			}
		});
		menu.add(ownItem);

		JRadioButtonMenuItem betacraftItem = new JRadioButtonMenuItem(Lang.FOLDER_DATA_BETACRAFT);
		betacraftItem.setSelected(betacraft);
		betacraftItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (!BC.betaCraftInstalled()) {
					JOptionPane.showMessageDialog(mainWindow, String.format(Lang.FOLDER_BETACRAFT_MISSING, BC.getBetaCraftDir()), Lang.FOLDERS_BUTTON, JOptionPane.WARNING_MESSAGE);
					return;
				}
				changeDataDir(BC.getBetaCraftDir());
			}
		});
		menu.add(betacraftItem);

		JRadioButtonMenuItem customItem = new JRadioButtonMenuItem(Lang.FOLDER_DATA_CUSTOM);
		customItem.setSelected(!own && !betacraft);
		customItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JFileChooser chooser = new JFileChooser(BC.get());
				chooser.setDialogTitle(Lang.FOLDER_DATA_CHOOSE_TITLE);
				chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				if (chooser.showOpenDialog(mainWindow) == JFileChooser.APPROVE_OPTION) {
					changeDataDir(chooser.getSelectedFile().getAbsolutePath());
				}
			}
		});
		menu.add(customItem);

		return menu;
	}

	/** Switches the data folder (null = own one) and restarts the launcher */
	public static void changeDataDir(String path) {
		String target = path == null ? BC.getOwnDir() : path;
		if (BC.samePath(target, BC.get())) return;

		int res = JOptionPane.showConfirmDialog(mainWindow, String.format(Lang.FOLDER_DATA_RESTART, target), Lang.FOLDERS_BUTTON, JOptionPane.YES_NO_OPTION);
		if (res != JOptionPane.YES_OPTION) return;

		BC.setLinkedDir(path);
		Launcher.restart(Launcher.javaRuntime.getAbsolutePath());
	}

	public static void openGameDir() {
		openFolder(new File(Launcher.currentInstance.gameDir));
	}

	public static void openFolder(File folder) {
		File dir = folder.getAbsoluteFile();
		dir.mkdirs();
		try {
			if (OS.isWindows()) {
				// Explorer is more reliable here than Desktop.open
				new ProcessBuilder("explorer.exe", dir.getPath()).start();
			} else {
				java.awt.Desktop.getDesktop().open(dir);
			}
		} catch (Throwable t) {
			t.printStackTrace();
			new SimpleWebAddressFrame(dir.getAbsolutePath());
		}
	}

	public static void setTextInField(final JTextField field, final String toSet) {
		Runnable set = new Runnable() {
			public void run() {
				field.setText(toSet);
			}
		};
		SwingUtilities.invokeLater(set);
	}

	public static void setStatus(final JButton button, final String toSet) {
		Runnable set = new Runnable() {
			public void run() {
				button.setText(toSet);
			}
		};
		SwingUtilities.invokeLater(set);
	}

	public enum Tab {
		CHANGELOG,
		INSTANCES,
		SERVER_LIST,
		MY_SERVERS;
	}
}

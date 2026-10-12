package org.betacraft.launcher;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import org.betacraft.launcher.InstanceSettings.OptionsPanel;

import uk.betacraft.auth.Authenticator;
import uk.betacraft.auth.Credentials;
import uk.betacraft.auth.Credentials.AccountType;
import uk.betacraft.auth.NoAuth;
import uk.betacraft.auth.jsons.microsoft.DeviceCodeRequest;
import uk.betacraft.auth.jsons.microsoft.DeviceCodeResponse;

/** Window for managing several accounts: one or more licensed (Microsoft) and any number of offline ones */
public class AccountManager extends JFrame implements LanguageElement {
	public static final Color LICENSED_COLOR = new Color(0x55FF55);
	public static final Color OFFLINE_COLOR = new Color(0xAAAAAA);
	/** Darker green for light backgrounds, like menus */
	public static final Color LICENSED_MENU_COLOR = new Color(0x1E7A1E);
	private static final Pattern NICK_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,16}");

	/** Set while an account switch is running in the background */
	public static volatile boolean switching = false;

	JList list;
	DefaultListModel listModel;
	JButton selectButton, addOfflineButton, addMicrosoftButton, renameButton, removeButton, closeButton;
	JLabel hint;

	public AccountManager() {
		System.out.println("Account manager opened.");
		Window.accountManager = this;
		this.setIconImage(Window.img);
		this.setTitle(Lang.ACCOUNTS_TITLE);
		this.setMinimumSize(new Dimension(420, 340));
		this.setResizable(true);
		this.setLayout(new BorderLayout());

		JPanel panel = new OptionsPanel();
		panel.setLayout(new GridBagLayout());

		GridBagConstraints constr = new GridBagConstraints();
		constr.gridx = 0;
		constr.gridy = 0;
		constr.gridheight = 6;
		constr.weightx = 1.0;
		constr.weighty = 1.0;
		constr.fill = GridBagConstraints.BOTH;
		constr.insets = new Insets(10, 10, 4, 6);

		listModel = new DefaultListModel();
		list = new JList(listModel);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setBackground(Color.BLACK);
		list.setCellRenderer(new AccountRenderer());
		list.setVisibleRowCount(8);
		list.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) selectChosen();
			}
		});
		JScrollPane scroller = new JScrollPane(list);
		scroller.setWheelScrollingEnabled(true);
		panel.add(scroller, constr);

		constr.gridx = 1;
		constr.gridheight = 1;
		constr.weightx = 0.0;
		constr.weighty = 0.0;
		constr.fill = GridBagConstraints.HORIZONTAL;
		constr.anchor = GridBagConstraints.NORTH;
		constr.insets = new Insets(10, 0, 2, 10);

		selectButton = new JButton(Lang.SELECT);
		selectButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				selectChosen();
			}
		});
		panel.add(selectButton, constr);

		constr.gridy++;
		constr.insets = new Insets(8, 0, 2, 10);
		addMicrosoftButton = new JButton(Lang.ACCOUNT_ADD_MICROSOFT_SHORT);
		addMicrosoftButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				startMicrosoftLogin(AccountManager.this);
			}
		});
		panel.add(addMicrosoftButton, constr);

		constr.gridy++;
		constr.insets = new Insets(0, 0, 2, 10);
		addOfflineButton = new JButton(Lang.ACCOUNT_ADD_OFFLINE_SHORT);
		addOfflineButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				addOffline(AccountManager.this);
			}
		});
		panel.add(addOfflineButton, constr);

		constr.gridy++;
		constr.insets = new Insets(8, 0, 2, 10);
		renameButton = new JButton(Lang.ACCOUNT_RENAME);
		renameButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Credentials c = getChosen();
				if (c != null) rename(AccountManager.this, c);
			}
		});
		panel.add(renameButton, constr);

		constr.gridy++;
		constr.insets = new Insets(0, 0, 2, 10);
		removeButton = new JButton(Lang.REMOVE);
		removeButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Credentials c = getChosen();
				if (c != null) remove(AccountManager.this, c);
			}
		});
		panel.add(removeButton, constr);

		// filler pushing the buttons up
		constr.gridy++;
		constr.weighty = 1.0;
		panel.add(new JLabel(), constr);

		constr.gridx = 0;
		constr.gridy = 6;
		constr.gridwidth = 2;
		constr.weighty = 0.0;
		constr.insets = new Insets(0, 10, 10, 10);
		hint = new JLabel(Lang.ACCOUNTS_HINT);
		hint.setForeground(Color.LIGHT_GRAY);
		panel.add(hint, constr);

		JPanel bottom = new JPanel();
		closeButton = new JButton(Lang.CLOSE);
		closeButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				close();
			}
		});
		bottom.add(closeButton);

		this.add(panel, BorderLayout.CENTER);
		this.add(bottom, BorderLayout.SOUTH);

		this.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent windowEvent) {
				close();
			}
		});

		refresh();
		this.pack();
		this.setLocationRelativeTo(Window.mainWindow);
		this.setVisible(true);
	}

	public void close() {
		Window.accountManager = null;
		dispose();
	}

	public void update() {
		this.setTitle(Lang.ACCOUNTS_TITLE);
		selectButton.setText(Lang.SELECT);
		addMicrosoftButton.setText(Lang.ACCOUNT_ADD_MICROSOFT_SHORT);
		addOfflineButton.setText(Lang.ACCOUNT_ADD_OFFLINE_SHORT);
		renameButton.setText(Lang.ACCOUNT_RENAME);
		removeButton.setText(Lang.REMOVE);
		closeButton.setText(Lang.CLOSE);
		hint.setText(Lang.ACCOUNTS_HINT);
		refresh();
		this.pack();
	}

	/** Rebuilds the list, keeping the selection where possible */
	public void refresh() {
		Credentials chosen = getChosen();
		listModel.clear();
		if (Launcher.accounts.accounts == null) return;

		int select = -1;
		Credentials current = Launcher.auth == null ? null : Launcher.auth.getCredentials();
		for (Credentials c : Launcher.accounts.accounts) {
			if (c == null) continue;
			listModel.addElement(c);
			if (c == chosen || (chosen == null && current != null && sameAccount(c, current))) {
				select = listModel.size() - 1;
			}
		}
		if (select == -1 && listModel.size() > 0) select = 0;
		list.setSelectedIndex(select);

		boolean enabled = !switching;
		selectButton.setEnabled(enabled);
		removeButton.setEnabled(enabled);
		renameButton.setEnabled(enabled);
	}

	public Credentials getChosen() {
		if (list == null) return null;
		return (Credentials) list.getSelectedValue();
	}

	private void selectChosen() {
		Credentials c = getChosen();
		if (c != null) switchTo(c);
	}

	public static boolean sameAccount(Credentials a, Credentials b) {
		if (a == b) return true;
		if (a == null || b == null || a.local_uuid == null) return false;
		return a.local_uuid.equals(b.local_uuid);
	}

	public static boolean isLicensed(Credentials c) {
		return c != null && c.account_type == AccountType.MICROSOFT;
	}

	public static String typeName(Credentials c) {
		return isLicensed(c) ? Lang.ACCOUNT_LICENSED : Lang.ACCOUNT_OFFLINE;
	}

	public static String displayName(Credentials c) {
		String name = c.username == null || c.username.length() == 0 ? "?" : c.username;
		return name + "  [" + typeName(c) + "]";
	}

	/** Switches the active account. Licensed accounts are refreshed in the background. */
	public static void switchTo(final Credentials c) {
		if (switching) return;
		if (Launcher.auth != null && sameAccount(c, Launcher.auth.getCredentials())) {
			Window.onAccountChanged();
			return;
		}

		licenseLoginFailed = false;
		if (!isLicensed(c)) {
			Launcher.auth = new NoAuth(c);
			Launcher.accounts.setCurrent(c);
			Util.saveAccounts();
			Launcher.auth.authenticate();
			return;
		}

		switching = true;
		Window.onAccountSwitching();
		new Thread("Account-Switch") {
			public void run() {
				Authenticator a = Util.getAuthenticator(c);
				boolean ok = false;
				try {
					ok = a.authenticate();
				} catch (Throwable t) {
					t.printStackTrace();
				}
				switching = false;

				if (ok) {
					Launcher.auth = a;
					Launcher.accounts.setCurrent(a.getCredentials());
					Util.saveAccounts();
				} else {
					final String name = c.username;
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							JOptionPane.showMessageDialog(Window.mainWindow, String.format(Lang.ACCOUNT_SWITCH_FAILED, name), Lang.LOGIN_MICROSOFT_ERROR, JOptionPane.WARNING_MESSAGE);
						}
					});
				}
				Window.onAccountChanged();
			}
		}.start();
	}

	/** True while the launcher plays offline only because logging in to the license failed */
	public static volatile boolean licenseLoginFailed = false;

	/** Called when the launcher couldn't log in to the saved account. The account is kept, an offline one is used instead. */
	public static void fallbackToOffline() {
		fallbackToOffline(false);
	}

	/**
	 * @param remember make the offline account the one picked on the next start. Not done after a failed
	 *                 license login, so the next start tries the license again instead of "forgetting" it.
	 */
	public static void fallbackToOffline(boolean remember) {
		Credentials off = Launcher.accounts.getFirstOffline();
		if (off == null) {
			off = new NoAuth(defaultNick()).getCredentials();
			Launcher.accounts.addAccount(off);
		}
		Launcher.auth = new NoAuth(off);
		if (remember || Launcher.accounts.getCurrent() == null) Launcher.accounts.setCurrent(off);
		licenseLoginFailed = !remember;
		Util.saveAccounts();
		Launcher.auth.authenticate();
	}

	public static String defaultNick() {
		String base = "Player";
		if (!Launcher.accounts.hasOfflineNamed(base, null)) return base;
		for (int i = 2; ; i++) {
			if (!Launcher.accounts.hasOfflineNamed(base + i, null)) return base + i;
		}
	}

	private static String askNick(Component parent, String title, String initial, Credentials except) {
		String nick = initial;
		while (true) {
			nick = (String) JOptionPane.showInputDialog(parent, Lang.ACCOUNT_NICKNAME, title, JOptionPane.PLAIN_MESSAGE, null, null, nick);
			if (nick == null) return null;
			nick = nick.trim();
			if (!NICK_PATTERN.matcher(nick).matches()) {
				JOptionPane.showMessageDialog(parent, Lang.ACCOUNT_NICK_INVALID, title, JOptionPane.WARNING_MESSAGE);
			} else if (Launcher.accounts.hasOfflineNamed(nick, except)) {
				JOptionPane.showMessageDialog(parent, Lang.ACCOUNT_NICK_TAKEN, title, JOptionPane.WARNING_MESSAGE);
			} else {
				return nick;
			}
		}
	}

	public static void addOffline(Component parent) {
		String nick = askNick(parent, Lang.ACCOUNT_ADD_OFFLINE_SHORT, defaultNick(), null);
		if (nick == null) return;

		Credentials c = new NoAuth(nick).getCredentials();
		Launcher.accounts.addAccount(c);
		Util.saveAccounts();
		switchTo(c);
	}

	public static void startMicrosoftLogin(final Component parent) {
		new Thread("MSA-Device-Code") {
			public void run() {
				final DeviceCodeResponse dcr = new DeviceCodeRequest().perform();
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						if (dcr == null || dcr.user_code == null) {
							JOptionPane.showMessageDialog(parent, Lang.ERR_NO_CONNECTION, Lang.LOGIN_MICROSOFT_ERROR, JOptionPane.ERROR_MESSAGE);
							return;
						}
						new AwaitingMSALogin(dcr.verification_uri, dcr.user_code, dcr.device_code, dcr.expires_in, dcr.interval);
					}
				});
			}
		}.start();
	}

	public static void rename(Component parent, Credentials c) {
		if (isLicensed(c)) {
			JOptionPane.showMessageDialog(parent, Lang.ACCOUNT_RENAME_LICENSED, Lang.ACCOUNT_RENAME, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String nick = askNick(parent, Lang.ACCOUNT_RENAME, c.username, c);
		if (nick == null) return;

		c.username = nick;
		Util.saveAccounts();
		Window.onAccountChanged();
	}

	public static void remove(Component parent, Credentials c) {
		int res = JOptionPane.showConfirmDialog(parent, String.format(Lang.ACCOUNT_REMOVE_QUESTION, displayName(c)), Lang.REMOVE, JOptionPane.YES_NO_OPTION);
		if (res != JOptionPane.YES_OPTION) return;

		boolean wasCurrent = Launcher.auth != null && sameAccount(c, Launcher.auth.getCredentials());
		Launcher.accounts.removeAccount(c);

		if (wasCurrent) {
			// Offline accounts switch instantly and can't fail, so never end up on the removed account
			fallbackToOffline(true);
		}
		Util.saveAccounts();
		Window.onAccountChanged();
	}

	static class AccountRenderer extends DefaultListCellRenderer {
		public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
			Credentials c = (Credentials) value;
			boolean current = Launcher.auth != null && sameAccount(c, Launcher.auth.getCredentials());
			String text = (current ? "» " : "") + displayName(c);

			JLabel label = (JLabel) super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
			label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
			final JList owner = list;
			label.setIcon(new javax.swing.ImageIcon(Heads.get(c, 24, new Runnable() {
				public void run() {
					owner.repaint();
				}
			})));
			label.setIconTextGap(8);
			label.setFont(label.getFont().deriveFont(current ? Font.BOLD : Font.PLAIN));
			if (!isSelected) {
				label.setBackground(Color.BLACK);
				label.setForeground(isLicensed(c) ? LICENSED_COLOR : OFFLINE_COLOR);
			}
			return label;
		}
	}
}

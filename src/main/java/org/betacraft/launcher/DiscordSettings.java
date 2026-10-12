package org.betacraft.launcher;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.betacraft.launcher.InstanceSettings.OptionsPanel;

/** What the Discord status shows while playing */
public class DiscordSettings extends JDialog {
	private final JCheckBox instance, server, players, nick;
	private final JLabel previewTitle, previewDetails, previewState;

	public static void show(Component parent) {
		DiscordSettings d = new DiscordSettings(parent == null ? null : SwingUtilities.getWindowAncestor(parent));
		d.setVisible(true);
	}

	private DiscordSettings(java.awt.Window owner) {
		super(owner, Lang.DISCORD_SETTINGS_TITLE, ModalityType.APPLICATION_MODAL);
		this.setIconImage(Window.img);

		JPanel panel = new OptionsPanel();
		panel.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		c.insets = new Insets(8, 10, 0, 10);

		ActionListener refresh = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				updatePreview();
			}
		};
		instance = check(Lang.DISCORD_SHOW_INSTANCE, RichPresence.SHOW_INSTANCE, refresh);
		panel.add(instance, c);
		c.gridy++;
		c.insets = new Insets(2, 10, 0, 10);
		server = check(Lang.DISCORD_SHOW_SERVER, RichPresence.SHOW_SERVER, refresh);
		panel.add(server, c);
		c.gridy++;
		c.insets = new Insets(2, 30, 0, 10);
		players = check(Lang.DISCORD_SHOW_PLAYERS, RichPresence.SHOW_PLAYERS, refresh);
		panel.add(players, c);
		c.gridy++;
		c.insets = new Insets(2, 10, 0, 10);
		nick = check(Lang.DISCORD_SHOW_NICK, RichPresence.SHOW_NICK, refresh);
		panel.add(nick, c);

		// a little mock of Discord's "Playing" card
		JPanel preview = new JPanel(new BorderLayout());
		preview.setBackground(new Color(0x2B2D31));
		preview.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
		previewTitle = new JLabel();
		previewTitle.setForeground(Color.WHITE);
		previewTitle.setFont(previewTitle.getFont().deriveFont(Font.BOLD));
		previewDetails = new JLabel();
		previewDetails.setForeground(new Color(0xDBDEE1));
		previewState = new JLabel();
		previewState.setForeground(new Color(0xDBDEE1));
		JPanel lines = new JPanel(new java.awt.GridLayout(3, 1));
		lines.setOpaque(false);
		lines.add(previewTitle);
		lines.add(previewDetails);
		lines.add(previewState);
		preview.add(lines, BorderLayout.CENTER);

		c.gridy++;
		c.insets = new Insets(12, 10, 10, 10);
		panel.add(preview, c);

		JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton ok = new JButton(Lang.OPTIONS_OK);
		ok.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				save();
				dispose();
			}
		});
		bottom.add(ok);
		JButton cancel = new JButton(Lang.CANCEL);
		cancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				dispose();
			}
		});
		bottom.add(cancel);

		this.setLayout(new BorderLayout());
		this.add(panel, BorderLayout.CENTER);
		this.add(bottom, BorderLayout.SOUTH);
		updatePreview();
		this.pack();
		this.setResizable(false);
		this.setLocationRelativeTo(owner);
	}

	private static JCheckBox check(String text, String key, ActionListener l) {
		JCheckBox b = new JCheckBox(text, RichPresence.flag(key));
		b.setOpaque(false);
		b.setForeground(Color.LIGHT_GRAY);
		b.addActionListener(l);
		return b;
	}

	private void updatePreview() {
		players.setEnabled(server.isSelected());
		Instance i = Launcher.currentInstance;
		String inst = i == null ? "My instance" : i.name;
		String ver = i == null ? "b1.7.3" : i.version;
		String user = Launcher.getNickname();

		previewTitle.setText(Lang.DISCORD_PREVIEW_PLAYING);
		previewDetails.setText(instance.isSelected() ? inst + " (" + ver + ")" : String.format(Lang.WRAP_VERSION, ver));
		String state;
		if (server.isSelected()) {
			state = String.format(Lang.RPC_ON_SERVER, "Modern Beta SMP") + (players.isSelected() ? "  (19 / 400)" : "");
		} else if (nick.isSelected()) {
			state = String.format(Lang.WRAP_USER, user);
		} else {
			state = Lang.RPC_MULTIPLAYER;
		}
		previewState.setText(state);
		pack();
	}

	private void save() {
		BC.SETTINGS.setProperty(RichPresence.SHOW_INSTANCE, Boolean.toString(instance.isSelected()));
		BC.SETTINGS.setProperty(RichPresence.SHOW_SERVER, Boolean.toString(server.isSelected()));
		BC.SETTINGS.setProperty(RichPresence.SHOW_PLAYERS, Boolean.toString(players.isSelected()));
		BC.SETTINGS.setProperty(RichPresence.SHOW_NICK, Boolean.toString(nick.isSelected()));
		BC.SETTINGS.flushToDisk();
	}
}

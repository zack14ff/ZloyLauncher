package org.betacraft.launcher;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import org.betacraft.launcher.InstanceSettings.OptionsPanel;
import org.betacraft.launcher.SavedServers.Server;

/** "My servers" tab (the user's own list, like the old Multiplayer screen) and the "Servers" tab (BetaCraft's public list) */
public class ServersPanel extends JPanel {
	private static BufferedImage background, packIcon;

	private final DefaultListModel model = new DefaultListModel();
	private final JList list;
	private final JLabel status;

	public ServersPanel(boolean mineTab) {
		super(new BorderLayout());
		try {
			if (background == null) background = ImageIO.read(ServersPanel.class.getResourceAsStream("/icons/news_bg.png"));
			if (packIcon == null) packIcon = Instance.scaleIcon(ImageIO.read(ServersPanel.class.getResourceAsStream("/icons/server_pack.png")), 32);
		} catch (Throwable t) {
			t.printStackTrace();
		}
		SavedServers.load();

		JPanel mine = new JPanel(new BorderLayout());
		mine.setOpaque(false);

		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		JLabel title = new JLabel(Lang.SERVERS_MINE);
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
		title.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
		header.add(title, BorderLayout.WEST);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 3));
		buttons.setOpaque(false);
		buttons.add(button(Lang.SERVERS_JOIN, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Server s = selected();
				if (s != null) join(s, ServersPanel.this);
			}
		}));
		buttons.add(button(Lang.SERVERS_ADD, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Server s = ServerEditor.edit(ServersPanel.this, null);
				if (s != null) {
					SavedServers.servers.add(s);
					SavedServers.save();
					refresh();
				}
			}
		}));
		buttons.add(button(Lang.SERVERS_EDIT, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				editSelected();
			}
		}));
		buttons.add(button(Lang.REMOVE, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				removeSelected();
			}
		}));
		header.add(buttons, BorderLayout.EAST);
		mine.add(header, BorderLayout.NORTH);

		list = new JList(model);
		list.setOpaque(false);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new ServerRenderer());
		list.setVisibleRowCount(6);
		list.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				int index = list.locationToIndex(e.getPoint());
				boolean onItem = index >= 0 && list.getCellBounds(index, index).contains(e.getPoint());
				if (onItem) list.setSelectedIndex(index);

				if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
					if (onItem) showMenu(e.getX(), e.getY());
				} else if (e.getClickCount() == 2 && onItem) {
					join(selected(), ServersPanel.this);
				}
			}
		});
		JScrollPane scroller = new JScrollPane(list);
		scroller.setOpaque(false);
		scroller.getViewport().setOpaque(false);
		scroller.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(0x404040)));
		mine.add(scroller, BorderLayout.CENTER);

		status = new JLabel(" ");
		status.setForeground(Color.LIGHT_GRAY);
		status.setBorder(BorderFactory.createEmptyBorder(1, 8, 1, 8));
		mine.add(status, BorderLayout.SOUTH);
		if (mineTab) this.add(mine, BorderLayout.CENTER);

		// BetaCraft's public server list, as before
		JPanel publicList = new JPanel(new BorderLayout());
		publicList.setOpaque(false);
		JLabel publicTitle = new JLabel(Lang.SERVERS_PUBLIC);
		publicTitle.setForeground(Color.WHITE);
		publicTitle.setFont(publicTitle.getFont().deriveFont(Font.BOLD, 13f));
		publicTitle.setBorder(BorderFactory.createEmptyBorder(4, 8, 2, 0));
		publicList.add(publicTitle, BorderLayout.NORTH);
		JLabel publicHint = new JLabel(Lang.SERVERS_PUBLIC_HINT);
		publicHint.setForeground(Color.GRAY);
		publicHint.setBorder(BorderFactory.createEmptyBorder(2, 8, 3, 8));
		publicList.add(publicHint, BorderLayout.SOUTH);
		if (!mineTab) {
			publicList.add(new WebsitePanel().getServers(true), BorderLayout.CENTER);
			this.add(publicList, BorderLayout.CENTER);
		}

		refresh();
	}

	protected void paintComponent(Graphics g) {
		if (background == null) {
			g.setColor(new Color(0x202020));
			g.fillRect(0, 0, getWidth(), getHeight());
			return;
		}
		for (int x = 0; x < getWidth(); x += background.getWidth())
			for (int y = 0; y < getHeight(); y += background.getHeight())
				g.drawImage(background, x, y, null);
	}

	private static JButton button(String text, ActionListener l) {
		JButton b = new JButton(text);
		b.setFocusable(false);
		b.addActionListener(l);
		return b;
	}

	public void refresh() {
		Server sel = selected();
		model.clear();
		for (Server s : SavedServers.servers) model.addElement(s);
		if (sel != null && model.contains(sel)) list.setSelectedValue(sel, true);
		else if (!model.isEmpty()) list.setSelectedIndex(0);
		status.setText(model.isEmpty() ? Lang.SERVERS_EMPTY : " ");
	}

	Server selected() {
		return (Server) list.getSelectedValue();
	}

	private void editSelected() {
		Server s = selected();
		if (s == null) return;
		if (ServerEditor.edit(this, s) != null) {
			SavedServers.save();
			refresh();
		}
	}

	private void removeSelected() {
		Server s = selected();
		if (s == null) return;
		int res = JOptionPane.showConfirmDialog(this, String.format(Lang.SERVERS_REMOVE_QUESTION, s.name), Lang.REMOVE, JOptionPane.YES_NO_OPTION);
		if (res != JOptionPane.YES_OPTION) return;
		SavedServers.servers.remove(s);
		SavedServers.save();
		refresh();
	}

	private void showMenu(int x, int y) {
		final Server s = selected();
		if (s == null) return;
		JPopupMenu menu = new JPopupMenu();
		JMenuItem join = new JMenuItem(Lang.SERVERS_JOIN);
		join.setFont(join.getFont().deriveFont(Font.BOLD));
		join.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				join(s, ServersPanel.this);
			}
		});
		menu.add(join);
		JMenuItem redetect = new JMenuItem(Lang.SERVERS_REDETECT);
		redetect.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				s.detectedVersion = null;
				s.detectedHow = null;
				detectAsync(s, false, ServersPanel.this);
			}
		});
		menu.add(redetect);
		menu.addSeparator();
		JMenuItem edit = new JMenuItem(Lang.SERVERS_EDIT);
		edit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				editSelected();
			}
		});
		menu.add(edit);
		JMenuItem remove = new JMenuItem(Lang.REMOVE);
		remove.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				removeSelected();
			}
		});
		menu.add(remove);
		menu.show(list, x, y);
	}

	// ---- joining ----

	/** Joins the server on the current instance, detecting the version first if it's set to auto */
	public static void join(Server s, Component parent) {
		if (s == null) return;
		String version = s.effectiveVersion();
		if (version == null) {
			detectAsync(s, true, parent);
			return;
		}
		s.lastJoined = System.currentTimeMillis();
		SavedServers.save();
		Launcher.launchOnServer(version, s.fullAddress(), "-");
	}

	private static void detectAsync(final Server s, final boolean joinAfter, final Component parent) {
		setStatus(String.format(Lang.DETECT_RUNNING, s.address));
		Window.playButton.setEnabled(false);
		new Thread("Server-Detect") {
			public void run() {
				final ServerVersionDetector.Result r = ServerVersionDetector.detect(s.address);
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						Window.playButton.setEnabled(true);
						if (r.version == null) {
							setStatus(r.how);
							JOptionPane.showMessageDialog(parent, r.how + "\n" + Lang.DETECT_CHOOSE_MANUALLY, s.name, JOptionPane.WARNING_MESSAGE);
							if (ServerEditor.edit(parent, s) != null) {
								SavedServers.save();
								refreshAll();
								if (joinAfter && s.effectiveVersion() != null) ServersPanel.join(s, parent);
							}
							return;
						}
						s.detectedVersion = r.version;
						s.detectedHow = r.how;
						SavedServers.save();
						setStatus(r.how);
						refreshAll();
						if (joinAfter) ServersPanel.join(s, parent);
					}
				});
			}
		}.start();
	}

	static void setStatus(String text) {
		if (Window.centerPanel instanceof ServersPanel) ((ServersPanel) Window.centerPanel).status.setText(text);
	}

	static void refreshAll() {
		if (Window.centerPanel instanceof ServersPanel) ((ServersPanel) Window.centerPanel).refresh();
	}

	/** Saves a server from BetaCraft's public list into the user's list */
	public static void saveFromPublicList(String address, String version) {
		Server s = SavedServers.find(address);
		if (s == null) {
			s = new Server(SavedServers.host(address), address, SavedServers.AUTO);
			SavedServers.servers.add(s);
		}
		if (version != null) {
			s.detectedVersion = version;
			s.detectedHow = String.format(Lang.DETECT_BY_LIST, version);
		}
		SavedServers.save();
		refreshAll();
	}

	// ---- looks ----

	static class ServerRenderer extends JComponent implements ListCellRenderer {
		static final int HEIGHT = 44;
		Server server;
		boolean selected;

		ServerRenderer() {
			setPreferredSize(new Dimension(300, HEIGHT));
		}

		public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
			this.server = (Server) value;
			this.selected = isSelected;
			return this;
		}

		protected void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0;
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			if (selected) {
				// the old Multiplayer screen outlined the selected server
				g.setColor(new Color(0, 0, 0, 120));
				g.fillRect(2, 1, getWidth() - 4, HEIGHT - 2);
				g.setColor(new Color(0x808080));
				g.drawRect(2, 1, getWidth() - 5, HEIGHT - 3);
			}
			if (packIcon != null) g.drawImage(packIcon, 8, (HEIGHT - 32) / 2, null);

			int x = 50;
			g.setFont(getFont().deriveFont(Font.BOLD, 12f));
			g.setColor(Color.WHITE);
			g.drawString(server.name == null || server.name.length() == 0 ? server.address : server.name, x, 17);

			g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
			g.setColor(new Color(0x808080));
			g.drawString(server.address, x, 31);

			String version;
			if (!server.isAuto()) version = server.version;
			else if (server.detectedVersion != null) version = String.format(Lang.SERVERS_AUTO_DETECTED, server.detectedVersion);
			else version = Lang.SERVERS_AUTO;
			int w = g.getFontMetrics().stringWidth(version);
			g.setColor(server.isAuto() ? new Color(0x9C9CFF) : new Color(0xA0A0A0));
			g.drawString(version, getWidth() - w - 10, 24);
		}
	}

	// ---- add / edit dialog ----

	static class ServerEditor extends JDialog {
		private final JTextField name, address;
		private final JComboBox version;
		private final JLabel detected;
		private Server result = null;
		private String detectedVersion, detectedHow;

		/** Edits the server (or a new one when null), returns it or null if cancelled */
		static Server edit(Component parent, Server server) {
			ServerEditor e = new ServerEditor(parent == null ? null : SwingUtilities.getWindowAncestor(parent), server);
			e.setVisible(true);
			return e.result;
		}

		private ServerEditor(java.awt.Window owner, final Server server) {
			super(owner, server == null ? Lang.SERVERS_ADD_TITLE : Lang.SERVERS_EDIT_TITLE, ModalityType.APPLICATION_MODAL);
			this.setIconImage(Window.img);

			JPanel panel = new OptionsPanel();
			panel.setLayout(new GridBagLayout());
			GridBagConstraints c = new GridBagConstraints();
			c.insets = new Insets(6, 10, 2, 10);
			c.fill = GridBagConstraints.HORIZONTAL;
			c.gridx = 0;
			c.gridy = 0;

			panel.add(label(Lang.SERVERS_NAME), c);
			c.gridx = 1;
			c.weightx = 1.0;
			name = new JTextField(server == null ? "" : server.name, 24);
			panel.add(name, c);

			c.gridx = 0;
			c.gridy++;
			c.weightx = 0.0;
			panel.add(label(Lang.SERVERS_ADDRESS), c);
			c.gridx = 1;
			address = new JTextField(server == null ? "" : server.address, 24);
			panel.add(address, c);

			c.gridx = 0;
			c.gridy++;
			panel.add(label(Lang.SERVERS_VERSION), c);
			c.gridx = 1;
			version = new JComboBox(versionChoices());
			version.setMaximumRowCount(20);
			String current = server == null ? SavedServers.AUTO : server.version;
			version.setSelectedItem(SavedServers.AUTO.equals(current) ? Lang.SERVERS_AUTO : current);
			panel.add(version, c);

			c.gridx = 1;
			c.gridy++;
			JPanel detectRow = new JPanel(new BorderLayout(6, 0));
			detectRow.setOpaque(false);
			JButton detect = new JButton(Lang.SERVERS_DETECT_NOW);
			detect.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					detectNow();
				}
			});
			detectRow.add(detect, BorderLayout.WEST);
			// detectedVersion/How only hold a detection made in this dialog, the label shows the last known one
			detected = label(server == null || server.detectedHow == null ? " " : server.detectedHow);
			detectRow.add(detected, BorderLayout.CENTER);
			panel.add(detectRow, c);

			JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
			JButton ok = new JButton(Lang.OPTIONS_OK);
			ok.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					save(server);
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
			this.getRootPane().setDefaultButton(ok);
			this.pack();
			this.setResizable(false);
			this.setLocationRelativeTo(owner);
		}

		private static JLabel label(String text) {
			JLabel l = new JLabel(text);
			l.setForeground(Color.LIGHT_GRAY);
			return l;
		}

		/** "Auto" first, then the custom/modded versions (they're usually what a server needs), then vanilla */
		static Object[] versionChoices() {
			List<String> custom = new ArrayList<String>();
			List<String> vanilla = new ArrayList<String>();
			for (Release r : Release.versions) {
				boolean isCustom = r.getInfo() != null && (r.getInfo().isCustom() || ModsRepository.getMod(r.getName()) != null);
				(isCustom ? custom : vanilla).add(r.getName());
			}
			List<Object> all = new ArrayList<Object>();
			all.add(Lang.SERVERS_AUTO);
			all.addAll(custom);
			all.addAll(vanilla);
			return all.toArray();
		}

		private void detectNow() {
			final String addr = address.getText().trim();
			if (addr.length() == 0) return;
			detected.setText(String.format(Lang.DETECT_RUNNING, addr));
			new Thread("Server-Detect") {
				public void run() {
					final ServerVersionDetector.Result r = ServerVersionDetector.detect(addr);
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							detected.setText(r.how);
							if (r.version != null) {
								detectedVersion = r.version;
								detectedHow = r.how;
							}
							pack();
						}
					});
				}
			}.start();
		}

		private void save(Server server) {
			String addr = address.getText().trim();
			if (addr.length() == 0) {
				JOptionPane.showMessageDialog(this, Lang.SERVERS_NO_ADDRESS, getTitle(), JOptionPane.WARNING_MESSAGE);
				return;
			}
			Server s = server == null ? new Server() : server;
			boolean addressChanged = s.address != null && !SavedServers.sameAddress(s.address, addr);
			s.address = addr;
			s.name = name.getText().trim().length() > 0 ? name.getText().trim() : SavedServers.host(addr);
			Object v = version.getSelectedItem();
			s.version = (v == null || Lang.SERVERS_AUTO.equals(v)) ? SavedServers.AUTO : v.toString();
			if (addressChanged) {
				s.detectedVersion = null;
				s.detectedHow = null;
			}
			if (detectedVersion != null) {
				s.detectedVersion = detectedVersion;
				s.detectedHow = detectedHow;
			}
			result = s;
			dispose();
		}
	}
}
